# ADR 0002 — GTIN details SPEC §7 leaves open

- Status: accepted
- Date: 2026-11-23

## Decisions
1. `original_code` stores the entered digits after step 1 (whitespace and hyphens removed), not the raw string. Matches the §8.5 example (`"original_code": "8901234567890"`).
2. When the scanner reports a symbology, it is trusted: no fallback guessing. A length that does not match the reported symbology → `INVALID_BARCODE`.
3. Manual 8-digit entry where neither EAN-8 nor UPC-E is valid → `INVALID_CHECK_DIGIT`.
4. UPC-E with number system other than 0/1 → `INVALID_BARCODE`.
5. Restricted circulation is evaluated on the GTIN-13 form (GTIN-14 indicator digit `0`, then prefix `02`, `04`, `20`–`29`). So UPC-A number system 2 is restricted. EAN-8 restricted ranges are not in SPEC and are not flagged.
6. Ambiguous candidates are ordered EAN-8 first, then UPC-E.

All of these are pinned by vectors in `packages/fixtures/gtin.json`, which the Python implementation must also pass.
