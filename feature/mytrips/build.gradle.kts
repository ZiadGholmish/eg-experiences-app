// My trips (M3-M0, user decisions 2026-10-12): the fifth tab, Upcoming · Waiting list · Past for the
// signed-in account, a sign-in prompt otherwise. A feature like the others: core:* only.
plugins {
    id("bahr.kmp.feature")
    id("bahr.kmp.screenshots")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Upcoming and Past are paged lists (`GET /me/bookings`), on AndroidX Paging like the trip lists.
            implementation(libs.androidx.paging.common)
            implementation(libs.androidx.paging.compose)
            // Signed out: this device's waiting-list joins (WaitlistMemory) with the titles the card cache
            // knows, and the shared image and paging helpers. An allowed edge: a feature may use any core module.
            implementation(projects.core.catalog)
        }
        commonTest.dependencies {
            // MyTripsApiServiceTest runs the real client (core:network's apiHttpClient) on a MockEngine.
            implementation(libs.ktor.client.mock)
            implementation(libs.androidx.paging.testing)
        }
    }
}
