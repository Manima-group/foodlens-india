from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request, Response
from fastapi.middleware.cors import CORSMiddleware

from app.auth.guest import router as guest_router
from app.catalog.routes import router as catalog_router
from app.consumer.routes import router as consumer_router
from app.contributions.routes import router as contributions_router
from app.core.config import get_settings
from app.core.db import dispose_engine
from app.core.errors import ApiError, ErrorCode, install_error_handlers
from app.core.request_id import RequestIdMiddleware
from app.meta.routes import router as meta_router

PREFIX = "/api/v1"
_ALWAYS_OPEN = (f"{PREFIX}/health", f"{PREFIX}/meta/config")


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    get_settings().upload_dir.mkdir(parents=True, exist_ok=True)
    yield
    await dispose_engine()


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(
        title="AAHAR API",
        version="0.1.0",
        lifespan=lifespan,
        openapi_url=f"{PREFIX}/openapi.json",
        docs_url=f"{PREFIX}/docs",
    )
    install_error_handlers(app)

    @app.middleware("http")
    async def app_version_gate(
        request: Request, call_next: Callable[[Request], Awaitable[Response]]
    ) -> Response:
        path = request.url.path
        if not path.startswith(_ALWAYS_OPEN):
            if get_settings().maintenance:
                return await _error(request, ApiError(ErrorCode.MAINTENANCE))
            version = request.headers.get("X-App-Version")
            if version and version.isdigit() and int(version) < get_settings().min_supported_app_version_code:
                return await _error(request, ApiError(ErrorCode.APP_VERSION_UNSUPPORTED))
        return await call_next(request)

    app.add_middleware(RequestIdMiddleware)
    if settings.cors_allow_origins:
        app.add_middleware(
            CORSMiddleware,
            allow_origins=settings.cors_allow_origins,
            allow_methods=["*"],
            allow_headers=["*"],
        )
    for r in (meta_router, guest_router, catalog_router, consumer_router, contributions_router):
        app.include_router(r, prefix=PREFIX)
    return app


async def _error(request: Request, exc: ApiError) -> Response:
    handler = request.app.exception_handlers[ApiError]
    response: Response = await handler(request, exc)
    return response


app = create_app()
