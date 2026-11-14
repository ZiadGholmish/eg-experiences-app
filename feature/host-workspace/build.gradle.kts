// Host mode (M5-M1): a host's own password sign-in (forced change included), their dates, the manifest of one date,
// the broadcast and manual bookings, all through `/api/v1/host/` with host mode's own session and client
// (core:network's HostSession / HostHttpClient). Named host-workspace like the web's: feature:host is the public host page.
plugins {
    id("bahr.kmp.feature")
    id("bahr.kmp.screenshots")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kermit)
        }
        // The ApiService tests run the real host client (core:network's hostApiHttpClient) on a MockEngine.
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}
