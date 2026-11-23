import org.gradle.api.Plugin
import org.gradle.api.Project

/** Feature modules: Compose + Hilt, depend on core:* only, never on each other (SPEC §10.2). */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("foodlens.android.library")
        pluginManager.apply("foodlens.android.compose")
        pluginManager.apply("foodlens.hilt")
        listOf(":core:model", ":core:designsystem", ":core:ui", ":core:data").forEach {
            dependencies.add("implementation", dependencies.project(mapOf("path" to it)))
        }
        listOf(
            "androidx-lifecycle-viewmodel-compose",
            "androidx-lifecycle-runtime-compose",
            "hilt-navigation-compose",
            "androidx-compose-material-icons-core",
            "kotlinx-serialization-json"
        ).forEach { dependencies.add("implementation", libs.lib(it)) }
        dependencies.add("testImplementation", libs.lib("androidx-test-ext-junit"))
        dependencies.add("testImplementation", libs.lib("androidx-test-core"))
    }
}
