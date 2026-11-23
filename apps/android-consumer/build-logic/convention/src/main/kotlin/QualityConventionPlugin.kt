import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/** Detekt + ktlint on every module (SPEC §10.12 static checks). */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("io.gitlab.arturbosch.detekt")
        pluginManager.apply("org.jlleitschuh.gradle.ktlint")
        extensions.configure<DetektExtension> {
            buildUponDefaultConfig = true
            config.setFrom(rootDir.resolve("config/detekt/detekt.yml"))
            parallel = true
        }
        extensions.configure<KtlintExtension> {
            android.set(true)
            ignoreFailures.set(false)
        }
    }
}
