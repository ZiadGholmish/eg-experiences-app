import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    // Declared here without applying so each plugin is loaded once, into the
    // root classloader, rather than once per subproject.
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.spotless)
}

// Spotless with one ktlint config, so the formatter behaves identically
// everywhere. Wired into `check`, so `./gradlew check` fails on a violation.
//
// Applied only to projects that actually hold sources. `:core` and `:feature`
// are grouping projects with none, and Spotless walks a project's whole
// directory before filtering — so configuring it there means walking every
// child's `build/`, including Kotlin/Native klib paths that appear and vanish
// mid-build, which fails the task at random.
val ktlintVersion = libs.versions.ktlint.get()

fun SpotlessExtension.kotlinSources() {
    kotlin {
        target("src/**/*.kt")
        targetExclude("**/build/**", "**/generated/**", "**/.kotlin/**")
        ktlint(ktlintVersion).editorConfigOverride(
            mapOf(
                "ktlint_code_style" to "ktlint_official",
                // @Composable functions are PascalCase per Compose convention;
                // exempt them from `function-naming`.
                "ktlint_function_naming_ignore_when_annotated_with" to
                    "Composable,Preview",
                // Compose code-style uses PascalCase for top-level Dp / Color /
                // Shape design tokens (`CardRadius = 20.dp`). `property-naming`
                // would demand SCREAMING_SNAKE_CASE.
                "ktlint_standard_property-naming" to "disabled",
            ),
        )
    }
}

fun SpotlessExtension.ownBuildScripts() {
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(ktlintVersion).editorConfigOverride(
            mapOf("ktlint_code_style" to "ktlint_official"),
        )
    }
}

// The root holds build scripts but no Kotlin sources.
spotless { ownBuildScripts() }

subprojects {
    if (!file("src").isDirectory) return@subprojects
    apply(plugin = "com.diffplug.spotless")
    extensions.configure<SpotlessExtension> {
        kotlinSources()
        ownBuildScripts()
    }
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn("spotlessCheck")
    }
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn("spotlessCheck")
}

// Run once after cloning. The hook runs spotlessCheck on commit and points at
// spotlessApply when it fails.
tasks.register("installGitHooks") {
    group = "git hooks"
    description = "Copy scripts/git-hooks/pre-commit into .git/hooks/."
    val source = rootProject.file("scripts/git-hooks/pre-commit")
    val destination = rootProject.file(".git/hooks/pre-commit")
    inputs.file(source)
    outputs.file(destination)
    doLast {
        if (!source.exists()) {
            throw GradleException("Missing $source — check it into the repo first.")
        }
        destination.parentFile.mkdirs()
        source.copyTo(destination, overwrite = true)
        destination.setExecutable(true)
        println("Installed pre-commit hook at $destination")
    }
}
