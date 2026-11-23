"""Analysis engine, SPEC §9. Pure functions: no DB, no web, no billing/campaign imports (import-linter).

Nothing here invents a score. `compute_score` returns None unless a *published* methodology and a scorer
exist, and production ships no scorer (trust rules T4, T8).
"""

from __future__ import annotations

import re
import unicodedata
from collections.abc import Iterable, Mapping, Sequence
from dataclasses import dataclass, field
from typing import Literal, Protocol

KJ_PER_KCAL = 4.184
SALT_PER_SODIUM = 2.5
SALT_SODIUM_TOLERANCE = 0.10
ENERGY_TOLERANCE = 0.15

Basis = Literal["per_100g", "per_100ml", "per_serving"]
Level = Literal["low", "medium", "high"]


@dataclass(frozen=True)
class Serving:
    size: float
    unit: str  # "g" | "ml"


@dataclass(frozen=True)
class LabelNutrition:
    """Values as declared on the label, keyed by canonical field name (energy_kj and salt_g also accepted)."""

    basis: Basis
    product_form: Literal["solid", "liquid"]
    nutrients: Mapping[str, float]
    serving: Serving | None = None


@dataclass
class NormalizedNutrition:
    basis: Literal["per_100g", "per_100ml"]
    per_100: dict[str, float] = field(default_factory=dict)
    per_serving: dict[str, float] = field(default_factory=dict)
    serving: Serving | None = None
    warnings: list[str] = field(default_factory=list)


@dataclass(frozen=True)
class Completeness:
    missing_fields: list[str]

    @property
    def is_complete(self) -> bool:
        return not self.missing_fields


@dataclass(frozen=True)
class Threshold:
    nutrient: str
    basis: str
    low_max: float
    high_min: float
    is_placeholder: bool
    reference_id: str | None = None


@dataclass(frozen=True)
class Indicator:
    nutrient: str
    level: Level
    value: float
    basis: str
    is_placeholder_threshold: bool


def _round(value: float) -> float:
    return round(value, 4)


def normalize_nutrition(label: LabelNutrition) -> NormalizedNutrition:
    warnings: list[str] = []
    declared: dict[str, float] = {}
    for key, raw in label.nutrients.items():
        value = float(raw)
        if value < 0:
            warnings.append(f"invalid_value:{key}")
            continue
        declared[key] = value

    if "energy_kcal" not in declared and "energy_kj" in declared:
        declared["energy_kcal"] = declared["energy_kj"] / KJ_PER_KCAL
        warnings.append("energy_from_kj")
    declared.pop("energy_kj", None)

    salt = declared.pop("salt_g", None)
    if salt is not None:
        from_salt = salt / SALT_PER_SODIUM * 1000
        if "sodium_mg" not in declared:
            declared["sodium_mg"] = from_salt
            warnings.append("sodium_from_salt")
        elif abs(declared["sodium_mg"] - from_salt) > SALT_SODIUM_TOLERANCE * max(
            declared["sodium_mg"], from_salt
        ):
            warnings.append("salt_sodium_mismatch")

    serving = label.serving
    if label.basis == "per_serving":
        if serving is None or serving.size <= 0:
            warnings.append("missing_serving_size")
            basis: Literal["per_100g", "per_100ml"] = (
                "per_100ml" if label.product_form == "liquid" else "per_100g"
            )
            return NormalizedNutrition(basis=basis, serving=None, warnings=warnings)
        basis = "per_100ml" if serving.unit == "ml" else "per_100g"
        factor = 100.0 / serving.size
        per_100 = {k: _round(v * factor) for k, v in declared.items()}
        per_serving = {k: _round(v) for k, v in declared.items()}
        warnings.append("per_serving_converted")
    else:
        basis = label.basis
        per_100 = {k: _round(v) for k, v in declared.items()}
        per_serving = {}
        if serving is not None and serving.size > 0:
            serving_basis = "per_100ml" if serving.unit == "ml" else "per_100g"
            if serving_basis != basis:
                warnings.append("basis_uncertain")  # label mixes g and ml: never assume density
            else:
                per_serving = {k: _round(v * serving.size / 100.0) for k, v in per_100.items()}

    result = NormalizedNutrition(basis=basis, per_100=per_100, per_serving=per_serving, serving=serving)
    result.warnings = warnings + _plausibility(result)
    return result


def _plausibility(n: NormalizedNutrition) -> list[str]:
    out: list[str] = []
    p = n.per_100
    if n.basis == "per_100g":
        macros = sum(p.get(k, 0.0) for k in ("fat_g", "carbohydrate_g", "protein_g", "fibre_g"))
        if macros > 100.0:
            out.append("macros_exceed_100")
    if all(k in p for k in ("energy_kcal", "fat_g", "carbohydrate_g", "protein_g")):
        estimate = 4 * p["carbohydrate_g"] + 4 * p["protein_g"] + 9 * p["fat_g"]
        if estimate > 0 and abs(p["energy_kcal"] - estimate) > ENERGY_TOLERANCE * estimate:
            out.append("energy_implausible")
    return out


def check_completeness(normalized: NormalizedNutrition, required_fields: Sequence[str]) -> Completeness:
    """Required fields come from the rule version, not from code (SPEC §9.2)."""
    return Completeness([f for f in required_fields if f not in normalized.per_100])


def nutrient_indicators(normalized: NormalizedNutrition, thresholds: Iterable[Threshold]) -> list[Indicator]:
    out: list[Indicator] = []
    for t in thresholds:
        if t.basis != normalized.basis or t.nutrient not in normalized.per_100:
            continue
        value = normalized.per_100[t.nutrient]
        level: Level = "low" if value <= t.low_max else "high" if value > t.high_min else "medium"
        out.append(Indicator(t.nutrient, level, value, t.basis, t.is_placeholder))
    return out


# --- ingredients ---------------------------------------------------------------------------------

_INS = re.compile(r"\b(?:ins|e)\s*-?\s*(\d{3,4}[a-z]?)\b", re.IGNORECASE)


def normalize_text(text: str) -> str:
    return " ".join(unicodedata.normalize("NFC", text).casefold().split())


@dataclass(frozen=True)
class AliasIndex:
    by_alias: Mapping[str, str]
    by_ins: Mapping[str, str]
    additives: frozenset[str]


@dataclass(frozen=True)
class MatchedItem:
    position: int
    raw_text: str
    ingredient_key: str | None
    match_confidence: float


def match_ingredients(items: Sequence[str], index: AliasIndex) -> list[MatchedItem]:
    out: list[MatchedItem] = []
    for i, raw in enumerate(items):
        key = index.by_alias.get(normalize_text(raw))
        confidence = 1.0 if key else 0.0
        if key is None:
            m = _INS.search(raw)
            if m and m.group(1).lower() in index.by_ins:
                key, confidence = index.by_ins[m.group(1).lower()], 0.95
        out.append(MatchedItem(i, raw, key, confidence))
    return out


@dataclass(frozen=True)
class ProcessingIndicator:
    value: dict[str, int] | None
    reason: str | None


def processing_indicator(
    matched: Sequence[MatchedItem], *, is_complete: bool, index: AliasIndex
) -> ProcessingIndicator:
    """Descriptive counts only, and only if the list is complete and every item matched (SPEC §9.4)."""
    if not is_complete:
        return ProcessingIndicator(None, "ingredients_incomplete")
    if not matched or any(m.ingredient_key is None for m in matched):
        return ProcessingIndicator(None, "unmatched_ingredients")
    additives = sum(1 for m in matched if m.ingredient_key in index.additives)
    return ProcessingIndicator({"ingredient_count": len(matched), "additive_count": additives}, None)


# --- allergens -----------------------------------------------------------------------------------


@dataclass(frozen=True)
class AllergenDeclaration:
    allergen: str
    kind: Literal["contains", "may_contain"]
    source: str
    evidence_level: str


def allergen_matches(
    declarations: Sequence[AllergenDeclaration], user_prefs: Iterable[str]
) -> list[AllergenDeclaration]:
    """Matches declarations only. Absence of a declaration is never reported as 'free from' (T7)."""
    wanted = {p.casefold() for p in user_prefs}
    return [d for d in declarations if d.allergen.casefold() in wanted]


# --- status & score ------------------------------------------------------------------------------


class Scorer(Protocol):
    def score(self, normalized: NormalizedNutrition) -> float: ...


def compute_score(
    normalized: NormalizedNutrition,
    completeness: Completeness,
    *,
    methodology_published: bool,
    scorer: Scorer | None,
) -> float | None:
    """Missing data never yields a score (T4), whatever the methodology state."""
    if not completeness.is_complete or not methodology_published or scorer is None:
        return None
    return scorer.score(normalized)


def determine_status(
    completeness: Completeness,
    *,
    evidence_level: str,
    review_state: str,
    methodology_published: bool,
) -> str:
    """Order fixed by SPEC §9.7."""
    if not completeness.is_complete:
        return "insufficient_data"
    if evidence_level == "community_submitted" and review_state != "approved":
        return "unverified_provisional"
    if not methodology_published:
        return "methodology_pending"
    return "scored"
