"""Guest tokens: random install ID in, opaque token out; no PII (SPEC §8.7)."""

import hashlib
import secrets
from datetime import UTC, datetime, timedelta
from typing import Annotated

from fastapi import APIRouter, Depends, Header
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.core.db import get_session
from app.core.errors import ApiError, ErrorCode
from app.models import GuestDevice

router = APIRouter(tags=["guest"])
GUEST_SCOPES = ["catalog.read", "contributions.write", "scan_events.write"]


def sha256(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()


class GuestRegisterIn(BaseModel):
    install_id: str = Field(min_length=16, max_length=128, pattern=r"^[A-Za-z0-9\-_]+$")


class GuestRegisterOut(BaseModel):
    guest_token: str
    token_type: str = "Bearer"  # noqa: S105 - scheme name, not a secret
    expires_at: datetime
    scopes: list[str]


@router.post("/guest/register", response_model=GuestRegisterOut)
async def register(
    body: GuestRegisterIn, session: Annotated[AsyncSession, Depends(get_session)]
) -> GuestRegisterOut:
    token = secrets.token_urlsafe(32)
    expires = datetime.now(UTC) + timedelta(days=get_settings().guest_token_ttl_days)
    install_hash = sha256(body.install_id)
    device = await session.scalar(select(GuestDevice).where(GuestDevice.install_id_hash == install_hash))
    if device is None:
        session.add(
            GuestDevice(install_id_hash=install_hash, token_hash=sha256(token), token_expires_at=expires)
        )
    else:
        device.token_hash = sha256(token)  # re-register rotates the token
        device.token_expires_at = expires
    await session.commit()
    return GuestRegisterOut(guest_token=token, expires_at=expires, scopes=GUEST_SCOPES)


async def require_guest(
    session: Annotated[AsyncSession, Depends(get_session)],
    authorization: Annotated[str | None, Header()] = None,
) -> GuestDevice:
    if not authorization or not authorization.startswith("Bearer "):
        raise ApiError(ErrorCode.UNAUTHENTICATED, "A guest token is required.")
    device = await session.scalar(
        select(GuestDevice).where(
            GuestDevice.token_hash == sha256(authorization.removeprefix("Bearer ").strip())
        )
    )
    if device is None or device.token_expires_at < datetime.now(UTC):
        raise ApiError(ErrorCode.UNAUTHENTICATED, "Guest token is invalid or expired.")
    return device
