plugins {
    id("foodlens.android.feature")
}

android {
    namespace = "com.manima.foodlens.feature.compare"
}

dependencies {
    testImplementation(libs.robolectric)
}
