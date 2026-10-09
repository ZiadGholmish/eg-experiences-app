package eg.bahr.buildlogic

import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Each covered module contributes its Android `debug` variant to a custom `mobile` variant; the
 * root (`bahr.root`) merges every module's `mobile` variant into one report and gate. Kover
 * measures JVM code only, i.e. the Android unit tests (`testDebugUnitTest`); commonTest runs there
 * too, so iOS-only paths are the gap.
 */
internal fun Project.configureKoverVariant() {
    pluginManager.apply("org.jetbrains.kotlinx.kover")
    extensions.configure<KoverProjectExtension> {
        currentProject { createVariant(COVERAGE_VARIANT) { add("debug") } }
    }
}

internal const val COVERAGE_VARIANT = "mobile"
