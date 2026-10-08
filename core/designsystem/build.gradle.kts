import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.roborazzi)
}

compose.resources {
    // Internal: features reach fonts and icons only through BahrTheme and BahrIcons.
    publicResClass = false
    packageOfResClass = "eg.bahr.core.designsystem.generated.resources"
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            // Allowed edge (bahr-modularization graph): component copy comes from string resources.
            implementation(projects.core.localization)
            // BahrFormat takes LocalDate / LocalTime (tokens: 24h, Western digits).
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
        }
        // Screenshot + Compose UI tests: Android JVM (Robolectric), see README.md → Testing.
        androidUnitTest.dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.androidx.compose.ui.test.junit4)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            // Test-only edge: the screenshot tests drive ProvideAppLanguage with AppLanguage.
            implementation(projects.core.common)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "eg.bahr.core.designsystem"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        // Robolectric needs the merged assets: compose-resources ships fonts, strings and
        // drawables as Android assets.
        unitTests.isIncludeAndroidResources = true
    }
}

androidComponents {
    // Compose UI tests need ui-test-manifest's ComponentActivity, which is debug-only so it never
    // ships; the release unit-test variant would run the same common tests again without it.
    beforeVariants(selector().withBuildType("release")) { variant ->
        (variant as com.android.build.api.variant.HasUnitTestBuilder).enableUnitTest = false
    }
}

dependencies {
    // Registers the ComponentActivity that createComposeRule() launches under Robolectric.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

roborazzi {
    // Goldens are committed next to the tests that produce them.
    outputDir.set(file("src/androidUnitTest/screenshots"))
}
