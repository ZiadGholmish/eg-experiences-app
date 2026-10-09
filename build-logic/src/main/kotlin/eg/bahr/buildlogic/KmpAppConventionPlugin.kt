package eg.bahr.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `bahr.kmp.app`: the app module (`:composeApp`), wiring only: NavHost, Koin start, platform entry
 * points. The Android application plus the iOS framework Xcode links (`ComposeApp`, static).
 * Application id, versions and per-environment flavors stay in the module's build file.
 */
class KmpAppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.application")
            pluginManager.apply("bahr.kmp.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            configureKotlinMultiplatform()
            extensions.configure<KotlinMultiplatformExtension> {
                listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
                    iosTarget.binaries.framework {
                        baseName = "ComposeApp"
                        isStatic = true
                        // Without this the linker cannot infer a bundle ID and falls back
                        // to the bundle name, which breaks crash symbolication later.
                        binaryOption("bundleId", "eg.bahr.shared")
                    }
                }
            }

            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)
                defaultConfig.targetSdk = libs.version("android-targetSdk").toInt()
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
                "debugImplementation"(libs.library("compose-uiTooling"))
            }

            configureSpotless()
            registerStringResourceCheck()
        }
}
