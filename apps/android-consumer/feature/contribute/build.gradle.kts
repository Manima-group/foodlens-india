plugins {
    id("foodlens.android.feature")
}

android {
    namespace = "com.manima.foodlens.feature.contribute"
}

dependencies {
    implementation(libs.coil.compose)
    implementation(libs.androidx.activity.compose)
    testImplementation(libs.robolectric)
}
