"""SPEC §8.2 error envelope and fixed codes. Extend codes only via ADR."""

from enum import StrEnum
from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException


class ErrorCode(StrEnum):
    VALIDATION_ERROR = "VALIDATION_ERROR"
    INVALID_BARCODE = "INVALID_BARCODE"
    INVALID_CHECK_DIGIT = "INVALID_CHECK_DIGIT"
    AMBIGUOUS_BARCODE = "AMBIGUOUS_BARCODE"
    PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND"
    NOT_FOUND = "NOT_FOUND"
    UNAUTHENTICATED = "UNAUTHENTICATED"
    FORBIDDEN = "FORBIDDEN"
    CONFLICT = "CONFLICT"
    IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT"
    RATE_LIMITED = "RATE_LIMITED"
    QUOTA_EXCEEDED = "QUOTA_EXCEEDED"
    UPSTREAM_UNAVAILABLE = "UPSTREAM_UNAVAILABLE"
    MAINTENANCE = "MAINTENANCE"
    APP_VERSION_UNSUPPORTED = "APP_VERSION_UNSUPPORTED"
    INTERNAL = "INTERNAL"


HTTP_STATUS: dict[ErrorCode, int] = {
    ErrorCode.VALIDATION_ERROR: 422,
    ErrorCode.INVALID_BARCODE: 422,
    ErrorCode.INVALID_CHECK_DIGIT: 422,
    ErrorCode.AMBIGUOUS_BARCODE: 409,
    ErrorCode.PRODUCT_NOT_FOUND: 404,
    ErrorCode.NOT_FOUND: 404,
    ErrorCode.UNAUTHENTICATED: 401,
    ErrorCode.FORBIDDEN: 403,
    ErrorCode.CONFLICT: 409,
    ErrorCode.IDEMPOTENCY_CONFLICT: 409,
    ErrorCode.RATE_LIMITED: 429,
    ErrorCode.QUOTA_EXCEEDED: 402,
    ErrorCode.UPSTREAM_UNAVAILABLE: 503,
    ErrorCode.MAINTENANCE: 503,
    ErrorCode.APP_VERSION_UNSUPPORTED: 426,
    ErrorCode.INTERNAL: 500,
}

_MESSAGES: dict[ErrorCode, str] = {
    ErrorCode.PRODUCT_NOT_FOUND: "No product found for this barcode.",
    ErrorCode.INVALID_BARCODE: "The barcode is not valid.",
    ErrorCode.INVALID_CHECK_DIGIT: "The barcode check digit is wrong.",
    ErrorCode.AMBIGUOUS_BARCODE: "The barcode could be read more than one way.",
}


class ApiError(Exception):
    def __init__(self, code: ErrorCode, message: str | None = None, details: dict[str, Any] | None = None):
        super().__init__(code.value)
        self.code = code
        self.message = message or _MESSAGES.get(code, code.value.replace("_", " ").capitalize() + ".")
        self.details = details or {}


def envelope(
    code: ErrorCode, message: str, details: dict[str, Any], request_id: str | None
) -> dict[str, Any]:
    return {"error": {"code": code.value, "message": message, "details": details, "request_id": request_id}}


def _rid(request: Request) -> str | None:
    rid = getattr(request.state, "request_id", None)
    return rid if isinstance(rid, str) else None


def install_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(ApiError)
    async def _api_error(request: Request, exc: ApiError) -> JSONResponse:
        return JSONResponse(
            envelope(exc.code, exc.message, exc.details, _rid(request)), status_code=HTTP_STATUS[exc.code]
        )

    @app.exception_handler(RequestValidationError)
    async def _validation(request: Request, exc: RequestValidationError) -> JSONResponse:
        details = {"errors": [{"loc": list(e["loc"]), "msg": e["msg"]} for e in exc.errors()]}
        body = envelope(ErrorCode.VALIDATION_ERROR, "Request validation failed.", details, _rid(request))
        return JSONResponse(body, status_code=422)

    @app.exception_handler(StarletteHTTPException)
    async def _http(request: Request, exc: StarletteHTTPException) -> JSONResponse:
        code = {404: ErrorCode.NOT_FOUND, 401: ErrorCode.UNAUTHENTICATED, 403: ErrorCode.FORBIDDEN}.get(
            exc.status_code, ErrorCode.VALIDATION_ERROR if exc.status_code == 405 else ErrorCode.INTERNAL
        )
        status = exc.status_code if exc.status_code in (401, 403, 404, 405) else 500
        return JSONResponse(envelope(code, str(exc.detail), {}, _rid(request)), status_code=status)

    @app.exception_handler(Exception)
    async def _unhandled(request: Request, exc: Exception) -> JSONResponse:
        return JSONResponse(
            envelope(ErrorCode.INTERNAL, "Internal error.", {}, _rid(request)), status_code=500
        )
