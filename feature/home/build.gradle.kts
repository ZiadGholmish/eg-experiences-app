// Home (M4-M1c, split out of feature:trips): the app bar, search entry, server-driven sections and
// "All trips". Opens the trip lists, the trip page and the map through lambdas wired in :composeApp.
plugins {
    id("bahr.kmp.feature")
    id("bahr.kmp.screenshots")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Logs a Home section it drops (unknown type) or a failed /home, as the contract asks (M4-M1a).
            implementation(libs.kermit)
            // "All trips" is paged (M4-M1b).
            implementation(libs.androidx.paging.common)
            implementation(libs.androidx.paging.compose)
            // `GET /trips`, the trip card, the paged list's parts and the waiting-list memory, shared with
            // the trip lists and the map. An allowed edge: a feature may depend on any core module.
            implementation(projects.core.catalog)
        }
        commonTest.dependencies {
            // HomeApiServiceTest runs the real client (core:network's apiHttpClient) on a MockEngine.
            implementation(libs.ktor.client.mock)
            // asSnapshot for the view model's PagingData.
            implementation(libs.androidx.paging.testing)
            // The tests build a WaitlistMemory over a fake of core:datastore's WaitlistJoinsStore.
            implementation(projects.core.datastore)
        }
    }
}
