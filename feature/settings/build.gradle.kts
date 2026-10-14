// Profile (M3-M0b; the Settings tab of M4-M8, user decision 2026-10-11): the account, Edit personal info,
// the language switch, the app version and the about line.
// The Profile tab's root; a feature like the others, so it depends on core:* only.
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
        // ProfileApiServiceTest and the Koin graph test run the real client on a MockEngine.
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}
