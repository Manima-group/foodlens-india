"""Runs packages/fixtures/analysis.json (quality gate 2: missing data never yields a score)."""

from typing import Any

import pytest

from app.analysis.engine import (
    AliasIndex,
    AllergenDeclaration,
    LabelNutrition,
    NormalizedNutrition,
    Serving,
    Threshold,
    allergen_matches,
    check_completeness,
    compute_score,
    determine_status,
    match_ingredients,
    normalize_nutrition,
    normalize_text,
    nutrient_indicators,
    processing_indicator,
)
from tests.fixtures import load
from tests.placeholder_scorer import TestOnlyPlaceholderScorer

DOC = load("analysis.json")
THRESHOLDS = [Threshold(**t) for t in DOC["thresholds"]]
CASES = DOC["cases"]


def _index() -> AliasIndex:
    by_alias: dict[str, str] = {}
    by_ins: dict[str, str] = {}
    additives = set()
    for row in DOC["ingredient_aliases"]:
        for _lang, _script, alias in row["aliases"]:
            by_alias[normalize_text(alias)] = row["ingredient"]
        by_alias[normalize_text(row["ingredient"].replace("_", " "))] = row["ingredient"]
        if row.get("ins_code"):
            by_ins[row["ins_code"]] = row["ingredient"]
        if row["is_additive"]:
            additives.add(row["ingredient"])
    return AliasIndex(by_alias, by_ins, frozenset(additives))


def _label(raw: dict[str, Any]) -> LabelNutrition:
    s = raw["serving"]
    return LabelNutrition(raw["basis"], raw["product_form"], raw["nutrients"], Serving(**s) if s else None)


def test_at_least_25_cases() -> None:
    assert len(CASES) >= 25


@pytest.mark.parametrize("case", [c for c in CASES if c["kind"] == "nutrition"], ids=lambda c: c["id"])
def test_nutrition_case(case: dict[str, Any]) -> None:
    exp = case["expected"]
    n = normalize_nutrition(_label(case["label"]))
    comp = check_completeness(n, DOC["required_fields"])
    status = determine_status(comp, methodology_published=case["methodology_published"], **case["review"])
    score = compute_score(
        n, comp, methodology_published=case["methodology_published"], scorer=TestOnlyPlaceholderScorer()
    )

    assert status == exp["analysis_status"]
    assert comp.missing_fields == exp["missing_fields"]
    for w in exp["warnings"]:
        assert w in n.warnings, n.warnings
    for w in exp["absent_warnings"]:
        assert w not in n.warnings, n.warnings
    for k, v in exp.get("per_100", {}).items():
        assert n.per_100[k] == pytest.approx(v, abs=0.01), k
    if "per_serving" in exp:
        assert set(n.per_serving) == set(exp["per_serving"])
        for k, v in exp["per_serving"].items():
            assert n.per_serving[k] == pytest.approx(v, abs=0.01), k
    if "basis" in exp:
        assert n.basis == exp["basis"]
    levels = {i.nutrient: i.level for i in nutrient_indicators(n, THRESHOLDS)}
    for k, v in exp.get("indicators", {}).items():
        assert levels[k] == v, k
    # T4: a score exists only when status is scored.
    assert (score is not None) == (status == "scored")


def test_missing_field_is_not_zero() -> None:
    n = normalize_nutrition(LabelNutrition("per_100g", "solid", {"energy_kcal": 100}))
    assert "sodium_mg" not in n.per_100


def test_all_seed_thresholds_are_placeholders() -> None:
    assert all(t.is_placeholder for t in THRESHOLDS)


def test_production_has_no_scorer() -> None:
    n = NormalizedNutrition(basis="per_100g", per_100={"energy_kcal": 1})
    assert compute_score(n, check_completeness(n, []), methodology_published=True, scorer=None) is None


@pytest.mark.parametrize("case", [c for c in CASES if c["kind"] == "ingredients"], ids=lambda c: c["id"])
def test_ingredient_case(case: dict[str, Any]) -> None:
    idx = _index()
    matched = match_ingredients(case["ingredients"]["items"], idx)
    assert [m.ingredient_key for m in matched] == case["expected"]["matched"]
    proc = processing_indicator(matched, is_complete=case["ingredients"]["is_complete"], index=idx)
    assert proc.value == case["expected"]["processing"]
    if proc.value is None:
        assert proc.reason == case["expected"]["reason"]


@pytest.mark.parametrize("case", [c for c in CASES if c["kind"] == "allergens"], ids=lambda c: c["id"])
def test_allergen_case(case: dict[str, Any]) -> None:
    decls = [AllergenDeclaration(**d) for d in case["declarations"]]
    got = allergen_matches(decls, case["user_prefs"])
    assert [d.__dict__ for d in got] == case["expected"]["matches"]
