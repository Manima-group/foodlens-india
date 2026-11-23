plugins {
    id("foodlens.android.feature")
}

android {
    namespace = "com.manima.foodlens.feature.product"
}

dependencies {
    implementation(libs.coil.compose)
    testImplementation(project(":core:testing"))
    testImplementation(libs.robolectric)
}
