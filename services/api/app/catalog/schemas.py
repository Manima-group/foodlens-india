"""Response models. ProductOut / AnalysisSummaryOut match SPEC §8.5 field-for-field."""

from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, Field


class BrandOut(BaseModel):
    id: str
    name: str
    is_verified: bool


class VariantOut(BaseModel):
    net_quantity: float | None
    unit: str | None
    pack_format: str | None


class CategoryOut(BaseModel):
    id: str
    slug: str
    name: str


class ImageOut(BaseModel):
    kind: str
    url: str
    licence: str | None
    attribution: str | None


class LabelVersionOut(BaseModel):
    id: str
    observed_at: datetime
    evidence_level: str


class CompletenessOut(BaseModel):
    nutrition: bool
    ingredients: bool
    allergens: bool
    missing_fields: list[str]


class ProductOut(BaseModel):
    id: str
    gtin14: str
    original_code: str
    name: str
    brand: BrandOut
    variant: VariantOut | None
    category: CategoryOut | None
    images: list[ImageOut]
    label_version: LabelVersionOut | None
    data_freshness: Literal["fresh", "stale", "unknown"]
    completeness: CompletenessOut
    badges: list[str]


class HeadlineIndicatorOut(BaseModel):
    nutrient: str
    level: Literal["low", "medium", "high"]
    is_placeholder_threshold: bool
    basis: str


class AnalysisSummaryOut(BaseModel):
    analysis_status: str
    score_value: float | None
    confidence: float | None
    rule_version: str
    headline_indicators: list[HeadlineIndicatorOut]


class ProductLookupOut(BaseModel):
    product: ProductOut
    analysis_summary: AnalysisSummaryOut
    sponsored: list[dict[str, Any]] = Field(default_factory=list)


class NutrientValueOut(BaseModel):
    nutrient: str
    value: float
    unit: str


class ServingOut(BaseModel):
    size: float
    unit: str
    servings_per_pack: float | None


class IndicatorOut(BaseModel):
    nutrient: str
    level: Literal["low", "medium", "high"]
    value: float
    basis: str
    is_placeholder_threshold: bool


class NutritionOut(BaseModel):
    declared_basis: str | None
    basis: str
    per_100: list[NutrientValueOut]
    per_serving: list[NutrientValueOut]
    serving: ServingOut | None
    indicators: list[IndicatorOut]
    thresholds_are_placeholder: bool
    warnings: list[str]
    evidence_level: str | None


class IngredientItemOut(BaseModel):
    position: int
    raw_text: str
    ingredient_id: str | None
    ingredient_name: str | None
    is_additive: bool
    ins_code: str | None
    match_confidence: float


class IngredientsOut(BaseModel):
    is_complete: bool
    items: list[IngredientItemOut]
    processing: dict[str, int] | None
    processing_reason: str | None
    evidence_level: str | None


class AllergenDeclarationOut(BaseModel):
    code: str
    name: str
    name_hi: str | None
    kind: Literal["contains", "may_contain"]
    source: str
    evidence_level: str


class LabInformationOut(BaseModel):
    status: Literal["none", "verified"]
    message: str


class ProductDetailOut(ProductLookupOut):
    nutrition: NutritionOut
    ingredients: IngredientsOut
    allergens: list[AllergenDeclarationOut]
    dietary_declarations: list[str]
    lab_information: LabInformationOut
    limitations: list[str]


class MethodologyOut(BaseModel):
    id: str
    version: str
    status: str


class ExplainInputOut(BaseModel):
    field: str
    value: float
    basis: str
    source: str
    observed_at: datetime


class AnalysisExplainOut(BaseModel):
    """SPEC §8.6."""

    analysis_status: str
    score_value: float | None
    methodology: MethodologyOut
    rule_version: str
    inputs: list[ExplainInputOut]
    missing_fields: list[str]
    factors: list[dict[str, Any]]
    limitations: list[str]


class ProductSummaryOut(BaseModel):
    id: str
    gtin14: str
    name: str
    brand: BrandOut
    category: CategoryOut | None
    variant: VariantOut | None
    analysis_status: str
    data_freshness: str
    headline_indicators: list[HeadlineIndicatorOut]


class Page[T](BaseModel):
    items: list[T]
    next_cursor: str | None


class AlternativesOut(BaseModel):
    items: list[ProductSummaryOut]
    sponsored: list[dict[str, Any]] = Field(default_factory=list)
    suppressed_reason: str | None
    message: str | None
    ranking_basis: str


class CompareItemOut(BaseModel):
    product: ProductSummaryOut
    basis: str
    per_100: list[NutrientValueOut]
    missing_fields: list[str]


class CompareOut(BaseModel):
    items: list[CompareItemOut]
    nutrients: list[str]
    note: str


class SourceOut(BaseModel):
    field_name: str
    source: str
    licence_code: str
    evidence_level: str
    observed_at: datetime


class SourcesOut(BaseModel):
    sources: list[SourceOut]
    attributions: list[str]


class CategoryNodeOut(BaseModel):
    id: str
    slug: str
    name: str
    name_hi: str | None
    parent_id: str | None
    product_count: int


class IngredientCardOut(BaseModel):
    id: str
    key: str
    name: str
    description: str | None
    is_additive: bool
    ins_code: str | None
    function: str | None
    aliases: list[dict[str, str]]
    references: list[dict[str, str]]
    references_note: str
