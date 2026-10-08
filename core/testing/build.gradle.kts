import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Test-only helpers. Consumed from other modules' test source sets only, never from main.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.test)
            implementation(libs.kotlin.test)
        }
        // Screenshot helpers for androidUnitTest source sets (Robolectric + Roborazzi).
        androidMain.dependencies {
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
        }
    }
}

android {
    namespace = "eg.bahr.core.testing"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
