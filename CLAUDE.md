# CLAUDE.md — FoodLens India

Read `docs/SPEC.md` fully before writing code. It is the source of truth; this file holds working rules.

## Working loop
1. Work on **one tier at a time** (SPEC §3). Don't start the next tier until the current tier's Definition of Done passes.
2. For each module: write the failing test first (use `packages/fixtures/*` where they exist) → implement → run → fix → rerun.
3. After every meaningful change, run `make lint test`. Never leave the repo red at the end of a session.
4. Commit small, coherent changes with clear messages. One concern per commit.
5. At the end of each session, update `docs/REPORT.md`: done / verified / failing / next.

## Hard rules
- Use the enums, error codes, JSON shapes and GTIN rules from SPEC §2, §7, §8 **verbatim**.
- Never hard-code secrets. Use `.env` (gitignored) and `.env.example` with dummy values.
- Never invent scores, lab results, endorsements, credentials or real product data. Seed data is synthetic and labelled "(Synthetic)".
- Scoring/ranking code must never import billing or campaign code (import-linter contract).
- Every brand-scoped query takes `org_id`; add an IDOR test for every new brand endpoint.
- External integrations = adapter + fake + feature flag (default off) + entry in `docs/BLOCKERS.md`.
- Any choice SPEC leaves open → write an ADR in `docs/architecture/adr/` before implementing.
- Don't add microservices, new databases or new frameworks beyond SPEC §5 without an ADR.
- Never say "bug-free" or "complete" without listing the passing checks.

## Commands
```
make up            # docker compose stack
make seed          # synthetic data + dev users
make test          # api + workers + web tests
make lint          # ruff, mypy, eslint, tsc
make openapi       # regenerate packages/openapi + ts-client
make android-test  # ./gradlew test lint detekt ktlintCheck
make android-debug # assemble debug APK
```

## When stuck
If something fails 3 times with the same approach, stop, write what you tried in `docs/REPORT.md` under "Stuck", and try a different approach or flag it as a blocker. Don't silently skip tests or mark them xfail without logging the reason in `RISK_REGISTER.md`.
