plugins {
    id("bahr.kmp.library")
    id("bahr.kmp.compose")
    id("bahr.kmp.screenshots")
}

compose.resources {
    publicResClass = true
    packageOfResClass = "eg.bahr.core.localization.generated.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.compose.components.resources)
            implementation(projects.core.common)
        }
        androidUnitTest.dependencies {
            implementation(libs.compose.foundation)
        }
    }
}
