"""Turns a ProductBundle + AnalysisResult into SPEC-shaped response models."""

from app.catalog.schemas import (
    AllergenDeclarationOut,
    AnalysisSummaryOut,
    BrandOut,
    CategoryOut,
    CompletenessOut,
    HeadlineIndicatorOut,
    ImageOut,
    IndicatorOut,
    IngredientItemOut,
    IngredientsOut,
    LabelVersionOut,
    LabInformationOut,
    NutrientValueOut,
    NutritionOut,
    ProductDetailOut,
    ProductLookupOut,
    ProductOut,
    ProductSummaryOut,
    ServingOut,
    VariantOut,
)
from app.catalog.service import AnalysisResult, ProductBundle, RuleContext, freshness

NO_LAB_INFO = "No verified laboratory information available"
NUTRIENT_ORDER = [
    "energy_kcal",
    "fat_g",
    "saturated_fat_g",
    "trans_fat_g",
    "carbohydrate_g",
    "sugars_g",
    "added_sugars_g",
    "fibre_g",
    "protein_g",
    "sodium_mg",
]
_BADGES = {"veg_mark": "label_declared_veg", "non_veg_mark": "label_declared_non_veg"}


def unit_for(nutrient: str) -> str:
    return "kcal" if nutrient.endswith("_kcal") else "mg" if nutrient.endswith("_mg") else "g"


def _ordered(values: dict[str, float]) -> list[NutrientValueOut]:
    keys = sorted(values, key=lambda k: (NUTRIENT_ORDER.index(k) if k in NUTRIENT_ORDER else 99, k))
    return [NutrientValueOut(nutrient=k, value=values[k], unit=unit_for(k)) for k in keys]


def brand_out(b: ProductBundle) -> BrandOut:
    return BrandOut(id=str(b.brand.id), name=b.brand.name, is_verified=b.brand.is_verified)


def category_out(b: ProductBundle) -> CategoryOut | None:
    c = b.category
    return CategoryOut(id=str(c.id), slug=c.slug, name=c.name) if c else None


def variant_out(b: ProductBundle) -> VariantOut | None:
    v = b.variant
    return VariantOut(net_quantity=v.net_quantity, unit=v.unit, pack_format=v.pack_format) if v else None


def headline(result: AnalysisResult) -> list[HeadlineIndicatorOut]:
    return [
        HeadlineIndicatorOut(
            nutrient=i.nutrient,
            level=i.level,
            is_placeholder_threshold=i.is_placeholder_threshold,
            basis=i.basis,
        )
        for i in result.indicators
    ]


def product_out(b: ProductBundle, result: AnalysisResult) -> ProductOut:
    label = b.label
    return ProductOut(
        id=str(b.product.id),
        gtin14=b.gtin.gtin14,
        original_code=b.gtin.original_code,
        name=b.product.name,
        brand=brand_out(b),
        variant=variant_out(b),
        category=category_out(b),
        images=[
            ImageOut(kind=i.kind, url=i.url, licence=i.licence, attribution=i.attribution) for i in b.images
        ],
        label_version=LabelVersionOut(
            id=str(label.id), observed_at=label.observed_at, evidence_level=label.evidence_level
        )
        if label
        else None,
        data_freshness=freshness(label),
        completeness=CompletenessOut(
            nutrition=result.completeness.is_complete,
            ingredients=bool(label and label.ingredients_complete and b.ingredients),
            allergens=label is not None,
            missing_fields=result.completeness.missing_fields,
        ),
        badges=[_BADGES[d] for d in b.dietary if d in _BADGES],
    )


def summary_out(b: ProductBundle, result: AnalysisResult, rules: RuleContext) -> AnalysisSummaryOut:
    return AnalysisSummaryOut(
        analysis_status=result.status,
        score_value=result.score,
        confidence=None,
        rule_version=rules.rule_version,
        headline_indicators=headline(result),
    )


def lookup_out(b: ProductBundle, result: AnalysisResult, rules: RuleContext) -> ProductLookupOut:
    return ProductLookupOut(
        product=product_out(b, result), analysis_summary=summary_out(b, result, rules), sponsored=[]
    )


def product_summary(b: ProductBundle, result: AnalysisResult) -> ProductSummaryOut:
    return ProductSummaryOut(
        id=str(b.product.id),
        gtin14=b.gtin.gtin14,
        name=b.product.name,
        brand=brand_out(b),
        category=category_out(b),
        variant=variant_out(b),
        analysis_status=result.status,
        data_freshness=freshness(b.label),
        headline_indicators=headline(result),
    )


def limitations(result: AnalysisResult) -> list[str]:
    out: list[str] = []
    if result.completeness.missing_fields:
        out.append(
            "Not enough data to score: the label does not declare "
            + ", ".join(result.completeness.missing_fields)
            + "."
        )
    if result.status == "methodology_pending":
        out.append("No public score: the scoring methodology is pending expert review.")
    if result.status == "unverified_provisional":
        out.append("Community-submitted data that a reviewer has not checked yet.")
    if result.indicators:
        out.append("Indicative thresholds — pending expert review.")
    for w in result.normalized.warnings:
        if w == "basis_uncertain":
            out.append(
                "The label mixes grams and millilitres; per-serving values are not shown (density unknown)."
            )
        elif w == "salt_sodium_mismatch":
            out.append("Declared salt and sodium disagree by more than 10%; declared sodium is used.")
        elif w in ("energy_implausible", "macros_exceed_100"):
            out.append("Some declared values look implausible; please report a problem if the label differs.")
    return out


def detail_out(b: ProductBundle, result: AnalysisResult, rules: RuleContext) -> ProductDetailOut:
    label = b.label
    evidence = label.evidence_level if label else None
    n = result.normalized
    return ProductDetailOut(
        **lookup_out(b, result, rules).model_dump(),
        nutrition=NutritionOut(
            declared_basis=label.nutrient_basis if label else None,
            basis=n.basis,
            per_100=_ordered(n.per_100),
            per_serving=_ordered(n.per_serving),
            serving=ServingOut(
                size=b.serving.serving_size,
                unit=b.serving.serving_unit,
                servings_per_pack=b.serving.servings_per_pack,
            )
            if b.serving
            else None,
            indicators=[
                IndicatorOut(
                    nutrient=i.nutrient,
                    level=i.level,
                    value=i.value,
                    basis=i.basis,
                    is_placeholder_threshold=i.is_placeholder_threshold,
                )
                for i in result.indicators
            ],
            thresholds_are_placeholder=any(t.is_placeholder for t in rules.thresholds),
            warnings=n.warnings,
            evidence_level=evidence,
        ),
        ingredients=IngredientsOut(
            is_complete=bool(label and label.ingredients_complete),
            items=[
                IngredientItemOut(
                    position=iv.item.position,
                    raw_text=iv.item.raw_text,
                    ingredient_id=str(iv.ingredient_id) if iv.ingredient_id else None,
                    ingredient_name=iv.name,
                    is_additive=iv.is_additive,
                    ins_code=iv.ins_code,
                    match_confidence=iv.item.match_confidence,
                )
                for iv in b.ingredients
            ],
            processing=result.processing.value,
            processing_reason=result.processing.reason,
            evidence_level=evidence,
        ),
        allergens=[
            AllergenDeclarationOut(
                code=a.code,
                name=a.name,
                name_hi=a.name_hi,
                kind=kind,
                source="label_declared",
                evidence_level=evidence or "label_declared",
            )
            for a, kind in b.allergens
        ],
        dietary_declarations=b.dietary,
        lab_information=LabInformationOut(status="none", message=NO_LAB_INFO),
        limitations=limitations(result),
    )
