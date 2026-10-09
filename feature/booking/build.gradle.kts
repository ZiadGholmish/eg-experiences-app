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
        }
        // BookingApiServiceTest runs the real client (core:network's apiHttpClient) on a MockEngine.
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}
