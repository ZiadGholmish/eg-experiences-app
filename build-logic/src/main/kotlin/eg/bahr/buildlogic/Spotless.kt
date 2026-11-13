package eg.bahr.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * One ktlint config everywhere, so the formatter behaves identically in every module, wired into
 * `check` so `./gradlew check` fails on a violation.
 *
 * Applied per module (by the module conventions) rather than from the root over the whole tree, and
 * every target is a file tree whose walk never enters `build/` (MV-M14). Spotless turns each *string*
 * pattern, including a `targetExclude` string, into a file tree over the whole project directory, and
 * an exclude tree gets none of Spotless's default build-dir excludes: the old string
 * `targetExclude` of every `build` directory listed each file under `build/` while the same build was writing
 * Kotlin/Native klibs and processed resources there, failing the task at random with "Could not read
 * path …/build/…". Excludes given as patterns *inside* a file tree are only matched, never walked.
 */
internal fun Project.configureSpotless() {
    pluginManager.apply("com.diffplug.spotless")
    val ktlintVersion = libs.version("ktlint")
    extensions.configure<SpotlessExtension> {
        bahrKotlin(
            ktlintVersion,
            fileTree("src") {
                include("**/*.kt")
                exclude("**/build/**", "**/generated/**", "**/.kotlin/**")
            },
        )
        // Rooted at the project directory, but Gradle only descends into directories an include pattern can
        // match into, and a top-level `*.gradle.kts` matches none: `build/` is never entered.
        bahrKotlinGradle(ktlintVersion, fileTree(projectDir) { include("*.gradle.kts") })
    }
    tasks.matching { it.name == "check" }.configureEach { dependsOn("spotlessCheck") }
}

internal fun SpotlessExtension.bahrKotlin(
    ktlintVersion: String,
    target: Any,
) {
    kotlin {
        // Callers pass a file tree, never a string pattern or a `targetExclude` string: see configureSpotless.
        target(target)
        ktlint(ktlintVersion).editorConfigOverride(
            mapOf(
                "ktlint_code_style" to "ktlint_official",
                // @Composable functions are PascalCase per Compose convention;
                // exempt them from `function-naming`.
                "ktlint_function_naming_ignore_when_annotated_with" to "Composable,Preview",
                // Compose code-style uses PascalCase for top-level Dp / Color /
                // Shape design tokens (`CardRadius = 20.dp`). `property-naming`
                // would demand SCREAMING_SNAKE_CASE.
                "ktlint_standard_property-naming" to "disabled",
            ),
        )
    }
}

internal fun SpotlessExtension.bahrKotlinGradle(
    ktlintVersion: String,
    vararg targets: Any,
) {
    kotlinGradle {
        target(*targets)
        ktlint(ktlintVersion).editorConfigOverride(mapOf("ktlint_code_style" to "ktlint_official"))
    }
}
