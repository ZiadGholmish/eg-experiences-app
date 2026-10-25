import eg.bahr.buildlogic.localSetting

// The Android application (M3-M13): AGP 9 keeps `com.android.application` out of Kotlin Multiplatform modules, so
// the APK is built here around the shared `:composeApp` library. Only the manifest, launcher resources, BuildConfig,
// the flavors and the Application/Activity shells live here; the wiring stays in :composeApp (its androidMain
// `startApp` / `onDeepLink`). bahr-modularization graph: :androidApp → :composeApp only.
plugins {
    id("bahr.android.app")
    // Firebase config (M3-M19a): reads androidApp/src/<flavor>/google-services.json when it is there. Always
    // applied, so dropping the file in turns it on with no build change; a flavor without one builds as before
    // (`missingGoogleServicesStrategy` below). The Firebase SDK and push itself come with M3-M5.
    alias(libs.plugins.googleServices)
}

// `local.properties` is gitignored, so each developer points the local flavor at
// whatever host runs their api deployable. See local.properties.example.
// 10.0.2.2 is the host machine as seen from the Android emulator; 8084 is the
// api deployable's port (admin is 8082 and is not a client of this app).
val localBaseUrl: String = localSetting("LOCAL_BASE_URL", "http://10.0.2.2:8084/api/v1/")

// The host of the share links (`https://<host>/t/{slug}`) the local build opens. `localhost` matches
// the api's own default `SITE_PUBLIC_BASE_URL` (http://localhost:8084), so the "Open in app" link the
// local SSR page builds (`intent://localhost:8084/t/…`) resolves to this app. Set it to a tunnel's
// host to check App Links verification end to end; see README → Deep links.
val localAppLinkHost: String = localSetting("LOCAL_APP_LINK_HOST", "localhost")

// Google Maps key (M4-M2) from the gitignored local.properties (or the CI's environment); never committed. An
// empty one still builds: the map screen then draws its pins on a plain ground and logs a warning once. Only
// presence is ever reported here, never the value.
val androidMapsKey: String = localSetting("MAPS_API_KEY_ANDROID")

// Sign in with Google (M3-M0): the Google "web" (server) OAuth client id Credential Manager puts in the ID
// token's `aud`, the same value the backend lists in AUTH_GOOGLE_CLIENT_IDS. From local.properties or the
// environment, like the Maps key. One per flavor (M3-M19a), GOOGLE_SERVER_CLIENT_ID_<FLAVOR>, because setting
// it is what switches a flavor to real sign-in: one shared value would also turn off the local flavor's fake
// sign-in, which the local api's fake verifier needs. Empty: the local flavor signs in with fake tokens, the
// others offer no sign-in (the sheet says sign-in is not set up). docs/setup-signin-push.md.
fun googleServerClientId(flavor: String): String = localSetting("GOOGLE_SERVER_CLIENT_ID_${flavor.uppercase()}")

// The single pre-M3-M19a name is no longer read; say so rather than silently building without sign-in.
if (localSetting("GOOGLE_SERVER_CLIENT_ID").isNotEmpty()) {
    logger.warn("GOOGLE_SERVER_CLIENT_ID is ignored: set GOOGLE_SERVER_CLIENT_ID_LOCAL/_DEV/_PROD (docs/setup-signin-push.md).")
}
if (androidMapsKey.isEmpty()) logger.warn("MAPS_API_KEY_ANDROID is not set: the Android map shows pins on a plain ground.")

googleServices {
    // A flavor without its google-services.json (every flavor on a fresh checkout) still builds: the app then has
    // no Firebase config and push stays off (M3-M5). The file is per flavor because each Firebase app is made
    // for one package (eg.bahr.app.dev, …), and the plugin refuses a file with no client for the variant's id.
    missingGoogleServicesStrategy = com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy.IGNORE
}

dependencies {
    implementation(projects.composeApp)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
}

android {
    // Keeps BuildConfig in `eg.bahr`, where the entry points import it from (and the application id below
    // unchanged, so signing and App Links verification are the same app).
    namespace = "eg.bahr"

    defaultConfig {
        applicationId = "eg.bahr.app"
        versionCode = 1
        versionName = "0.1"
        // AndroidManifest.xml → com.google.android.geo.API_KEY. Restrict the key to this app's package
        // names and signing SHA-1 in the Google Cloud console.
        manifestPlaceholders["mapsApiKey"] = androidMapsKey
        // Fake sign-in only where the backend can verify it: the local flavor (see "local" below).
        buildConfigField("boolean", "ALLOW_FAKE_SIGN_IN", "false")
    }

    flavorDimensions += "environment"

    // Per environment: the api's BASE_URL and the share-link host (App Links). The dev hosts are
    // placeholders until the deployables are actually deployed — see ../docs/PLAN.md (M7). The app
    // link host is set twice from one value: the manifest's intent filter (placeholder) and the
    // shared parser (BuildConfig), so the two cannot disagree.
    productFlavors {
        create("local") {
            dimension = "environment"
            googleSignIn(googleServerClientId("local"))
            applicationIdSuffix = ".local"
            versionNameSuffix = "-local"
            buildConfigField("String", "BASE_URL", "\"$localBaseUrl\"")
            appLinks(host = localAppLinkHost, allowsHttp = true)
            // The local api runs the `local` profile, whose fake verifier takes made-up ID tokens (M3-B1).
            buildConfigField("boolean", "ALLOW_FAKE_SIGN_IN", "true")
        }
        create("dev") {
            dimension = "environment"
            googleSignIn(googleServerClientId("dev"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "BASE_URL", "\"https://d1epegbe1ipzwy.cloudfront.net/api/v1/\"")
            appLinks(host = "d1epegbe1ipzwy.cloudfront.net", allowsHttp = false)
        }
        create("prod") {
            dimension = "environment"
            googleSignIn(googleServerClientId("prod"))
            buildConfigField("String", "BASE_URL", "\"https://api.example.invalid/api/v1/\"")
            appLinks(host = "bahr.eg", allowsHttp = false)
        }
    }
}

/** The flavor's Google server client id ([googleServerClientId]) into BuildConfig; empty when unset. */
fun com.android.build.api.dsl.ApplicationProductFlavor.googleSignIn(serverClientId: String) {
    buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$serverClientId\"")
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
