"""Loads a product's current label and runs the pure analysis engine over it."""

from __future__ import annotations

import uuid
from dataclasses import dataclass, field
from datetime import UTC, datetime, timedelta

from sqlalchemy import delete, select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.analysis.engine import (
    AliasIndex,
    AllergenDeclaration,
    Completeness,
    Indicator,
    LabelNutrition,
    MatchedItem,
    NormalizedNutrition,
    ProcessingIndicator,
    Serving,
    Threshold,
    check_completeness,
    compute_score,
    determine_status,
    normalize_nutrition,
    nutrient_indicators,
    processing_indicator,
)
from app.models import (
    Additive,
    Allergen,
    Brand,
    Category,
    FieldProvenance,
    Ingredient,
    LabelAllergenDeclaration,
    LabelDietaryDeclaration,
    LabelIngredientItem,
    LabelNutrient,
    LabelServing,
    NutrientThreshold,
    Product,
    ProductAnalysis,
    ProductAnalysisFactor,
    ProductCategoryLink,
    ProductGtin,
    ProductImage,
    ProductLabelVersion,
    ProductVariant,
    ScoringMethodology,
    ScoringRuleVersion,
    SourceRecord,
)

STALE_AFTER = timedelta(days=365)


@dataclass
class RuleContext:
    rule_version_id: uuid.UUID
    rule_version: str
    required_fields: list[str]
    thresholds: list[Threshold]
    methodology_id: uuid.UUID
    methodology_version: str
    methodology_status: str

    @property
    def methodology_published(self) -> bool:
        return self.methodology_status == "published"


@dataclass
class IngredientView:
    item: MatchedItem
    ingredient_id: uuid.UUID | None
    name: str | None
    is_additive: bool
    ins_code: str | None


@dataclass
class ProductBundle:
    product: Product
    brand: Brand
    gtin: ProductGtin
    variant: ProductVariant | None
    category: Category | None
    images: list[ProductImage]
    label: ProductLabelVersion | None
    serving: LabelServing | None
    nutrients: dict[str, float]
    ingredients: list[IngredientView]
    allergens: list[tuple[Allergen, str]]
    dietary: list[str]


@dataclass
class AnalysisResult:
    status: str
    score: float | None
    normalized: NormalizedNutrition
    completeness: Completeness
    indicators: list[Indicator]
    processing: ProcessingIndicator
    allergen_declarations: list[AllergenDeclaration] = field(default_factory=list)


async def load_rule_context(session: AsyncSession) -> RuleContext:
    row = (
        await session.execute(
            select(ScoringRuleVersion, ScoringMethodology)
            .join(ScoringMethodology, ScoringMethodology.id == ScoringRuleVersion.methodology_id)
            .where(ScoringRuleVersion.is_active)
            .order_by(ScoringRuleVersion.created_at.desc())
            .limit(1)
        )
    ).one()
    rule, methodology = row
    thresholds = (
        await session.scalars(select(NutrientThreshold).where(NutrientThreshold.rule_version_id == rule.id))
    ).all()
    return RuleContext(
        rule.id,
        rule.version,
        list(rule.required_fields),
        [Threshold(t.nutrient, t.basis, t.low_max, t.high_min, t.is_placeholder) for t in thresholds],
        methodology.id,
        methodology.version,
        methodology.status,
    )


async def find_product_id_by_gtin(session: AsyncSession, gtin14: str) -> uuid.UUID | None:
    return await session.scalar(
        select(ProductGtin.product_id)
        .join(Product, Product.id == ProductGtin.product_id)
        .where(ProductGtin.gtin14 == gtin14, ProductGtin.market == "IN")
        .where(Product.is_published, Product.archived_at.is_(None))
    )


async def load_bundle(session: AsyncSession, product_id: uuid.UUID) -> ProductBundle | None:
    product = await session.get(Product, product_id)
    if product is None or not product.is_published or product.archived_at is not None:
        return None
    brand = await session.get(Brand, product.brand_id)
    assert brand is not None
    gtin = await session.scalar(select(ProductGtin).where(ProductGtin.product_id == product_id).limit(1))
    assert gtin is not None
    variant = await session.scalar(
        select(ProductVariant).where(ProductVariant.product_id == product_id).limit(1)
    )
    category = await session.scalar(
        select(Category)
        .join(ProductCategoryLink, ProductCategoryLink.category_id == Category.id)
        .where(ProductCategoryLink.product_id == product_id)
        .limit(1)
    )
    images = list(
        (await session.scalars(select(ProductImage).where(ProductImage.product_id == product_id))).all()
    )
    label = await session.scalar(
        select(ProductLabelVersion)
        .where(ProductLabelVersion.product_id == product_id, ProductLabelVersion.is_current)
        .order_by(ProductLabelVersion.observed_at.desc())
        .limit(1)
    )
    serving = None
    nutrients: dict[str, float] = {}
    ingredients: list[IngredientView] = []
    allergens: list[tuple[Allergen, str]] = []
    dietary: list[str] = []
    if label is not None:
        serving = await session.scalar(select(LabelServing).where(LabelServing.label_version_id == label.id))
        nutrients = {
            n.nutrient: n.amount
            for n in (
                await session.scalars(select(LabelNutrient).where(LabelNutrient.label_version_id == label.id))
            )
        }
        rows = (
            await session.execute(
                select(LabelIngredientItem, Ingredient, Additive)
                .outerjoin(Ingredient, Ingredient.id == LabelIngredientItem.ingredient_id)
                .outerjoin(Additive, Additive.ingredient_id == Ingredient.id)
                .where(LabelIngredientItem.label_version_id == label.id)
                .order_by(LabelIngredientItem.position)
            )
        ).all()
        for item, ing, add in rows:
            ingredients.append(
                IngredientView(
                    MatchedItem(
                        item.position, item.raw_text, ing.key if ing else None, item.match_confidence
                    ),
                    ing.id if ing else None,
                    ing.name if ing else None,
                    bool(ing and ing.is_additive),
                    add.ins_code if add else None,
                )
            )
        allergens = [
            (a, d.kind)
            for d, a in (
                await session.execute(
                    select(LabelAllergenDeclaration, Allergen)
                    .join(Allergen, Allergen.id == LabelAllergenDeclaration.allergen_id)
                    .where(LabelAllergenDeclaration.label_version_id == label.id)
                    .order_by(Allergen.code)
                )
            ).all()
        ]
        dietary = list(
            (
                await session.scalars(
                    select(LabelDietaryDeclaration.kind).where(
                        LabelDietaryDeclaration.label_version_id == label.id
                    )
                )
            ).all()
        )
    return ProductBundle(
        product,
        brand,
        gtin,
        variant,
        category,
        images,
        label,
        serving,
        nutrients,
        ingredients,
        allergens,
        dietary,
    )


def analyze(bundle: ProductBundle, rules: RuleContext) -> AnalysisResult:
    label = bundle.label
    form = bundle.category.product_form if bundle.category else "solid"
    if label is None:
        normalized = NormalizedNutrition(basis="per_100ml" if form == "liquid" else "per_100g")
    else:
        serving = (
            Serving(bundle.serving.serving_size, bundle.serving.serving_unit) if bundle.serving else None
        )
        normalized = normalize_nutrition(
            LabelNutrition(label.nutrient_basis, form, bundle.nutrients, serving)  # type: ignore[arg-type]
        )
    completeness = check_completeness(normalized, rules.required_fields)
    status = determine_status(
        completeness,
        evidence_level=label.evidence_level if label else "community_submitted",
        review_state=label.review_state if label else "draft",
        methodology_published=rules.methodology_published,
    )
    # Production ships no scorer: compute_score returns None (T4, T8).
    score = compute_score(
        normalized, completeness, methodology_published=rules.methodology_published, scorer=None
    )
    if score is None and status == "scored":
        status = "methodology_pending"
    matched = [iv.item for iv in bundle.ingredients]
    additive_keys = frozenset(
        iv.item.ingredient_key for iv in bundle.ingredients if iv.is_additive and iv.item.ingredient_key
    )
    processing = processing_indicator(
        matched,
        is_complete=bool(label and label.ingredients_complete),
        index=AliasIndex({}, {}, additive_keys),
    )
    evidence = label.evidence_level if label else "community_submitted"
    allergen_decls = [
        AllergenDeclaration(a.code, kind, "label_declared", evidence)  # type: ignore[arg-type]
        for a, kind in bundle.allergens
    ]
    return AnalysisResult(
        status,
        score,
        normalized,
        completeness,
        nutrient_indicators(normalized, rules.thresholds),
        processing,
        allergen_decls,
    )


async def persist_analysis(
    session: AsyncSession, bundle: ProductBundle, rules: RuleContext, result: AnalysisResult
) -> None:
    if bundle.label is None:
        return
    stmt = (
        insert(ProductAnalysis)
        .values(
            product_id=bundle.product.id,
            label_version_id=bundle.label.id,
            rule_version_id=rules.rule_version_id,
            analysis_status=result.status,
            score_value=result.score,
            confidence=None,
            missing_fields=result.completeness.missing_fields,
            warnings=result.normalized.warnings,
        )
        .on_conflict_do_update(
            index_elements=["label_version_id", "rule_version_id"],
            set_={
                "analysis_status": result.status,
                "score_value": result.score,
                "missing_fields": result.completeness.missing_fields,
                "warnings": result.normalized.warnings,
                "computed_at": datetime.now(UTC),
            },
        )
        .returning(ProductAnalysis.id)
    )
    analysis_id = (await session.execute(stmt)).scalar_one()
    await session.execute(
        delete(ProductAnalysisFactor).where(ProductAnalysisFactor.analysis_id == analysis_id)
    )
    for ind in result.indicators:
        session.add(
            ProductAnalysisFactor(
                analysis_id=analysis_id,
                nutrient=ind.nutrient,
                level=ind.level,
                value=ind.value,
                basis=ind.basis,
                is_placeholder_threshold=ind.is_placeholder_threshold,
            )
        )


def freshness(label: ProductLabelVersion | None, now: datetime | None = None) -> str:
    if label is None:
        return "unknown"
    now = now or datetime.now(UTC)
    return "fresh" if now - label.observed_at <= STALE_AFTER else "stale"


async def sources_for(
    session: AsyncSession, bundle: ProductBundle
) -> list[tuple[FieldProvenance, SourceRecord]]:
    if bundle.label is None:
        return []
    rows = await session.execute(
        select(FieldProvenance, SourceRecord)
        .join(SourceRecord, SourceRecord.id == FieldProvenance.source_record_id)
        .where(FieldProvenance.entity_id == bundle.label.id)
        .order_by(FieldProvenance.field_name)
    )
    return [(p, s) for p, s in rows.all()]
