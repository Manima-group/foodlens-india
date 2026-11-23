"""Missing-product contributions (SPEC Tier 2 intake): create → upload photos → complete → confirm.

OCR extraction and the admin review queue are not implemented yet (docs/REPORT.md); confirmed
contributions wait in state `submitted` and never become public without human moderation.
"""

import hashlib
import json
import secrets
import uuid
from datetime import UTC, datetime, timedelta
from typing import Annotated, Any, Literal

from fastapi import APIRouter, Depends, Header, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.guest import require_guest, sha256
from app.core.config import get_settings
from app.core.db import get_session
from app.core.errors import ApiError, ErrorCode
from app.gtin import Valid, normalize
from app.integrations.storage import LocalStorage, magic_ok
from app.models import CatalogContribution, ContributionPhoto, GuestDevice, IdempotencyKey

router = APIRouter(tags=["contributions"])
Session = Annotated[AsyncSession, Depends(get_session)]
Guest = Annotated[GuestDevice, Depends(require_guest)]
MAX_PER_DAY = 10  # SPEC §14 contributions 10/day/device
PhotoKind = Literal["front", "back", "ingredients", "nutrition", "barcode", "other"]


def storage() -> LocalStorage:
    return LocalStorage(get_settings().upload_dir)


class ContributionIn(BaseModel):
    gtin14: str = Field(min_length=8, max_length=14)
    product_name: str | None = Field(default=None, max_length=300)
    brand_name: str | None = Field(default=None, max_length=200)
    notes: str | None = Field(default=None, max_length=2000)


class PhotoOut(BaseModel):
    id: str
    kind: str
    state: str


class ContributionOut(BaseModel):
    id: str
    gtin14: str
    product_name: str | None
    brand_name: str | None
    state: str
    photos: list[PhotoOut]
    created_at: datetime


class PhotoIn(BaseModel):
    kind: PhotoKind
    content_type: Literal["image/jpeg", "image/png", "image/webp"]
    size_bytes: int = Field(gt=0)


class PhotoUploadOut(BaseModel):
    photo_id: str
    upload_url: str
    upload_method: str = "PUT"
    upload_headers: dict[str, str]
    expires_at: datetime


async def _own(session: AsyncSession, guest: GuestDevice, contribution_id: str) -> CatalogContribution:
    try:
        cid = uuid.UUID(contribution_id)
    except ValueError as exc:
        raise ApiError(ErrorCode.NOT_FOUND) from exc
    c = await session.get(CatalogContribution, cid)
    if c is None or c.guest_device_id != guest.id:  # object-level check: 404, not 403, to avoid probing
        raise ApiError(ErrorCode.NOT_FOUND, "Contribution not found.")
    return c


async def _out(session: AsyncSession, c: CatalogContribution) -> ContributionOut:
    photos = (
        await session.scalars(select(ContributionPhoto).where(ContributionPhoto.contribution_id == c.id))
    ).all()
    return ContributionOut(
        id=str(c.id),
        gtin14=c.gtin14,
        product_name=c.product_name,
        brand_name=c.brand_name,
        state=c.state,
        photos=[PhotoOut(id=str(p.id), kind=p.kind, state=p.state) for p in photos],
        created_at=c.created_at,
    )


@router.post("/contributions", status_code=201, response_model=ContributionOut)
async def create(body: ContributionIn, session: Session, guest: Guest) -> ContributionOut:
    g = normalize(body.gtin14)
    if not isinstance(g, Valid):
        raise ApiError(ErrorCode.INVALID_BARCODE, details={"gtin14": body.gtin14})
    since = datetime.now(UTC) - timedelta(days=1)
    today = await session.scalar(
        select(func.count())
        .select_from(CatalogContribution)
        .where(CatalogContribution.guest_device_id == guest.id, CatalogContribution.created_at >= since)
    )
    if (today or 0) >= MAX_PER_DAY:
        raise ApiError(ErrorCode.RATE_LIMITED, details={"retry_after_seconds": 86400})
    c = CatalogContribution(
        guest_device_id=guest.id,
        gtin14=g.gtin14,
        product_name=body.product_name,
        brand_name=body.brand_name,
        notes=body.notes,
        state="draft",
    )
    session.add(c)
    await session.commit()
    await session.refresh(c)
    return await _out(session, c)


@router.get("/me/contributions", response_model=list[ContributionOut])
async def mine(session: Session, guest: Guest) -> list[ContributionOut]:
    rows = (
        await session.scalars(
            select(CatalogContribution)
            .where(CatalogContribution.guest_device_id == guest.id)
            .order_by(CatalogContribution.created_at.desc())
            .limit(50)
        )
    ).all()
    return [await _out(session, c) for c in rows]


@router.post("/contributions/{contribution_id}/photos", status_code=201, response_model=PhotoUploadOut)
async def add_photo(contribution_id: str, body: PhotoIn, session: Session, guest: Guest) -> PhotoUploadOut:
    c = await _own(session, guest, contribution_id)
    settings = get_settings()
    if c.state not in ("draft", "photos_uploaded"):
        raise ApiError(ErrorCode.CONFLICT, "Contribution is already submitted.")
    if body.size_bytes > settings.upload_max_bytes:
        raise ApiError(
            ErrorCode.VALIDATION_ERROR, "Photo is too large.", {"max_bytes": settings.upload_max_bytes}
        )
    token = secrets.token_urlsafe(32)
    expires = datetime.now(UTC) + timedelta(seconds=settings.upload_url_ttl_seconds)
    ext = body.content_type.split("/")[1].replace("jpeg", "jpg")
    photo = ContributionPhoto(
        contribution_id=c.id,
        kind=body.kind,
        content_type=body.content_type,
        storage_key=f"contributions/{c.id}/{uuid.uuid4().hex}.{ext}",
        upload_token_hash=sha256(token),
        upload_expires_at=expires,
        size_bytes=body.size_bytes,
        state="pending",
    )
    session.add(photo)
    await session.commit()
    return PhotoUploadOut(
        photo_id=str(photo.id),
        upload_url=f"{settings.public_base_url.rstrip('/')}/api/v1/uploads/{token}",
        upload_headers={"Content-Type": body.content_type},
        expires_at=expires,
    )


@router.put("/uploads/{token}", status_code=204, include_in_schema=True)
async def upload(token: str, request: Request, session: Session) -> JSONResponse:
    """Local stand-in for an S3 presigned PUT: the token is the credential (15-min TTL, single use)."""
    photo = await session.scalar(
        select(ContributionPhoto).where(ContributionPhoto.upload_token_hash == sha256(token))
    )
    if photo is None or photo.state != "pending":
        raise ApiError(ErrorCode.NOT_FOUND, "Upload URL is invalid or already used.")
    if photo.upload_expires_at < datetime.now(UTC):
        raise ApiError(ErrorCode.FORBIDDEN, "Upload URL has expired.")
    if request.headers.get("content-type", "").split(";")[0].strip() != photo.content_type:
        raise ApiError(ErrorCode.VALIDATION_ERROR, "Content-Type does not match the requested type.")
    data = await request.body()
    limit = min(photo.size_bytes or 0, get_settings().upload_max_bytes)
    if not data or len(data) > limit:
        raise ApiError(ErrorCode.VALIDATION_ERROR, "Upload size does not match.", {"max_bytes": limit})
    if not magic_ok(photo.content_type, data[:16]):
        photo.state = "rejected"
        await session.commit()
        raise ApiError(ErrorCode.VALIDATION_ERROR, "File content is not a valid image of the declared type.")
    storage().put(photo.storage_key, data)
    photo.state = "uploaded"
    photo.size_bytes = len(data)
    await session.commit()
    return JSONResponse(status_code=204, content=None)


@router.post("/contributions/{contribution_id}/photos/{photo_id}/complete", response_model=ContributionOut)
async def complete_photo(
    contribution_id: str, photo_id: str, session: Session, guest: Guest
) -> ContributionOut:
    c = await _own(session, guest, contribution_id)
    try:
        pid = uuid.UUID(photo_id)
    except ValueError as exc:
        raise ApiError(ErrorCode.NOT_FOUND) from exc
    photo = await session.get(ContributionPhoto, pid)
    if photo is None or photo.contribution_id != c.id:
        raise ApiError(ErrorCode.NOT_FOUND, "Photo not found.")
    if photo.state != "uploaded" or not storage().exists(photo.storage_key):
        raise ApiError(ErrorCode.CONFLICT, "Photo has not been uploaded.")
    if c.state == "draft":
        c.state = "photos_uploaded"
    await session.commit()
    return await _out(session, c)


@router.post("/contributions/{contribution_id}/confirm", response_model=ContributionOut)
async def confirm(
    contribution_id: str,
    session: Session,
    guest: Guest,
    idempotency_key: Annotated[str | None, Header(max_length=200)] = None,
) -> Any:
    if not idempotency_key:
        raise ApiError(ErrorCode.VALIDATION_ERROR, "Idempotency-Key header is required.")
    scope = f"contribution_confirm:{guest.id}"
    request_hash = hashlib.sha256(contribution_id.encode()).hexdigest()
    existing = await session.scalar(
        select(IdempotencyKey).where(IdempotencyKey.key == idempotency_key, IdempotencyKey.scope == scope)
    )
    if existing is not None:
        if existing.request_hash != request_hash:
            raise ApiError(ErrorCode.IDEMPOTENCY_CONFLICT)
        return JSONResponse(existing.response, status_code=existing.status_code)
    c = await _own(session, guest, contribution_id)
    if c.state != "photos_uploaded":
        raise ApiError(ErrorCode.CONFLICT, "Add at least one photo before submitting.", {"state": c.state})
    c.state = "submitted"
    await session.flush()
    out = await _out(session, c)
    session.add(
        IdempotencyKey(
            key=idempotency_key,
            scope=scope,
            request_hash=request_hash,
            response=json.loads(out.model_dump_json()),
            status_code=200,
        )
    )
    await session.commit()
    return out
