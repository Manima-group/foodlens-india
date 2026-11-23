from datetime import datetime
from typing import Annotated

from fastapi import APIRouter, Depends, Header
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.guest import require_guest, sha256
from app.core.db import get_session
from app.models import GuestDevice, ScanEvent

router = APIRouter(tags=["consumer"])


class ScanEventIn(BaseModel):
    gtin14: str = Field(pattern=r"^[0-9]{14}$")
    scanned_at: datetime
    found: bool


class ScanEventsIn(BaseModel):
    events: list[ScanEventIn] = Field(min_length=1, max_length=50)


@router.post("/scan-events", status_code=202)
async def scan_events(
    body: ScanEventsIn,
    session: Annotated[AsyncSession, Depends(get_session)],
    guest: Annotated[GuestDevice, Depends(require_guest)],
    x_app_version: Annotated[str, Header(max_length=20)] = "unknown",
) -> dict[str, int]:
    """Anonymous, batched, consent-gated on the client (SPEC §10.5). Timestamps coarsened to the hour."""
    device_hash = sha256("scan:" + str(guest.id))
    for e in body.events:
        session.add(
            ScanEvent(
                device_hash=device_hash,
                gtin14=e.gtin14,
                hour_bucket=e.scanned_at.replace(minute=0, second=0, microsecond=0),
                app_version=x_app_version,
                found=e.found,
            )
        )
    await session.commit()
    return {"accepted": len(body.events)}
