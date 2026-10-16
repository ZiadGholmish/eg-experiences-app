package eg.bahr.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * `bahr.kmp.library`: a Kotlin Multiplatform library module (every `core:*`, and the base of
 * `bahr.kmp.feature`). Android (AGP 9's `com.android.kotlin.multiplatform.library`) + iOS targets, Android
 * namespace from the module path, kotlin-test and coroutines-test in `commonTest`, Spotless, the
 * string-resource lint and a Kover variant.
 *
 * Adds no `project(...)` dependency: a module's edges stay visible in its own build file.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            configureKotlinMultiplatform()
            configureSpotless()
            registerStringResourceCheck()
            configureKoverVariant()
        }
}
