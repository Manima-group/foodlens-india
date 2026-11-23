plugins {
    id("foodlens.android.library")
    id("foodlens.hilt")
    alias(libs.plugins.room)
}

android {
    namespace = "com.manima.foodlens.core.database"
    // Exported schemas feed MigrationTestHelper (SPEC §10.12: migration tests for every schema version).
    sourceSets.getByName("test").assets.directories.add("$projectDir/schemas")
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
}
