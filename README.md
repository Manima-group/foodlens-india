# FoodLens India

Free Android app that scans packaged-food barcodes sold in India and explains the label. Source of truth: [`docs/SPEC.md`](docs/SPEC.md). Working rules: [`CLAUDE.md`](CLAUDE.md). Latest status: [`docs/REPORT.md`](docs/REPORT.md).

> Status (2026-11-23): Tier 1 Android in progress. The app opens, and you can type a barcode and see it validated. Scanning, product details and the backend API are not built yet, so a valid barcode currently shows only its 14-digit form.

## Repository layout

```
apps/android-consumer/   Android app (Gradle multi-module)
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

### 4. Try it

1. Tap **Enter barcode**.
2. Type one of these codes, then tap **Look up**:
   - `8901234567890` is valid and shows `08901234567890`.
   - `8901234567891` shows the "last digit doesn't match" error.
   - `01234565` can be read as two barcode types, so the app asks you to choose one.
3. To try Hindi, open the device's **Settings → Apps → FoodLens India (debug) → Language** and choose हिन्दी.

### Backend API (not built yet)

Debug builds call `http://10.0.2.2:8000`, which is your computer's `localhost` as seen from the emulator (SPEC §16). Once the API exists:
- **Emulator:** start the API with `make up`. The emulator reaches it at `10.0.2.2:8000`.
- **Physical phone:** run `adb reverse tcp:8000 tcp:8000` first.

## Tests and checks

```bash
cd apps/android-consumer
./gradlew test                      # unit tests (JVM + Robolectric), no emulator needed
./gradlew detekt ktlintCheck        # static checks
./gradlew lint                      # Android Lint
./gradlew ktlintFormat              # auto-fix formatting

# From the repo root:
make android-test                   # test + lint + detekt + ktlintCheck
make lint test                      # everything (api/web are skipped until they exist)
```

Test reports are written to `<module>/build/reports/tests/` and lint reports to `<module>/build/reports/`.

## Troubleshooting

| Problem | Fix |
|---|---|
| `SDK location not found` | Create `local.properties` (step 2), or set `ANDROID_HOME`. |
| `requires ... compile against version 37.1 or later` | Install `platforms;android-37.2`. |
| `Unsupported class file major version` / JDK errors | Use JDK 21. In Android Studio: Settings → Build Tools → Gradle → Gradle JDK. |
| `429 Too Many Requests` while downloading dependencies | The build already tries Google's Maven Central mirror first. Wait a minute and retry. |
| `adb: no devices` | Start an emulator, or turn on USB debugging on your phone and accept the prompt. |

## Docs

- Decisions: [`docs/DECISIONS.md`](docs/DECISIONS.md)
- Blockers: [`docs/BLOCKERS.md`](docs/BLOCKERS.md)
- Risks: [`docs/RISK_REGISTER.md`](docs/RISK_REGISTER.md)
