pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        // Google's official Maven Central mirror first: Central rate-limits large artifacts (Robolectric runtimes).
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Google's official Maven Central mirror first: Central rate-limits large artifacts (Robolectric runtimes).
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        mavenCentral()
    }
}

rootProject.name = "foodlens-android"

include(":core:model")
include(":core:gtin")
include(":core:network")
include(":core:database")
include(":core:datastore")
include(":core:data")
include(":core:designsystem")
include(":core:ui")
include(":feature:scan")
include(":feature:product")
include(":feature:search")
include(":feature:compare")
include(":feature:history")
include(":feature:contribute")
include(":feature:onboarding")
include(":feature:profile")
include(":app")
include(":core:testing")
