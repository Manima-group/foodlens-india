import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** SPEC §10.1 / ADR 0001. */
internal object AndroidSdk {
    /** Compose BOM 2026.11.01 requires compiling against 37.1+ (AAR metadata). Target stays at Play's level. */
    const val COMPILE = 37
    const val COMPILE_MINOR = 2
    const val TARGET = 36
    const val MIN = 26
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

internal fun Project.configureKotlinAndroid() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(true)
        }
    }
    tasks.withType<Test>().configureEach {
        systemProperty("foodlens.fixtures.dir", rootDir.resolve("../../packages/fixtures").canonicalPath)
        // Robolectric downloads android-all runtimes itself; use the same Central mirror as the build.
        systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
    }
    dependencies.add("testImplementation", libs.lib("junit4"))
    dependencies.add("testImplementation", libs.lib("kotlinx-coroutines-test"))
    dependencies.add("testImplementation", libs.lib("turbine"))
    dependencies.add("testImplementation", libs.lib("mockk"))
}
