plugins {
    `kotlin-dsl`
}

group = "in.foodlens.buildlogic"

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("jvmLibrary") {
            id = "foodlens.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("quality") {
            id = "foodlens.quality"
            implementationClass = "QualityConventionPlugin"
        }
    }
}
