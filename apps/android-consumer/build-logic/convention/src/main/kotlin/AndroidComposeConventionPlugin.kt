import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.findByType

/** Apply after foodlens.android.application or foodlens.android.library. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.findByType<ApplicationExtension>()?.buildFeatures?.compose = true
        extensions.findByType<LibraryExtension>()?.buildFeatures?.compose = true
        val bom = dependencies.platform(libs.lib("androidx-compose-bom"))
        dependencies.add("implementation", bom)
        dependencies.add("testImplementation", bom)
        dependencies.add("implementation", libs.lib("androidx-compose-ui"))
        dependencies.add("implementation", libs.lib("androidx-compose-material3"))
        dependencies.add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
        dependencies.add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
        dependencies.add("debugImplementation", libs.lib("androidx-compose-ui-test-manifest"))
        dependencies.add("testImplementation", libs.lib("androidx-compose-ui-test-junit4"))
        dependencies.add("testImplementation", libs.lib("robolectric"))
    }
}
