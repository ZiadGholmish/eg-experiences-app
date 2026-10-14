// Sign-in with Apple or Google, the first-sign-in profile and account deletion (M3-M0, user decisions
// 2026-10-12). A feature like the others: core:* only.
plugins {
    id("bahr.kmp.feature")
    id("bahr.kmp.screenshots")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kermit)
        }
        // Sign in with Google through Credential Manager (Android only; iOS is a Swift bridge).
        androidMain.dependencies {
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.playServicesAuth)
            implementation(libs.google.identity.googleid)
        }
        // AuthApiServiceTest runs the real client (core:network's apiHttpClient) on a MockEngine.
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}
