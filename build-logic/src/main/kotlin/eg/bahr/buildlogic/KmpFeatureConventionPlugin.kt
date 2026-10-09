package eg.bahr.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `bahr.kmp.feature`: one business area of the app (`feature:<x>`), laid out as
 * model/ data/ presentation/ navigation/ di/ (bahr-modularization skill).
 *
 * Adds the `core:*` edges every feature has. They are all allowed by the skill graph
 * (`feature:<x> → core:*`); `:architecture-tests` reads the real Gradle dependencies, so these
 * are checked like edges declared in a module's own build file. Never add a `feature:*` here.
 */
class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("bahr.kmp.library")
            pluginManager.apply("bahr.kmp.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.commonMain.dependencies {
                    implementation(project(":core:common"))
                    implementation(project(":core:network"))
                    implementation(project(":core:designsystem"))
                    implementation(project(":core:localization"))

                    listOf(
                        "compose-foundation",
                        "compose-material3",
                        "compose-components-resources",
                        "androidx-lifecycle-viewmodelCompose",
                        "androidx-lifecycle-runtimeCompose",
                        "koin-core",
                        "koin-compose",
                        "koin-compose-viewmodel",
                        "navigation-compose",
                        "kotlinx-serialization-json",
                        "kotlinx-coroutines-core",
                        "kotlinx-datetime",
                        "coil-compose",
                    ).forEach { implementation(libs.library(it)) }
                }
                sourceSets.commonTest.dependencies {
                    implementation(project(":core:testing"))
                }
            }
        }
}
