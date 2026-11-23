plugins {
    id("foodlens.android.library")
    id("foodlens.android.compose")
}

android {
    namespace = "com.manima.foodlens.core.ui"
}

dependencies {
    api(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(libs.androidx.compose.material.icons.core)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
