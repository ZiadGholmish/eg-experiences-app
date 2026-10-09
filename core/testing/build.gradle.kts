// Test-only helpers. Consumed from other modules' test source sets only, never from main.
plugins {
    id("bahr.kmp.library")
}

kotlin {
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
