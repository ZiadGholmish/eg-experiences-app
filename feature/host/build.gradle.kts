// A host's public page (M4-M10), opened from the trip page's host card; split from feature:trips (M3-M16).
plugins {
    id("bahr.kmp.feature")
    id("bahr.kmp.screenshots")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The host's trips, paged (M4-M1b).
            implementation(libs.androidx.paging.common)
            implementation(libs.androidx.paging.compose)
            // `GET /trips?host=`, the trip card, the review and rating pieces the trip page and reviews share.
            implementation(projects.core.catalog)
        }
        commonTest.dependencies {
            // The ApiService tests run the real client (core:network's apiHttpClient) on a MockEngine.
            implementation(libs.ktor.client.mock)
            // asSnapshot / TestPager for the view model's PagingData.
            implementation(libs.androidx.paging.testing)
            // The Koin graph test binds core:datastore's waiting-list store (catalogModule's WaitlistMemory reads it).
            implementation(projects.core.datastore)
        }
    }
}
