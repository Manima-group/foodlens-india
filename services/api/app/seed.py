"""Synthetic seed data (SPEC Tier 1): ≥ 60 products in ≥ 8 categories, incl. deliberately incomplete ones.

Every product and brand is fictional and labelled "(Synthetic)". GTINs use the restricted-circulation
prefix 29 so they cannot collide with real retail products (ADR 0005), except the SPEC §8.5 example.
Refuses to run when APP_ENV=production.

    uv run python -m app.seed
"""

import asyncio
import random
from dataclasses import dataclass, field
from datetime import UTC, datetime, timedelta

from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.analysis.engine import normalize_text
from app.catalog.service import analyze, load_bundle, load_rule_context, persist_analysis
from app.core.config import get_settings
from app.core.db import Base, get_engine, get_sessionmaker
from app.gtin import compute_check_digit, is_restricted_circulation
from app.models import (
    Additive,
    Allergen,
    Brand,
    Category,
    FieldProvenance,
    Ingredient,
    IngredientAlias,
    LabelAllergenDeclaration,
    LabelDietaryDeclaration,
    LabelIngredientItem,
    LabelNutrient,
    LabelServing,
    NutrientThreshold,
    Product,
    ProductCategoryLink,
    ProductGtin,
    ProductLabelVersion,
    ProductVariant,
    ScoringMethodology,
    ScoringRuleVersion,
    SourceRecord,
)

RULE_VERSION = "nutr-ind-0.1-placeholder"
REQUIRED = ["energy_kcal", "fat_g", "saturated_fat_g", "carbohydrate_g", "sugars_g", "protein_g", "sodium_mg"]
# Placeholder thresholds: NOT a validated methodology (is_placeholder=true; UI says "pending expert review").
THRESHOLDS = {
    "per_100g": [
        ("fat_g", 3, 17.5),
        ("saturated_fat_g", 1.5, 5),
        ("sugars_g", 5, 22.5),
        ("sodium_mg", 120, 600),
    ],
    "per_100ml": [
        ("fat_g", 1.5, 8.75),
        ("saturated_fat_g", 0.75, 2.5),
        ("sugars_g", 2.5, 11.25),
        ("sodium_mg", 120, 600),
    ],
}

# key, name, is_additive, ins_code, function, aliases[(lang, script, alias)], description
INGREDIENTS: list[tuple[str, str, bool, str | None, str | None, list[tuple[str, str, str]], str]] = [
    (
        "wheat_flour",
        "Wheat flour",
        False,
        None,
        None,
        [
            ("en", "Latn", "wheat flour"),
            ("en", "Latn", "atta"),
            ("en", "Latn", "refined wheat flour (maida)"),
            ("hi", "Deva", "गेहूं का आटा"),
            ("hi", "Deva", "मैदा"),
        ],
        "Flour milled from wheat. Contains gluten.",
    ),
    (
        "sugar",
        "Sugar",
        False,
        None,
        None,
        [("en", "Latn", "sugar"), ("hi", "Deva", "चीनी"), ("hi", "Deva", "शक्कर")],
        "Sucrose, usually from sugarcane.",
    ),
    (
        "salt",
        "Salt",
        False,
        None,
        None,
        [("en", "Latn", "salt"), ("en", "Latn", "iodised salt"), ("hi", "Deva", "नमक")],
        "Sodium chloride.",
    ),
    (
        "palmolein",
        "Palmolein oil",
        False,
        None,
        None,
        [
            ("en", "Latn", "palmolein"),
            ("en", "Latn", "edible vegetable oil (palmolein)"),
            ("hi", "Deva", "पामोलीन तेल"),
        ],
        "Liquid fraction of palm oil.",
    ),
    (
        "gram_flour",
        "Gram flour (besan)",
        False,
        None,
        None,
        [("en", "Latn", "gram flour"), ("en", "Latn", "besan"), ("hi", "Deva", "बेसन")],
        "Flour from chickpeas.",
    ),
    (
        "peanuts",
        "Peanuts",
        False,
        None,
        None,
        [("en", "Latn", "peanuts"), ("hi", "Deva", "मूंगफली")],
        "Groundnuts.",
    ),
    (
        "milk_solids",
        "Milk solids",
        False,
        None,
        None,
        [("en", "Latn", "milk solids"), ("en", "Latn", "milk powder"), ("hi", "Deva", "दूध के ठोस पदार्थ")],
        "Dried milk.",
    ),
    (
        "toned_milk",
        "Toned milk",
        False,
        None,
        None,
        [("en", "Latn", "toned milk"), ("hi", "Deva", "टोन्ड दूध")],
        "Milk.",
    ),
    (
        "cocoa_solids",
        "Cocoa solids",
        False,
        None,
        None,
        [("en", "Latn", "cocoa solids"), ("hi", "Deva", "कोको")],
        "Cocoa.",
    ),
    (
        "rolled_oats",
        "Rolled oats",
        False,
        None,
        None,
        [("en", "Latn", "rolled oats"), ("hi", "Deva", "जई")],
        "Oats.",
    ),
    (
        "corn_grits",
        "Corn grits",
        False,
        None,
        None,
        [("en", "Latn", "corn grits"), ("hi", "Deva", "मक्का")],
        "Maize.",
    ),
    ("rice", "Rice", False, None, None, [("en", "Latn", "rice"), ("hi", "Deva", "चावल")], "Rice."),
    (
        "lentils",
        "Lentils",
        False,
        None,
        None,
        [("en", "Latn", "lentils"), ("en", "Latn", "dal"), ("hi", "Deva", "दाल")],
        "Pulses.",
    ),
    (
        "tomato_paste",
        "Tomato paste",
        False,
        None,
        None,
        [("en", "Latn", "tomato paste"), ("hi", "Deva", "टमाटर का पेस्ट")],
        "Tomatoes.",
    ),
    ("water", "Water", False, None, None, [("en", "Latn", "water"), ("hi", "Deva", "पानी")], "Water."),
    (
        "fruit_pulp",
        "Fruit pulp",
        False,
        None,
        None,
        [("en", "Latn", "mango pulp"), ("en", "Latn", "fruit pulp"), ("hi", "Deva", "आम का गूदा")],
        "Fruit.",
    ),
    (
        "spices",
        "Spices and condiments",
        False,
        None,
        None,
        [("en", "Latn", "spices and condiments"), ("en", "Latn", "spices"), ("hi", "Deva", "मसाले")],
        "Mixed spices.",
    ),
    (
        "soy_lecithin",
        "Lecithin (soy)",
        True,
        "322",
        "Emulsifier",
        [("en", "Latn", "lecithin"), ("en", "Latn", "soy lecithin")],
        "Emulsifier, commonly from soybean.",
    ),
    (
        "sodium_bicarbonate",
        "Sodium bicarbonate",
        True,
        "500(ii)",
        "Raising agent",
        [("en", "Latn", "sodium bicarbonate"), ("en", "Latn", "raising agent (500(ii))")],
        "Raising agent.",
    ),
    (
        "citric_acid",
        "Citric acid",
        True,
        "330",
        "Acidity regulator",
        [("en", "Latn", "citric acid")],
        "Acidity regulator.",
    ),
    (
        "msg",
        "Monosodium glutamate",
        True,
        "621",
        "Flavour enhancer",
        [("en", "Latn", "flavour enhancer (621)"), ("en", "Latn", "monosodium glutamate")],
        "Flavour enhancer.",
    ),
    (
        "caramel_colour",
        "Caramel colour",
        True,
        "150d",
        "Colour",
        [("en", "Latn", "caramel colour (150d)")],
        "Colour.",
    ),
]

ALLERGENS = [
    ("cereals_gluten", "Cereals containing gluten", "ग्लूटेन युक्त अनाज"),
    ("milk", "Milk", "दूध"),
    ("peanut", "Peanut", "मूंगफली"),
    ("tree_nuts", "Tree nuts", "मेवे"),
    ("soy", "Soy", "सोया"),
    ("egg", "Egg", "अंडा"),
    ("fish", "Fish", "मछली"),
    ("crustacean", "Crustaceans", "क्रस्टेशियन"),
    ("sesame", "Sesame", "तिल"),
    ("mustard", "Mustard", "सरसों"),
    ("sulphite", "Sulphite", "सल्फाइट"),
]


@dataclass
class Template:
    slug: str
    name: str
    name_hi: str
    form: str
    unit: str
    pack: str
    sizes: list[float]
    serving: float
    macros: dict[str, tuple[float, float]]  # nutrient → (min, max) per 100
    products: list[str]
    ingredients: list[str]
    allergens: list[tuple[str, str]] = field(default_factory=list)


T = [
    Template(
        "namkeen-savoury-snacks",
        "Namkeen & savoury snacks",
        "नमकीन और स्नैक्स",
        "solid",
        "g",
        "pouch",
        [150, 200, 400],
        30,
        {
            "fat_g": (25, 38),
            "saturated_fat_g": (9, 16),
            "carbohydrate_g": (38, 52),
            "sugars_g": (1, 5),
            "protein_g": (8, 16),
            "sodium_mg": (500, 1100),
        },
        [
            "Masala Peanut Mix",
            "Aloo Bhujia",
            "Moong Dal Crunch",
            "Khatta Meetha Mix",
            "Ratlami Sev",
            "Cornflakes Chivda",
            "Navratan Mix",
        ],
        ["gram_flour", "palmolein", "peanuts", "lentils", "salt", "spices", "citric_acid"],
        [("peanut", "contains"), ("cereals_gluten", "may_contain")],
    ),
    Template(
        "biscuits-cookies",
        "Biscuits & cookies",
        "बिस्कुट और कुकीज़",
        "solid",
        "g",
        "pack",
        [75, 120, 250],
        25,
        {
            "fat_g": (12, 25),
            "saturated_fat_g": (6, 13),
            "carbohydrate_g": (62, 74),
            "sugars_g": (18, 34),
            "protein_g": (6, 9),
            "sodium_mg": (200, 450),
        },
        [
            "Glucose Biscuits",
            "Butter Cookies",
            "Cream Sandwich Biscuits",
            "Digestive Wheat Biscuits",
            "Coconut Cookies",
            "Marie Tea Biscuits",
            "Choco Chip Cookies",
        ],
        ["wheat_flour", "sugar", "palmolein", "milk_solids", "sodium_bicarbonate", "soy_lecithin", "salt"],
        [
            ("cereals_gluten", "contains"),
            ("milk", "contains"),
            ("soy", "contains"),
            ("tree_nuts", "may_contain"),
        ],
    ),
    Template(
        "breakfast-cereals",
        "Breakfast cereals",
        "नाश्ते के अनाज",
        "solid",
        "g",
        "box",
        [250, 500],
        30,
        {
            "fat_g": (1, 9),
            "saturated_fat_g": (0.3, 2),
            "carbohydrate_g": (62, 84),
            "sugars_g": (1, 30),
            "protein_g": (7, 13),
            "sodium_mg": (5, 600),
        },
        [
            "Corn Flakes",
            "Masala Oats",
            "Honey Muesli",
            "Chocolate Fills",
            "Plain Rolled Oats",
            "Wheat Flakes",
        ],
        ["corn_grits", "rolled_oats", "sugar", "salt", "spices"],
        [("cereals_gluten", "contains"), ("milk", "may_contain")],
    ),
    Template(
        "instant-noodles",
        "Instant noodles & pasta",
        "इंस्टेंट नूडल्स",
        "solid",
        "g",
        "pack",
        [70, 280],
        70,
        {
            "fat_g": (13, 19),
            "saturated_fat_g": (6, 9),
            "carbohydrate_g": (55, 64),
            "sugars_g": (1, 4),
            "protein_g": (8, 11),
            "sodium_mg": (900, 1500),
        },
        [
            "Masala Noodles",
            "Atta Noodles",
            "Chilli Garlic Noodles",
            "Curry Cup Noodles",
            "Vegetable Pasta Masala",
            "Tomato Noodles",
        ],
        ["wheat_flour", "palmolein", "salt", "spices", "msg"],
        [("cereals_gluten", "contains"), ("soy", "may_contain")],
    ),
    Template(
        "fruit-beverages",
        "Juices & fruit drinks",
        "जूस और फ्रूट ड्रिंक",
        "liquid",
        "ml",
        "tetra pack",
        [200, 1000],
        200,
        {
            "fat_g": (0, 0.2),
            "saturated_fat_g": (0, 0.1),
            "carbohydrate_g": (9, 16),
            "sugars_g": (8, 15),
            "protein_g": (0, 0.6),
            "sodium_mg": (3, 30),
        },
        ["Mango Drink", "Mixed Fruit Juice", "Guava Nectar", "Litchi Drink", "Orange Juice", "Apple Juice"],
        ["water", "fruit_pulp", "sugar", "citric_acid"],
    ),
    Template(
        "dairy-drinks",
        "Milk & dairy drinks",
        "दूध और डेयरी पेय",
        "liquid",
        "ml",
        "bottle",
        [180, 200, 500],
        200,
        {
            "fat_g": (1.5, 4),
            "saturated_fat_g": (1, 2.6),
            "carbohydrate_g": (5, 14),
            "sugars_g": (4.5, 12),
            "protein_g": (3, 4),
            "sodium_mg": (40, 120),
        },
        [
            "Toned Milk",
            "Chocolate Milk",
            "Masala Chaas",
            "Kesar Badam Milk",
            "Sweet Lassi",
            "Rose Flavoured Milk",
        ],
        ["toned_milk", "sugar", "cocoa_solids", "spices"],
        [("milk", "contains"), ("tree_nuts", "may_contain")],
    ),
    Template(
        "chocolates-confectionery",
        "Chocolates & confectionery",
        "चॉकलेट और मिठाई",
        "solid",
        "g",
        "wrapper",
        [13, 40, 120],
        20,
        {
            "fat_g": (24, 34),
            "saturated_fat_g": (14, 21),
            "carbohydrate_g": (52, 62),
            "sugars_g": (45, 56),
            "protein_g": (5, 8),
            "sodium_mg": (50, 150),
        },
        [
            "Milk Chocolate Bar",
            "Dark Chocolate 55%",
            "Wafer Chocolate",
            "Fruit and Nut Bar",
            "Caramel Toffee",
            "Choco Eclairs",
        ],
        ["sugar", "milk_solids", "cocoa_solids", "palmolein", "soy_lecithin"],
        [("milk", "contains"), ("soy", "contains"), ("tree_nuts", "may_contain"), ("peanut", "may_contain")],
    ),
    Template(
        "sauces-spreads",
        "Sauces, ketchups & spreads",
        "सॉस और स्प्रेड",
        "solid",
        "g",
        "bottle",
        [200, 500, 950],
        15,
        {
            "fat_g": (0.1, 2),
            "saturated_fat_g": (0, 0.5),
            "carbohydrate_g": (22, 32),
            "sugars_g": (18, 28),
            "protein_g": (1, 2),
            "sodium_mg": (800, 1300),
        },
        [
            "Tomato Ketchup",
            "Chilli Sauce",
            "Tamarind Chutney",
            "Green Chilli Sauce",
            "Schezwan Chutney",
            "Mint Mayonnaise Style Dip",
        ],
        ["tomato_paste", "sugar", "salt", "spices", "citric_acid", "water"],
        [("mustard", "may_contain")],
    ),
    Template(
        "ready-to-eat",
        "Ready-to-eat meals",
        "तैयार भोजन",
        "solid",
        "g",
        "retort pouch",
        [285, 300],
        285,
        {
            "fat_g": (3, 9),
            "saturated_fat_g": (1, 4.5),
            "carbohydrate_g": (10, 22),
            "sugars_g": (1, 4),
            "protein_g": (3, 7),
            "sodium_mg": (350, 650),
        },
        ["Dal Makhani", "Rajma Masala", "Veg Pulao", "Palak Paneer", "Chana Masala", "Kadhi Pakora"],
        ["lentils", "water", "tomato_paste", "rice", "milk_solids", "spices", "salt"],
        [("milk", "contains")],
    ),
    Template(
        "bakery",
        "Breads & bakery",
        "ब्रेड और बेकरी",
        "solid",
        "g",
        "loaf",
        [200, 400],
        50,
        {
            "fat_g": (1.5, 6),
            "saturated_fat_g": (0.5, 2.5),
            "carbohydrate_g": (44, 55),
            "sugars_g": (2, 8),
            "protein_g": (7, 11),
            "sodium_mg": (350, 550),
        },
        ["Whole Wheat Bread", "Milk Bread", "Multigrain Bread", "Pav Buns", "Brown Bread", "Fruit Rusk"],
        ["wheat_flour", "water", "sugar", "salt", "palmolein"],
        [("cereals_gluten", "contains"), ("milk", "may_contain"), ("sesame", "may_contain")],
    ),
]

BRANDS = [
    "Demo Snacks Co",
    "Sample Foods Pvt (Synthetic)",
    "Example Dairy (Synthetic)",
    "Placeholder Bakers (Synthetic)",
    "Test Kitchen Brands (Synthetic)",
    "Mock Beverages (Synthetic)",
]


def synthetic_gtin(n: int) -> str:
    body = f"29{n:010d}"
    return "0" + body + str(compute_check_digit(body))


def _energy(m: dict[str, float]) -> float:
    return round(4 * m["carbohydrate_g"] + 4 * m["protein_g"] + 9 * m["fat_g"], 1)


async def _reference_data(
    s: AsyncSession,
) -> tuple[dict[str, Ingredient], dict[str, Allergen], dict[str, Category], SourceRecord]:
    m = ScoringMethodology(version="pending", status="draft", approvals=0)
    s.add(m)
    await s.flush()
    rule = ScoringRuleVersion(
        methodology_id=m.id, version=RULE_VERSION, required_fields=REQUIRED, is_active=True
    )
    s.add(rule)
    await s.flush()
    for basis, rows in THRESHOLDS.items():
        for nutrient, lo, hi in rows:
            s.add(
                NutrientThreshold(
                    rule_version_id=rule.id,
                    nutrient=nutrient,
                    basis=basis,
                    low_max=lo,
                    high_min=hi,
                    is_placeholder=True,
                )
            )
    ingredients: dict[str, Ingredient] = {}
    for key, name, is_add, ins, fn, aliases, desc in INGREDIENTS:
        ing = Ingredient(key=key, name=name, is_additive=is_add, description=desc)
        s.add(ing)
        await s.flush()
        ingredients[key] = ing
        for lang, script, alias in aliases:
            s.add(
                IngredientAlias(ingredient_id=ing.id, lang=lang, script=script, alias=normalize_text(alias))
            )
        if ins:
            s.add(Additive(ingredient_id=ing.id, ins_code=ins, function=fn))
    allergens = {}
    for code, allergen_name, allergen_hi in ALLERGENS:
        a = Allergen(code=code, name=allergen_name, name_hi=allergen_hi)
        s.add(a)
        allergens[code] = a
    categories = {}
    for t in T:
        c = Category(slug=t.slug, name=t.name, name_hi=t.name_hi, path=t.slug, product_form=t.form)
        s.add(c)
        categories[t.slug] = c
    src = SourceRecord(
        source="synthetic_seed",
        licence_code="SYNTHETIC",
        retrieved_at=datetime.now(UTC),
        reference="services/api/app/seed.py",
    )
    s.add(src)
    await s.flush()
    return ingredients, allergens, categories, src


async def seed(s: AsyncSession) -> int:
    rng = random.Random(20261123)  # noqa: S311 - deterministic synthetic data, not security
    ingredients, allergens, categories, src = await _reference_data(s)
    brands = [Brand(name=b, is_verified=False) for b in BRANDS]
    s.add_all(brands)
    await s.flush()
    now = datetime.now(UTC)
    n = 0
    product_ids = []
    for ti, t in enumerate(T):
        for pi, pname in enumerate(t.products):
            n += 1
            is_demo = ti == 0 and pi == 0
            name = "Masala Crunch Namkeen (Synthetic)" if is_demo else f"{pname} (Synthetic)"
            brand = brands[0] if is_demo else brands[(ti + pi) % len(brands)]
            p = Product(brand_id=brand.id, name=name, is_published=True, is_synthetic=True)
            s.add(p)
            await s.flush()
            product_ids.append(p.id)
            size = 150 if is_demo else t.sizes[pi % len(t.sizes)]
            v = ProductVariant(product_id=p.id, net_quantity=size, unit=t.unit, pack_format=t.pack)
            s.add(v)
            await s.flush()
            code = "8901234567890" if is_demo else synthetic_gtin(n)
            gtin14 = code.zfill(14)
            s.add(
                ProductGtin(
                    product_id=p.id,
                    variant_id=v.id,
                    gtin14=gtin14,
                    original_code=code.lstrip("0") if not is_demo else code,
                    symbology="EAN_13",
                    is_restricted_circulation=is_restricted_circulation(gtin14),
                )
            )
            s.add(ProductCategoryLink(product_id=p.id, category_id=categories[t.slug].id))

            # Deliberately varied completeness/evidence (SPEC Tier 1: incl. deliberately incomplete ones).
            variant_kind = n % 9
            evidence, review = "reviewer_verified", "approved"
            if variant_kind == 4:
                evidence, review = "community_submitted", "submitted"  # → unverified_provisional
            elif variant_kind == 6:
                evidence = "label_declared"
            basis = "per_100ml" if t.form == "liquid" else "per_100g"
            values = {k: round(rng.uniform(lo, hi), 1) for k, (lo, hi) in t.macros.items()}
            values["saturated_fat_g"] = min(values["saturated_fat_g"], values["fat_g"])
            values["sugars_g"] = min(values["sugars_g"], values["carbohydrate_g"])
            values["sodium_mg"] = round(values["sodium_mg"])
            values["energy_kcal"] = _energy(values)
            if is_demo:
                values = {
                    "energy_kcal": 512,
                    "fat_g": 30,
                    "saturated_fat_g": 12,
                    "carbohydrate_g": 52,
                    "sugars_g": 4,
                    "protein_g": 8,
                    "sodium_mg": 820,
                }
            serving_size: float | None = t.serving
            if variant_kind == 2:
                values.pop("sodium_mg")  # missing sodium → insufficient_data
            elif variant_kind == 3:
                values["salt_g"] = round(values.pop("sodium_mg") * 2.5 / 1000, 2)  # salt-only label
            elif variant_kind == 5 and t.form == "solid":
                values.pop("saturated_fat_g")
                values.pop("sugars_g")
            elif variant_kind == 7:
                basis = "per_serving"
                factor = t.serving / 100
                values = {k: round(v * factor, 2) for k, v in values.items()}
                if n % 2 == 1:
                    serving_size = None  # per-serving only, no serving size → insufficient_data
            elif variant_kind == 8:
                values["energy_kj"] = round(values.pop("energy_kcal") * 4.184)  # kJ-only label
            label = ProductLabelVersion(
                product_id=p.id,
                observed_at=now - timedelta(days=30 + n * 3),
                evidence_level=evidence,
                review_state=review,
                is_current=True,
                nutrient_basis=basis,
                ingredients_complete=variant_kind != 1,
            )
            s.add(label)
            await s.flush()
            if serving_size:
                s.add(
                    LabelServing(
                        label_version_id=label.id,
                        serving_size=serving_size,
                        serving_unit=t.unit,
                        servings_per_pack=round(size / serving_size, 1),
                    )
                )
            for k, val in values.items():
                s.add(LabelNutrient(label_version_id=label.id, nutrient=k, amount=val))
            hindi = n % 13 == 0
            items = t.ingredients[: 3 + (pi % (len(t.ingredients) - 2))]
            for pos, key in enumerate(items):
                ing = ingredients[key]
                hi_alias = next(
                    (a for lg, _, a in INGREDIENTS[[x[0] for x in INGREDIENTS].index(key)][5] if lg == "hi"),
                    None,
                )
                raw = hi_alias if (hindi and hi_alias) else ing.name
                s.add(
                    LabelIngredientItem(
                        label_version_id=label.id,
                        position=pos,
                        raw_text=raw,
                        ingredient_id=ing.id,
                        match_confidence=1.0,
                    )
                )
            if variant_kind == 1:
                s.add(
                    LabelIngredientItem(
                        label_version_id=label.id,
                        position=len(items),
                        raw_text="Natural flavouring substances",
                        ingredient_id=None,
                        match_confidence=0.0,
                    )
                )
            for code_, kind in t.allergens:
                s.add(
                    LabelAllergenDeclaration(
                        label_version_id=label.id, allergen_id=allergens[code_].id, kind=kind
                    )
                )
            s.add(LabelDietaryDeclaration(label_version_id=label.id, kind="veg_mark"))
            for fname in [*values.keys(), "ingredients", "allergens"]:
                s.add(
                    FieldProvenance(
                        entity_type="label_version",
                        entity_id=label.id,
                        field_name=fname,
                        source_record_id=src.id,
                        evidence_level=evidence,
                        observed_at=label.observed_at,
                        licence_code="SYNTHETIC",
                    )
                )
    await s.flush()
    rules = await load_rule_context(s)
    for pid in product_ids:
        bundle = await load_bundle(s, pid)
        assert bundle is not None
        await persist_analysis(s, bundle, rules, analyze(bundle, rules))
    await s.commit()
    return n


async def reset_and_seed() -> int:
    if get_settings().app_env == "production":
        raise SystemExit("Refusing to seed: APP_ENV=production")
    engine = get_engine()
    async with engine.begin() as conn:
        for table in reversed(Base.metadata.sorted_tables):
            await conn.execute(delete(table))
    async with get_sessionmaker()() as s:
        count = await seed(s)
        total = await s.scalar(select(Product.id).limit(1))
        assert total is not None
    return count


if __name__ == "__main__":
    print(f"Seeded {asyncio.run(reset_and_seed())} synthetic products.")
