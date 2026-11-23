# ADR 0004 — Contribution photo uploads: local adapter now, S3/MinIO later

- Status: accepted
- Date: 2026-11-23

## Context
SPEC §5/§14 call for presigned S3 PUT URLs (MinIO locally), a 15-minute TTL, a content-type allowlist and magic-byte checks.

## Decision
- `app/integrations/storage.py` defines an `ObjectStorage` protocol. The only implementation so far is `LocalStorage` (filesystem under `UPLOAD_DIR`).
- The API returns an upload URL of the form `PUT /api/v1/uploads/{token}`. The random token is the credential: it is stored hashed, single use, expires after 15 minutes, and accepts only the declared content type and size.
- Uploads are refused unless the first bytes match JPEG, PNG or WebP.
- Contribution flow: `draft → photos_uploaded → submitted`. OCR (`extracting`, `awaiting_confirmation`) and moderation are not built yet. A submitted contribution never becomes a public product without human review.

## Consequences
- The Android client already treats the upload URL as opaque, so switching to S3 presigned URLs needs no app change.
- An S3/MinIO adapter, the ClamAV scan and server-side re-encoding are still to do. They are tracked in `docs/BLOCKERS.md` and `docs/REPORT.md`.
