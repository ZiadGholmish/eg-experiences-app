package eg.bahr.core.common.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppAppearanceTest {
    @Test
    fun `light is the default - dark is opt-in`() {
        assertEquals(AppAppearance.LIGHT, AppAppearance.fromTag(null))
        assertEquals(AppAppearance.LIGHT, AppAppearance.fromTag("sepia"))
        AppAppearance.entries.forEach { assertEquals(it, AppAppearance.fromTag(it.tag)) }
    }

    @Test
    fun `only System follows the phone`() {
        assertFalse(AppAppearance.LIGHT.isDark(systemDark = true))
        assertTrue(AppAppearance.DARK.isDark(systemDark = false))
        assertTrue(AppAppearance.SYSTEM.isDark(systemDark = true))
        assertFalse(AppAppearance.SYSTEM.isDark(systemDark = false))
    }
}
