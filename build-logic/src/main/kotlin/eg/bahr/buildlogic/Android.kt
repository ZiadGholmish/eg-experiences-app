package eg.bahr.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * The Android half of every KMP module (AGP 9's `com.android.kotlin.multiplatform.library`, M3-M13): SDK levels
 * from the catalog, Java 11 bytecode, host (unit) tests on, namespace from the module path.
 *
 * Single variant: the KMP library plugin has no build types or flavors, so there is no `debug`/`release` split
 * and no `debugImplementation`. Flavors live in `:androidApp` only.
 *
 * Core library desugaring is on in every Android module: kotlinx-datetime's JVM artifact (every `LocalDate` the
 * app decodes, `TimeZone.of`) is built on `java.time`, which Android only ships from API 26, and minSdk is 24.
 * Without it, Android 7.x throws `NoClassDefFoundError` on the first date (M4-M5 review #1). Enabled in libraries
 * too, so their unit tests and lint see the same API surface.
 */
internal fun Project.configureKmpAndroidLibrary(android: KotlinMultiplatformAndroidLibraryTarget) {
    android.apply {
        namespace = bahrNamespace
        compileSdk = libs.version("android-compileSdk").toInt()
        minSdk = libs.version("android-minSdk").toInt()
        // Kotlin and Java of the Android compilations (the KMP plugin's jvmTarget covers both).
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
        enableCoreLibraryDesugaring = true
        // commonTest runs on the Android JVM too, as `testDebugUnitTest` did before. Merged Android resources for
        // every module, not only the Robolectric ones: the plugin allows one host-test setup per module, and the
        // conventions that need them (bahr.kmp.screenshots, bahr.kmp.app) are applied after this one. Robolectric
        // needs them: compose-resources ships fonts, strings and drawables as Android assets.
        withHostTest { isIncludeAndroidResources = true }
    }
    dependencies.add("coreLibraryDesugaring", libs.library("desugar-jdk-libs"))
}

/** The Android application (`:androidApp`): same SDK levels, Java 11 and desugaring as the libraries. */
internal fun Project.configureAndroidApplication(android: ApplicationExtension) {
    android.apply {
        compileSdk = libs.version("android-compileSdk").toInt()
        defaultConfig {
            minSdk = libs.version("android-minSdk").toInt()
            targetSdk = libs.version("android-targetSdk").toInt()
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
            isCoreLibraryDesugaringEnabled = true
        }
    }
    dependencies.add("coreLibraryDesugaring", libs.library("desugar-jdk-libs"))
}

/** `:core:common` → `eg.bahr.core.common`; also the package of each module's generated Android classes. */
internal val Project.bahrNamespace: String
    get() = "eg.bahr" + path.replace(':', '.')
