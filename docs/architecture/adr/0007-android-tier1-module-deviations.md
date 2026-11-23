# ADR 0007: Android Tier 1 deviations from the SPEC §10 module list

- Status: accepted (engineering, 2026-11-23)

## Context
SPEC §10 lists `:feature:legal`, an OkHttp `Authenticator` for token refresh, and the Play in-app update flow on `426`. Tier 1 has guest accounts only (no sign-in and no refresh token), and the app isn't on Play yet.

## Decision
1. **No `:feature:legal` module.** About, methodology, disclaimer and privacy text live in `:feature:profile` (Settings). We'll split them into their own module when the legal copy grows or needs versioning (SPEC §18 legal review).
2. **Guest token renewal in `GuestSession.authorized {}`, not an OkHttp `Authenticator`.** A guest token can't be refreshed; on `401` the repository registers a new guest once (single-flight) and retries the call once. Local data is kept. When sign-in lands (Tier 2+), we'll add an `Authenticator` for refresh tokens.
3. **Update-required screen without the Play in-app update library.** `426 APP_VERSION_UNSUPPORTED` (or `min_supported_version_code` from `/meta/config`) shows a blocking screen with a store link. The Play Core in-app update flow needs a Play listing (BLOCKERS: Play Console), so we'll add it at release.
4. **Physical-device default API URL.** Debug builds use `http://10.0.2.2:8000` on an emulator and `http://127.0.0.1:8000` on a phone (with `adb reverse tcp:8000 tcp:8000`). Settings → Developer (debug builds only) can override the URL with a LAN address.

## Consequences
The module graph is one module shorter than SPEC §10. Revisit items 2 and 3 when sign-in and the Play listing exist.
