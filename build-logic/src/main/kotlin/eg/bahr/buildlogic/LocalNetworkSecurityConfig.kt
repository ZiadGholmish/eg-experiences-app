package eg.bahr.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * The localDebug network security config (MV-M10). The checked-in template lists the hosts every developer shares
 * (10.0.2.2, localhost, 127.0.0.1); a physical phone also needs the Mac's LAN address, because the local api hands
 * out photo URLs on it (ASSET_BASE_URL, so a physical iPhone can load them). That address is personal and DHCP, so it
 * comes from the gitignored `local.properties` (`LOCAL_LAN_HOST`) and is added here at build time, never in git.
 *
 * Cleartext domains match on the host only, so one entry covers the api (8084) and MinIO (9000) on that host.
 */
object LocalNetworkSecurityConfig {
    /** Where the template's shared domains end; the LAN host goes just before it. */
    private const val DOMAIN_CONFIG_END = "</domain-config>"

    /** The indentation of the template's `<domain>` lines, so the added one lines up. */
    private const val DOMAIN_INDENT = "        "

    /**
     * An IPv4 address or a DNS hostname: letters, digits, dots and hyphens. Nothing else, so a pasted URL
     * (`http://…:9000`) fails with a clear message and nothing can be injected into the XML.
     */
    private val HOST = Regex("""[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?""")

    /**
     * [template] unchanged when [lanHost] is blank (so a build without `LOCAL_LAN_HOST` is identical to one before
     * MV-M10); otherwise [template] with one more cleartext `<domain>` for [lanHost].
     */
    fun render(
        template: String,
        lanHost: String,
    ): String {
        val host = lanHost.trim()
        if (host.isEmpty()) return template
        if (!HOST.matches(host)) {
            throw GradleException(
                "LOCAL_LAN_HOST must be a bare IP address or hostname (e.g. 192.168.1.20), not \"$host\": " +
                    "no scheme, port or path.",
            )
        }
        val end = template.lastIndexOf(DOMAIN_CONFIG_END)
        if (end < 0) throw GradleException("The network security config template has no $DOMAIN_CONFIG_END.")
        val lineStart = template.lastIndexOf('\n', end) + 1
        val domain = "$DOMAIN_INDENT<domain includeSubdomains=\"false\">$host</domain>\n"
        return template.substring(0, lineStart) + domain + template.substring(lineStart)
    }
}

/**
 * Writes `xml/network_security_config.xml` from [template] plus [lanHost] into [outputDir], a generated res directory
 * of the localDebug variant (wired in `:androidApp`).
 */
@CacheableTask
abstract class GenerateLocalNetworkSecurityConfigTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val template: RegularFileProperty

    /** Empty when `LOCAL_LAN_HOST` is unset. */
    @get:Input
    abstract val lanHost: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val xml = out.resolve("xml").apply { mkdirs() }
        xml
            .resolve("network_security_config.xml")
            .writeText(LocalNetworkSecurityConfig.render(template.get().asFile.readText(), lanHost.get()))
    }
}
