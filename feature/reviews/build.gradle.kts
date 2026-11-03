// "See all reviews" of a trip or a host (M4-M11); split from feature:trips (M3-M16). Since M3-M4 also the review page.
plugins {
    id("bahr.kmp.feature")
    id("bahr.kmp.screenshots")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Logs a failed read of the host's trips for the Trip filter, and a trip that lost its reviews list.
            implementation(libs.kermit)
            // The paged reviews (M4-M11).
            implementation(libs.androidx.paging.common)
            implementation(libs.androidx.paging.compose)
            // The review card and rating summary the trip and host pages share, `GET /trips?host=` for the Trip filter.
            implementation(projects.core.catalog)
        }
        androidMain.dependencies {
            // The system Photo Picker (rememberLauncherForActivityResult + PickVisualMedia) for the review's photos (M3-M4).
            implementation(libs.androidx.activity.compose)
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
