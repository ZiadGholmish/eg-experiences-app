plugins {
    id("bahr.kmp.feature")
    id("bahr.kmp.screenshots")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Warns once when the map runs without an API key (the pins then sit on a plain ground).
            implementation(libs.kermit)
        }
        androidMain.dependencies {
            // The map itself on Android (M4-M2, Google Maps by user decision). iOS draws it with the
            // Google Maps SDK for iOS through a Swift bridge (di/TripMapNativeViews).
            implementation(libs.google.maps.compose)
        }
        commonTest.dependencies {
            // TripMapApiServiceTest runs the real client (core:network's apiHttpClient) on a MockEngine.
            implementation(libs.ktor.client.mock)
        }
    }
}
