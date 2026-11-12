// What the trip-listing screens share (M4-M1c): Home (feature:home), the category page, "See all" and
// search (feature:trip-listing, feature:search), the map (feature:map), and since M3-M16 the
// review, host and page pieces two or more of feature:trip-details, feature:host and feature:reviews share. No screens: wire shapes, the `GET /trips` read,
// the paged list's parts, the trip card and the waiting-list memory. bahr-modularization graph:
// core:catalog → core:common, core:network, core:datastore, core:localization, core:designsystem.
plugins {
    id("bahr.kmp.library")
    id("bahr.kmp.compose")
    // The full-screen viewer's goldens (MV-M3).
    id("bahr.kmp.screenshots")
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
            // The full-screen viewer (MV-M3): Back closes it, and a video pauses when the app goes to the background.
            implementation(libs.navigationevent.compose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        androidMain.dependencies {
            // The viewer's video player on Android (MV-M3); AVPlayer on iOS. Libraries, not module edges.
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.ui.compose)
            // WindowCompat: the viewer hides the status and navigation bars while it is open.
            implementation(libs.androidx.core)
        }
        getByName("androidHostTest").dependencies {
            // The viewer's Back test: activity 1.12+ makes the test activity a NavigationEventDispatcherOwner (as :composeApp's).
            implementation(libs.androidx.activity.compose)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            // The `GET /trips` tests run the real client (core:network's apiHttpClient) on a MockEngine.
            implementation(libs.ktor.client.mock)
        }
    }
}
