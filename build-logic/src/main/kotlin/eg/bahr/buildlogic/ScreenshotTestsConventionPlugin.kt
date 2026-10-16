package eg.bahr.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.kotlin.dsl.withGroovyBuilder

/**
 * `bahr.kmp.screenshots`: Roborazzi screenshot + Compose UI tests on Robolectric, in the module's
 * `src/androidHostTest/` (see README → Testing). Goldens are committed next to the tests.
 */
class ScreenshotTestsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("bahr.kmp.library")
            pluginManager.apply("io.github.takahirom.roborazzi")

            configureRobolectricHostTests()
            kmpSourceSet("androidHostTest") {
                implementation(libs.library("roborazzi"))
                implementation(libs.library("roborazzi-compose"))
            }

            // Untyped on purpose: the Roborazzi plugin is built with newer Kotlin metadata than the Kotlin that
            // compiles build-logic (Gradle's embedded one) can read.
            val roborazzi = extensions.getByName("roborazzi")
            val outputDir = roborazzi.withGroovyBuilder { "getOutputDir"() } as DirectoryProperty
            outputDir.set(file("src/androidHostTest/screenshots"))
        }
}

/**
 * Compose UI tests on Robolectric in `androidHostTest`: shared by `bahr.kmp.screenshots` and `:composeApp`'s
 * tab-host tests. The merged Android resources they need are on for every module (configureKmpAndroidLibrary).
 */
internal fun Project.configureRobolectricHostTests() {
    kmpSourceSet("androidHostTest") {
        listOf(
            "junit",
            "robolectric",
            "androidx-compose-ui-test-junit4",
            // Registers the ComponentActivity that createComposeRule() launches under Robolectric. On the host
            // test's classpath only, so the activity never reaches an app's manifest (the KMP library plugin has
            // no `debugImplementation`).
            "androidx-compose-ui-test-manifest",
        ).forEach { implementation(libs.library(it)) }
    }
}
