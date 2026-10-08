package eg.bahr.core.network

import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Pins the wire shape of the contract's `Money` (openapi.yaml: `amount` int64 whole EGP). */
class MoneyDtoTest {
    @Test
    fun `the openapi example decodes`() {
        val money = HttpClientFactory.json.decodeFromString<MoneyDto>("""{"amount":450,"currency":"EGP"}""")

        assertEquals(MoneyDto(amount = 450, currencyCode = "EGP"), money)
    }

    @Test
    fun `a fractional amount is rejected rather than silently truncated`() {
        // The contract says whole pounds. A server sending 450.0 or 450.5 is a contract break, and
        // callApi surfaces it as AppError.Serialization instead of showing a rounded price.
        assertFailsWith<SerializationException> {
            HttpClientFactory.json.decodeFromString<MoneyDto>("""{"amount":450.0,"currency":"EGP"}""")
        }
    }
}
