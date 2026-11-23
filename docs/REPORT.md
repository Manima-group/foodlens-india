# Session report

## 2026-11-23 (later): AAHAR app, Tier 1 end to end

### Done
- Branding (ADR 0006): app name AAHAR, the tagline "Know What You Eat.", orange/white/grey light and dark themes, logo and adaptive launcher icon. Package `com.manima.foodlens` is unchanged.
- Data layer `:core:data`:
  - Offline-first product repository: Room as source of truth, ETag, queued lookups.
  - Guest session with single-flight register and one re-register on 401.
  - Catalog (search, categories, alternatives, compare, sources, ingredient, explain), library (history and favourites) and config repositories.
  - Resumable contribution upload with an idempotency key.
  - WorkManager workers for uploads, queued lookups and consented scan events.
  - Image processing: EXIF rotate, ≤2000 px, ≤2 MB.
- Feature modules:
  - onboarding (language, how it works, allergens, consent off by default);
  - home dashboard;
  - CameraX + ML Kit scanner (stabiliser, torch, zoom, tap to focus, scan from photo) and manual entry;
  - product detail in SPEC §10.9 order, ingredient card and sources;
  - search, compare (2 to 4 products, "Not declared" for gaps) and alternatives;
  - recent scans and favourites;
  - missing-product and correction submission, and my contributions;
  - settings, including a debug-only API URL override;
  - update-required screen.
- Shared `:core:ui` loading, offline, error and empty states. Strings are in English and Hindi.
- Trust safeguards in the UI:
  - No score is computed or shown on the device; only the API's status banner appears.
  - Allergens are labelled "Declared on label".
  - The lab section shows the API's fixed text.
  - Sponsored content appears only in its own labelled section.
- ADR 0007 (module and token-renewal deviations). `docs/testing/seed-barcodes.html` holds printable synthetic EAN-13 codes, decoded and checked with zxing-cpp.

### Verified (cloud container, JDK 21, local PostgreSQL 16 + API)
| Check | Result |
|---|---|
| `./gradlew test lint detekt ktlintCheck` (`make android-test`) | 130 tests, 0 failures, 2 skipped (opt-in live tests); lint "No issues found" in every module; detekt and ktlint clean |
| `FOODLENS_LIVE_API=http://127.0.0.1:8000 ... --tests '*LiveApiTest*'` | 2/2 pass against the real seeded API: guest register, lookup, detail, search, alternatives, compare, then contribution create, photo upload, complete and confirm |
| `make api-test` | 81 passed |
| `make api-lint` | ruff, ruff format, mypy and import-linter (3 contracts kept) clean |
| `./gradlew :app:assembleDebug` | `apps/android-consumer/app/build/outputs/apk/debug/app-debug.apk` (46.3 MB, debug) |

### Not verified
- Not run on an emulator or a phone from here, because the container has no KVM. Camera scanning, permissions, WorkManager under Doze, and the layout at font scale 2.0 need a manual pass on the Nothing Phone.
- `make up` (Docker Compose): Docker Hub returns 429 in this container. The API and Postgres were run natively instead.
- Hindi copy has not had a native-speaker review (R2). The contrast of white on orange is below AA (R4).

### Failing
- None.

### Not built yet (by SPEC tier)
- Tier 1 extras: Roborazzi screenshot matrix, Baseline Profile and Macrobenchmark, instrumented scanner tests with fixture images, Play in-app update (ADR 0007).
- Tier 2: OCR worker, moderation/admin web, S3/MinIO + ClamAV (ADR 0004), Redis rate limits, sign-in and cloud sync, revision history.
- Tiers 3 to 5: brand portal, billing, sponsored campaigns, release pipeline.

### Next
1. Manual device pass on the Nothing Phone using README steps 4 to 6 and the seed barcodes.
2. Instrumented tests on Firebase Test Lab once there is an account.
3. Fix the orange contrast for text-bearing fills (R4), then Hindi review (R2).
4. Tier 2 workstream (OCR + moderation) per SPEC §3.

## 2026-11-23: Android, Tier 1 (first slice)

### Done
- Monorepo skeleton: `CLAUDE.md`, `Makefile`, `.env.example`, `docs/` (SPEC, BLOCKERS, RISK_REGISTER, DECISIONS, ADR 0001–0002).
- Shared fixtures: `packages/fixtures/gtin.json` (14 SPEC §7 vectors + 17 edge cases), `enums.json` (SPEC §2), `api-examples/*.json` (SPEC §8.2, §8.4–§8.6).
- `apps/android-consumer`: Gradle 9.9.2, AGP 9.5.2, Kotlin 2.4.21, version catalog, convention plugins (`foodlens.jvm.library`, `foodlens.android.application|library|compose`, `foodlens.hilt`, `foodlens.quality`).
- Modules: `:core:model`, `:core:gtin`, `:core:testing`, `:core:network`, `:core:designsystem`, `:feature:scan` (manual entry), `:app` (Hilt, Compose, type-safe nav, debug/staging/release, debug-only cleartext, AD_ID removed, no backups, per-app locales en/hi).

### Verified (all run on 2026-11-23, cloud container, JDK 21)
| Check | Result |
|---|---|
| `./gradlew test` | 58 tests, 0 failures (GTIN 38, network 10, scan 5, design system 3, enums 2) |
| `./gradlew detekt ktlintCheck` | clean |
| `./gradlew lint` | 0 errors; 3 warnings in `:app` (2 strings unused until product screen lands) |
| `./gradlew :app:assembleDebug` | `app-debug.apk` built (14.6 MB, debug, unminified) |
| `make lint test` | green (api/web trees not created yet, reported as such) |

### Not verified
- Nothing run on an emulator or device yet (no emulator in this container).
- Release/AAB build, R8 rules, Roborazzi screenshot matrix, Room migrations: not written yet.

### Failing
- None.

### Next (Tier 1 Android, in order)
1. `:core:database` (Room, product cache LRU 200, `pending_lookups`) + migration tests.
2. `:core:datastore` (settings, consent, locale; Tink-encrypted tokens) and guest register.
3. `:core:data` offline-first `ProductRepository` (Room = source of truth, ETag).
4. `:feature:product` detail in SPEC §10.9 order; "Insufficient data to score" path.
5. `:feature:scan` CameraX + ML Kit bundled scanner, stability/debounce logic test-first, fixture images.
6. Search, alternatives, compare, history, onboarding; Roborazzi en/hi × light/dark × font 1.0/2.0.
7. Python GTIN module against the same `gtin.json` (RISK R1) when the API workstream starts.

### Build commands
```
cd apps/android-consumer
echo "sdk.dir=/path/to/android-sdk" > local.properties   # needs platforms;android-37.2
./gradlew test detekt ktlintCheck lint      # = make android-test
./gradlew :app:assembleDebug                # = make android-debug
```
