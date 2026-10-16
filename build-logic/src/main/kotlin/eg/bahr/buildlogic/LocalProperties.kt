package eg.bahr.buildlogic

import org.gradle.api.Project
import java.util.Properties

/**
 * A developer setting from the gitignored root `local.properties`, else the environment (CI), else empty.
 * Trimmed. Used by `:androidApp` (Maps key, base URL, sign-in client id) and `:composeApp` (the iOS Maps key),
 * which used to share one build file. Callers report only whether a secret is set, never its value.
 */
fun Project.localSetting(name: String): String =
    localProperties()
        .getProperty(name)
        ?.trim()
        .orEmpty()
        .ifEmpty { System.getenv(name).orEmpty() }

/** A `local.properties` value with a default, for settings that are not secrets (the local base URL). */
fun Project.localSetting(
    name: String,
    default: String,
): String = localProperties().getProperty(name, default)

private fun Project.localProperties(): Properties =
    Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { stream -> load(stream) }
    }
