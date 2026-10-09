package eg.bahr.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The mobile half of the `bahr-modularization` skill, enforced.
 *
 * Dependency rules read the Gradle model (`writeModuleGraph`, see build-logic
 * `ArchitectureTestsConventionPlugin`): every `project(...)` dependency any module declares, on any
 * configuration, including the ones convention plugins add. Source rules read the sources with
 * Konsist.
 *
 * Adding a module: put it in [allowedProduction] (core) or rely on the feature / app rules, after
 * the skill graph and PLAN.md name it. A new edge is a plan change.
 */
class ModuleGraphTest {
    // ---- Dependency graph (Gradle model) ----

    @Test
    fun `the module graph was read`() {
        assertTrue(":composeApp" in graph.modules && ":core:common" in graph.modules, "modules: ${graph.modules}")
        assertTrue(graph.edges.any { it.from == ":composeApp" && it.to.startsWith(":feature:") }, "edges: ${graph.edges}")
    }

    @Test
    fun `every module is one the graph knows`() {
        val unknown =
            graph.modules.filterNot {
                it in allowedProduction || it.isFeature || it == APP || it == ARCHITECTURE_TESTS
            }
        assertNone(unknown, "Modules missing from ModuleGraphTest.allowedProduction (add them with the skill graph)")
    }

    @Test
    fun `no feature depends on another feature`() {
        assertNone(
            graph.edges.filter { it.from.isFeature && it.to.isFeature },
            "feature → feature (navigate across features through lambdas wired in :composeApp)",
        )
    }

    @Test
    fun `no core module depends on a feature`() {
        assertNone(graph.edges.filter { it.from.isCore && it.to.isFeature }, "core → feature")
    }

    @Test
    fun `production edges are the skill graph's`() {
        assertNone(
            graph.edges.filter { !it.isTestOnly && it.to !in allowedProductionTargets(it.from) },
            "Production edges outside the bahr-modularization graph",
        )
    }

    @Test
    fun `test-only edges point only at upstream core modules or core testing`() {
        assertNone(
            graph.edges.filter { it.isTestOnly && it.to !in allowedTestTargets(it.from) },
            "Test-only edges outside the bahr-modularization graph",
        )
    }

    @Test
    fun `modules depend on each other with implementation, never api`() {
        assertNone(
            graph.edges.filter { it.configuration == "api" || it.configuration.endsWith("Api") },
            "api(...) between modules leaks internals transitively (skill principle 7)",
        )
    }

    // ---- Sources (Konsist) ----

    @Test
    fun `feature sources import no other feature`() {
        val violations =
            featureFiles.flatMap { file ->
                val own = "eg.bahr.feature.${file.module.substringAfterLast(':')}."
                file.imports
                    .map { it.name }
                    .filter {
                        it.startsWith(
                            "eg.bahr.feature.",
                        ) &&
                            !it.startsWith(own)
                    }.map { "${file.rel}: import $it" }
            }
        assertNone(violations, "feature → feature imports")
    }

    @Test
    fun `core sources import no feature`() {
        val violations =
            coreFiles.flatMap { file ->
                file.imports
                    .map { it.name }
                    .filter { it.startsWith("eg.bahr.feature.") }
                    .map { "${file.rel}: import $it" }
            }
        assertNone(violations, "core → feature imports")
    }

    @Test
    fun `composeApp holds no screen, only the app root and the NavHost`() {
        val composables =
            appFiles.flatMap { file ->
                file
                    .functions(includeNested = true, includeLocal = true)
                    .filter { it.hasAnnotationWithName("Composable") }
                    .map { "${file.rel}: ${it.name}" }
            }
        // Proves the annotation lookup works; an empty list would pass the rule below vacuously.
        assertTrue(composables.any { it.endsWith(": AppNavHost") }, "found: $composables")
        assertNone(
            composables.filterNot { it.substringAfterLast(": ") in allowedAppComposables },
            "@Composable functions in :composeApp other than ${allowedAppComposables.sorted()} (screens belong in a feature)",
        )
    }

    @Test
    fun `feature sources hold no design literals`() {
        val violations =
            featureFiles.flatMap { file ->
                val code = file.text.withoutComments()
                designLiterals.flatMap { (pattern, why) ->
                    pattern.findAll(code).map { match -> "${file.rel}:${code.lineAt(match.range.first)}: `${match.value}` ($why)" }
                }
            }
        assertNone(violations, "Design literals in feature/* (use BahrTheme / MaterialTheme / BahrSpacing / BahrMotion)")
    }

    @Test
    fun `feature sources follow the model data presentation navigation di layout`() {
        val violations =
            featureFiles
                .filter { it.isMain }
                .filterNot { file ->
                    val featurePackage = "eg.bahr.feature.${file.module.substringAfterLast(':')}"
                    featureLayers.any { layer ->
                        file.packageName.let {
                            it == "$featurePackage.$layer" ||
                                it.startsWith("$featurePackage.$layer.")
                        }
                    }
                }.map { "${it.rel}: package ${it.packageName}" }
        assertNone(violations, "Feature code outside eg.bahr.feature.<x>.{${featureLayers.joinToString()}}")
    }

    @Test
    fun `feature declarations are internal outside navigation and di`() {
        val publicSurface = Regex("""/(navigation|di)/""")
        val violations =
            featureFiles
                .filter { it.isMain && !publicSurface.containsMatchIn(it.rel) }
                .flatMap { file -> file.publicTopLevelNames().map { "${file.rel}: $it" } }
        assertNone(violations, "Public declarations in a feature outside navigation/ and di/ (make them internal)")
    }

    private companion object {
        const val APP = ":composeApp"
        const val ARCHITECTURE_TESTS = ":architecture-tests"
        const val CORE_TESTING = ":core:testing"

        /** The skill's mobile graph for `core:*`. Features and the app follow the rules in [allowedProductionTargets]. */
        val allowedProduction: Map<String, Set<String>> =
            mapOf(
                ":core:common" to emptySet(),
                ":core:network" to setOf(":core:common"),
                ":core:datastore" to setOf(":core:common"),
                ":core:localization" to setOf(":core:common", ":core:datastore"),
                ":core:designsystem" to setOf(":core:localization"),
                // Test-only module: its main code is consumed by other modules' test source sets.
                CORE_TESTING to setOf(":core:common"),
            )

        val allowedAppComposables = setOf("App", "AppNavHost")

        /** The skill's "Inside a feature" layout. */
        val featureLayers = listOf("model", "data", "presentation", "navigation", "di")

        val designLiterals =
            listOf(
                Regex("""\bColor\(\s*0x""") to "raw colour",
                // Any receiver, not just a number: `GAP.dp` with a feature-local constant is still a
                // raw size (the M0-M2 grep was `\.dp\b`). Tokens are values, never `.dp`/`.sp` calls.
                Regex("""\.(dp|sp)\b""") to "raw size",
                Regex("""\bDp\(""") to "raw size",
                Regex("""\bTextUnit\(""") to "raw size",
                // Named colours (`Color.White`); `Unspecified` / `Transparent` mean "no colour", not a design choice.
                Regex("""\bColor\.(?!Unspecified\b|Transparent\b)[A-Z]\w*""") to "named colour",
                Regex("""\bFontFamily\(""") to "font family",
            )

        val rootDir = File(System.getProperty("bahr.rootDir") ?: error("Run through Gradle: bahr.rootDir is not set"))

        val graph: ModuleGraph by lazy {
            ModuleGraph.read(File(System.getProperty("bahr.moduleGraph") ?: error("Run through Gradle: bahr.moduleGraph is not set")))
        }

        val sourceFiles: List<SourceFile> by lazy {
            Konsist
                .scopeFromProject()
                .files
                .map { SourceFile(it, File(it.path).relativeTo(rootDir).invariantSeparatorsPath) }
                .filterNot { "/build/" in it.rel }
        }
        val featureFiles by lazy { sourceFiles.filter { it.rel.startsWith("feature/") }.nonEmpty("feature") }
        val coreFiles by lazy { sourceFiles.filter { it.rel.startsWith("core/") }.nonEmpty("core") }
        val appFiles by lazy { sourceFiles.filter { it.rel.startsWith("composeApp/") }.nonEmpty("composeApp") }

        val String.isFeature get() = startsWith(":feature:")
        val String.isCore get() = startsWith(":core:")

        fun allowedProductionTargets(from: String): Set<String> {
            val core = allowedProduction.keys
            return when {
                from.isCore -> allowedProduction[from].orEmpty()
                from.isFeature -> core - CORE_TESTING
                from == APP -> (core - CORE_TESTING) + graph.modules.filter { it.isFeature }
                else -> emptySet()
            }
        }

        /**
         * Skill "(test-only)" line: a core module's tests may use the core modules upstream of it
         * (its production closure) plus core:testing; never a feature. Features and the app may use
         * any core module in tests.
         */
        fun allowedTestTargets(from: String): Set<String> =
            when {
                from.isCore -> upstreamOf(from) + CORE_TESTING - from
                from.isFeature -> allowedProduction.keys
                from == APP -> allowedProduction.keys + graph.modules.filter { it.isFeature }
                else -> emptySet()
            }

        fun upstreamOf(module: String): Set<String> {
            val seen = mutableSetOf<String>()
            val stack = ArrayDeque(allowedProduction[module].orEmpty())
            while (stack.isNotEmpty()) {
                val next = stack.removeLast()
                if (seen.add(next)) stack += allowedProduction[next].orEmpty()
            }
            return seen
        }

        fun assertNone(
            violations: Collection<Any>,
            rule: String,
        ) {
            if (violations.isNotEmpty()) fail("$rule:\n" + violations.joinToString("\n") { "  - $it" })
        }

        /** An empty scope passes every rule; fail loudly instead. */
        fun <T> List<T>.nonEmpty(what: String): List<T> = also { check(it.isNotEmpty()) { "No $what sources found under $rootDir" } }

        /** Line and block comments out (KDoc may name the very literals it forbids); offsets kept. */
        fun String.withoutComments(): String =
            replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)) { it.value.replace(Regex("[^\n]"), " ") }
                .replace(Regex("""//[^\n]*""")) { " ".repeat(it.value.length) }

        fun String.lineAt(offset: Int): Int = take(offset).count { it == '\n' } + 1
    }
}

private class SourceFile(
    private val declaration: KoFileDeclaration,
    val rel: String,
) {
    val imports get() = declaration.imports
    val text get() = declaration.text
    val packageName: String get() = declaration.packagee?.name.orEmpty()

    fun functions(
        includeNested: Boolean,
        includeLocal: Boolean,
    ) = declaration.functions(includeNested = includeNested, includeLocal = includeLocal)

    /** Production code: `src/commonMain`, `src/androidMain`, `src/iosMain`, … (not test source sets). */
    val isMain: Boolean get() = Regex("""/src/\w+Main/""").containsMatchIn(rel)

    /** Top-level classes, interfaces, objects, functions and properties with no `internal`/`private`. */
    fun publicTopLevelNames(): List<String> {
        val d = declaration
        val classes = d.classes(includeNested = false).filterNot { it.hasInternalModifier || it.hasPrivateModifier }.map { it.name }
        val interfaces = d.interfaces(includeNested = false).filterNot { it.hasInternalModifier || it.hasPrivateModifier }.map { it.name }
        val objects = d.objects(includeNested = false).filterNot { it.hasInternalModifier || it.hasPrivateModifier }.map { it.name }
        val functions =
            d
                .functions(includeNested = false, includeLocal = false)
                .filterNot {
                    it.hasInternalModifier || it.hasPrivateModifier
                }.map { it.name }
        val properties = d.properties(includeNested = false).filterNot { it.hasInternalModifier || it.hasPrivateModifier }.map { it.name }
        return classes + interfaces + objects + functions + properties
    }

    /** `feature/trips/src/...` → `:feature:trips`. */
    val module: String get() = ":" + rel.split('/').take(2).joinToString(":")
}

private data class Edge(
    val from: String,
    val to: String,
    val configuration: String,
) {
    /** `commonTestImplementation`, `androidUnitTestImplementation`, `iosTestImplementation`, `testImplementation`, … */
    val isTestOnly: Boolean get() = configuration.startsWith("test") || "Test" in configuration

    override fun toString() = "$from → $to ($configuration)"
}

private class ModuleGraph(
    val modules: Set<String>,
    val edges: List<Edge>,
) {
    companion object {
        fun read(file: File): ModuleGraph {
            val lines = file.readLines().map { it.split(' ') }
            return ModuleGraph(
                modules = lines.filter { it.first() == "module" }.map { it[1] }.toSet(),
                edges = lines.filter { it.first() == "edge" }.map { Edge(from = it[1], to = it[2], configuration = it[3]) },
            )
        }
    }
}
