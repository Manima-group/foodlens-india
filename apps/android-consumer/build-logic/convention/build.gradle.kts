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

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "foodlens.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "foodlens.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "foodlens.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "foodlens.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("hilt") {
            id = "foodlens.hilt"
            implementationClass = "HiltConventionPlugin"
        }
    }
}
