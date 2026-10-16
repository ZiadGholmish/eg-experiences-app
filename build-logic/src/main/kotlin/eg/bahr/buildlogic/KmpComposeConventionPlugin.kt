package eg.bahr.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `bahr.kmp.compose`: Compose Multiplatform plus the Compose compiler, and the two artifacts every
 * Compose module uses. Each module adds the rest (foundation, material3, resources) itself.
 */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("org.jetbrains.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
                extensions.configure<KotlinMultiplatformExtension> {
                    sourceSets.commonMain.dependencies {
                        implementation(libs.library("compose-runtime"))
                        implementation(libs.library("compose-ui"))
                    }
                }
            }
            // The KMP library plugin turns Android resources off by default; compose-resources needs them on to
            // ship its strings, fonts and drawables (as Android assets) in the Android target.
            pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
                kmpAndroid { androidResources.enable = true }
            }
        }
}
