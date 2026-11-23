"""Tier 1 (+ contribution intake) tables, SPEC §6. Enums are CHECK constraints (ADR 0003)."""

import uuid
from datetime import datetime
from typing import Any

from sqlalchemy import (
    Boolean,
    CheckConstraint,
    DateTime,
    Float,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
    UniqueConstraint,
    func,
    text,
)
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.db import Base
from app.core.ids import uuid7
from app.enums import check_in


def pk() -> Mapped[uuid.UUID]:
    return mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid7)


def fk(target: str, *, nullable: bool = False, ondelete: str = "CASCADE") -> Mapped[Any]:
    return mapped_column(
        UUID(as_uuid=True), ForeignKey(target, ondelete=ondelete), nullable=nullable, index=True
    )


class Timestamps:
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )


# --- identity -----------------------------------------------------------------------------------


class User(Timestamps, Base):
    __tablename__ = "users"
    id: Mapped[uuid.UUID] = pk()
    email: Mapped[str | None] = mapped_column(String(320), unique=True)
    display_name: Mapped[str | None] = mapped_column(String(120))


class GuestDevice(Timestamps, Base):
    """Random installation ID only; no hardware IDs (SPEC §6.2)."""

    __tablename__ = "guest_devices"
    id: Mapped[uuid.UUID] = pk()
    install_id_hash: Mapped[str] = mapped_column(String(64), unique=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    token_expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))


# --- catalog ------------------------------------------------------------------------------------


class Brand(Timestamps, Base):
    __tablename__ = "brands"
    id: Mapped[uuid.UUID] = pk()
    name: Mapped[str] = mapped_column(String(200))
    is_verified: Mapped[bool] = mapped_column(Boolean, default=False)
    __table_args__ = (
        Index("ix_brands_name_trgm", "name", postgresql_using="gin", postgresql_ops={"name": "gin_trgm_ops"}),
    )


class Category(Timestamps, Base):
    __tablename__ = "categories"
    id: Mapped[uuid.UUID] = pk()
    parent_id: Mapped[uuid.UUID | None] = fk("categories.id", nullable=True, ondelete="RESTRICT")
    slug: Mapped[str] = mapped_column(String(120), unique=True)
    name: Mapped[str] = mapped_column(String(200))
    name_hi: Mapped[str | None] = mapped_column(String(200))
    path: Mapped[str] = mapped_column(String(500))  # materialised path of slugs, ADR 0003
    product_form: Mapped[str] = mapped_column(String(10), default="solid")
    __table_args__ = (CheckConstraint("product_form IN ('solid', 'liquid')", name="ck_categories_form"),)


class Product(Timestamps, Base):
    __tablename__ = "products"
    id: Mapped[uuid.UUID] = pk()
    brand_id: Mapped[uuid.UUID] = fk("brands.id", ondelete="RESTRICT")
    name: Mapped[str] = mapped_column(String(300))
    is_published: Mapped[bool] = mapped_column(Boolean, default=True)
    is_synthetic: Mapped[bool] = mapped_column(Boolean, default=False)
    archived_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    brand: Mapped[Brand] = relationship(lazy="raise")
    __table_args__ = (
        Index("ix_products_published", "id", postgresql_where=text("is_published AND archived_at IS NULL")),
        Index(
            "ix_products_name_trgm", "name", postgresql_using="gin", postgresql_ops={"name": "gin_trgm_ops"}
        ),
    )


class ProductVariant(Timestamps, Base):
    __tablename__ = "product_variants"
    id: Mapped[uuid.UUID] = pk()
    product_id: Mapped[uuid.UUID] = fk("products.id")
    net_quantity: Mapped[float | None] = mapped_column(Float)
    unit: Mapped[str | None] = mapped_column(String(10))
    pack_format: Mapped[str | None] = mapped_column(String(40))


class ProductGtin(Timestamps, Base):
    __tablename__ = "product_gtins"
    id: Mapped[uuid.UUID] = pk()
    product_id: Mapped[uuid.UUID] = fk("products.id")
    variant_id: Mapped[uuid.UUID | None] = fk("product_variants.id", nullable=True)
    gtin14: Mapped[str] = mapped_column(String(14))
    market: Mapped[str] = mapped_column(String(2), default="IN")
    original_code: Mapped[str] = mapped_column(String(14))
    symbology: Mapped[str] = mapped_column(String(10))
    is_restricted_circulation: Mapped[bool] = mapped_column(Boolean, default=False)
    __table_args__ = (
        UniqueConstraint("gtin14", "market", name="uq_product_gtins_gtin14_market"),
        CheckConstraint("gtin14 ~ '^[0-9]{14}$'", name="ck_product_gtins_gtin14"),
        CheckConstraint(check_in("symbology", "symbology"), name="ck_product_gtins_symbology"),
    )


class ProductCategoryLink(Base):
    __tablename__ = "product_category_links"
    product_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("products.id", ondelete="CASCADE"), primary_key=True
    )
    category_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("categories.id", ondelete="RESTRICT"), primary_key=True, index=True
    )


class ProductImage(Timestamps, Base):
    __tablename__ = "product_images"
    id: Mapped[uuid.UUID] = pk()
    product_id: Mapped[uuid.UUID] = fk("products.id")
    kind: Mapped[str] = mapped_column(String(20))
    url: Mapped[str] = mapped_column(Text)
    licence: Mapped[str | None] = mapped_column(String(40))
    attribution: Mapped[str | None] = mapped_column(Text)


class ProductLabelVersion(Timestamps, Base):
    """Nutrition and ingredients belong to a label version, not to the product (SPEC §6.2)."""

    __tablename__ = "product_label_versions"
    id: Mapped[uuid.UUID] = pk()
    product_id: Mapped[uuid.UUID] = fk("products.id")
    observed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    evidence_level: Mapped[str] = mapped_column(String(30))
    review_state: Mapped[str] = mapped_column(String(20), default="approved")
    is_current: Mapped[bool] = mapped_column(Boolean, default=True)
    nutrient_basis: Mapped[str] = mapped_column(String(12))
    ingredients_complete: Mapped[bool] = mapped_column(Boolean, default=False)
    __table_args__ = (
        CheckConstraint(check_in("evidence_level", "evidence_level"), name="ck_label_versions_evidence"),
        CheckConstraint(check_in("review_state", "review_state"), name="ck_label_versions_review"),
        CheckConstraint(check_in("nutrient_basis", "nutrient_basis"), name="ck_label_versions_basis"),
    )


class LabelServing(Base):
    __tablename__ = "label_servings"
    id: Mapped[uuid.UUID] = pk()
    label_version_id: Mapped[uuid.UUID] = fk("product_label_versions.id")
    serving_size: Mapped[float] = mapped_column(Float)
    serving_unit: Mapped[str] = mapped_column(String(5))
    servings_per_pack: Mapped[float | None] = mapped_column(Float)
    __table_args__ = (CheckConstraint("serving_unit IN ('g', 'ml')", name="ck_label_servings_unit"),)


class LabelNutrient(Base):
    """Values as declared (field names like energy_kcal, energy_kj, salt_g, sodium_mg)."""

    __tablename__ = "label_nutrients"
    id: Mapped[uuid.UUID] = pk()
    label_version_id: Mapped[uuid.UUID] = fk("product_label_versions.id")
    nutrient: Mapped[str] = mapped_column(String(40))
    amount: Mapped[float] = mapped_column(Float)
    __table_args__ = (UniqueConstraint("label_version_id", "nutrient"),)


class Ingredient(Timestamps, Base):
    __tablename__ = "ingredients"
    id: Mapped[uuid.UUID] = pk()
    key: Mapped[str] = mapped_column(String(80), unique=True)
    name: Mapped[str] = mapped_column(String(200))
    description: Mapped[str | None] = mapped_column(Text)
    is_additive: Mapped[bool] = mapped_column(Boolean, default=False)


class IngredientAlias(Base):
    __tablename__ = "ingredient_aliases"
    id: Mapped[uuid.UUID] = pk()
    ingredient_id: Mapped[uuid.UUID] = fk("ingredients.id")
    lang: Mapped[str] = mapped_column(String(8))
    script: Mapped[str] = mapped_column(String(8))
    alias: Mapped[str] = mapped_column(String(200))
    __table_args__ = (
        UniqueConstraint("lang", "alias"),
        Index(
            "ix_ingredient_aliases_trgm",
            "alias",
            postgresql_using="gin",
            postgresql_ops={"alias": "gin_trgm_ops"},
        ),
    )


class Additive(Base):
    __tablename__ = "additives"
    id: Mapped[uuid.UUID] = pk()
    ingredient_id: Mapped[uuid.UUID] = fk("ingredients.id")
    ins_code: Mapped[str] = mapped_column(String(10), unique=True)
    function: Mapped[str | None] = mapped_column(String(100))


class LabelIngredientItem(Base):
    __tablename__ = "label_ingredient_items"
    id: Mapped[uuid.UUID] = pk()
    label_version_id: Mapped[uuid.UUID] = fk("product_label_versions.id")
    position: Mapped[int] = mapped_column(Integer)
    raw_text: Mapped[str] = mapped_column(Text)
    ingredient_id: Mapped[uuid.UUID | None] = fk("ingredients.id", nullable=True, ondelete="SET NULL")
    match_confidence: Mapped[float] = mapped_column(Float, default=0.0)


class Allergen(Base):
    __tablename__ = "allergens"
    id: Mapped[uuid.UUID] = pk()
    code: Mapped[str] = mapped_column(String(40), unique=True)
    name: Mapped[str] = mapped_column(String(100))
    name_hi: Mapped[str | None] = mapped_column(String(100))


class LabelAllergenDeclaration(Base):
    __tablename__ = "label_allergen_declarations"
    id: Mapped[uuid.UUID] = pk()
    label_version_id: Mapped[uuid.UUID] = fk("product_label_versions.id")
    allergen_id: Mapped[uuid.UUID] = fk("allergens.id", ondelete="RESTRICT")
    kind: Mapped[str] = mapped_column(String(12))
    __table_args__ = (CheckConstraint("kind IN ('contains', 'may_contain')", name="ck_allergen_decl_kind"),)


class LabelDietaryDeclaration(Base):
    __tablename__ = "label_dietary_declarations"
    id: Mapped[uuid.UUID] = pk()
    label_version_id: Mapped[uuid.UUID] = fk("product_label_versions.id")
    kind: Mapped[str] = mapped_column(String(40))  # e.g. veg_mark, non_veg_mark


class SourceRecord(Base):
    __tablename__ = "source_records"
    id: Mapped[uuid.UUID] = pk()
    source: Mapped[str] = mapped_column(String(40))  # synthetic_seed | community | manufacturer | off
    licence_code: Mapped[str] = mapped_column(String(40))
    retrieved_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    reference: Mapped[str | None] = mapped_column(Text)


class FieldProvenance(Base):
    __tablename__ = "field_provenance"
    id: Mapped[uuid.UUID] = pk()
    entity_type: Mapped[str] = mapped_column(String(40))
    entity_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), index=True)
    field_name: Mapped[str] = mapped_column(String(60))
    source_record_id: Mapped[uuid.UUID] = fk("source_records.id", ondelete="RESTRICT")
    evidence_level: Mapped[str] = mapped_column(String(30))
    observed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    licence_code: Mapped[str] = mapped_column(String(40))
    __table_args__ = (
        CheckConstraint(check_in("evidence_level", "evidence_level"), name="ck_provenance_evidence"),
    )


# --- analysis -----------------------------------------------------------------------------------


class ScoringMethodology(Timestamps, Base):
    __tablename__ = "scoring_methodologies"
    id: Mapped[uuid.UUID] = pk()
    version: Mapped[str] = mapped_column(String(40), unique=True)
    status: Mapped[str] = mapped_column(String(20), default="draft")
    approvals: Mapped[int] = mapped_column(Integer, default=0)
    __table_args__ = (
        CheckConstraint("status IN ('draft', 'published', 'retired')", name="ck_methodology_status"),
        CheckConstraint("status <> 'published' OR approvals >= 2", name="ck_methodology_two_approvals"),
    )


class ScoringRuleVersion(Timestamps, Base):
    __tablename__ = "scoring_rule_versions"
    id: Mapped[uuid.UUID] = pk()
    methodology_id: Mapped[uuid.UUID] = fk("scoring_methodologies.id", ondelete="RESTRICT")
    version: Mapped[str] = mapped_column(String(60), unique=True)
    required_fields: Mapped[list[str]] = mapped_column(JSONB)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)


class NutrientThreshold(Base):
    __tablename__ = "nutrient_thresholds"
    id: Mapped[uuid.UUID] = pk()
    rule_version_id: Mapped[uuid.UUID] = fk("scoring_rule_versions.id")
    category_id: Mapped[uuid.UUID | None] = fk("categories.id", nullable=True)
    nutrient: Mapped[str] = mapped_column(String(40))
    basis: Mapped[str] = mapped_column(String(12))
    low_max: Mapped[float] = mapped_column(Float)
    high_min: Mapped[float] = mapped_column(Float)
    is_placeholder: Mapped[bool] = mapped_column(Boolean, default=True)
    reference_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))


class ProductAnalysis(Base):
    __tablename__ = "product_analyses"
    id: Mapped[uuid.UUID] = pk()
    product_id: Mapped[uuid.UUID] = fk("products.id")
    label_version_id: Mapped[uuid.UUID] = fk("product_label_versions.id")
    rule_version_id: Mapped[uuid.UUID] = fk("scoring_rule_versions.id", ondelete="RESTRICT")
    analysis_status: Mapped[str] = mapped_column(String(30))
    score_value: Mapped[float | None] = mapped_column(Float)
    confidence: Mapped[float | None] = mapped_column(Float)
    missing_fields: Mapped[list[str]] = mapped_column(JSONB, default=list)
    warnings: Mapped[list[str]] = mapped_column(JSONB, default=list)
    computed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    __table_args__ = (
        UniqueConstraint("label_version_id", "rule_version_id"),
        Index("ix_product_analyses_product_rule", "product_id", "rule_version_id"),
        CheckConstraint(check_in("analysis_status", "analysis_status"), name="ck_analyses_status"),
        CheckConstraint(
            "score_value IS NULL OR analysis_status = 'scored'", name="ck_analyses_score_only_scored"
        ),
    )


class ProductAnalysisFactor(Base):
    __tablename__ = "product_analysis_factors"
    id: Mapped[uuid.UUID] = pk()
    analysis_id: Mapped[uuid.UUID] = fk("product_analyses.id")
    nutrient: Mapped[str] = mapped_column(String(40))
    level: Mapped[str] = mapped_column(String(10))
    value: Mapped[float] = mapped_column(Float)
    basis: Mapped[str] = mapped_column(String(12))
    is_placeholder_threshold: Mapped[bool] = mapped_column(Boolean)


# --- consumer & contributions -------------------------------------------------------------------


class ScanEvent(Base):
    """No user ID; hashed device ID; timestamp coarsened to the hour (SPEC §6.2)."""

    __tablename__ = "scan_events"
    id: Mapped[uuid.UUID] = pk()
    device_hash: Mapped[str] = mapped_column(String(64))
    gtin14: Mapped[str] = mapped_column(String(14))
    hour_bucket: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    app_version: Mapped[str] = mapped_column(String(20))
    found: Mapped[bool] = mapped_column(Boolean)


class CatalogContribution(Timestamps, Base):
    __tablename__ = "catalog_contributions"
    id: Mapped[uuid.UUID] = pk()
    guest_device_id: Mapped[uuid.UUID] = fk("guest_devices.id")
    gtin14: Mapped[str] = mapped_column(String(14))
    product_name: Mapped[str | None] = mapped_column(String(300))
    brand_name: Mapped[str | None] = mapped_column(String(200))
    notes: Mapped[str | None] = mapped_column(Text)
    state: Mapped[str] = mapped_column(String(30), default="draft")
    __table_args__ = (
        CheckConstraint(check_in("state", "contribution_state"), name="ck_contributions_state"),
        CheckConstraint("gtin14 ~ '^[0-9]{14}$'", name="ck_contributions_gtin14"),
    )


class ContributionPhoto(Timestamps, Base):
    __tablename__ = "contribution_photos"
    id: Mapped[uuid.UUID] = pk()
    contribution_id: Mapped[uuid.UUID] = fk("catalog_contributions.id")
    kind: Mapped[str] = mapped_column(String(20))
    content_type: Mapped[str] = mapped_column(String(40))
    storage_key: Mapped[str] = mapped_column(String(200), unique=True)
    upload_token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    upload_expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    size_bytes: Mapped[int | None] = mapped_column(Integer)
    state: Mapped[str] = mapped_column(String(20), default="pending")
    __table_args__ = (
        CheckConstraint(
            "kind IN ('front', 'back', 'ingredients', 'nutrition', 'barcode', 'other')", name="ck_photo_kind"
        ),
        CheckConstraint("state IN ('pending', 'uploaded', 'clean', 'rejected')", name="ck_photo_state"),
        CheckConstraint(
            "content_type IN ('image/jpeg', 'image/png', 'image/webp')", name="ck_photo_content_type"
        ),
    )


class IdempotencyKey(Base):
    __tablename__ = "idempotency_keys"
    id: Mapped[uuid.UUID] = pk()
    key: Mapped[str] = mapped_column(String(200))
    scope: Mapped[str] = mapped_column(String(100))
    request_hash: Mapped[str] = mapped_column(String(64))
    response: Mapped[dict[str, Any]] = mapped_column(JSONB)
    status_code: Mapped[int] = mapped_column(Integer)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    __table_args__ = (UniqueConstraint("key", "scope"),)
