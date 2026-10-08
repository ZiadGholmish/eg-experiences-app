package eg.bahr.core.localization

import eg.bahr.core.common.result.ApiErrorCodes
import eg.bahr.core.common.result.AppError
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.error_generic
import eg.bahr.core.localization.generated.resources.error_hold_expired
import eg.bahr.core.localization.generated.resources.error_network
import eg.bahr.core.localization.generated.resources.error_no_seats_available
import eg.bahr.core.localization.generated.resources.error_timeout
import kotlin.test.Test
import kotlin.test.assertEquals

class AppErrorMessagesTest {
    private fun api(
        code: String,
        message: String? = "server text the user must never see",
    ) = AppError.Api(code = code, message = message, httpStatus = 409)

    @Test
    fun `transport failures have their own copy`() {
        assertEquals(Res.string.error_network, AppError.Network.messageRes())
        assertEquals(Res.string.error_timeout, AppError.Timeout.messageRes())
    }

    @Test
    fun `mapped codes branch on the code and ignore the message`() {
        assertEquals(Res.string.error_no_seats_available, api(ApiErrorCodes.NO_SEATS_AVAILABLE).messageRes())
        assertEquals(Res.string.error_hold_expired, api(ApiErrorCodes.HOLD_EXPIRED, message = null).messageRes())
    }

    @Test
    fun `an unmapped code and non-api failures fall back to the generic copy`() {
        assertEquals(Res.string.error_generic, api("SOMETHING_NEW").messageRes())
        assertEquals(Res.string.error_generic, AppError.Serialization("bad json").messageRes())
        assertEquals(Res.string.error_generic, AppError.Unknown(null).messageRes())
    }
}
