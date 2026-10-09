plugins {
    id("bahr.kmp.library")
    id("bahr.kmp.compose")
    id("bahr.kmp.screenshots")
}

compose.resources {
    // Internal: features reach fonts and icons only through BahrTheme and BahrIcons.
    publicResClass = false
    packageOfResClass = "eg.bahr.core.designsystem.generated.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
            // Allowed edge (bahr-modularization graph): component copy comes from string resources.
            implementation(projects.core.localization)
            // BahrFormat takes LocalDate / LocalTime (tokens: 24h, Western digits).
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            // Test-only edge: the screenshot tests drive ProvideAppLanguage with AppLanguage.
            implementation(projects.core.common)
        }
    }
}
