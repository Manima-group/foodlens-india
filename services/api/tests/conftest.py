import os
import subprocess
from collections.abc import AsyncIterator
from pathlib import Path

import pytest

TEST_DB = os.environ.get("TEST_DATABASE_URL", "postgresql+asyncpg://postgres@127.0.0.1:5432/foodlens_test")
os.environ["DATABASE_URL"] = TEST_DB
os.environ.setdefault("UPLOAD_DIR", str(Path(__file__).parent / ".uploads"))
os.environ["PUBLIC_BASE_URL"] = "http://test"

import httpx  # noqa: E402

from app.core.config import get_settings  # noqa: E402

get_settings.cache_clear()


@pytest.fixture(scope="session")
async def seeded() -> AsyncIterator[None]:
    root = Path(__file__).resolve().parents[1]
    env = {**os.environ, "DATABASE_URL": TEST_DB}
    for args in (["downgrade", "base"], ["upgrade", "head"]):
        subprocess.run(["alembic", *args], cwd=root, env=env, check=True, capture_output=True)
    from app.seed import reset_and_seed

    await reset_and_seed()
    yield
    from app.core.db import dispose_engine

    await dispose_engine()


@pytest.fixture
async def client(seeded: None) -> AsyncIterator[httpx.AsyncClient]:
    from app.main import app

    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test/api/v1") as c:
        yield c


async def register(client: httpx.AsyncClient, install_id: str) -> dict[str, str]:
    r = await client.post("/guest/register", json={"install_id": install_id})
    assert r.status_code == 200, r.text
    return {"Authorization": f"Bearer {r.json()['guest_token']}"}
