# ADR 0001 — Android toolchain and target SDK

- Status: accepted
- Date: 2026-11-23

## Context
SPEC §10.1 says `compileSdk`/`targetSdk` must equal the level Google Play requires for new apps at build time, recorded in an ADR. SPEC §5 says pin versions in `gradle/libs.versions.toml`.

## Decision
- `targetSdk = 36`, `minSdk = 26`. `compileSdk = 37.2`: Compose BOM 2026.11.01 AARs require compiling against 37.1 or later; compileSdk does not change runtime behaviour, targetSdk does. Source: Play target-API page (developer.android.com/google/play/requirements/target-sdk), read 2026-11-23: "Starting August 31 2026: New apps and app updates must target Android 16 (API level 36) or higher".
- Gradle 9.9.2 (wrapper), Android Gradle Plugin 9.5.2 (built-in Kotlin), Kotlin 2.4.21, KSP 2.3.14, Hilt 2.60.1, Compose BOM 2026.11.01. These were the latest stable releases on Google Maven / Maven Central on that date.
- JVM toolchain 21; bytecode target 17.
- Application ID / Kotlin package root: `com.manima.foodlens` (placeholder; product name needs trademark clearance, SPEC §18).
- Pure-Kotlin modules (`:core:model`, `:core:gtin`, `:core:testing`) are plain Kotlin/JVM so they test fast with JUnit 5 and need no Android SDK.

- Unit tests: JUnit 5 in pure-Kotlin modules; JUnit 4 + Robolectric in Android modules (Robolectric and Compose UI tests require the JUnit 4 runner).
- Repositories: Google's official Maven Central mirror (`maven-central.storage-download.googleapis.com`) is listed before `mavenCentral()`, and Robolectric's runtime downloads use it too, because Central rate-limits large artifacts (HTTP 429 seen on 2026-11-23).

## Consequences
Re-check the Play target-API page before each release and bump via a new ADR.
