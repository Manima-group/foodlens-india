import os
from typing import Annotated, Any

from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.core.db import get_session

router = APIRouter()


@router.get("/health/live", tags=["health"])
async def live() -> dict[str, str]:
    return {"status": "ok"}


@router.get("/health/ready", tags=["health"])
async def ready(session: Annotated[AsyncSession, Depends(get_session)]) -> JSONResponse:
    checks: dict[str, str] = {}
    try:
        await session.execute(text("SELECT 1"))
        checks["database"] = "ok"
    except Exception:
        checks["database"] = "unavailable"
    try:
        upload_dir = get_settings().upload_dir
        upload_dir.mkdir(parents=True, exist_ok=True)
        checks["storage"] = "ok" if os.access(upload_dir, os.W_OK) else "unavailable"
    except OSError:
        checks["storage"] = "unavailable"
    ok = all(v == "ok" for v in checks.values())
    return JSONResponse(
        {"status": "ok" if ok else "degraded", "checks": checks}, status_code=200 if ok else 503
    )


@router.get("/meta/config", tags=["meta"])
async def meta_config() -> dict[str, Any]:
    """SPEC §8.4 shape. Android caches this for 6 h."""
    s = get_settings()
    return {
        "min_supported_app_version_code": s.min_supported_app_version_code,
        "latest_app_version_code": s.latest_app_version_code,
        "maintenance": s.maintenance,
        "features": {
            "contributions": s.feature_contributions,
            "public_score": s.feature_public_score,
            "sponsored": s.feature_sponsored,
            "off_lookup": s.feature_off_lookup,
            "google_sign_in": s.feature_google_sign_in,
        },
        "legal": {
            "privacy_version": "2026-11-01",
            "terms_version": "2026-11-01",
            "methodology_version": "pending",
        },
        "supported_locales": ["en", "hi"],
    }
