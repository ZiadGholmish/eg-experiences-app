// Test-only helpers. Consumed from other modules' test source sets only, never from main.
plugins {
    id("bahr.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.test)
            implementation(libs.kotlin.test)
            // The waiting-list fakes over core:datastore's store (M4-M7: the approved test-only edge
            // core:testing → core:datastore; this module is only ever a test dependency).
            implementation(projects.core.datastore)
            // The Koin graph tests' lifecycle and JSON MockEngine (libraries, not module edges).
            implementation(libs.koin.core)
            implementation(libs.ktor.client.mock)
        }
        // Screenshot helpers for androidHostTest source sets (Robolectric + Roborazzi).
        androidMain.dependencies {
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
        }
    }
}
