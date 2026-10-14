package eg.bahr.core.common.phone

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhoneNumbersTest {
    @Test
    fun `cleaning keeps digits and a leading plus`() {
        assertEquals("01012345678", PhoneNumbers.clean("010 1234-5678"))
        assertEquals("+201012345678", PhoneNumbers.clean(" +20 (10) 1234 5678"))
        // A plus anywhere but first is dropped with the other punctuation.
        assertEquals("2010", PhoneNumbers.clean("20+10"))
    }

    @Test
    fun `cleaning cuts at the longest valid length`() {
        assertEquals(PhoneNumbers.MAX_LENGTH, PhoneNumbers.clean("+" + "1".repeat(30)).length)
    }

    @Test
    fun `non-Western digits are kept but are not valid`() {
        // As before the move: Arabic-Indic digits survive cleaning, and the contract's [0-9] refuses them.
        assertFalse(PhoneNumbers.isValid(PhoneNumbers.clean("٠١٠١٢٣٤٥٦٧٨")))
    }

    @Test
    fun `validity is the contract pattern`() {
        assertTrue(PhoneNumbers.isValid("+201001234567"))
        assertTrue(PhoneNumbers.isValid("1234567"))
        assertFalse(PhoneNumbers.isValid("123456"))
        assertFalse(PhoneNumbers.isValid("+" + "1".repeat(16)))
        assertFalse(PhoneNumbers.isValid(""))
    }
}
