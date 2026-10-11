// What the trip-listing screens share (M4-M1c): Home (feature:home), the category page, "See all" and
// search (feature:trips), and the map (feature:map). No screens: wire shapes, the `GET /trips` read,
// the paged list's parts, the trip card and the waiting-list memory. bahr-modularization graph:
// core:catalog → core:common, core:network, core:datastore, core:localization, core:designsystem.
plugins {
    id("bahr.kmp.library")
    id("bahr.kmp.compose")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.network)
            // WaitlistMemory reads the stored waiting-list joins (M4-M5).
            implementation(projects.core.datastore)
            implementation(projects.core.localization)
            implementation(projects.core.designsystem)

            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
            // collectWaitlistTags runs in a list's view model scope.
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.paging.common)
            implementation(libs.androidx.paging.compose)
            implementation(libs.coil.compose)
            implementation(libs.kermit)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            // The `GET /trips` tests run the real client (core:network's apiHttpClient) on a MockEngine.
            implementation(libs.ktor.client.mock)
        }
    }
}
