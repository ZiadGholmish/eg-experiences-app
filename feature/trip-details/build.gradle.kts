// The trip page (M3-M16, split from feature:trips): hero, overview, story, dates and the waiting list.
plugins {
    id("bahr.kmp.feature")
    id("bahr.kmp.screenshots")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The card cache (text-first while the trip loads), the waiting-list memory, the review and host pieces
            // the host page shares (M4-M1c, M3-M16).
            implementation(projects.core.catalog)
        }
        commonTest.dependencies {
            // The ApiService tests run the real client (core:network's apiHttpClient) on a MockEngine.
            implementation(libs.ktor.client.mock)
            // The Koin graph test binds core:datastore's waiting-list store (catalogModule's WaitlistMemory reads it).
            implementation(projects.core.datastore)
        }
    }
}
