# Blockers

Pre-filled from SPEC §18; kept current by whoever is working.

| Blocker | Owner | Affects | Status |
|---|---|---|---|
| Qualified Indian nutrition/food-science reviewers to approve methodology & thresholds | Product | Public score (T8), thresholds | open |
| Legal counsel: DPDP, ODbL boundaries, claims wording, health-app positioning | Product | Privacy, OFF adapter, copy | open |
| CA/tax advisor: GST rates, SAC codes, invoice format | Finance | Tier 4 billing | open |
| GS1 India / FSSAI data access agreements | Product | Adapters (interface + fake only) | open |
| Razorpay live KYC and keys | Finance | Tier 4 (test mode only until then) | open |
| Google Play Console org account, D-U-N-S, signing key custody, health app declaration | Product | Release (Tier 5) | open |
| Trademark/domain clearance for product name (now "AAHAR", ADR 0006) | Product | Listing, store name | open |
| Android: Firebase Test Lab device matrix (SPEC §10.12) | Eng | Device matrix tests | open: no account yet |
| Object storage: S3/MinIO adapter, ClamAV scan, image re-encode (ADR 0004) | Eng | Contribution photos in production | open: local adapter only |
| OCR worker (Tesseract eng+hin) + admin review queue | Eng | Contributions reaching the catalog | open: Tier 2 |
| Docker Hub pull rate limit (429) in the cloud dev container | Eng | `make up` unverified here; API and Postgres run natively instead | open: environment |
| No KVM in the cloud dev container, so no emulator | Eng | Instrumented/UI tests on a device, scanner fixture-image tests | open: run on a dev machine or Firebase Test Lab |
| Play listing needed for the in-app update flow (ADR 0007) | Product | `426` update UX | open |
