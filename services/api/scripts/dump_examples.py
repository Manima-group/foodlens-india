"""Writes real API responses (seeded DB) to packages/fixtures/api-examples/live/.

The Android contract test (LiveContractTest) decodes these strictly.

    DATABASE_URL=... uv run python -m scripts.dump_examples
"""

import asyncio
import json
from pathlib import Path

import httpx

from app.main import app

OUT = Path(__file__).resolve().parents[3] / "packages" / "fixtures" / "api-examples" / "live"


async def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://x/api/v1") as c:
        demo = (await c.get("/products/by-barcode/8901234567890")).json()
        pid = demo["product"]["id"]
        search = (await c.get("/search", params={"limit": 50})).json()
        bad = next(i for i in search["items"] if i["analysis_status"] == "insufficient_data")
        same_cat = [i["id"] for i in search["items"] if i["category"]["slug"] == "namkeen-savoury-snacks"][:2]
        guest = (await c.post("/guest/register", json={"install_id": "dump-examples-0000"})).json()
        auth = {"Authorization": f"Bearer {guest['guest_token']}"}
        contribution = (await c.post("/contributions", headers=auth, json={"gtin14": "4006381333931"})).json()
        photo = (
            await c.post(
                f"/contributions/{contribution['id']}/photos",
                headers=auth,
                json={"kind": "front", "content_type": "image/jpeg", "size_bytes": 1000},
            )
        ).json()
        responses = {
            "by-barcode.json": demo,
            "product-detail.json": (await c.get(f"/products/{pid}")).json(),
            "product-detail-insufficient.json": (await c.get(f"/products/{bad['id']}")).json(),
            "alternatives.json": (await c.get(f"/products/{pid}/alternatives")).json(),
            "alternatives-suppressed.json": (await c.get(f"/products/{bad['id']}/alternatives")).json(),
            "compare.json": (await c.get("/products/compare", params={"ids": ",".join(same_cat)})).json(),
            "search.json": (await c.get("/search", params={"q": "noodle", "limit": 3})).json(),
            "categories.json": (await c.get("/categories")).json(),
            "sources.json": (await c.get(f"/products/{pid}/sources")).json(),
            "explain.json": (await c.get(f"/products/{pid}/analysis/explain")).json(),
            "guest-register.json": guest,
            "contribution.json": contribution,
            "photo-upload.json": photo,
            "not-found.json": (await c.get("/products/by-barcode/4006381333931")).json(),
            "ambiguous.json": (await c.get("/products/by-barcode/01234565")).json(),
        }
        items = responses["product-detail.json"]["ingredients"]["items"]
        ing = next(i["ingredient_id"] for i in items if i["ingredient_id"])
        responses["ingredient.json"] = (await c.get(f"/ingredients/{ing}")).json()
    for name, body in responses.items():
        (OUT / name).write_text(json.dumps(body, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"wrote {len(responses)} files to {OUT}")


if __name__ == "__main__":
    asyncio.run(main())
