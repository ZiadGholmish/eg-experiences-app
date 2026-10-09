plugins {
    id("bahr.kmp.feature")
    id("bahr.kmp.screenshots")
}

kotlin {
    sourceSets {
        // The held-seats screen asks before Back gives the seats up. NavigationBackHandler (Android
        // system back, iOS back gesture) is only a runtime dependency of navigation-compose; Compose's
        // own BackHandler is deprecated in its favour.
        commonMain.dependencies {
            implementation(libs.navigationevent.compose)
            // The live hold is stored so it survives a restart and Home can offer to continue it
            // (M2-M4). An allowed edge: a feature may depend on any core module.
            implementation(projects.core.datastore)
            // Logs a failed local save of the hold (M2-M4 review #6); the hold itself still goes on.
            implementation(libs.kermit)
        }
        // BookingApiServiceTest runs the real client (core:network's apiHttpClient) on a MockEngine.
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}
