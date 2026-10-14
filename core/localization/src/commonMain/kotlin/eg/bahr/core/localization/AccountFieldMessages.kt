package eg.bahr.core.localization

import androidx.compose.runtime.Composable
import eg.bahr.core.common.phone.MobileProblem
import eg.bahr.core.common.phone.PhoneNumbers
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.account_phone_count
import eg.bahr.core.localization.generated.resources.account_phone_empty
import eg.bahr.core.localization.generated.resources.account_phone_long
import eg.bahr.core.localization.generated.resources.account_phone_short
import eg.bahr.core.localization.generated.resources.account_phone_start
import org.jetbrains.compose.resources.stringResource

/**
 * What the account's mobile field says when the number is not an Egyptian mobile (M3-M0b, handoff §2),
 * for both forms that take it (feature:auth's first-sign-in sheet, feature:settings' Edit personal
 * info), so they say it the same way.
 */
@Composable
fun MobileProblem.localizedMessage(): String =
    when (this) {
        MobileProblem.Empty -> stringResource(Res.string.account_phone_empty)
        is MobileProblem.TooShort -> stringResource(Res.string.account_phone_short, digits)
        is MobileProblem.TooLong -> stringResource(Res.string.account_phone_long, digits)
        MobileProblem.WrongStart -> stringResource(Res.string.account_phone_start)
    }

/** The mobile field's counter, "10/11": the digits typed, once normalised, of the number's length. */
@Composable
fun mobileDigitsCounter(digits: Int): String = stringResource(Res.string.account_phone_count, digits, PhoneNumbers.MOBILE_LENGTH)
