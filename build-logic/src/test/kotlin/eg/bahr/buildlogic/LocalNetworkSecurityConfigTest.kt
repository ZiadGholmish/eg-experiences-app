package eg.bahr.buildlogic

import org.gradle.api.GradleException
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalNetworkSecurityConfigTest {
    // The real checked-in template, so the test also fails if someone edits it into a shape `render` cannot extend.
    // Gradle runs an included build's tests from build-logic/.
    private val template = File("../androidApp/src/localDebug/network_security_config.xml").readText()

    @Test
    fun `without a LAN host the template is copied unchanged`() {
        assertEquals(template, LocalNetworkSecurityConfig.render(template, ""))
        assertEquals(template, LocalNetworkSecurityConfig.render(template, "   "))
    }

    @Test
    fun `a LAN host becomes one more cleartext domain inside the domain config`() {
        val rendered = LocalNetworkSecurityConfig.render(template, " 192.168.1.20 ")

        val added = "        <domain includeSubdomains=\"false\">192.168.1.20</domain>\n"
        assertEquals(template.length + added.length, rendered.length)
        assertTrue(
            rendered.contains("$added    </domain-config>"),
            "The host goes last inside <domain-config>:\n$rendered",
        )
        // Nothing else changes: removing the added line gives the template back.
        assertEquals(template, rendered.replace(added, ""))
    }

    @Test
    fun `a hostname is accepted`() {
        val rendered = LocalNetworkSecurityConfig.render(template, "my-mac.local")
        assertTrue(rendered.contains(">my-mac.local</domain>"))
    }

    @Test
    fun `a URL, a port or markup is rejected`() {
        listOf("http://192.168.1.20", "192.168.1.20:9000", "192.168.1.20/", "a</domain><domain>b", "-host").forEach {
            assertFailsWith<GradleException>(it) { LocalNetworkSecurityConfig.render(template, it) }
        }
    }
}
