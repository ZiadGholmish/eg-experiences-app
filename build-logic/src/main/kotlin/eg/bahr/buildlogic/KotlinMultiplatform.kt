package eg.bahr.buildlogic

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Every module ships the same three targets: Android, iOS device, iOS simulator (Apple silicon). */
internal fun Project.configureKotlinMultiplatform() {
    extensions.configure<KotlinMultiplatformExtension> {
        androidTarget {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11)
            }
        }
        iosArm64()
        iosSimulatorArm64()

        sourceSets.commonTest.dependencies {
            implementation(libs.library("kotlin-test"))
            implementation(libs.library("kotlinx-coroutines-test"))
        }
    }
}
