plugins {
    `kotlin-dsl`
}

// compileOnly: the root build script declares the same plugins (`apply false`), so they load once,
// into the root classloader, and every project applies the same class. Bundling them here as
// `implementation` would load a second copy.
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.kover.gradlePlugin)
    compileOnly(libs.spotless.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "bahr.kmp.library"
            implementationClass = "eg.bahr.buildlogic.KmpLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id = "bahr.kmp.compose"
            implementationClass = "eg.bahr.buildlogic.KmpComposeConventionPlugin"
        }
        register("kmpScreenshots") {
            id = "bahr.kmp.screenshots"
            implementationClass = "eg.bahr.buildlogic.ScreenshotTestsConventionPlugin"
        }
        register("kmpFeature") {
            id = "bahr.kmp.feature"
            implementationClass = "eg.bahr.buildlogic.KmpFeatureConventionPlugin"
        }
        register("kmpApp") {
            id = "bahr.kmp.app"
            implementationClass = "eg.bahr.buildlogic.KmpAppConventionPlugin"
        }
        register("architectureTests") {
            id = "bahr.architecture-tests"
            implementationClass = "eg.bahr.buildlogic.ArchitectureTestsConventionPlugin"
        }
        register("root") {
            id = "bahr.root"
            implementationClass = "eg.bahr.buildlogic.RootConventionPlugin"
        }
    }
}
