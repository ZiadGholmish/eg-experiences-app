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

    @Test
    fun `a mobile is normalised - Arabic-Indic digits - spaces and dashes - the country code`() {
        assertEquals("01012345678", PhoneNumbers.normalizeMobile("٠١٠١٢٣٤٥٦٧٨"))
        assertEquals("01012345678", PhoneNumbers.normalizeMobile("۰۱۰۱۲۳۴۵۶۷۸"))
        assertEquals("01012345678", PhoneNumbers.normalizeMobile(" 010 1234-5678 "))
        assertEquals("01012345678", PhoneNumbers.normalizeMobile("+20 101 234 5678"))
        assertEquals("01012345678", PhoneNumbers.normalizeMobile("0020 101 234 5678"))
        assertEquals("01012345678", PhoneNumbers.normalizeMobile("201012345678"))
        // The national 0 typed after the country code by habit is kept once, not doubled.
        assertEquals("01012345678", PhoneNumbers.normalizeMobile("+20 0101 234 5678"))
        // Mixed scripts in one number.
        assertEquals("01512345678", PhoneNumbers.normalizeMobile("015-١٢٣٤-5678"))
    }

    @Test
    fun `an Egyptian mobile is 11 digits starting 010 011 012 or 015`() {
        listOf("01012345678", "01112345678", "01212345678", "01512345678").forEach {
            assertTrue(PhoneNumbers.isEgyptianMobile(it), it)
        }
        listOf("01312345678", "02012345678", "0101234567", "010123456789", "+201012345678", "").forEach {
            assertFalse(PhoneNumbers.isEgyptianMobile(it), it)
        }
    }

    @Test
    fun `a mobile problem says what is wrong`() {
        assertEquals(null, PhoneNumbers.mobileProblem("01012345678"))
        assertEquals(MobileProblem.Empty, PhoneNumbers.mobileProblem(""))
        assertEquals(MobileProblem.TooShort(10), PhoneNumbers.mobileProblem("0101234567"))
        assertEquals(MobileProblem.TooLong(12), PhoneNumbers.mobileProblem("010123456789"))
        assertEquals(MobileProblem.WrongStart, PhoneNumbers.mobileProblem("01312345678"))
    }

    @Test
    fun `every Egyptian mobile also passes the contract pattern - one rule - the server stays the authority`() {
        listOf("01012345678", "01112345678", "01212345678", "01512345678").forEach {
            assertTrue(PhoneNumbers.isValid(it), it)
        }
    }
}
