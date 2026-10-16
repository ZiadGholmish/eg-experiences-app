package eg.bahr.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `bahr.android.app`: the Android application (`:androidApp`, M3-M13). AGP 9 does not let the Kotlin
 * Multiplatform plugin and `com.android.application` share a module, so the APK is built here around the shared
 * `:composeApp` library: the manifest, launcher resources, `Application` and `Activity` classes, BuildConfig and
 * the per-environment flavors. Kotlin is AGP 9's built-in Kotlin support (no `org.jetbrains.kotlin.android`).
 * Application id, versions and flavors stay in the module's build file.
 */
class AndroidAppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("com.android.application")
            // `setContent { App(...) }` is a composable lambda.
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<ApplicationExtension> {
                configureAndroidApplication(this)
                buildFeatures.buildConfig = true
                packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
                buildTypes.getByName("release") {
                    isMinifyEnabled = false
                    // Debug keystore so a release build is installable before there is a
                    // signing config. Replace before any store submission.
                    signingConfig = signingConfigs.getByName("debug")
                }
            }

            dependencies {
                // The Compose runtime the entry point's `setContent` lambda compiles against.
                "implementation"(libs.library("compose-runtime"))
                "debugImplementation"(libs.library("compose-uiTooling"))
            }

            configureSpotless()
            registerStringResourceCheck()
        }
}
