plugins {
    id("foodlens.android.feature")
}

android {
    namespace = "com.manima.foodlens.feature.scan"
}

dependencies {
    implementation(project(":core:gtin"))
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // Bundled model: works offline from first launch and without Play services (SPEC §10.4).
    implementation(libs.mlkit.barcode)
    implementation(libs.androidx.activity.compose)
}
