// Notifications (M3-M0b, user 2026-10-14): the notifications page (a tab until M3-M6a, now pushed from Home's bell), since
// M3-M6 the inbox itself, the account's or (signed out) this install's guest inbox, and the bell's count. A feature like
// the others: core:* only.
// Since M3-M5 also the push pre-prompt (a slot the trip page places after a waiting-list join) and the platform's
// notification permission behind it.
plugins {
    id("bahr.kmp.feature")
    id("bahr.kmp.screenshots")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The pre-prompt's "Not now" count (core:datastore's PushPromptStore).
            implementation(projects.core.datastore)
            // The inbox is a paged list (`GET /me/notifications`, `/devices/notifications`), on AndroidX Paging.
            implementation(libs.androidx.paging.common)
            implementation(libs.androidx.paging.compose)
        }
        commonTest.dependencies {
            // NotificationsApiServiceTest runs the real client (core:network's apiHttpClient) on a MockEngine.
            implementation(libs.ktor.client.mock)
            implementation(libs.androidx.paging.testing)
        }
        androidMain.dependencies {
            // rememberLauncherForActivityResult for Android 13+'s POST_NOTIFICATIONS dialog.
            implementation(libs.androidx.activity.compose)
        }
    }
}
