import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Pure Kotlin/JVM module (no Android): :core:model, :core:gtin, :core:testing. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("foodlens.quality")
        // Android Lint also analyses pure-Kotlin modules (SPEC §10.12).
        pluginManager.apply("com.android.lint")
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(JVM_TOOLCHAIN)
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
                allWarningsAsErrors.set(true)
            }
        }
        extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
            sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
            targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
        }
        dependencies {
            add("testImplementation", platform(libs.findLibrary("junit5-bom").get()))
            add("testImplementation", libs.findLibrary("junit5-jupiter").get())
            add("testImplementation", libs.findLibrary("junit5-params").get())
            add("testRuntimeOnly", libs.findLibrary("junit5-launcher").get())
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            // Shared fixtures live at <repo>/packages/fixtures (SPEC §4).
            systemProperty("foodlens.fixtures.dir", rootDir.resolve("../../packages/fixtures").canonicalPath)
        }
    }
}

internal const val JVM_TOOLCHAIN = 21
