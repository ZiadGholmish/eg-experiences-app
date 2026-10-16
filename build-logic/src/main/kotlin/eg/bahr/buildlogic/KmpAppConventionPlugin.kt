package eg.bahr.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `bahr.kmp.app`: the shared app module (`:composeApp`), wiring only: NavHost, Koin start, deep links, the
 * platform entry functions. Since M3-M13 (AGP 9) a KMP library: the Android application around it is
 * `:androidApp` (`bahr.android.app`), and the iOS framework Xcode links (`ComposeApp`, static) is built here.
 *
 * Not `bahr.kmp.library`: no Kover variant, as before (the merged report counts `core:*` and `feature:*`).
 */
class KmpAppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            configureKotlinMultiplatform()
            pluginManager.apply("bahr.kmp.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

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

            // The tab host's Compose tests (AppTabsTest, BackSwipeTest, AppNavHostRootsTest) on Robolectric.
            configureRobolectricHostTests()

            configureSpotless()
            registerStringResourceCheck()
        }
}
