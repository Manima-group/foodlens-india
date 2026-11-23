import uuid
from typing import Annotated

from fastapi import APIRouter, Depends, Path, Query, Request, Response
from sqlalchemy import func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.alternatives.ranking import Candidate, rank
from app.catalog.schemas import (
    AlternativesOut,
    AnalysisExplainOut,
    CategoryNodeOut,
    CompareItemOut,
    CompareOut,
    ExplainInputOut,
    IngredientCardOut,
    MethodologyOut,
    Page,
    ProductDetailOut,
    ProductLookupOut,
    ProductSummaryOut,
    SourceOut,
    SourcesOut,
)
from app.catalog.service import (
    AnalysisResult,
    ProductBundle,
    RuleContext,
    analyze,
    find_product_id_by_gtin,
    load_bundle,
    load_rule_context,
    persist_analysis,
    sources_for,
)
from app.catalog.views import NUTRIENT_ORDER, _ordered, detail_out, limitations, lookup_out, product_summary
from app.core.db import get_session
from app.core.errors import ApiError, ErrorCode
from app.core.http import decode_cursor, encode_cursor, etag_response
from app.gtin import Ambiguous, Invalid, normalize
from app.models import (
    Additive,
    Brand,
    Category,
    Ingredient,
    IngredientAlias,
    Product,
    ProductCategoryLink,
)

router = APIRouter(tags=["catalog"])
Session = Annotated[AsyncSession, Depends(get_session)]
LICENCE_ATTRIBUTION = {
    "ODbL": "Contains information from Open Food Facts, available under the Open Database License (ODbL).",
    "SYNTHETIC": "Synthetic demonstration data. Not a real product.",
}


async def _analyzed(
    session: AsyncSession, product_id: uuid.UUID
) -> tuple[ProductBundle, AnalysisResult, RuleContext]:
    bundle = await load_bundle(session, product_id)
    if bundle is None:
        raise ApiError(ErrorCode.NOT_FOUND, "Product not found.")
    rules = await load_rule_context(session)
    result = analyze(bundle, rules)
    await persist_analysis(session, bundle, rules, result)
    await session.commit()
    return bundle, result, rules


def _parse_uuid(value: str) -> uuid.UUID:
    try:
        return uuid.UUID(value)
    except ValueError as exc:
        raise ApiError(ErrorCode.NOT_FOUND, "Product not found.") from exc


@router.get("/products/by-barcode/{code}", response_model=ProductLookupOut)
async def by_barcode(
    request: Request,
    session: Session,
    code: Annotated[str, Path(max_length=32)],
    symbology: Annotated[str | None, Query()] = None,
) -> Response:
    result = normalize(code, symbology)
    if isinstance(result, Invalid):
        raise ApiError(ErrorCode(result.code.value), details={"code": code})
    if isinstance(result, Ambiguous):
        raise ApiError(
            ErrorCode.AMBIGUOUS_BARCODE,
            details={
                "candidates": [{"gtin14": c.gtin14, "symbology": c.symbology} for c in result.candidates]
            },
        )
    product_id = await find_product_id_by_gtin(session, result.gtin14)
    if product_id is None:
        raise ApiError(ErrorCode.PRODUCT_NOT_FOUND, details={"gtin14": result.gtin14, "can_contribute": True})
    bundle, analysis, rules = await _analyzed(session, product_id)
    return etag_response(request, lookup_out(bundle, analysis, rules))


@router.get("/products/compare", response_model=CompareOut)
async def compare(
    session: Session, ids: Annotated[str, Query(description="2 to 4 comma-separated product IDs")]
) -> CompareOut:
    parsed = [_parse_uuid(i.strip()) for i in ids.split(",") if i.strip()]
    if not 2 <= len(set(parsed)) <= 4:
        raise ApiError(ErrorCode.VALIDATION_ERROR, "Compare needs 2 to 4 different products.")
    items: list[CompareItemOut] = []
    seen: set[str] = set()
    for pid in dict.fromkeys(parsed):
        bundle, result, _ = await _analyzed(session, pid)
        items.append(
            CompareItemOut(
                product=product_summary(bundle, result),
                basis=result.normalized.basis,
                per_100=_ordered(result.normalized.per_100),
                missing_fields=result.completeness.missing_fields,
            )
        )
        seen.update(result.normalized.per_100)
    nutrients = [n for n in NUTRIENT_ORDER if n in seen] + sorted(seen - set(NUTRIENT_ORDER))
    bases = {i.basis for i in items}
    note = (
        "Values are per 100 g/ml as declared on each label. "
        "Missing values are shown as not declared, never as 0."
    )
    if len(bases) > 1:
        note += " These products use different bases (g vs ml) and are not directly comparable."
    return CompareOut(items=items, nutrients=nutrients, note=note)


@router.get("/products/{product_id}", response_model=ProductDetailOut)
async def product_detail(request: Request, session: Session, product_id: str) -> Response:
    bundle, result, rules = await _analyzed(session, _parse_uuid(product_id))
    return etag_response(request, detail_out(bundle, result, rules))


@router.get("/products/{product_id}/analysis", response_model=ProductLookupOut)
async def product_analysis(request: Request, session: Session, product_id: str) -> Response:
    bundle, result, rules = await _analyzed(session, _parse_uuid(product_id))
    return etag_response(request, lookup_out(bundle, result, rules))


@router.get("/products/{product_id}/analysis/explain", response_model=AnalysisExplainOut)
async def explain(request: Request, session: Session, product_id: str) -> Response:
    bundle, result, rules = await _analyzed(session, _parse_uuid(product_id))
    label = bundle.label
    observed = label.observed_at if label else None
    inputs = (
        [
            ExplainInputOut(
                field=k,
                value=v,
                basis=result.normalized.basis,
                source=label.evidence_level,
                observed_at=label.observed_at,
            )
            for k, v in result.normalized.per_100.items()
        ]
        if label and observed
        else []
    )
    out = AnalysisExplainOut(
        analysis_status=result.status,
        score_value=result.score,
        methodology=MethodologyOut(
            id=str(rules.methodology_id), version=rules.methodology_version, status=rules.methodology_status
        ),
        rule_version=rules.rule_version,
        inputs=inputs,
        missing_fields=result.completeness.missing_fields,
        factors=[],  # no factors until a published methodology exists (T8)
        limitations=limitations(result),
    )
    return etag_response(request, out)


@router.get("/products/{product_id}/sources", response_model=SourcesOut)
async def sources(session: Session, product_id: str) -> SourcesOut:
    bundle = await load_bundle(session, _parse_uuid(product_id))
    if bundle is None:
        raise ApiError(ErrorCode.NOT_FOUND, "Product not found.")
    rows = await sources_for(session, bundle)
    licences = sorted({s.licence_code for _, s in rows})
    return SourcesOut(
        sources=[
            SourceOut(
                field_name=p.field_name,
                source=s.source,
                licence_code=p.licence_code,
                evidence_level=p.evidence_level,
                observed_at=p.observed_at,
            )
            for p, s in rows
        ],
        attributions=[LICENCE_ATTRIBUTION[lc] for lc in licences if lc in LICENCE_ATTRIBUTION],
    )


@router.get("/products/{product_id}/alternatives", response_model=AlternativesOut)
async def alternatives(session: Session, product_id: str) -> AlternativesOut:
    bundle, result, rules = await _analyzed(session, _parse_uuid(product_id))
    basis_note = (
        "Same category and pack type, ranked by fewer high nutrient indicators, "
        "then data completeness, then name."
    )
    if result.status == "insufficient_data":
        return AlternativesOut(
            items=[],
            sponsored=[],
            suppressed_reason="insufficient_data",
            message="Not enough data to compare",
            ranking_basis=basis_note,
        )
    if bundle.category is None:
        return AlternativesOut(
            items=[],
            suppressed_reason="no_category",
            message="No category to compare within",
            ranking_basis=basis_note,
        )
    ids = (
        await session.scalars(
            select(ProductCategoryLink.product_id).where(
                ProductCategoryLink.category_id == bundle.category.id
            )
        )
    ).all()

    def to_candidate(b: ProductBundle, r: AnalysisResult) -> Candidate:
        unit = (b.variant.unit if b.variant else None) or ("ml" if r.normalized.basis == "per_100ml" else "g")
        return Candidate(
            str(b.product.id),
            b.product.name,
            str(b.category.id) if b.category else None,
            "volume" if unit in ("ml", "l") else "mass",
            r.status,
            sum(1 for i in r.indicators if i.level == "high"),
            sum(1 for i in r.indicators if i.level == "medium"),
            len(r.completeness.missing_fields),
        )

    pool: dict[str, tuple[ProductBundle, AnalysisResult]] = {}
    for pid in ids:
        b = await load_bundle(session, pid)
        if b is not None:
            pool[str(pid)] = (b, analyze(b, rules))
    ranked = rank(to_candidate(bundle, result), [to_candidate(b, r) for b, r in pool.values()])
    return AlternativesOut(
        items=[product_summary(*pool[c.product_id]) for c in ranked],
        sponsored=[],
        suppressed_reason=None if ranked else "no_comparable_products",
        message=None if ranked else "No comparable products yet",
        ranking_basis=basis_note,
    )


@router.get("/search", response_model=Page[ProductSummaryOut])
async def search(
    session: Session,
    q: Annotated[str, Query(min_length=1, max_length=100)] = "",
    category: Annotated[str | None, Query(max_length=120)] = None,
    limit: Annotated[int, Query(ge=1, le=50)] = 20,
    cursor: str | None = None,
) -> Page[ProductSummaryOut]:
    offset = decode_cursor(cursor)
    term = q.strip()
    stmt = (
        select(Product.id)
        .join(Brand, Brand.id == Product.brand_id)
        .where(Product.is_published, Product.archived_at.is_(None))
    )
    if term:
        like = f"%{term}%"
        stmt = stmt.where(
            or_(
                Product.name.ilike(like),
                Brand.name.ilike(like),
                func.similarity(Product.name, term) > 0.2,
                func.similarity(Brand.name, term) > 0.3,
            )
        ).order_by(
            func.greatest(func.similarity(Product.name, term), func.similarity(Brand.name, term)).desc(),
            Product.name,
        )
    else:
        stmt = stmt.order_by(Product.name)
    if category:
        stmt = (
            stmt.join(ProductCategoryLink, ProductCategoryLink.product_id == Product.id)
            .join(Category, Category.id == ProductCategoryLink.category_id)
            .where(Category.slug == category)
        )
    ids = (await session.scalars(stmt.offset(offset).limit(limit + 1))).all()
    rules = await load_rule_context(session)
    items: list[ProductSummaryOut] = []
    for pid in ids[:limit]:
        b = await load_bundle(session, pid)
        if b is not None:
            items.append(product_summary(b, analyze(b, rules)))
    return Page[ProductSummaryOut](
        items=items, next_cursor=encode_cursor(offset + limit) if len(ids) > limit else None
    )


@router.get("/categories", response_model=Page[CategoryNodeOut])
async def categories(session: Session) -> Page[CategoryNodeOut]:
    counts = dict(
        (
            await session.execute(
                select(ProductCategoryLink.category_id, func.count()).group_by(
                    ProductCategoryLink.category_id
                )
            )
        ).all()
    )
    rows = (await session.scalars(select(Category).order_by(Category.path))).all()
    return Page[CategoryNodeOut](
        items=[
            CategoryNodeOut(
                id=str(c.id),
                slug=c.slug,
                name=c.name,
                name_hi=c.name_hi,
                parent_id=str(c.parent_id) if c.parent_id else None,
                product_count=int(counts.get(c.id, 0)),
            )
            for c in rows
        ],
        next_cursor=None,
    )


@router.get("/ingredients/{ingredient_id}", response_model=IngredientCardOut)
async def ingredient(session: Session, ingredient_id: str) -> IngredientCardOut:
    try:
        iid = uuid.UUID(ingredient_id)
    except ValueError as exc:
        raise ApiError(ErrorCode.NOT_FOUND, "Ingredient not found.") from exc
    ing = await session.get(Ingredient, iid)
    if ing is None:
        raise ApiError(ErrorCode.NOT_FOUND, "Ingredient not found.")
    additive = await session.scalar(select(Additive).where(Additive.ingredient_id == iid))
    aliases = (
        await session.scalars(select(IngredientAlias).where(IngredientAlias.ingredient_id == iid))
    ).all()
    return IngredientCardOut(
        id=str(ing.id),
        key=ing.key,
        name=ing.name,
        description=ing.description,
        is_additive=ing.is_additive,
        ins_code=additive.ins_code if additive else None,
        function=additive.function if additive else None,
        aliases=[{"lang": a.lang, "script": a.script, "alias": a.alias} for a in aliases],
        references=[],
        references_note="No reviewed scientific references yet; pending expert review.",
    )
