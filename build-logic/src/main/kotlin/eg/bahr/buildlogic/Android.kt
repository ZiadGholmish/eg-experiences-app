package eg.bahr.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

/**
 * SDK levels from the catalog, Java 11 bytecode, matching the Kotlin `androidTarget` JVM target.
 *
 * Core library desugaring is on in every Android module: kotlinx-datetime's JVM artifact (every
 * `LocalDate` the app decodes, `TimeZone.of`) is built on `java.time`, which Android only ships from
 * API 26, and minSdk is 24. Without it, Android 7.x throws `NoClassDefFoundError` on the first date
 * (M4-M5 review #1). Enabled in libraries too, so their unit tests and lint see the same API surface.
 */
internal fun Project.configureAndroidCommon(android: CommonExtension<*, *, *, *, *, *>) {
    android.apply {
        compileSdk = libs.version("android-compileSdk").toInt()
        defaultConfig {
            minSdk = libs.version("android-minSdk").toInt()
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
