package eg.bahr.buildlogic

import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Each covered module contributes its Android variant to a custom `mobile` variant; the root
 * (`bahr.root`) merges every module's `mobile` variant into one report and gate. Kover measures JVM
 * code only, i.e. the Android host tests (`testAndroidHostTest`); commonTest runs there too, so
 * iOS-only paths are the gap.
 */
internal fun Project.configureKoverVariant() {
    pluginManager.apply("org.jetbrains.kotlinx.kover")
    extensions.configure<KoverProjectExtension> {
        currentProject { createVariant(COVERAGE_VARIANT) { add(KMP_ANDROID_VARIANT) } }
    }
}

internal const val COVERAGE_VARIANT = "mobile"

/**
 * Kover's name for the single variant of AGP 9's KMP library plugin (M3-M13; it was the `debug` build
 * type before, and the plugin has no build types). Its own tasks are `koverHtmlReportAndroid` etc.
 */
private const val KMP_ANDROID_VARIANT = "android"
