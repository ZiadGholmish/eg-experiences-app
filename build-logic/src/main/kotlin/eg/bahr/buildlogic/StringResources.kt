package eg.bahr.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.work.DisableCachingByDefault

/**
 * String resources that compile but misrender (see AGENTS.md "Compose resource gotchas"):
 *  - `\'` in compose resources: compose-resources does not unescape it, so the backslash ships.
 *    (Android `res/` strings go through aapt, where `\'` is the correct escape, so they are exempt.)
 *  - bare `%s` / `%d` anywhere: format args must be positional (`%1$s`), or Arabic word order
 *    cannot reorder them.
 *
 * Registered in every module as `checkStringResources` and wired into its `check`, so
 * `./gradlew checkStringResources` from the root still scans the whole app.
 */
@DisableCachingByDefault(because = "A quick text scan with no outputs")
abstract class CheckStringResourcesTask : DefaultTask() {
    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val composeStrings: ConfigurableFileCollection

    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val androidStrings: ConfigurableFileCollection

    /** Only for readable `path:line` messages. */
    @get:Internal
    abstract val reportRelativeTo: DirectoryProperty

    @TaskAction
    fun scan() {
        val root = reportRelativeTo.get().asFile
        val escapedQuote = Regex("""\\'""")
        val bareFormat = Regex("""%[sd]""")
        val problems = mutableListOf<String>()

        fun scan(
            files: Iterable<java.io.File>,
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
        scan(composeStrings, rejectEscapedQuote = true)
        scan(androidStrings, rejectEscapedQuote = false)
        if (problems.isNotEmpty()) throw GradleException(problems.joinToString("\n"))
    }
}

internal fun Project.registerStringResourceCheck() {
    val checkStrings =
        tasks.register<CheckStringResourcesTask>("checkStringResources") {
            group = "verification"
            description = "Fail on `\\'` in compose string resources and on non-positional %s/%d anywhere."
            composeStrings.from(fileTree("src") { include("*/composeResources/values*/strings.xml") })
            androidStrings.from(fileTree("src") { include("*/res/values*/strings.xml") })
            reportRelativeTo.set(rootProject.layout.projectDirectory)
        }
    tasks.matching { it.name == "check" }.configureEach { dependsOn(checkStrings) }
}
