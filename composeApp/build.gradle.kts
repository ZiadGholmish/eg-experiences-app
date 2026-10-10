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

// The host of the share links (`https://<host>/t/{slug}`) the local build opens. `localhost` matches
// the api's own default `SITE_PUBLIC_BASE_URL` (http://localhost:8084), so the "Open in app" link the
// local SSR page builds (`intent://localhost:8084/t/…`) resolves to this app. Set it to a tunnel's
// host to check App Links verification end to end; see README → Deep links.
val localAppLinkHost: String = localProperties.getProperty("LOCAL_APP_LINK_HOST", "localhost")

// Google Maps keys (M4-M2), one per platform, from the gitignored local.properties (or the CI's
// environment); never committed. An empty one still builds: the map screen then draws its pins on a
// plain ground and logs a warning once. Only presence is ever reported here, never the value.
fun mapsKey(name: String): String =
    localProperties
        .getProperty(name)
        ?.trim()
        .orEmpty()
        .ifEmpty { System.getenv(name).orEmpty() }

val androidMapsKey: String = mapsKey("MAPS_API_KEY_ANDROID")
if (androidMapsKey.isEmpty()) logger.warn("MAPS_API_KEY_ANDROID is not set: the Android map shows pins on a plain ground.")

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
            implementation(projects.feature.map)

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
        // AndroidManifest.xml → com.google.android.geo.API_KEY. Restrict the key to this app's package
        // names and signing SHA-1 in the Google Cloud console.
        manifestPlaceholders["mapsApiKey"] = androidMapsKey
    }

    flavorDimensions += "environment"

    // Per environment: the api's BASE_URL and the share-link host (App Links). The dev hosts are
    // placeholders until the deployables are actually deployed — see ../docs/PLAN.md (M7). The app
    // link host is set twice from one value: the manifest's intent filter (placeholder) and the
    // shared parser (BuildConfig), so the two cannot disagree.
    productFlavors {
        create("local") {
            dimension = "environment"
            applicationIdSuffix = ".local"
            versionNameSuffix = "-local"
            buildConfigField("String", "BASE_URL", "\"$localBaseUrl\"")
            appLinks(host = localAppLinkHost, allowsHttp = true)
        }
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "BASE_URL", "\"https://api-dev.example.invalid/api/v1/\"")
            appLinks(host = "dev.example.invalid", allowsHttp = false)
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "BASE_URL", "\"https://api.example.invalid/api/v1/\"")
            appLinks(host = "bahr.eg", allowsHttp = false)
        }
    }
}

/**
 * App Links for one flavor. Only `local` takes plain http: the local api serves its share page over
 * http, so its "Open in app" intent says `scheme=http`. Every other flavor's filter lists https
 * twice, which is the same as once.
 */
fun com.android.build.api.dsl.ApplicationProductFlavor.appLinks(
    host: String,
    allowsHttp: Boolean,
) {
    manifestPlaceholders["appLinkHost"] = host
    manifestPlaceholders["appLinkLocalScheme"] = if (allowsHttp) "http" else "https"
    buildConfigField("String", "APP_LINK_HOST", "\"$host\"")
    buildConfigField("boolean", "APP_LINK_ALLOWS_HTTP", allowsHttp.toString())
}

/**
 * iOS Maps key (M4-M2): writes MAPS_API_KEY_IOS from local.properties (or the environment) into the
 * gitignored `iosApp/Configuration/Maps.private.xcconfig`, which Config.xcconfig includes if present;
 * Info.plist passes it to the app, which hands it to `GMSServices`. The Xcode build runs this through
 * `embedAndSignAppleFrameworkForXcode`, but Xcode reads xcconfig files before its build phases, so a
 * changed key reaches the app on the build after; run this task once by hand after setting it.
 *
 * With no key in local.properties or the environment it leaves the file alone, so a key pasted into
 * a hand-copied Maps.private.xcconfig (see its .example) survives every Xcode build.
 */
val writeIosMapsKey by tasks.registering {
    group = "build setup"
    description = "Writes MAPS_API_KEY_IOS into the gitignored iosApp/Configuration/Maps.private.xcconfig."
    val key = mapsKey("MAPS_API_KEY_IOS")
    val target = rootProject.layout.projectDirectory.file("iosApp/Configuration/Maps.private.xcconfig")
    // No declared output: the task never deletes or replaces a file it did not write this run.
    doLast {
        val file = target.asFile
        if (key.isEmpty()) {
            if (!file.exists()) logger.warn("MAPS_API_KEY_IOS is not set: the iOS map shows pins on a plain ground.")
            return@doLast
        }
        file.writeText(
            "// Generated by ./gradlew :composeApp:writeIosMapsKey from local.properties. Gitignored: never commit.\n" +
                "MAPS_API_KEY_IOS = $key\n",
        )
    }
}
tasks.matching { it.name == "embedAndSignAppleFrameworkForXcode" }.configureEach { dependsOn(writeIosMapsKey) }
