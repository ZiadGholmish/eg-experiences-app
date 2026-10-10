plugins {
    id("bahr.kmp.feature")
    id("bahr.kmp.screenshots")
}

kotlin {
    sourceSets {
        // Logs a Home section it drops (unknown type) or a failed /home, as the contract asks (M4-M1a).
        commonMain.dependencies {
            implementation(libs.kermit)
        }
        // TripApiServiceTest runs the real client (core:network's apiHttpClient) on a MockEngine.
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}
