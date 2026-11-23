import base64
import hashlib
import json

from fastapi import Request, Response
from pydantic import BaseModel

from app.core.errors import ApiError, ErrorCode


def etag_response(request: Request, model: BaseModel) -> Response:
    """ETag on product/analysis responses; 304 on If-None-Match (SPEC §8.1)."""
    body = model.model_dump_json()
    tag = '"' + hashlib.sha256(body.encode()).hexdigest()[:32] + '"'
    headers = {"ETag": tag, "Cache-Control": "no-cache"}
    if request.headers.get("If-None-Match") == tag:
        return Response(status_code=304, headers=headers)
    return Response(content=body, media_type="application/json", headers=headers)


def encode_cursor(offset: int) -> str:
    return base64.urlsafe_b64encode(json.dumps({"o": offset}).encode()).decode().rstrip("=")


def decode_cursor(cursor: str | None) -> int:
    if not cursor:
        return 0
    try:
        padded = cursor + "=" * (-len(cursor) % 4)
        offset = int(json.loads(base64.urlsafe_b64decode(padded))["o"])
    except (ValueError, KeyError, TypeError) as exc:
        raise ApiError(ErrorCode.VALIDATION_ERROR, "Invalid cursor.") from exc
    if offset < 0:
        raise ApiError(ErrorCode.VALIDATION_ERROR, "Invalid cursor.")
    return offset
