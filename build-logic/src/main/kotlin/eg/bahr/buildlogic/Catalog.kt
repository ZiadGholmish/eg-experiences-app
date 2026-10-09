package eg.bahr.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

/** The root `gradle/libs.versions.toml`: one place for versions, also for the convention plugins. */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { IllegalStateException("No library `$alias` in libs.versions.toml") }

internal fun VersionCatalog.version(alias: String): String =
    findVersion(alias).orElseThrow { IllegalStateException("No version `$alias` in libs.versions.toml") }.requiredVersion
