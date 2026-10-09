package eg.bahr.feature.booking.data

import eg.bahr.core.common.result.AppError
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.error_hold_expired
import eg.bahr.core.localization.generated.resources.error_no_seats_available
import eg.bahr.core.localization.messageRes
import eg.bahr.core.network.ApiErrorCodes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `core:localization` matches error codes as literals, because it may not depend on
 * `core:network`, which owns [ApiErrorCodes]. This module sees both, and booking is where
 * these two codes come from, so the literals are pinned to the constants here. Every literal
 * `messageRes` matches needs a line in this test.
 */
class ErrorCodeMessagesTest {
    private fun api(code: String) = AppError.Api(code = code, message = null, httpStatus = 409)

    @Test
    fun `the codes booking handles have their own copy`() {
        assertEquals(Res.string.error_hold_expired, api(ApiErrorCodes.HOLD_EXPIRED).messageRes())
        assertEquals(Res.string.error_no_seats_available, api(ApiErrorCodes.NO_SEATS_AVAILABLE).messageRes())
    }
}
