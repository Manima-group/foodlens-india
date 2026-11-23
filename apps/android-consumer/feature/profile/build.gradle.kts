plugins {
    id("foodlens.android.feature")
}

android {
    namespace = "com.manima.foodlens.feature.profile"
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
}
