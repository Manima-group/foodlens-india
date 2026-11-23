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
include(":core:designsystem")
include(":feature:scan")
include(":app")
include(":core:testing")
include(":core:gtin")
include(":core:network")
include(":core:database")
include(":core:datastore")
include(":core:designsystem")
include(":feature:scan")
include(":app")
