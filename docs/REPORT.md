# Session report

## 2026-11-23 — Android, Tier 1 (first slice)

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
