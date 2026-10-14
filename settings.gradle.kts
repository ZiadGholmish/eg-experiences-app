rootProject.name = "Bahr"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    // Convention plugins (bahr.kmp.library, bahr.kmp.feature, bahr.kmp.app, …).
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":composeApp")

// Core modules — no core module may depend on a feature module.
include(":core:common")
include(":core:network")
include(":core:designsystem")
include(":core:localization")
include(":core:datastore")
// What Home, the trip lists and the map share (M4-M1c): no screens.
include(":core:catalog")

// Test-only: helpers for other modules' test source sets (bahr-modularization: core:testing → core:common).
include(":core:testing")

// Feature modules — one per area of the `/api/v1/**` contract.
include(":feature:splash")
include(":feature:home")
include(":feature:trips")
include(":feature:booking")
include(":feature:map")
// The Settings tab (M4-M8, user decision 2026-10-11).
include(":feature:settings")
// Sign-in with Apple or Google, the account (M3-M0).
include(":feature:auth")
// The My trips tab (M3-M0).
include(":feature:mytrips")

// JVM, test-only: ModuleGraphTest (Konsist) enforces the bahr-modularization rules.
include(":architecture-tests")
