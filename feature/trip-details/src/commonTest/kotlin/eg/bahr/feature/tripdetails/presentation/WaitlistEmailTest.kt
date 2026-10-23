package eg.bahr.feature.tripdetails.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The client's copy of the server's email rule, run on both targets (Kotlin/Native has its own regex
 * engine). The cases are bahr-be `WaitlistEntryTest`'s, so a change on either side shows up here.
 */
class WaitlistEmailTest {
    @Test
    fun `accepts what the server accepts`() {
        listOf(
            "sara@example.com",
            "sara.ali+trips@mail.example.eg",
            "o'brien@example.co.uk",
            "x@a-b.io",
            " Sara.Ali@Example.COM ",
            "sara@${"a".repeat(MAX_LABEL)}.com",
        ).forEach { assertTrue(WaitlistEmail.isValid(it), "[$it] is valid") }
    }

    @Test
    fun `refuses what the server refuses`() {
        listOf(
            "",
            "   ",
            "sara",
            "sara@",
            "@example.com",
            "sara@example",
            "sara@localhost",
            "sara@@example.com",
            "sa ra@example.com",
            "sara@exa mple.com",
            "sara@-example.com",
            "sara@example-.com",
            "sara@example.com.",
            "sara@.example.com",
            "سارة@example.com",
            "sara@مثال.com",
            "sara@${"a".repeat(MAX_LABEL + 1)}.com",
        ).forEach { assertFalse(WaitlistEmail.isValid(it), "[$it] is not valid") }
    }

    @Test
    fun `takes 254 characters and refuses 255 - counted after trimming`() {
        val domain = "@example.com"
        val longest = "a".repeat(WaitlistEmail.MAX_LENGTH - domain.length) + domain

        assertTrue(WaitlistEmail.isValid(longest))
        assertTrue(WaitlistEmail.isValid("  $longest  "), "the spaces around it are not counted")
        assertFalse(WaitlistEmail.isValid("a$longest"))
    }

    @Test
    fun `what is sent is the address without the spaces around it - its case kept`() {
        assertEquals("Sara.Ali@Example.COM", WaitlistEmail.normalised(" Sara.Ali@Example.COM\n"))
    }

    private companion object {
        /** The longest DNS label the pattern takes. */
        const val MAX_LABEL = 63
    }
}
