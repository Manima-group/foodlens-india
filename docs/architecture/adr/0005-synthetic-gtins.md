# ADR 0005 — Synthetic seed GTINs

- Status: accepted
- Date: 2026-11-23

Seed products must never be mistaken for real products, including when someone scans a real pack.

## Decision
- Synthetic GTIN-13s use the restricted-circulation prefix `29` (`29` + a 10-digit sequence + a check digit). GS1 reserves that range for in-store use, so it cannot collide with a nationally traded product.
- The one exception is the SPEC §8.5 example `8901234567890` ("Masala Crunch Namkeen (Synthetic)"), kept because the SPEC and its fixtures name it.
- Every seeded product and brand name ends in "(Synthetic)" or is an obviously fictional demo brand. Every source record has licence `SYNTHETIC`, and the API's sources endpoint shows "Synthetic demonstration data. Not a real product."
- `docs/testing/seed-barcodes.html` holds printable barcodes for scanning seed products on a physical phone.
