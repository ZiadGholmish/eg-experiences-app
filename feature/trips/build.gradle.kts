plugins {
    id("bahr.kmp.feature")
    id("bahr.kmp.screenshots")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Logs a list's stale filter or category key it falls back from.
            implementation(libs.kermit)
            // Every vertical paged trip list: a category page, a row's "See all", search (M4-M1b).
            implementation(libs.androidx.paging.common)
            implementation(libs.androidx.paging.compose)
            // Recent searches survive a restart (M4-M3). An allowed edge: a feature may depend on any core module.
            implementation(projects.core.datastore)
            // What the trip lists share with Home and the map (M4-M1c): `GET /trips`, the trip card, the
            // paged list's parts, the waiting-list memory.
            implementation(projects.core.catalog)
        }
        commonTest.dependencies {
            // The ApiService tests run the real client (core:network's apiHttpClient) on a MockEngine.
            implementation(libs.ktor.client.mock)
            // asSnapshot / TestPager for the paging sources and the view models' PagingData.
            implementation(libs.androidx.paging.testing)
        }
    }
}
