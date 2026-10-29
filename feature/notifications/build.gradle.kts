// Notifications (M3-M0b, user 2026-10-14): the Alerts page (a tab until M3-M6a, now pushed from Home's bell). Until M3-B9/M3-M6 bring the list, it shows
// the empty state signed in and a sign-in prompt signed out. A feature like the others: core:* only.
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
        }
        androidMain.dependencies {
            // rememberLauncherForActivityResult for Android 13+'s POST_NOTIFICATIONS dialog.
            implementation(libs.androidx.activity.compose)
        }
    }
}
