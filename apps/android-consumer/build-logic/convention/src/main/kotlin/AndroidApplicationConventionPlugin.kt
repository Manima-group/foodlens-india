import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("foodlens.quality")
        extensions.configure<ApplicationExtension> {
            compileSdk {
                version = release(AndroidSdk.COMPILE) { minorApiLevel = AndroidSdk.COMPILE_MINOR }
            }
            defaultConfig {
                minSdk = AndroidSdk.MIN
                targetSdk = AndroidSdk.TARGET
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            testOptions.unitTests.isIncludeAndroidResources = true
            lint {
                warningsAsErrors = false
                abortOnError = true
                checkDependencies = true
                xmlReport = false
                htmlReport = true
            }
        }
        configureKotlinAndroid()
    }
}
