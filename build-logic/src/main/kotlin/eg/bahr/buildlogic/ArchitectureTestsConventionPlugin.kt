package eg.bahr.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

/**
 * `bahr.architecture-tests`: the JVM module that holds `ModuleGraphTest` (Konsist).
 *
 * The dependency rules are checked against the **Gradle model**, not by parsing build files:
 * `writeModuleGraph` lists every `project(...)` dependency declared on any configuration of any
 * module, after all projects are configured. That includes the edges convention plugins add
 * (`bahr.kmp.feature` adds its `core:*` edges), and every spelling (`projects.core.common`,
 * `project(":core:common")`), which a text parser would have to chase.
 *
 * Reading other projects' configurations works with the configuration cache, but not with Gradle
 * project isolation. If that is adopted, have each module publish its own edges instead.
 */
class ArchitectureTestsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            configureSpotless()

            dependencies {
                "testImplementation"(libs.library("konsist"))
                "testImplementation"(libs.library("kotlin-test"))
                "testImplementation"(libs.library("junit"))
            }

            val root = rootProject
            val graphFile = layout.buildDirectory.file("module-graph/graph.txt")
            val writeModuleGraph =
                tasks.register<WriteModuleGraphTask>("writeModuleGraph") {
                    // Evaluated lazily, once every project is configured and has declared its edges.
                    lines.set(provider { moduleGraph(root) })
                    output.set(graphFile)
                }

            tasks.named<Test>("test") {
                useJUnit()
                systemProperty("bahr.moduleGraph", graphFile.get().asFile.absolutePath)
                systemProperty("bahr.rootDir", root.rootDir.absolutePath)
                inputs
                    .file(writeModuleGraph.flatMap { it.output })
                    .withPropertyName("moduleGraph")
                    .withPathSensitivity(PathSensitivity.NONE)
                // Konsist reads the sources directly; without them as inputs a changed source file
                // could come back from the build cache as a stale pass.
                inputs
                    .files(
                        root.fileTree(root.rootDir) {
                            include("composeApp/src/**/*.kt", "core/*/src/**/*.kt", "feature/*/src/**/*.kt")
                            exclude("**/build/**")
                        },
                    ).withPropertyName("scannedSources")
                    .withPathSensitivity(PathSensitivity.RELATIVE)
            }
        }
}

/**
 * `module <path>` for every module with a build file, then one line per declared edge:
 * `edge <from> <to> <configuration>`, e.g. `edge :feature:trips :core:common commonMainImplementation`.
 */
private fun moduleGraph(root: Project): List<String> {
    val modules = root.subprojects.filter { it.buildFile.exists() }
    val moduleLines = modules.map { "module ${it.path}" }
    val edgeLines =
        modules.flatMap { module ->
            module.configurations.flatMap { configuration ->
                configuration.dependencies
                    .withType(ProjectDependency::class.java)
                    // AGP wires each module's test variants to its own main variant; not an edge.
                    .filter { it.path != module.path }
                    .map { "edge ${module.path} ${it.path} ${configuration.name}" }
            }
        }
    return (moduleLines + edgeLines).distinct().sorted()
}

@CacheableTask
abstract class WriteModuleGraphTask : DefaultTask() {
    @get:Input
    abstract val lines: ListProperty<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun write() {
        output.get().asFile.writeText(lines.get().joinToString("\n", postfix = "\n"))
    }
}
