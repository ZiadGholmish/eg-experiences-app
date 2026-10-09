package eg.bahr.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `bahr.kmp.library`: a Kotlin Multiplatform library module (every `core:*`, and the base of
 * `bahr.kmp.feature`). Android + iOS targets, Android namespace from the module path, kotlin-test
 * and coroutines-test in `commonTest`, Spotless, the string-resource lint and a Kover variant.
 *
 * Adds no `project(...)` dependency: a module's edges stay visible in its own build file.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.library")

            configureKotlinMultiplatform()
            extensions.configure<LibraryExtension> {
                namespace = bahrNamespace
                configureAndroidCommon(this)
            }
            configureSpotless()
            registerStringResourceCheck()
            configureKoverVariant()
        }
}
