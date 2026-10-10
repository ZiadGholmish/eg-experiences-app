plugins {
    id("bahr.kmp.feature")
    id("bahr.kmp.screenshots")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Logs a Home section it drops (unknown type) or a failed /home, as the contract asks (M4-M1a).
            implementation(libs.kermit)
            // Every vertical paged trip list: Home's "All trips", a category page, a row's "See all" (M4-M1b).
            implementation(libs.androidx.paging.common)
            implementation(libs.androidx.paging.compose)
        }
        commonTest.dependencies {
            // TripApiServiceTest runs the real client (core:network's apiHttpClient) on a MockEngine.
            implementation(libs.ktor.client.mock)
            // asSnapshot / TestPager for the paging sources and the view models' PagingData.
            implementation(libs.androidx.paging.testing)
        }
    }
}
