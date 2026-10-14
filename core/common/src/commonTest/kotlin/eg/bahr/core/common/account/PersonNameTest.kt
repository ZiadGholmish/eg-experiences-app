package eg.bahr.core.common.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonNameTest {
    @Test
    fun `a name is 2 to 60 characters once trimmed`() {
        assertTrue(PersonName.isValid("مي"))
        assertTrue(PersonName.isValid("a".repeat(PersonName.MAX_LENGTH)))
        assertFalse(PersonName.isValid(" م "))
        assertFalse(PersonName.isValid("   "))
        assertFalse(PersonName.isValid("a".repeat(PersonName.MAX_LENGTH + 1)))
        assertEquals("Menna Hassan", PersonName.clean("  Menna Hassan "))
    }
}
