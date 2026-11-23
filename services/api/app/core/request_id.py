"""Accept X-Request-ID or generate one; echo in response header (SPEC §8.1)."""

import re
from collections.abc import Awaitable, Callable

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

from app.core.ids import uuid7

_SAFE = re.compile(r"^[A-Za-z0-9._\-]{1,128}$")


class RequestIdMiddleware(BaseHTTPMiddleware):
    async def dispatch(
        self, request: Request, call_next: Callable[[Request], Awaitable[Response]]
    ) -> Response:
        incoming = request.headers.get("X-Request-ID")
        rid = incoming if incoming and _SAFE.match(incoming) else str(uuid7())
        request.state.request_id = rid
        response = await call_next(request)
        response.headers["X-Request-ID"] = rid
        return response
