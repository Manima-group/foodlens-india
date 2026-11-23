plugins {
    id("foodlens.android.library")
    id("foodlens.android.compose")
    id("foodlens.hilt")
}

android {
    namespace = "com.manima.foodlens.feature.scan"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:gtin"))
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hilt.navigation.compose)
    testImplementation(libs.androidx.test.ext.junit)
}
