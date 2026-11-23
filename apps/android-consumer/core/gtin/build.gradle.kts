plugins {
    id("foodlens.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))
    testImplementation(project(":core:testing"))
}
