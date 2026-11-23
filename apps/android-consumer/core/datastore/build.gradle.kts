plugins {
    id("foodlens.android.library")
    id("foodlens.hilt")
}

android {
    namespace = "com.manima.foodlens.core.datastore"
}

dependencies {
    api(libs.androidx.datastore.preferences)
    implementation(libs.tink.android)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
