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
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.kover)
}

// Spotless with one ktlint config, so the formatter behaves identically
// everywhere. Wired into `check`, so `./gradlew check` fails on a violation.
//
// Applied only to projects that actually hold sources. `:core` and `:feature`
// are grouping projects with none, and Spotless walks a project's whole
// directory before filtering — so configuring it there means walking every
// child's `build/`, including Kotlin/Native klib paths that appear and vanish
// mid-build, which fails the task at random.
// Raised as coverage grows; never set above what `koverHtmlReportMobile` measures.
val koverMinLinePercent = 74 // measured 74.7 % line (713/955) at M0-M4

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

// String resources that compile but misrender (see AGENTS.md "Compose resource gotchas"):
//  - `\'` in compose resources: compose-resources does not unescape it, so the backslash ships.
//    (Android `res/` strings go through aapt, where `\'` is the correct escape, so they are exempt.)
//  - bare `%s` / `%d` anywhere: format args must be positional (`%1$s`), or Arabic word order
//    cannot reorder them.
val composeStringFiles =
    fileTree(rootDir) {
        include("**/src/*/composeResources/values*/strings.xml")
        exclude("**/build/**")
    }
val androidStringFiles =
    fileTree(rootDir) {
        include("**/src/*/res/values*/strings.xml")
        exclude("**/build/**")
    }
val checkStringResources =
    tasks.register("checkStringResources") {
        group = "verification"
        description = "Fail on `\\'` in compose string resources and on non-positional %s/%d anywhere."
        val compose = composeStringFiles
        val android = androidStringFiles
        val root = rootDir
        inputs.files(compose, android)
        doLast {
            val escapedQuote = Regex("""\\'""")
            val bareFormat = Regex("""%[sd]""")
            val problems = mutableListOf<String>()

            fun scan(
                files: Iterable<File>,
                rejectEscapedQuote: Boolean,
            ) = files.forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    val where = "${file.relativeTo(root)}:${index + 1}"
                    if (rejectEscapedQuote && escapedQuote.containsMatchIn(line)) {
                        problems += "$where: `\\'` ships a visible backslash; use ’ or a plain '"
                    }
                    if (bareFormat.containsMatchIn(line)) {
                        problems += "$where: bare %s/%d; use positional %1\$s / %1\$d"
                    }
                }
            }
            scan(compose, rejectEscapedQuote = true)
            scan(android, rejectEscapedQuote = false)
            if (problems.isNotEmpty()) throw GradleException(problems.joinToString("\n"))
        }
    }

tasks.matching { it.name == "check" }.configureEach { dependsOn(checkStringResources) }
subprojects {
    tasks.matching { it.name == "check" }.configureEach { dependsOn(checkStringResources) }
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

// Coverage (Kover): `./gradlew koverHtmlReportMobile` / `koverXmlReportMobile` / `koverVerifyMobile`.
// Scope per PLAN §11: core:* plus view models. Kover measures JVM code only, i.e. the Android
// unit tests (`testDebugUnitTest`); commonTest runs there too, so iOS-only paths are the gap.
// Each covered module contributes its Android `debug` variant to a custom `mobile` variant, and
// the root merges every dependency's `mobile` variant into one report.
val koverProjects =
    listOf(
        ":core:common",
        ":core:network",
        ":core:datastore",
        ":core:localization",
        ":core:designsystem",
        ":feature:trips",
        ":feature:booking",
    )
koverProjects.forEach { path ->
    project(path) {
        apply(plugin = "org.jetbrains.kotlinx.kover")
        extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension> {
            currentProject { createVariant("mobile") { add("debug") } }
        }
    }
}

dependencies { koverProjects.forEach { kover(project(it)) } }

kover {
    currentProject { createVariant("mobile") {} }
    reports {
        filters {
            includes {
                classes("eg.bahr.core.*", "*ViewModel", "*ViewModel$*")
            }
            excludes {
                // Generated by compose-resources and the Compose compiler; not our code.
                classes("*.generated.resources.*", "*ComposableSingletons*", "*.BuildConfig")
            }
        }
        // Gate at the measured level (see README.md → Testing), so coverage can only go up.
        verify {
            rule {
                minBound(koverMinLinePercent)
            }
        }
    }
}

tasks.matching { it.name == "check" }.configureEach { dependsOn("koverVerifyMobile") }
