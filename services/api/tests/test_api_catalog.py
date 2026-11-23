from typing import Any

import httpx

from app.enums import ENUMS
from tests.fixtures import load

DEMO = "8901234567890"


def same_shape(expected: Any, actual: Any, path: str = "$") -> None:
    """Every key in the SPEC example exists in the response (recursively)."""
    if isinstance(expected, dict):
        assert isinstance(actual, dict), path
        for k, v in expected.items():
            assert k in actual, f"{path}.{k} missing"
            if actual[k] is not None and v is not None:
                same_shape(v, actual[k], f"{path}.{k}")
    elif isinstance(expected, list) and expected and actual:
        same_shape(expected[0], actual[0], path + "[0]")


def test_enums_match_spec() -> None:
    assert {k: list(v) for k, v in ENUMS.items()} == load("enums.json")


async def test_health_and_meta(client: httpx.AsyncClient) -> None:
    assert (await client.get("/health/live")).json() == {"status": "ok"}
    assert (await client.get("/health/ready")).status_code == 200
    meta = (await client.get("/meta/config")).json()
    assert meta == load("api-examples/meta-config.json")


async def test_by_barcode_matches_spec_shape_and_etag(client: httpx.AsyncClient) -> None:
    r = await client.get(f"/products/by-barcode/{DEMO}", headers={"X-Request-ID": "abc-123"})
    assert r.status_code == 200, r.text
    assert r.headers["X-Request-ID"] == "abc-123"
    body = r.json()
    same_shape(load("api-examples/product-by-barcode-200.json"), body)
    assert body["product"]["gtin14"] == "08901234567890"
    assert body["product"]["name"].endswith("(Synthetic)")
    assert body["analysis_summary"]["analysis_status"] == "methodology_pending"
    assert body["analysis_summary"]["score_value"] is None
    assert all(i["is_placeholder_threshold"] for i in body["analysis_summary"]["headline_indicators"])
    assert body["sponsored"] == []
    again = await client.get(f"/products/by-barcode/{DEMO}", headers={"If-None-Match": r.headers["ETag"]})
    assert again.status_code == 304


async def test_barcode_errors_use_envelope(client: httpx.AsyncClient) -> None:
    r = await client.get("/products/by-barcode/8901234567891")
    assert r.status_code == 422
    err = r.json()["error"]
    assert err["code"] == "INVALID_CHECK_DIGIT" and err["request_id"]
    r = await client.get("/products/by-barcode/01234565")
    assert r.status_code == 409 and r.json()["error"]["code"] == "AMBIGUOUS_BARCODE"
    assert len(r.json()["error"]["details"]["candidates"]) == 2
    r = await client.get("/products/by-barcode/4006381333931")
    assert r.status_code == 404
    assert r.json()["error"]["code"] == "PRODUCT_NOT_FOUND"
    assert r.json()["error"]["details"] == {"gtin14": "04006381333931", "can_contribute": True}
    r = await client.get("/products/not-a-uuid")
    assert r.status_code == 404 and r.json()["error"]["code"] == "NOT_FOUND"


async def _all_products(client: httpx.AsyncClient) -> list[dict[str, Any]]:
    items: list[dict[str, Any]] = []
    cursor = None
    while True:
        params: dict[str, Any] = {"limit": 50}
        if cursor:
            params["cursor"] = cursor
        page = (await client.get("/search", params=params)).json()
        items += page["items"]
        cursor = page["next_cursor"]
        if not cursor:
            return items


async def test_seed_size_and_no_scores_anywhere(client: httpx.AsyncClient) -> None:
    items = await _all_products(client)
    assert len(items) >= 60
    assert len({i["category"]["slug"] for i in items}) >= 8
    assert all(i["name"].endswith("(Synthetic)") for i in items)
    statuses = {i["analysis_status"] for i in items}
    assert {"insufficient_data", "methodology_pending", "unverified_provisional"} <= statuses
    assert statuses <= set(ENUMS["analysis_status"]) - {"scored"}  # T8: no published methodology


async def test_insufficient_product_detail_and_alternatives(client: httpx.AsyncClient) -> None:
    items = await _all_products(client)
    bad = next(i for i in items if i["analysis_status"] == "insufficient_data")
    d = (await client.get(f"/products/{bad['id']}")).json()
    assert d["analysis_summary"]["score_value"] is None
    assert d["product"]["completeness"]["missing_fields"]
    assert d["lab_information"]["message"] == "No verified laboratory information available"
    assert any("Not enough data to score" in x for x in d["limitations"])
    alt = (await client.get(f"/products/{bad['id']}/alternatives")).json()
    assert alt["items"] == [] and alt["message"] == "Not enough data to compare" and alt["sponsored"] == []


async def test_detail_sections(client: httpx.AsyncClient) -> None:
    pid = (await client.get(f"/products/by-barcode/{DEMO}")).json()["product"]["id"]
    d = (await client.get(f"/products/{pid}")).json()
    assert d["nutrition"]["basis"] == "per_100g"
    assert {n["nutrient"] for n in d["nutrition"]["per_100"]} >= {"energy_kcal", "sodium_mg"}
    assert d["nutrition"]["per_serving"], "serving size declared → per-serving values"
    assert d["nutrition"]["thresholds_are_placeholder"] is True
    assert [a["kind"] for a in d["allergens"] if a["code"] == "peanut"] == ["contains"]
    assert all(a["source"] == "label_declared" for a in d["allergens"])
    assert d["ingredients"]["items"][0]["position"] == 0
    ing_id = next(i["ingredient_id"] for i in d["ingredients"]["items"] if i["ingredient_id"])
    card = (await client.get(f"/ingredients/{ing_id}")).json()
    assert card["aliases"] and card["references"] == []
    src = (await client.get(f"/products/{pid}/sources")).json()
    assert src["sources"] and src["attributions"] == ["Synthetic demonstration data. Not a real product."]
    ex = (await client.get(f"/products/{pid}/analysis/explain")).json()
    same_shape(load("api-examples/analysis-explain.json"), ex)
    assert ex["methodology"]["status"] == "draft" and ex["factors"] == []


async def test_alternatives_are_same_category_and_comparable(client: httpx.AsyncClient) -> None:
    pid = (await client.get(f"/products/by-barcode/{DEMO}")).json()["product"]["id"]
    alt = (await client.get(f"/products/{pid}/alternatives")).json()
    assert alt["items"], alt
    assert {i["category"]["slug"] for i in alt["items"]} == {"namkeen-savoury-snacks"}
    assert all(i["analysis_status"] != "insufficient_data" and i["id"] != pid for i in alt["items"])
    assert alt["sponsored"] == []
    highs = [sum(1 for h in i["headline_indicators"] if h["level"] == "high") for i in alt["items"]]
    assert highs == sorted(highs)


async def test_search_compare_categories(client: httpx.AsyncClient) -> None:
    page = (await client.get("/search", params={"q": "noodle"})).json()
    assert page["items"] and all("Noodle" in i["name"] for i in page["items"][:3])
    hindi_brand = (await client.get("/search", params={"q": "Demo Snacks"})).json()
    assert hindi_brand["items"]
    by_cat = (await client.get("/search", params={"category": "fruit-beverages"})).json()
    assert by_cat["items"] and {i["category"]["slug"] for i in by_cat["items"]} == {"fruit-beverages"}
    a, b = by_cat["items"][0]["id"], by_cat["items"][1]["id"]
    cmp = (await client.get("/products/compare", params={"ids": f"{a},{b}"})).json()
    assert len(cmp["items"]) == 2 and cmp["items"][0]["basis"] == "per_100ml"
    assert (await client.get("/products/compare", params={"ids": a})).status_code == 422
    cats = (await client.get("/categories")).json()["items"]
    assert len(cats) >= 8 and all(c["product_count"] > 0 for c in cats)
    assert (await client.get("/search", params={"cursor": "!!bad"})).status_code == 422


async def test_app_version_gate(client: httpx.AsyncClient) -> None:
    r = await client.get(f"/products/by-barcode/{DEMO}", headers={"X-App-Version": "0"})
    assert r.status_code == 426 and r.json()["error"]["code"] == "APP_VERSION_UNSUPPORTED"
    assert (await client.get("/meta/config", headers={"X-App-Version": "0"})).status_code == 200
