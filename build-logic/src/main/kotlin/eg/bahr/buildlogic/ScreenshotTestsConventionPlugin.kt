package eg.bahr.buildlogic

import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.HasUnitTestBuilder
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withGroovyBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `bahr.kmp.screenshots`: Roborazzi screenshot + Compose UI tests on Robolectric, in the module's
 * `src/androidUnitTest/` (see README → Testing). Goldens are committed next to the tests.
 */
class ScreenshotTestsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("bahr.kmp.library")
            pluginManager.apply("io.github.takahirom.roborazzi")

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.getByName("androidUnitTest").dependencies {
                    implementation(libs.library("junit"))
                    implementation(libs.library("robolectric"))
                    implementation(libs.library("roborazzi"))
                    implementation(libs.library("roborazzi-compose"))
                    implementation(libs.library("androidx-compose-ui-test-junit4"))
                }
            }

            extensions.configure<LibraryExtension> {
                // Robolectric needs the merged assets: compose-resources ships fonts, strings and
                // drawables as Android assets.
                testOptions.unitTests.isIncludeAndroidResources = true
            }

            extensions.configure<LibraryAndroidComponentsExtension> {
                // Compose UI tests need ui-test-manifest's ComponentActivity, which is debug-only so it
                // never ships; the release unit-test variant would run the same common tests again
                // without it.
                beforeVariants(selector().withBuildType("release")) { variant ->
                    (variant as HasUnitTestBuilder).enableUnitTest = false
                }
            }

            dependencies {
                // Registers the ComponentActivity that createComposeRule() launches under Robolectric.
                "debugImplementation"(libs.library("androidx-compose-ui-test-manifest"))
            }

            // Untyped on purpose: the Roborazzi plugin is built with Kotlin 2.3 metadata, which the
            // Kotlin that compiles build-logic (Gradle's embedded one) cannot read.
            val roborazzi = extensions.getByName("roborazzi")
            val outputDir = roborazzi.withGroovyBuilder { "getOutputDir"() } as DirectoryProperty
            outputDir.set(file("src/androidUnitTest/screenshots"))
        }
}
