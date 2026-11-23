from datetime import UTC, datetime

import httpx

from tests.conftest import register

JPEG = b"\xff\xd8\xff\xe0" + b"\x00" * 200


async def test_guest_and_scan_events(client: httpx.AsyncClient) -> None:
    assert (await client.post("/scan-events", json={"events": []})).status_code == 401
    h = await register(client, "install-aaaaaaaaaaaaaaaa")
    now = datetime.now(UTC).isoformat()
    r = await client.post(
        "/scan-events",
        headers=h,
        json={"events": [{"gtin14": "08901234567890", "scanned_at": now, "found": True}]},
    )
    assert r.status_code == 202 and r.json() == {"accepted": 1}
    too_many = [{"gtin14": "08901234567890", "scanned_at": now, "found": True}] * 51
    assert (await client.post("/scan-events", headers=h, json={"events": too_many})).status_code == 422
    assert (await client.post("/guest/register", json={"install_id": "short"})).status_code == 422


async def test_contribution_flow_idempotency_and_idor(client: httpx.AsyncClient) -> None:
    h = await register(client, "install-bbbbbbbbbbbbbbbb")
    other = await register(client, "install-cccccccccccccccc")
    c = await client.post(
        "/contributions", headers=h, json={"gtin14": "4006381333931", "product_name": "Unknown biscuit"}
    )
    assert c.status_code == 201, c.text
    cid = c.json()["id"]
    assert c.json()["gtin14"] == "04006381333931" and c.json()["state"] == "draft"

    # IDOR: another guest cannot see or touch it.
    assert (
        await client.post(
            f"/contributions/{cid}/photos",
            headers=other,
            json={"kind": "front", "content_type": "image/jpeg", "size_bytes": 300},
        )
    ).status_code == 404
    assert (
        await client.post(f"/contributions/{cid}/confirm", headers={**other, "Idempotency-Key": "k"})
    ).status_code == 404

    # Confirm without photos is refused.
    r = await client.post(f"/contributions/{cid}/confirm", headers={**h, "Idempotency-Key": "k0"})
    assert r.status_code == 409

    p = await client.post(
        f"/contributions/{cid}/photos",
        headers=h,
        json={"kind": "front", "content_type": "image/jpeg", "size_bytes": len(JPEG)},
    )
    assert p.status_code == 201
    url = p.json()["upload_url"].removeprefix("http://test/api/v1")
    bad = await client.put(url, content=b"GIF89a" + b"\x00" * 50, headers={"Content-Type": "image/jpeg"})
    assert bad.status_code == 422  # wrong magic bytes → rejected, URL burned
    assert (await client.put(url, content=JPEG, headers={"Content-Type": "image/jpeg"})).status_code == 404

    p = await client.post(
        f"/contributions/{cid}/photos",
        headers=h,
        json={"kind": "front", "content_type": "image/jpeg", "size_bytes": len(JPEG)},
    )
    url = p.json()["upload_url"].removeprefix("http://test/api/v1")
    assert (await client.put(url, content=JPEG, headers={"Content-Type": "image/png"})).status_code == 422
    assert (await client.put(url, content=JPEG, headers={"Content-Type": "image/jpeg"})).status_code == 204
    done = await client.post(f"/contributions/{cid}/photos/{p.json()['photo_id']}/complete", headers=h)
    assert done.status_code == 200 and done.json()["state"] == "photos_uploaded"

    assert (await client.post(f"/contributions/{cid}/confirm", headers=h)).status_code == 422  # key required
    first = await client.post(f"/contributions/{cid}/confirm", headers={**h, "Idempotency-Key": "k1"})
    assert first.status_code == 200 and first.json()["state"] == "submitted"
    replay = await client.post(f"/contributions/{cid}/confirm", headers={**h, "Idempotency-Key": "k1"})
    assert replay.status_code == 200 and replay.json() == first.json()

    mine = (await client.get("/me/contributions", headers=h)).json()
    assert [m["id"] for m in mine] == [cid]
    assert (await client.get("/me/contributions", headers=other)).json() == []
    # Submitted contributions never become public products without moderation.
    assert (await client.get("/products/by-barcode/4006381333931")).status_code == 404


async def test_invalid_contribution_barcode(client: httpx.AsyncClient) -> None:
    h = await register(client, "install-dddddddddddddddd")
    r = await client.post("/contributions", headers=h, json={"gtin14": "8901234567891"})
    assert r.status_code == 422 and r.json()["error"]["code"] == "INVALID_BARCODE"
