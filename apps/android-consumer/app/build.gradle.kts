plugins {
    id("foodlens.android.application")
    id("foodlens.android.compose")
    id("foodlens.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.manima.foodlens"
    defaultConfig {
        applicationId = "com.manima.foodlens"
        // SPEC §10.13: versionCode = CI build number, versionName = semver.
        versionCode = (System.getenv("CI_BUILD_NUMBER") ?: "1").toInt()
        versionName = "0.1.0"
        buildConfigField("String", "API_BASE_URL", "\"https://api.staging.invalid\"")
        buildConfigField("String", "API_BASE_URL_DEVICE", "\"https://api.staging.invalid\"")
    }
    androidResources {
        generateLocaleConfig = true
    }
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            // Emulator reaches the host API here (SPEC §16). Cleartext allowed only in debug network config.
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8000\"")
            // Physical phone: `adb reverse tcp:8000 tcp:8000` makes the laptop API reachable on the phone's localhost.
            // Any other address (e.g. a LAN IP) can be set in Settings → Developer.
            buildConfigField("String", "API_BASE_URL_DEVICE", "\"http://127.0.0.1:8000\"")
        }
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:gtin"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:scan"))
    implementation(project(":feature:product"))
    implementation(project(":feature:search"))
    implementation(project(":feature:compare"))
    implementation(project(":feature:history"))
    implementation(project(":feature:contribute"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:profile"))
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
}
