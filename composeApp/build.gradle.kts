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
            implementation(projects.feature.home)
            implementation(projects.feature.trips)
            implementation(projects.feature.booking)
            implementation(projects.feature.map)
            implementation(projects.feature.settings)

            implementation(projects.core.common)
            implementation(projects.core.network)
            implementation(projects.core.designsystem)
            implementation(projects.core.localization)
            implementation(projects.core.datastore)
            // Its Koin module (the card cache, `GET /trips`, the waiting-list memory) is started here, M4-M1c.
            implementation(projects.core.catalog)

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
            // Back on a non-Home tab root goes to Home (M4-M8, user decision 2026-10-11).
            implementation(libs.navigationevent.compose)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            // A failed language write from a deep link's `?lang=` is logged, not shown (M4-M8).
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            // AppKoinGraphTest starts the full module list with core:testing's graph helper (M4-M7).
            implementation(projects.core.testing)
        }
        // The tab host's Compose tests on Robolectric (M4-M8): back stacks and scroll kept per tab, the
        // bar on tab roots only. No screenshots here: the app module cannot run Roborazzi (the bar's
        // goldens are core:designsystem's).
        androidUnitTest.dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.androidx.compose.ui.test.junit4)
            // TestNavHostController for AppNavHost's root mapping (M4-M9, M4-M8 review #6).
            implementation(libs.androidx.navigation.testing)
        }
    }
}

dependencies {
    // Registers the ComponentActivity that createComposeRule() launches under Robolectric.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

androidComponents {
    // As in bahr.kmp.screenshots: the Compose tests need ui-test-manifest's debug-only activity, so the
    // release unit-test variants (which would run the same common tests again) are off.
    beforeVariants(selector().withBuildType("release")) { variant ->
        (variant as com.android.build.api.variant.HasUnitTestBuilder).enableUnitTest = false
    }
}

android {
    // Keeps BuildConfig in `eg.bahr`, where the entry points import it from.
    namespace = "eg.bahr"

    // Robolectric needs the merged assets: compose-resources ships strings and drawables as assets.
    testOptions.unitTests.isIncludeAndroidResources = true

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
 * The file is written only when its content would change, so an unchanged key leaves its mtime alone
 * on every Xcode build (M4-M2 review #13). With no key in local.properties or the environment, a file
 * this task generated (it starts with `mapsGeneratedMarker`) is blanked, so a key removed from
 * local.properties stops reaching the app; a hand-copied file (see its .example) is left as it is.
 */
val mapsGeneratedMarker = "// Generated by ./gradlew :composeApp:writeIosMapsKey"
val writeIosMapsKey by tasks.registering {
    group = "build setup"
    description = "Writes MAPS_API_KEY_IOS into the gitignored iosApp/Configuration/Maps.private.xcconfig."
    val key = mapsKey("MAPS_API_KEY_IOS")
    val marker = mapsGeneratedMarker
    val target = rootProject.layout.projectDirectory.file("iosApp/Configuration/Maps.private.xcconfig")
    // No declared output: the task never deletes or replaces a file it did not generate.
    doLast {
        val file = target.asFile
        val current = if (file.exists()) file.readText() else null
        val wanted =
            when {
                key.isNotEmpty() ->
                    "$marker from local.properties or the MAPS_API_KEY_IOS environment variable. Gitignored: never commit.\n" +
                        "MAPS_API_KEY_IOS = $key\n"

                current?.startsWith(marker) == true ->
                    "$marker: no MAPS_API_KEY_IOS is set, so the map shows pins on a plain ground. Gitignored: never commit.\n" +
                        "MAPS_API_KEY_IOS =\n"

                else -> null
            }
        if (key.isEmpty() && current?.startsWith(marker) != false) {
            logger.warn("MAPS_API_KEY_IOS is not set: the iOS map shows pins on a plain ground.")
        }
        if (wanted != null && wanted != current) file.writeText(wanted)
    }
}
tasks.matching { it.name == "embedAndSignAppleFrameworkForXcode" }.configureEach { dependsOn(writeIosMapsKey) }
