plugins {
    // Declared here without applying so each plugin is loaded once, into the
    // root classloader, rather than once per subproject. build-logic compiles
    // against them (compileOnly) and applies them by id.
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.kover) apply false
    // Root-only setup: Spotless for build scripts and build-logic, the merged
    // Kover report + gate (koverVerifyMobile), installGitHooks.
    id("bahr.root")
}
