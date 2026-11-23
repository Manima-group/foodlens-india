# AAHAR: Know What You Eat

(Repository and package name: FoodLens India, `com.manima.foodlens`; see ADR 0006.)

Free Android app that scans packaged-food barcodes sold in India and explains the label. Source of truth: [`docs/SPEC.md`](docs/SPEC.md). Working rules: [`CLAUDE.md`](CLAUDE.md). Latest status: [`docs/REPORT.md`](docs/REPORT.md).

> Status (2026-11-23): The Tier 1 app and API work end to end against synthetic seed data. That covers onboarding, home, camera and manual barcode lookup, and product detail (status, ingredients, nutrition, declared allergens, sources). It also covers search, compare, alternatives, recent scans, favourites, missing-product submission, and offline and error states. Scores are never computed on the phone. Seed products show "methodology pending", "unverified provisional" or "insufficient data". See [`docs/REPORT.md`](docs/REPORT.md) for what is verified and what is left.

## Repository layout

```
apps/android-consumer/   Android app (Gradle multi-module)
services/api/            FastAPI backend + PostgreSQL (Alembic, synthetic seed)
packages/fixtures/       Shared test vectors (GTIN, enums, API examples)
docs/                    SPEC, ADRs, blockers, risk register, reports
Makefile                 make android-test | android-debug | lint | test
```

## Run the Android app

### 1. Install the tools

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 | `java -version` should print 21. Android Studio's bundled JDK works. |
| Android SDK | Platform **37.2**, Build-Tools 36+ | Install through Android Studio's SDK Manager, or with the command below |
| Android Studio | Latest stable (optional) | Easiest way to get an SDK and an emulator |

You don't need to install Gradle: the repo includes the Gradle wrapper (`./gradlew`), which downloads Gradle 9.9.2.

With the command-line tools only:

```bash
sdkmanager "platforms;android-37.2" "build-tools;36.0.0" "platform-tools"
# Only for an emulator:
sdkmanager "emulator" "system-images;android-36;google_apis;x86_64"
```

### 2. Point the build at your SDK

Create `apps/android-consumer/local.properties` (this file is gitignored):

```properties
# macOS:   sdk.dir=/Users/<you>/Library/Android/sdk
# Linux:   sdk.dir=/home/<you>/Android/Sdk
# Windows: sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
sdk.dir=/path/to/android-sdk
```

Instead, you can set the `ANDROID_HOME` environment variable. If you open `apps/android-consumer` in Android Studio, Studio writes this file for you.

### 3. Run it

**Option A: Android Studio (easiest)**
1. In Android Studio, choose **File → Open** and select `apps/android-consumer`. Don't open the repo root.
2. Wait for the Gradle sync to finish.
3. Open **Device Manager**, create a device (for example Pixel 8, API 36) and start it. Or plug in a phone with USB debugging turned on.
4. Select the `app` run configuration and the `debug` variant, then press **Run ▶**.

**Option B: Command line**

```bash
cd apps/android-consumer
./gradlew :app:assembleDebug                  # or from repo root: make android-debug
# The APK is written to app/build/outputs/apk/debug/app-debug.apk

adb devices                                   # check that an emulator or phone is listed
./gradlew :app:installDebug                   # or: adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.manima.foodlens.debug/com.manima.foodlens.MainActivity
```

To start an emulator from the command line:

```bash
avdmanager create avd -n foodlens -k "system-images;android-36;google_apis;x86_64"
emulator -avd foodlens
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

### 4. Start the backend

The app needs the API for lookups. Cached products and recent scans work offline.

**With Docker:** `make up && make seed`.

**Without Docker** (PostgreSQL 16 running locally, Python with [uv](https://docs.astral.sh/uv/)):

```bash
cp .env.example .env            # dummy values; set DATABASE_URL to your local Postgres
export DATABASE_URL=postgresql+asyncpg://postgres:<password>@127.0.0.1:5432/foodlens
make api-seed                   # synthetic products, all labelled "(Synthetic)"
make api-dev                    # migrates, then serves on 0.0.0.0:8000
```

### 5. Connect the app to the backend

| Where the app runs | What to do |
|---|---|
| Emulator | Nothing. Debug builds call `http://10.0.2.2:8000`, the emulator's alias for your computer. |
| Physical phone on USB (e.g. Nothing Phone) | Run `adb reverse tcp:8000 tcp:8000`. On a real device, debug builds call `http://127.0.0.1:8000`, which the reverse tunnel forwards to your computer. Run the command again after you reconnect the cable. |
| Physical phone on Wi-Fi | In the app, open **Settings → Developer**, then choose or type `http://<your-computer-LAN-IP>:8000` and tap **Test connection**. This works in debug builds only. |

### 6. Try it

Open [`docs/testing/seed-barcodes.html`](docs/testing/seed-barcodes.html) on your computer screen (or print it), then tap **Scan a barcode** on the home screen. Or tap **Enter barcode** and type one of these:

- `8901234567890`: Masala Crunch Namkeen (Synthetic). Shows the full product page.
- `2900000000025`: insufficient data. Shows no score and explains what's missing.
- `2900000009998`: valid but unknown. The app offers **Add this product** (photo submission).
- `8901234567891`: shows the "last digit doesn't match" error.
- `01234565`: can be read as two barcode types, so the app asks you to choose one.

Then try these:
- Search.
- Favourites: tap the heart on a product.
- Compare: select 2 to 4 favourites under **My products**.
- Airplane mode: cached products still open, and a new lookup is queued and retried.

To use Hindi, choose हिन्दी during onboarding or in **Settings → Language**.

## Tests and checks

```bash
cd apps/android-consumer
./gradlew test                      # unit tests (JVM + Robolectric), no emulator needed
./gradlew detekt ktlintCheck        # static checks
./gradlew lint                      # Android Lint
./gradlew ktlintFormat              # auto-fix formatting

# Against a running, seeded API (end-to-end core loop + contribution upload):
FOODLENS_LIVE_API=http://127.0.0.1:8000 ./gradlew :core:data:testDebugUnitTest --tests '*LiveApiTest*'

# From the repo root:
make android-test                   # test + lint + detekt + ktlintCheck
make api-test api-lint              # backend (needs PostgreSQL)
make lint test                      # everything (web is skipped until it exists)
```

Test reports are written to `<module>/build/reports/tests/` and lint reports to `<module>/build/reports/`.

## Troubleshooting

| Problem | Fix |
|---|---|
| `SDK location not found` | Create `local.properties` (step 2), or set `ANDROID_HOME`. |
| `requires ... compile against version 37.1 or later` | Install `platforms;android-37.2`. |
| `Unsupported class file major version` / JDK errors | Use JDK 21. In Android Studio: Settings → Build Tools → Gradle → Gradle JDK. |
| `429 Too Many Requests` while downloading dependencies | The build already tries Google's Maven Central mirror first. Wait a minute and retry. |
| A phone says "You're offline" or "temporarily unavailable" while online | Run `adb reverse tcp:8000 tcp:8000`, or set the LAN URL in Settings → Developer. Check that the API is listening on `0.0.0.0:8000`. |
| `adb: no devices` | Start an emulator, or turn on USB debugging on your phone and accept the prompt. |

## Docs

- Decisions: [`docs/DECISIONS.md`](docs/DECISIONS.md)
- Blockers: [`docs/BLOCKERS.md`](docs/BLOCKERS.md)
- Risks: [`docs/RISK_REGISTER.md`](docs/RISK_REGISTER.md)
