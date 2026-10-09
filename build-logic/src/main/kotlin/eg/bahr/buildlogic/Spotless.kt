package eg.bahr.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * One ktlint config everywhere, so the formatter behaves identically in every module, wired into
 * `check` so `./gradlew check` fails on a violation.
 *
 * Applied per module (by the module conventions) rather than from the root over the whole tree:
 * Spotless walks a project's whole directory before filtering, and walking every child's `build/`
 * from a parent hits Kotlin/Native klib paths that appear and vanish mid-build, which fails the
 * task at random.
 */
internal fun Project.configureSpotless() {
    pluginManager.apply("com.diffplug.spotless")
    val ktlintVersion = libs.version("ktlint")
    extensions.configure<SpotlessExtension> {
        bahrKotlin(ktlintVersion, "src/**/*.kt", excludes = listOf("**/build/**", "**/generated/**", "**/.kotlin/**"))
        bahrKotlinGradle(ktlintVersion, "*.gradle.kts")
    }
    tasks.matching { it.name == "check" }.configureEach { dependsOn("spotlessCheck") }
}

internal fun SpotlessExtension.bahrKotlin(
    ktlintVersion: String,
    target: Any,
    excludes: List<String> = emptyList(),
) {
    kotlin {
        target(target)
        // String excludes are matched over a walk of the whole project directory; pass none where
        // the target is already a narrow file tree (the root).
        if (excludes.isNotEmpty()) targetExclude(*excludes.toTypedArray())
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
