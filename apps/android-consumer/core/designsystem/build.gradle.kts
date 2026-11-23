plugins {
    id("foodlens.android.library")
    id("foodlens.android.compose")
}

android {
    namespace = "com.manima.foodlens.core.designsystem"
}

dependencies {
    api(project(":core:model"))
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
