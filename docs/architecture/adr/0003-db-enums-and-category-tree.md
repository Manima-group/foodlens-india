# ADR 0003 — Enums as CHECK constraints; category tree as materialised path

- Status: accepted
- Date: 2026-11-23

## Decisions
1. SPEC §2 enums are stored as `text` with `CHECK (col IN (...))`, generated from `app/enums.py`. Adding a value is a plain migration (no `ALTER TYPE` lock dance). `tests/test_api_catalog.py::test_enums_match_spec` keeps `app/enums.py` equal to `packages/fixtures/enums.json`.
2. `categories.path` is a materialised path of slugs (`parent/child`), not `ltree`, so no extra extension is needed. Tier 1 categories are flat.
3. Extra guard constraints: `product_analyses.score_value IS NULL OR analysis_status = 'scored'` (T4) and `scoring_methodologies.status <> 'published' OR approvals >= 2` (T8).
