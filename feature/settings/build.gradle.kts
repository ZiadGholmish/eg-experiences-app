// Settings (M4-M8, user decision 2026-10-11): the language switch, the app version and the about line.
// The Settings tab's root; a feature like the others, so it depends on core:* only.
plugins {
    id("bahr.kmp.feature")
    id("bahr.kmp.screenshots")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The stored language it reads and writes (AppSettingsStore). An allowed edge: a feature may
            // depend on any core module.
            implementation(projects.core.datastore)
        }
    }
}
