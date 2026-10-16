package eg.bahr.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler

/**
 * Every KMP module ships the same three targets: Android (AGP 9's KMP library target, `kotlin { android { } }`),
 * iOS device, iOS simulator (Apple silicon). Applies the plugins itself, so `bahr.kmp.library` and `bahr.kmp.app`
 * (`:composeApp`, a KMP library since M3-M13) set up the same way.
 */
internal fun Project.configureKotlinMultiplatform() {
    pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    pluginManager.apply("com.android.kotlin.multiplatform.library")
    // The KMP library plugin registers no `lint` task of its own (only the host tests' analysis). Android Lint's
    // standalone plugin brings back `lint` (androidMain + commonMain) per module, part of `check` as with the old
    // `com.android.library`.
    pluginManager.apply("com.android.lint")

    extensions.configure<KotlinMultiplatformExtension> {
        // The `android {}` block of the build script: AGP registers the target as an extension of `kotlin`.
        (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
            configureKmpAndroidLibrary(this)
        }
        iosArm64()
        iosSimulatorArm64()

        sourceSets.commonTest.dependencies {
            implementation(libs.library("kotlin-test"))
            implementation(libs.library("kotlinx-coroutines-test"))
        }
    }
}

/** The Android target of a KMP module, for the conventions that add to it (resources, host-test options). */
internal fun Project.kmpAndroid(configure: KotlinMultiplatformAndroidLibraryTarget.() -> Unit) {
    extensions.configure<KotlinMultiplatformExtension> {
        (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>(configure)
    }
}

/** Dependencies of one source set by name (`androidHostTest` has no typed accessor). */
internal fun Project.kmpSourceSet(
    name: String,
    dependencies: KotlinDependencyHandler.() -> Unit,
) {
    extensions.configure<KotlinMultiplatformExtension> {
        sourceSets.getByName(name).dependencies(dependencies)
    }
}
