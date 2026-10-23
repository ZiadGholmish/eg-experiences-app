import eg.bahr.buildlogic.localSetting

// The shared app module: NavHost, Koin start, deep links, the platform entry functions (no screens). A KMP library
// since M3-M13 (AGP 9): the Android application is :androidApp, the iOS framework Xcode links is built here.
plugins {
    id("bahr.kmp.app")
}

kotlin {
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            // AndroidEntry.kt: AppCompat's night mode follows the stored appearance.
            implementation(libs.androidx.appcompat)
            // Not called here, but the shared back handling needs it at runtime: activity 1.12 is what makes the
            // activity a NavigationEventDispatcherOwner (navigationevent-compose, NavHost's predictive back). Without
            // it the host tests resolve an older activity and find no dispatcher.
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            implementation(projects.feature.splash)
            implementation(projects.feature.home)
            // The trip area (split from feature:trips, M3-M16).
            implementation(projects.feature.tripDetails)
            implementation(projects.feature.tripListing)
            implementation(projects.feature.search)
            implementation(projects.feature.host)
            implementation(projects.feature.reviews)
            implementation(projects.feature.booking)
            implementation(projects.feature.map)
            implementation(projects.feature.settings)
            implementation(projects.feature.auth)
            implementation(projects.feature.mytrips)
            implementation(projects.feature.notifications)

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
        // goldens are core:designsystem's). junit, Robolectric and Compose ui-test come from bahr.kmp.app.
        getByName("androidHostTest").dependencies {
            // TestNavHostController for AppNavHost's root mapping (M4-M9, M4-M8 review #6).
            implementation(libs.androidx.navigation.testing)
        }
    }
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
val writeIosMapsKey =
    tasks.register("writeIosMapsKey") {
        group = "build setup"
        description = "Writes MAPS_API_KEY_IOS into the gitignored iosApp/Configuration/Maps.private.xcconfig."
        val key = localSetting("MAPS_API_KEY_IOS")
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
