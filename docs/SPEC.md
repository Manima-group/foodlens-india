# FoodLens India — Build Context & Engineering Specification (v2)

> **Purpose of this file:** the single source of truth a coding agent (Claude Code) or engineering team reads before writing code. It replaces the v1 "master blueprint" and adds the engineering decisions v1 left open: scope tiers, data contracts, Android architecture, GTIN rules with verified test vectors, error model, auth split, performance budgets, testing tools and Play release mechanics.
>
> **Place in repo as:** `docs/SPEC.md`. Pair with the root `CLAUDE.md` (agent working rules).
>
> **Date:** 23 November 2026 · **Working name:** FoodLens India (placeholder; clear trademark/domain before release) · **Implementer:** Manima AI-Tech Solutions

---

## 0. How to use this spec

1. Build **tier by tier** (§3). A tier is finished only when its Definition of Done passes. Do not start a tier while the previous one is red.
2. Where this spec gives an exact enum, schema, status code or rule — **use it verbatim**. Where it says "decide", record the decision in `docs/architecture/adr/NNNN-title.md`.
3. Anything needing external credentials, contracts, counsel or scientific sign-off is built as a **disabled adapter behind a feature flag** and listed in `docs/BLOCKERS.md`. Never invent credentials, endorsements, lab results or scores.
4. Honest reporting: "bug-free" is not a deliverable. Deliverables are passing automated checks + `docs/RISK_REGISTER.md`.

---

## 1. Product summary (unchanged intent from v1)

A free Android app that scans packaged-food barcodes sold in India and explains the label: ingredients, additives (INS), nutrition per 100 g/ml and per serving, allergen *declarations*, processing indicators, and comparable alternatives. Unknown products can be contributed via photos + OCR + user confirmation + human moderation. Brands pay only for transparently labelled presentation, tools, ads and analytics — **never for grades, rankings or suppression**. A web brand portal and a web admin console share one FastAPI backend.

### 1.1 Non-negotiable trust rules (enforced in code and tests)

| # | Rule | Enforcement point |
|---|---|---|
| T1 | Payment never changes score, flags, organic ranking or visibility of unfavourable data | Scoring/ranking modules have **no import path** to billing/campaign modules (checked by an import-linter contract in CI) |
| T2 | Every paid placement shows visible **"Sponsored"** text + placement reason, also in accessibility labels | `SponsoredCard` is the only component allowed to render campaign content; UI test asserts text + contentDescription |
| T3 | Catalog inclusion is free | No plan entitlement gates `products.is_published` |
| T4 | Missing data ⇒ `insufficient_data`, never 0 or a guessed score | `analysis_status` enum + test vectors |
| T5 | Contaminant/lab claims only from a verified, dated, batch-linked report | `laboratory_reports.status = 'verified'` required to render; else "No verified laboratory information available" |
| T6 | Every material fact carries an evidence level | `evidence_level` enum on facts (§6.3) |
| T7 | Allergen/veg/diet statements are shown as *declared on label* with source + confidence; never guaranteed | UI copy constants + snapshot tests |
| T8 | Public score disabled until an approved methodology version exists | `scoring_methodologies.status='published'` with 2 approvals; default status `methodology_pending` |
| T9 | Licensed sources only; OFF attribution (ODbL) shown wherever OFF data appears | `field_provenance.source` drives an attribution footer |
| T10 | Core app usable without login, payment or personal data | E2E test runs full scan → product → alternatives as guest |

Do not imitate Ivy branding, copy, assets, methodology or code. Do not describe the product as FSSAI-, GS1- or medically-endorsed.

---

## 2. Glossary & fixed enums (use verbatim)

```text
analysis_status:   scored | insufficient_data | methodology_pending | unverified_provisional | not_applicable
evidence_level:    label_declared | community_submitted | manufacturer_submitted | reviewer_verified | laboratory_tested
review_state:      draft | submitted | needs_changes | approved | rejected | archived
contribution_state:draft | photos_uploaded | extracting | awaiting_confirmation | submitted | in_review | accepted | rejected | spam
claim_state:       pending | evidence_requested | verified | disputed | rejected | revoked
org_verification:  unverified | pending | verified | suspended
lab_report_status: received | under_review | verified | rejected | expired
payment_state:     created | attempted | paid | failed | refunded | partially_refunded
subscription_state:trialing | active | past_due | grace | cancelled | expired
campaign_state:    draft | pending_approval | approved | active | paused | completed | rejected
placement_slot:    product_detail_sponsored | search_sponsored | discover_sponsored | brand_storefront
nutrient_basis:    per_100g | per_100ml | per_serving
data_freshness:    fresh | stale | unknown
symbology:         EAN_13 | EAN_8 | UPC_A | UPC_E | GTIN_14 | MANUAL
```

---

## 3. Scope tiers (priority order, not a schedule)

Each tier has a **Definition of Done (DoD)**. All DoDs also require: lint/type checks green, tests green in CI, OpenAPI regenerated, migrations up/down clean, docs updated.

### Tier 1 — Core consumer loop (the product must be useful here)
- Monorepo, Docker Compose stack, CI skeleton, env config.
- DB migrations for identity (guest + user), catalog, nutrition, ingredients, additives, allergens, provenance, analysis.
- GTIN module (Python + Kotlin) with shared fixtures.
- API: health, meta/config, by-barcode, product, analysis, explain, sources, search, categories, ingredients, alternatives, compare.
- Analysis engine: nutrition normalisation, completeness check, nutrient indicators with **placeholder thresholds clearly flagged `is_placeholder=true`**, `analysis_status` logic; public score stays `methodology_pending`.
- Synthetic seed: ≥ 60 synthetic products in ≥ 8 categories, incl. deliberately incomplete ones.
- Android: onboarding, guest mode, scanner, manual entry, product detail, nutrients, ingredients, alternatives, compare, search, history (local), offline cache, Hindi + English.
- **DoD:** guest scans a seeded barcode on emulator (or injected image) → sees product in < 3 s on emulator; incomplete product shows "Insufficient data to score"; offline re-open of cached product works; E2E test passes.

### Tier 2 — Contributions & moderation
- Presigned uploads (MinIO locally), contribution flow, server OCR worker (Tesseract `eng+hin` baseline), field-level extraction records, user confirmation.
- Android: missing-product flow with WorkManager uploads, EXIF stripping, crop/rotate, confirmation UI.
- Admin web: login + MFA, review queue, side-by-side image/OCR/fields, approve/reject/merge, audit log.
- Registered consumer: sign-in, cloud history/favourites, export, delete.
- **DoD:** contribution submitted from Android while offline completes when back online; admin approves; product becomes public with `evidence_level=reviewer_verified`; account deletion job removes user data and is tested.

### Tier 3 — Brand portal & claims
- Org onboarding, verification queue, GTIN claims with disputes, change proposals (never direct writes), CSV bulk import with validation preview, versioning, storefront.
- **DoD:** cross-tenant IDOR test suite passes; a brand cannot alter a published fact without reviewer approval; GTIN reassignment requires dispute resolution.

### Tier 4 — Billing, campaigns, analytics
- Plans/entitlements (admin-configurable), Razorpay **test mode** orders + signed webhooks, immutable ledger, GST invoice PDF, refunds/credit notes, grace/dunning.
- Campaign builder (contextual targeting only: category, language, region), approval, `SponsoredCard` delivery, daily aggregates, k-anonymity threshold (≥ 20) on analytics.
- **DoD:** webhook replay/duplicate/out-of-order tests pass; paid campaign provably does not change organic alternative order (property test).

### Tier 5 — Hardening & release prep
- Scoring methodology workflow (dual approval, publish/rollback), lab-report module, OFF adapter (flagged), observability, backups/restore drill, security tests, accessibility audit, Play listing drafts, Data safety answers, release AAB pipeline (signing via CI secrets only).
- **DoD:** release-blocking quality gates (§15) all green or explicitly waived in RISK_REGISTER with owner.

---

## 4. Monorepo layout

```
foodlens-india/
  CLAUDE.md
  README.md
  .env.example
  Makefile                      # make up | down | seed | test | lint | openapi | android-test | android-debug
  apps/
    android-consumer/
    web/                        # ONE Next.js app: route groups (brand) and (admin)
  services/
    api/
      app/
        core/        # config, db, security, errors, pagination, request-id, rate-limit, outbox
        auth/  catalog/  gtin/  ingredients/  analysis/  alternatives/
        contributions/  moderation/  brands/  billing/  campaigns/
        privacy/  integrations/  admin/  meta/
      alembic/
      tests/
    workers/                    # Celery app; tasks import from services/api/app
  packages/
    openapi/                    # openapi.json (generated, committed)
    ts-client/                  # generated TS client for web
    ui-tokens/                  # tokens.json → Tailwind config + Android Kotlin tokens (generated)
    fixtures/                   # SHARED test vectors: gtin.json, nutrition.json, analysis.json
  infra/
    docker/                     # Dockerfiles (multi-stage)
    compose/docker-compose.yml
    deploy/                     # reverse proxy, example prod compose, runbooks refs
  docs/
    SPEC.md  BLOCKERS.md  RISK_REGISTER.md  DECISIONS.md
    architecture/adr/  api/  db-schema/  methodology/  source-licenses/
    compliance/  privacy/  operations/  runbooks/  testing/  play-store/
  tests/
    e2e/  security/  load/
  .github/workflows/
```

**Decision (fixed):** one Next.js app with `(brand)` and `(admin)` route groups, separate layouts, separate session cookie scopes and server-enforced role checks. Halves auth/deploy work vs two apps. Admin routes are additionally IP-allowlist-capable via config.

---

## 5. Tech stack (pin exact versions in lockfiles at build time)

| Layer | Choice | Notes |
|---|---|---|
| API | Python 3.12, FastAPI, Pydantic v2, SQLAlchemy 2 (async, asyncpg), Alembic | `uv` for dependency management |
| Workers | Celery + Redis (Valkey acceptable) | Beat for scheduled jobs |
| DB | PostgreSQL 16 + `pg_trgm`, `unaccent`, `citext` | |
| Object storage | S3 API; **MinIO** locally | Private buckets, presigned PUT/GET |
| OCR (server) | Tesseract 5 (`eng`, `hin`) baseline; PaddleOCR behind flag after benchmark | |
| Email (dev) | Mailpit | |
| Web | Next.js (current stable, App Router), TypeScript strict, Tailwind, Radix primitives | |
| Android | Kotlin 2.x, Compose (BOM), Material 3, Hilt, Room, DataStore, CameraX, ML Kit, WorkManager, Retrofit + OkHttp + kotlinx.serialization, Coil, Navigation Compose (type-safe routes) | Versions in `gradle/libs.versions.toml` |
| Payments | Razorpay (test mode) | |
| Telemetry | OpenTelemetry, Prometheus, Sentry-compatible SDK | Consent-aware on Android |
| CI | GitHub Actions | |

Python quality: Ruff (lint+format), mypy `--strict` on `app/`, pytest + pytest-asyncio, coverage ≥ 80 % on `gtin`, `analysis`, `billing`, `auth`.

---

## 6. Data model

### 6.1 Conventions
- UUID v7 primary keys (time-ordered), `created_at`, `updated_at` (timestamptz, UTC), `created_by` nullable FK.
- All timestamps stored UTC; displayed in `Asia/Kolkata` by default.
- Money stored as `bigint` **paise**; currency column `INR`.
- Real FKs, CHECK constraints for enums (PostgreSQL enums or check constraints — decide once, ADR).
- Soft delete only on `products`, `organizations`, `brand_campaigns` (`archived_at`).
- Financial tables (`payment_transactions`, `invoices`, `invoice_line_items`, `ledger_entries`) are **append-only**: DB trigger rejects UPDATE/DELETE.

### 6.2 Core tables (Tier 1 must-haves in **bold**)

**Identity:** **`users`**, `user_identities`, **`guest_devices`** (random installation ID, rotated every 90 days, no hardware IDs), `roles`, `permissions`, `user_role_assignments`, `organizations`, `organization_members`, `organization_verifications`, `sessions`, `refresh_tokens` (hashed, family ID for rotation), `consents`, `data_subject_requests`.

**Catalog:** **`brands`**, **`products`**, **`product_variants`** (pack size/format), **`product_gtins`**, **`categories`** (tree via `parent_id` + `path ltree` or materialised path), **`product_category_links`**, **`product_images`**, **`product_label_versions`** (formulation revisions; nutrition/ingredients belong to a label version, not to the product directly), **`label_nutrients`**, **`label_servings`**, **`ingredients`**, **`ingredient_aliases`** (lang, script, alias), **`label_ingredient_items`** (ordered, raw text, matched ingredient_id nullable, match_confidence), **`additives`** (INS/E code), **`allergens`**, **`label_allergen_declarations`** (`contains` | `may_contain`), **`label_dietary_declarations`** (veg mark etc.), **`field_provenance`**, **`source_records`**, `brand_claims`, `catalog_contributions`, `contribution_photos`, `ocr_extractions`, `extracted_fields`, `moderation_decisions`, `product_corrections`.

**Analysis/trust:** **`scoring_methodologies`**, **`scoring_rule_versions`**, **`nutrient_thresholds`**, **`product_analyses`**, **`product_analysis_factors`**, `scientific_references`, `ingredient_references`, `laboratory_reports`, `lab_report_batches`, `quality_disputes`, `trust_audit_events`.

**Consumer:** `scan_events` (no user ID for guests; hashed device ID, coarse timestamp to hour, GTIN, app version), `user_history`, `favorites`, `notification_preferences`, `product_feedback`, `support_tickets`.

**Commercial:** `plans`, `plan_entitlements`, `brand_subscriptions`, `payment_orders`, `payment_transactions`, `payment_webhook_events` (unique `provider_event_id`), `ledger_entries`, `invoices`, `invoice_line_items`, `invoice_number_sequences`, `tax_profiles`, `refunds`, `credit_notes`, `brand_campaigns`, `campaign_creatives`, `campaign_placements`, `campaign_impressions_daily`, `campaign_clicks_daily`.

**Platform:** `admin_audit_logs`, `integration_configurations`, `feature_flags`, `data_import_jobs`, `outbox_events`, `worker_job_failures`, `user_notifications`, `legal_documents` (versioned), `translation_strings`, `privacy_deletion_jobs`, `idempotency_keys`.

### 6.3 Key constraints
- `product_gtins.gtin14 char(14)` **UNIQUE** where `market='IN'` (unique on `(gtin14, market)`), CHECK `gtin14 ~ '^[0-9]{14}$'`, plus `original_code`, `symbology`.
- `field_provenance(entity_type, entity_id, field_name, source_record_id, evidence_level, observed_at, licence_code)` — every public nutrient, ingredient list and allergen declaration has ≥ 1 row.
- `product_analyses` unique `(label_version_id, rule_version_id)`; index `(product_id, rule_version_id)`.
- Partial index `products(id) WHERE is_published AND archived_at IS NULL`.
- Trigram GIN indexes on `products.name`, `brands.name`, `ingredient_aliases.alias`.
- Tenant tables carry `organization_id`; every brand-scoped repository query takes `org_id` as a required argument (enforced by a base repository class + tests).
- Plan quotas enforced with `SELECT … FOR UPDATE` on the subscription row inside the mutating transaction.

---

## 7. GTIN rules (shared Kotlin + Python; most error-prone area)

**Canonical form:** GTIN-14 string, left-padded with zeros. Keep `original_code` and `symbology` for display.

**Algorithm:**
1. Strip whitespace/hyphens. Reject non-digits → `422 INVALID_BARCODE`.
2. Length 8 → could be EAN-8 **or** UPC-E. Use the scanner's reported symbology; for manual entry try EAN-8 check first, then UPC-E (only if first digit is 0 or 1) and, if both valid, return `409 AMBIGUOUS_BARCODE` with both candidates for the client to choose.
3. UPC-E → expand to UPC-A (rules below), then treat as UPC-A.
4. Lengths 12, 13, 14 → validate check digit (Mod-10, weights 3,1,3,… from the rightmost data digit).
5. Pad to 14 digits.
6. Lengths other than 8/12/13/14 → `422 INVALID_BARCODE`.
7. **Never strip leading zeros** in storage, URLs or JSON (always strings).
8. Restricted-circulation prefixes (GTIN-13 starting `02`, `04`, `20`–`29`) → accept for lookup but flag `is_restricted_circulation=true`; never merge across stores.

**UPC-E expansion** (`N d1 d2 d3 d4 d5 d6 C`, N ∈ {0,1}):
| d6 | Manufacturer | Product |
|---|---|---|
| 0,1,2 | d1 d2 d6 0 0 | 0 0 d3 d4 d5 |
| 3 | d1 d2 d3 0 0 | 0 0 0 d4 d5 |
| 4 | d1 d2 d3 d4 0 | 0 0 0 0 d5 |
| 5–9 | d1 d2 d3 d4 d5 | 0 0 0 0 d6 |

UPC-A = N + manufacturer + product + C.

**Verified test vectors** (commit to `packages/fixtures/gtin.json`; both languages must pass all):

| Input | Symbology | Expected GTIN-14 | Note |
|---|---|---|---|
| `8901234567890` | EAN_13 | `08901234567890` | valid, India prefix 890 |
| `8901234567891` | EAN_13 | error `INVALID_CHECK_DIGIT` | bad check digit |
| `8900000000012` | EAN_13 | `08900000000012` | valid |
| `4006381333931` | EAN_13 | `04006381333931` | valid |
| `036000291452` | UPC_A | `00036000291452` | valid |
| `0036000291452` | EAN_13 | `00036000291452` | **same GTIN** as row above — equivalence, not collision |
| `96385074` | EAN_8 | `00000096385074` | valid |
| `12345670` | EAN_8 | `00000012345670` | valid |
| `04252614` | UPC_E | `00042100005264` | expands to `042100005264` |
| `01234565` | UPC_E | `00012345000065` | expands to `012345000065` |
| `00123457` | UPC_E | `00001234000057` | expands to `001234000057` |
| `0000001234565` | EAN_13 | `00000001234565` | leading zeros preserved |
| `89012345` + letter | — | error `INVALID_BARCODE` | non-digit |
| `123` | — | error `INVALID_BARCODE` | bad length |

Add property tests: for random 11/12/13-digit bodies, `compute_check_digit` then `normalize` round-trips; normalize is idempotent.

---

## 8. API contract

### 8.1 Conventions
- Prefix `/api/v1`. JSON, `snake_case`. All IDs are UUID strings; GTINs are strings.
- Request ID: accept `X-Request-ID` or generate; echo in response header and error body.
- Pagination: cursor-based, `?limit=` (max 50) `&cursor=`; response `{ "items": [...], "next_cursor": "..." | null }`.
- Idempotency: `Idempotency-Key` header **required** on `POST /billing/orders`, `/brand/products/bulk-import`, `/contributions/{id}/confirm`; stored 24 h in `idempotency_keys`.
- Caching: `ETag` on product and analysis responses; Android sends `If-None-Match`.
- Rate limits (Redis token bucket), returned as `RateLimit-*` headers; `429` with `retry_after_seconds`.

### 8.2 Error envelope (all non-2xx)
```json
{
  "error": {
    "code": "PRODUCT_NOT_FOUND",
    "message": "No product found for this barcode.",
    "details": {"gtin14": "08901234567890"},
    "request_id": "01JD…"
  }
}
```
Fixed codes (extend only via ADR): `VALIDATION_ERROR` 422, `INVALID_BARCODE` 422, `INVALID_CHECK_DIGIT` 422, `AMBIGUOUS_BARCODE` 409, `PRODUCT_NOT_FOUND` 404, `NOT_FOUND` 404, `UNAUTHENTICATED` 401, `FORBIDDEN` 403, `CONFLICT` 409, `IDEMPOTENCY_CONFLICT` 409, `RATE_LIMITED` 429, `QUOTA_EXCEEDED` 402, `UPSTREAM_UNAVAILABLE` 503, `MAINTENANCE` 503, `APP_VERSION_UNSUPPORTED` 426, `INTERNAL` 500. Android maps codes (not messages) to localized strings.

### 8.3 Endpoint list
Same surface as v1 §11, plus: `GET /products/{id}/analysis/explain`, `GET /meta/config`, `POST /guest/register`, `POST /scan-events` (batched, guest-safe), `GET /me/contributions`, `POST /contributions/{id}/photos/{photo_id}/complete`.

### 8.4 `GET /meta/config` (Android calls on launch; cache 6 h)
```json
{
  "min_supported_app_version_code": 1,
  "latest_app_version_code": 1,
  "maintenance": false,
  "features": {"contributions": true, "public_score": false, "sponsored": false, "off_lookup": false, "google_sign_in": false},
  "legal": {"privacy_version": "2026-11-01", "terms_version": "2026-11-01", "methodology_version": "pending"},
  "supported_locales": ["en", "hi"]
}
```

### 8.5 `GET /products/by-barcode/{code}` — 200 example
```json
{
  "product": {
    "id": "0193…",
    "gtin14": "08901234567890",
    "original_code": "8901234567890",
    "name": "Masala Crunch Namkeen (Synthetic)",
    "brand": {"id": "0193…", "name": "Demo Snacks Co", "is_verified": false},
    "variant": {"net_quantity": 150, "unit": "g", "pack_format": "pouch"},
    "category": {"id": "…", "slug": "namkeen-savoury-snacks", "name": "Namkeen & savoury snacks"},
    "images": [{"kind": "front", "url": "https://…signed…", "licence": "CC-BY-SA-3.0", "attribution": "…"}],
    "label_version": {"id": "…", "observed_at": "2026-10-02T00:00:00Z", "evidence_level": "reviewer_verified"},
    "data_freshness": "fresh",
    "completeness": {"nutrition": true, "ingredients": true, "allergens": true, "missing_fields": []},
    "badges": ["label_declared_veg"]
  },
  "analysis_summary": {
    "analysis_status": "methodology_pending",
    "score_value": null,
    "confidence": null,
    "rule_version": "nutr-ind-0.1-placeholder",
    "headline_indicators": [
      {"nutrient": "sodium", "level": "high", "is_placeholder_threshold": true, "basis": "per_100g"}
    ]
  },
  "sponsored": []
}
```
404 → `PRODUCT_NOT_FOUND` with `details.can_contribute = true`.

### 8.6 `GET /products/{id}/analysis/explain`
```json
{
  "analysis_status": "insufficient_data",
  "score_value": null,
  "methodology": {"id": "…", "version": "pending", "status": "draft"},
  "rule_version": "nutr-ind-0.1-placeholder",
  "inputs": [
    {"field": "energy_kcal", "value": 512, "basis": "per_100g", "source": "label_declared", "observed_at": "2026-10-02T00:00:00Z"}
  ],
  "missing_fields": ["saturated_fat_g", "sodium_mg"],
  "factors": [],
  "limitations": ["Score requires saturated fat and sodium; not declared on submitted label."]
}
```

### 8.7 Auth split (fixed)
- **Android:** short-lived JWT access token (15 min) + opaque rotating refresh token (30 days, family revocation on reuse). Guests get a guest token from `POST /guest/register` (random install ID; no PII) with limited scopes.
- **Web:** server session in HTTP-only, Secure, SameSite=Lax cookie; CSRF token on mutations; TOTP MFA mandatory for brand admins and all staff roles; WebAuthn/passkeys as an addition (flag).
- Authorization in FastAPI dependencies: `require_permission("catalog.review")` + object-level `require_org_member(org_id)`. UI hiding is cosmetic only.

---

## 9. Analysis engine

Modules (pure functions, no DB access inside, fully unit-testable):

1. **`normalize_nutrition(label) -> NormalizedNutrition`** — convert to per 100 g/ml and per serving; kJ↔kcal (1 kcal = 4.184 kJ); salt↔sodium (salt g = sodium g × 2.5) with reconciliation warning if both declared and differ > 10 %; plausibility checks (macros sum ≤ 100 g per 100 g; energy within ±15 % of 4/4/9 estimate → else `plausibility_warning`). Liquids need density only if the label mixes g and ml — never assume density; mark `basis_uncertain`.
2. **`check_completeness(normalized, rules) -> Completeness`** — required fields come from the rule version, not code.
3. **`nutrient_indicators(normalized, category, thresholds)`** — low/medium/high per nutrient. Thresholds stored in DB with `reference_id` and `is_placeholder`. Seed placeholders are labelled `is_placeholder=true` and the UI shows "Indicative thresholds — pending expert review".
4. **`processing_indicator(ingredients)`** — only returns a value if ingredient list is complete and every item is matched; else `null` + reason.
5. **`allergen_matches(declarations, user_prefs)`** — matches *declarations only*; output always includes `source` and `evidence_level`.
6. **`compute_score(...)`** — interface implemented; production returns `methodology_pending` unless a `published` methodology exists. A `TestOnlyPlaceholderScorer` exists in `tests/` only; import-linter forbids importing it from `app/`.
7. **`determine_status(...)`** — order: missing required fields → `insufficient_data`; unreviewed contribution → `unverified_provisional`; no published methodology → `methodology_pending`; else `scored`.

Fixtures in `packages/fixtures/analysis.json`: ≥ 25 cases including: complete product; missing sodium; salt-only label; kJ-only; per-serving-only (needs serving size; if absent → insufficient); liquid in ml; implausible energy; zero-sugar declared; Hindi-only ingredient list matched via aliases.

**Alternatives:** eligibility = same leaf category (or sibling via configured map) + compatible pack format + `analysis_status` comparable. Rank by objective factors only; tie-break by data completeness then name. Suppress if baseline is `insufficient_data` (show "Not enough data to compare"). Sponsored items are returned in a **separate** `sponsored` array and never interleaved in `items`.

---

## 10. Android consumer app — engineering spec

### 10.1 Baseline
- `minSdk 26`, `compileSdk`/`targetSdk` = the level Google Play requires for new apps at build time (verify on Play's target-API policy page; record in ADR).
- Edge-to-edge enabled; predictive back supported; dark theme optional.
- R8 full mode on release; ProGuard keep rules for kotlinx.serialization and ML Kit.
- Build variants: `debug` (points to `http://10.0.2.2:8000`, cleartext allowed **only** in debug network-security-config), `staging`, `release`.
- App Bundle delivery; ABI splits automatic via AAB.
- Permissions: `CAMERA`, `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS` (requested only when user enables notifications). **No** storage/media permissions (use Photo Picker). Remove `com.google.android.gms.permission.AD_ID` via manifest `tools:node="remove"`.

### 10.2 Modules
```
:app                      # Application, MainActivity, NavHost, DI graph root
:core:model               # pure Kotlin data classes & enums (mirror §2)
:core:gtin                # GTIN normalisation (pure Kotlin, JVM tests against shared fixtures)
:core:network             # Retrofit services (generated from OpenAPI or hand-mirrored + contract test), OkHttp, auth interceptor, error mapping
:core:database            # Room DB, DAOs, entities, migrations (exportSchema = true)
:core:datastore           # Proto/Preferences DataStore: settings, consent, locale, tokens(encrypted)
:core:data                # repositories (offline-first), sync, WorkManager workers
:core:designsystem        # theme from ui-tokens, components incl. SponsoredCard, EvidenceChip, StatusBanner
:core:ui                  # shared composables, UiText, error/empty/loading states
:core:testing             # fakes, test rules, fixture loaders
:feature:onboarding  :feature:scan  :feature:product  :feature:search
:feature:compare  :feature:contribute  :feature:history  :feature:profile  :feature:legal
```
Gradle: version catalog + convention plugins in `build-logic/`. Features depend on `core:*` only, never on each other; navigation via route contracts in `:app`.

### 10.3 Architecture pattern
- Unidirectional data flow: `ViewModel` exposes `StateFlow<XUiState>` (sealed: `Loading | Content | Empty | Error(UiError)`), plus one-off `Channel<XEvent>` for navigation/snackbar.
- Repositories return `Flow<Result<T>>` from Room; network refresh writes to Room (Room is the single source of truth).
- Coroutines with injected dispatchers; no `GlobalScope`.
- Errors: `ApiErrorCode` enum → `UiError` with string resource ID; offline → dedicated offline banner.

### 10.4 Scanner
- CameraX `Preview` + `ImageAnalysis` with `STRATEGY_KEEP_ONLY_LATEST`, target resolution ~1280×720.
- ML Kit Barcode Scanning **bundled model** (works offline from first launch; +~2–3 MB acceptable). Formats restricted to `EAN_13, EAN_8, UPC_A, UPC_E`.
- Devices without Google Play Services still work because the bundled model doesn't need them; ADR records this.
- Multiple barcodes in frame: pick the one whose bounding box centre is nearest the frame centre; require the same value in 2 consecutive frames (stability) before accepting.
- Debounce: ignore the same GTIN for 2 000 ms after acceptance; haptic + short sound (respect silent mode).
- Torch toggle, tap-to-focus, pinch zoom; lifecycle-bound camera; release on background.
- Fallbacks: permission denied → explanation + Settings deep link + manual entry; no camera → manual entry; "Scan from photo" via Photo Picker → ML Kit on still image.
- Testability: `BarcodeAnalyzer` takes an `InputImage` source interface; instrumentation tests feed fixture images from `androidTest/assets/barcodes/`.

### 10.5 Offline & sync
- Room cache: last 200 viewed products (LRU by `last_viewed_at`) incl. analysis + ingredient cards; images cached by Coil disk cache (100 MB cap).
- Unknown barcode while offline → saved to `pending_lookups`; `LookupWorker` resolves on connectivity and posts a local notification only if enabled.
- `ContributionUploadWorker`: unique work per contribution, `NetworkType.CONNECTED`, exponential backoff, presigned PUT per photo, resumable by photo.
- `HistorySyncWorker` for signed-in users; guest history stays local only.
- `ScanEventWorker` batches anonymous scan events (max 50 per request) — only if analytics consent = true.

### 10.6 Images & privacy
- Before upload: decode, apply rotation, **strip all EXIF (incl. GPS)**, resize longest edge to 2 000 px, JPEG quality 85, max 2 MB.
- Crop/rotate UI in-app; optional blur/redact tool for faces/personal data in background.
- Photo Picker (`PickVisualMedia`) only.

### 10.7 Auth & storage
- Guest by default: on first launch call `/guest/register`; store guest token.
- Sign-in via Credential Manager (Google ID token → `/auth/session`); email magic link/OTP as alternative (flag).
- Tokens encrypted with Tink AEAD keyed by Android Keystore, stored in DataStore. (`EncryptedSharedPreferences` is deprecated — do not use.)
- OkHttp `Authenticator` performs single-flight refresh; on refresh failure → fall back to guest mode, keep local data.

### 10.8 Networking
- Timeouts: connect 10 s, read 20 s; retries only for idempotent GETs (max 2, backoff).
- `If-None-Match` ETags; honour `Retry-After`.
- Sends `X-App-Version`, `X-Request-ID`, `Accept-Language`.
- On `426 APP_VERSION_UNSUPPORTED` → blocking update screen with Play in-app update flow.

### 10.9 Product detail UI order (trust hierarchy)
1. Identity: image, name, brand, variant, GTIN, freshness + evidence chip.
2. Analysis status banner (scored / insufficient data / methodology pending / unverified) — text + icon, never colour alone.
3. Key nutrient indicators (per 100 g/ml with per-serving toggle).
4. Ingredients (ordered, each tappable → ingredient card with references).
5. Allergen & dietary **declarations** ("Declared on label: contains milk").
6. Lab information ("No verified laboratory information available" by default).
7. Alternatives (organic).
8. Sponsored section (separate, visually distinct, "Sponsored" text + reason).
9. Sources & attribution, report a problem, revision history.

### 10.10 Localization & accessibility
- `values/` (en) and `values-hi/`; architecture ready for `te, ta, kn, ml, mr, bn`.
- Per-app language via AppCompat `setApplicationLocales` + `locales_config.xml`.
- Pseudo-locales (`en-XA`, `ar-XB`) enabled in debug to catch truncation.
- Ingredient glossary translations come from the API (`translation_strings`), not hard-coded.
- Compose semantics on all interactive elements; `SponsoredCard` contentDescription starts with "Sponsored".
- Supports font scale up to 200 % without clipping (screenshot tests); min touch target 48 dp; respects "Remove animations".

### 10.11 Performance budgets (release-blocking on reference device: 3 GB RAM, Android 11, mid-2021 budget SoC)
| Metric | Budget |
|---|---|
| Cold start to interactive home | ≤ 2.0 s |
| Camera ready after tapping Scan | ≤ 1.0 s |
| Scan → product (cached) | ≤ 800 ms |
| Scan → product (4G, uncached) | ≤ 3.0 s p90 |
| Download size (AAB-derived) | ≤ 15 MB |
| Steady memory on product screen | ≤ 180 MB PSS |
| Crash-free sessions (internal track) | ≥ 99.5 % |

Use Baseline Profiles (`:baselineprofile` module) and Macrobenchmark for startup and scan flow.

### 10.12 Android testing
- Unit: JUnit 5, MockK, Turbine, kotlinx-coroutines-test; `:core:gtin` tests read `packages/fixtures/gtin.json`.
- Room: migration tests for every schema version.
- UI: Compose UI tests for every screen state; Roborazzi screenshot tests for en/hi × light/dark × font 1.0/2.0.
- Instrumentation: scanner with fixture images; WorkManager tests with `TestDriver`.
- Contract: Android parses every example in `packages/openapi/openapi.json` without error.
- Static: Android Lint (warnings as errors on release), Detekt, ktlint.
- Device matrix (when Firebase Test Lab available): 2 GB / 3 GB / 4 GB devices, Android 8, 11, 13, latest.

### 10.13 Play readiness (drafts only; no submission claims)
- `docs/play-store/`: listing text (en/hi), screenshots spec, feature graphic spec, Data safety answers mapped to actual SDK behaviour, Health apps declaration draft, content rating questionnaire notes, UGC policy compliance notes (report + block + moderation), account-deletion web URL (served by web app at `/delete-account`), privacy policy URL.
- Release: Play App Signing; upload key held outside repo; CI signs only when secrets present, else builds unsigned AAB + debug APK.
- `versionCode` = CI build number; `versionName` = semver.
- Staged rollout plan documented (internal → closed → production %), plus in-app update support.

---

## 11. Web app (brand + admin)

- Next.js App Router, server components fetch the API using the generated TS client with the server-side session.
- `(brand)`: onboarding wizard, org settings, team, claims, products (proposal editor with diff preview), bulk CSV import (template download, row-level validation report), storefront preview, campaigns, analytics (aggregates only, k ≥ 20), billing (Razorpay Checkout via web only), support.
- `(admin)`: dashboard, review queues (contributions, proposals, claims, org verification, campaigns, lab reports), source comparison view, ingredient/additive KB editor with references, methodology & thresholds editor with **dual-approval publish**, plans/pricing config, invoices/refunds (finance role), feature flags, integrations status, audit log search, privacy jobs, dead-letter queue.
- Accessibility: keyboard-navigable, visible focus, axe checks in Playwright.
- `/delete-account`, `/privacy`, `/terms`, `/methodology` public pages.

---

## 12. Billing (Razorpay test mode)

- Orders created server-side; amount computed server-side from `plans` (never from client).
- Webhook `POST /billing/webhooks/razorpay`: verify `X-Razorpay-Signature` HMAC-SHA256 over the **raw body**; insert into `payment_webhook_events` with unique `provider_event_id` (duplicates → 200 no-op); process via outbox; handle out-of-order by state-machine guards (e.g. `refund.processed` before `payment.captured` is queued and retried).
- Entitlement activates only after a verified `payment.captured`/`order.paid` event.
- GST: tax profile per org; CGST+SGST for intra-state, IGST for inter-state (state of supply vs supplier state from config); round per line to paise, invoice total = sum of rounded lines; gapless invoice numbering per financial year via `invoice_number_sequences` row lock. Rates and HSN/SAC come from config — counsel/CA to confirm (BLOCKERS).
- Ledger: double-entry `ledger_entries`; nightly reconciliation job compares ledger vs Razorpay test API (adapter, flag).
- B2B billing never appears inside the Android app.

---

## 13. Integrations (adapters behind flags)

| Adapter | Default | Notes |
|---|---|---|
| Open Food Facts v2 | off (dev: on optional) | Identifiable User-Agent `FoodLensIndia/0.1 (contact email)`, ≤ configured req/min, cache 7 days, ODbL attribution, images keep their licence; OFF-derived fields tagged `licence_code='ODbL'` |
| GS1 India DataKart | off, no implementation of calls without agreement | Interface + fake only |
| FSSAI FoSCoS licence lookup | off | Interface + manual verification by admin |
| Razorpay | test mode | Keys from env |
| FCM | off | Optional |
| Email | Mailpit locally | SMTP adapter |
| Server OCR | Tesseract on | PaddleOCR flag |

---

## 14. Security, privacy, operations

- Secrets only via env/secret manager; `.env.example` with dummy values; gitleaks in CI.
- Headers: HSTS, CSP (web), X-Content-Type-Options, Referrer-Policy, frame-ancestors none for admin.
- CORS allowlist from config. No open redirects (allowlisted return URLs).
- Uploads: presigned PUT with content-type allowlist (`image/jpeg`, `image/png`, `image/webp`, `application/pdf` for evidence only), max size, 15-min TTL; worker validates magic bytes, re-encodes images, ClamAV scan (container) before marking `clean`.
- Rate limits: by-barcode 120/min/device, contributions 10/day/device, OCR 30/day/device, auth 10/min/IP.
- Anti-abuse: Play Integrity verdict on contribution/report endpoints (adapter, flag; dev uses fake).
- Logs: structured JSON, PII redaction filter, no tokens/emails/phone numbers logged.
- Privacy: consent records versioned; export (JSON zip via signed URL) and delete (hard delete + tombstone; backup expiry documented) for registered users; guests can wipe locally. DPDP/GDPR mapping in `docs/privacy/` marked "for counsel review".
- Ops: `/health/live`, `/health/ready` (DB, Redis, storage); OpenTelemetry traces; Prometheus metrics; daily `pg_dump` in dev compose + documented PITR for prod; restore drill script `infra/deploy/restore-drill.sh`; runbooks for webhook failure, OCR backlog, DB restore, key rotation, incident comms.

---

## 15. Quality gates (release-blocking)

Automated unless noted:
1. GTIN fixtures pass in Python and Kotlin; property tests pass.
2. Analysis fixtures pass; missing-data never yields a score.
3. Sponsored never interleaved with organic; payment cannot alter ranking (property test).
4. Cross-tenant IDOR suite (every brand endpoint × foreign org) returns 403/404.
5. Privilege escalation tests per role matrix (`docs/compliance/permission-matrix.md`).
6. SQLi/XSS/CSRF tests; upload attack tests (wrong MIME, polyglot, oversize, expired URL).
7. Webhook: invalid signature, replay, duplicate, out-of-order, partial refund.
8. Migrations: upgrade from empty, downgrade one step, re-upgrade.
9. Privacy export/delete end-to-end.
10. Android: screen-state UI tests, screenshot matrix, Room migrations, scanner fixture tests, Macrobenchmark within §10.11 budgets.
11. Accessibility: TalkBack manual checklist (manual, documented), font 200 %, axe on web.
12. Network chaos: API down → app shows cached/offline states; server restart mid-upload resumes.
13. GST rounding/reconciliation fixtures.
14. Restore drill succeeds on seeded DB.

Any failing gate → fix or log in `RISK_REGISTER.md` with severity; Critical/High block release.

---

## 16. Local development

`make up` starts: `postgres`, `redis`, `minio` (+ bucket init), `mailpit`, `clamav`, `api` (uvicorn reload), `worker`, `beat`, `web`.
`make seed` loads synthetic data + dev users:

| Role | Email | Password |
|---|---|---|
| Super admin | `admin@foodlens.local` | `ChangeMe!dev1` |
| Catalog reviewer | `reviewer@foodlens.local` | `ChangeMe!dev1` |
| Scientific reviewer | `science@foodlens.local` | `ChangeMe!dev1` |
| Finance | `finance@foodlens.local` | `ChangeMe!dev1` |
| Brand admin (Demo Snacks Co) | `brand@demosnacks.local` | `ChangeMe!dev1` |
| Consumer | `user@foodlens.local` | `ChangeMe!dev1` |

(Dev-only; seed script refuses to run when `APP_ENV=production`. MFA for dev users uses a fixed TOTP secret printed by the seed script.)

Android: `make android-debug` → `apps/android-consumer/app/build/outputs/apk/debug/`. Emulator reaches API at `10.0.2.2:8000`; physical device via `adb reverse tcp:8000 tcp:8000`.

---

## 17. Required documentation outputs
`README.md` (run commands), `docs/BLOCKERS.md`, `docs/RISK_REGISTER.md`, `docs/DECISIONS.md` (ADR index), architecture diagram (Mermaid in `docs/architecture/`), permission matrix, DB ERD (generated), methodology status page ("pending expert review"), source licences, Play drafts, final `docs/REPORT.md`: implemented vs verified, test results, open blockers, exact build/run commands.

## 18. Known external blockers (pre-filled; agent keeps updated)
- Qualified Indian nutrition/food-science reviewers to approve methodology & thresholds.
- Legal counsel: DPDP obligations, ODbL derivative-database boundaries, claims wording, health-app positioning.
- CA/tax advisor: GST rates, SAC codes, invoice format.
- GS1 India / FSSAI data access agreements.
- Razorpay live KYC and keys.
- Google Play Console organisation account, D-U-N-S, signing key custody, health app declaration review.
- Trademark/domain clearance for product name.

## 19. References
FSSAI regulations https://fssai.gov.in/food-law/regulations · ICMR-NIN Dietary Guidelines https://www.nin.res.in/dietaryguidelines/ · Open Food Facts API https://openfoodfacts.github.io/openfoodfacts-server/api/ · OFF licensing https://openfoodfacts.github.io/openfoodfacts-server/api/tutorials/license-be-on-the-legal-side/ · GS1 India DataKart https://www.gs1india.org/services/datakart · ML Kit barcode https://developers.google.com/ml-kit/vision/barcode-scanning/android · Play target API requirements https://developer.android.com/google/play/requirements/target-sdk · Play health apps policy https://support.google.com/googleplay/android-developer/answer/16679511 · Play payments policy https://support.google.com/googleplay/android-developer/answer/10281818 · Razorpay webhooks https://razorpay.com/docs/webhooks/

*This is an engineering specification, not legal advice, food-safety certification or a validated scoring methodology.*
