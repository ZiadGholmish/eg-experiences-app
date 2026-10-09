import java.util.Properties

plugins {
    id("bahr.kmp.app")
}

// `local.properties` is gitignored, so each developer points the local flavor at
// whatever host runs their api deployable. See local.properties.example.
val localProperties =
    Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { stream -> load(stream) }
    }

// 10.0.2.2 is the host machine as seen from the Android emulator; 8084 is the
// api deployable's port (admin is 8082 and is not a client of this app).
val localBaseUrl: String =
    localProperties.getProperty(
        "LOCAL_BASE_URL",
        "http://10.0.2.2:8084/api/v1/",
    )

kotlin {
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.appcompat)
            implementation(libs.koin.android)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            implementation(projects.feature.splash)
            implementation(projects.feature.trips)
            implementation(projects.feature.booking)

            implementation(projects.core.common)
            implementation(projects.core.network)
            implementation(projects.core.designsystem)
            implementation(projects.core.localization)
            implementation(projects.core.datastore)

            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.navigation.compose)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

android {
    // Keeps BuildConfig in `eg.bahr`, where the entry points import it from.
    namespace = "eg.bahr"

    defaultConfig {
        applicationId = "eg.bahr.app"
        versionCode = 1
        versionName = "0.1"
    }

    flavorDimensions += "environment"

    // BASE_URL is the only per-environment value so far. Both dev and prod hosts
    // are placeholders until the api deployable is actually deployed — see
    // ../docs/PLAN.md (M7).
    productFlavors {
        create("local") {
            dimension = "environment"
            applicationIdSuffix = ".local"
            versionNameSuffix = "-local"
            buildConfigField("String", "BASE_URL", "\"$localBaseUrl\"")
        }
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "BASE_URL", "\"https://api-dev.example.invalid/api/v1/\"")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "BASE_URL", "\"https://api.example.invalid/api/v1/\"")
        }
    }
}
