// Search (M4-M3) and the last search (M3-M0b); split from feature:trips (M3-M16).
plugins {
    id("bahr.kmp.feature")
    id("bahr.kmp.screenshots")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Logs a recent-searches read or write that failed.
            implementation(libs.kermit)
            // The paged results (M4-M1b).
            implementation(libs.androidx.paging.common)
            implementation(libs.androidx.paging.compose)
            // Recent searches survive a restart (M4-M3).
            implementation(projects.core.datastore)
            // `GET /trips`, the trip card, the paged list's parts, the waiting-list memory (M4-M1c).
            implementation(projects.core.catalog)
        }
        commonTest.dependencies {
            // The Koin graph test's client runs on a MockEngine.
            implementation(libs.ktor.client.mock)
            // asSnapshot / TestPager for the view model's PagingData.
            implementation(libs.androidx.paging.testing)
        }
    }
}
