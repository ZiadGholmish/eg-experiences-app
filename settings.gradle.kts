rootProject.name = "EgExperiences"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
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

// Feature modules — one per area of the `/api/v1/**` contract.
include(":feature:splash")
include(":feature:trips")
include(":feature:booking")
