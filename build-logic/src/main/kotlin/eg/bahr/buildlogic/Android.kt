package eg.bahr.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

/** SDK levels from the catalog, Java 11 bytecode, matching the Kotlin `androidTarget` JVM target. */
internal fun Project.configureAndroidCommon(android: CommonExtension<*, *, *, *, *, *>) {
    android.apply {
        compileSdk = libs.version("android-compileSdk").toInt()
        defaultConfig {
            minSdk = libs.version("android-minSdk").toInt()
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }
}

/** `:core:common` → `eg.bahr.core.common`; also the package of each module's generated Android classes. */
internal val Project.bahrNamespace: String
    get() = "eg.bahr" + path.replace(':', '.')
