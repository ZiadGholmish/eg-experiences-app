package eg.bahr.feature.booking.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import eg.bahr.core.common.result.AppError
import eg.bahr.core.designsystem.components.BahrFormField
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrBorder
import eg.bahr.core.designsystem.theme.BahrElevation
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrShadow
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.account_name_invalid
import eg.bahr.core.localization.generated.resources.booking_guest_name
import eg.bahr.core.localization.generated.resources.booking_guest_phone
import eg.bahr.core.localization.generated.resources.booking_guest_phone_invalid
import eg.bahr.core.localization.generated.resources.booking_method_card
import eg.bahr.core.localization.generated.resources.booking_method_card_note
import eg.bahr.core.localization.generated.resources.booking_method_vf
import eg.bahr.core.localization.generated.resources.booking_method_vf_note
import eg.bahr.core.localization.generated.resources.booking_pay_too_late
import eg.bahr.core.localization.generated.resources.booking_pay_with
import eg.bahr.core.localization.generated.resources.booking_payer_card_name
import eg.bahr.core.localization.generated.resources.booking_payer_card_note
import eg.bahr.core.localization.generated.resources.booking_payer_vf_number
import eg.bahr.core.localization.generated.resources.error_payment_declined
import eg.bahr.core.localization.localizedMessage
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.feature.booking.presentation.PayerForm
import eg.bahr.feature.booking.presentation.PaymentMethod
import org.jetbrains.compose.resources.stringResource

/*
 * The checkout's parts (HANDOFF screen 5, M3-M1): "Pay with", the two method rows, and the fields that go with the
 * picked method. Feature-internal: the held-seats screen is their only caller.
 */

/**
 * "Pay with", then one row per method: the radio, a tinted icon disc and the method's name over its note. Picked:
 * `primaryContainer` ringed in `primary`, as a picked date row; otherwise `surface` at `elevation.level1`. The disc
 * tints are the prototype's: Vodafone Cash on `tertiaryContainer` in `error` (the brand's red), card on
 * `primaryContainer` in `primary`.
 */
@Composable
internal fun PaymentMethods(
    selected: PaymentMethod,
    enabled: Boolean,
    onSelect: (PaymentMethod) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
        Text(text = stringResource(Res.string.booking_pay_with), style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
            MethodRow(
                look =
                    MethodLook(
                        name = stringResource(Res.string.booking_method_vf),
                        note = stringResource(Res.string.booking_method_vf_note),
                        icon = BahrIcons.Smartphone,
                        disc = c.tertiaryContainer,
                        tint = c.error,
                    ),
                selected = selected == PaymentMethod.VodafoneCash,
                enabled = enabled,
                onSelect = { onSelect(PaymentMethod.VodafoneCash) },
                testTag = METHOD_VF_TAG,
            )
            MethodRow(
                look =
                    MethodLook(
                        name = stringResource(Res.string.booking_method_card),
                        note = stringResource(Res.string.booking_method_card_note),
                        icon = BahrIcons.CreditCard,
                        disc = c.primaryContainer,
                        tint = c.primary,
                    ),
                selected = selected == PaymentMethod.Card,
                enabled = enabled,
                onSelect = { onSelect(PaymentMethod.Card) },
                testTag = METHOD_CARD_TAG,
            )
        }
    }
}

private class MethodLook(
    val name: String,
    val note: String,
    val icon: BahrIcons,
    val disc: Color,
    val tint: Color,
)

@Composable
private fun MethodRow(
    look: MethodLook,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    testTag: String,
) {
    val c = MaterialTheme.colorScheme
    val x = BahrTheme.colors
    val shape = BahrTheme.shapes.extraLarge
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (!selected) Modifier.bahrShadow(BahrElevation.Level1, shape, x) else Modifier)
                .clip(shape)
                .background(if (selected) c.primaryContainer else c.surface)
                .then(if (selected) Modifier.border(BahrBorder.selected, c.primary, shape) else Modifier)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
                .testTag(testTag)
                .heightIn(min = BahrSpacing.minTouch)
                .padding(horizontal = BahrSpacing.lg, vertical = BahrSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        Radio(selected = selected, open = true)
        Box(
            modifier = Modifier.size(BahrSize.methodDisc).clip(BahrTheme.shapes.full).background(look.disc),
            contentAlignment = Alignment.Center,
        ) {
            // Decorative: the name beside it says which method this is.
            Icon(look.icon.filled(), contentDescription = null, tint = look.tint, modifier = Modifier.size(BahrSize.iconMedium))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
            Text(text = look.name, style = MaterialTheme.typography.titleSmall, color = c.onSurface)
            Text(text = look.note, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
        }
    }
}

/**
 * The fields of the picked method: Vodafone Cash asks the full name and the Vodafone Cash number; card asks the name
 * on the card and a mobile number (the payment needs one; the card itself is typed on the provider's page, said under
 * the fields). Numbers stay left to right. Their errors show once a pay tap found them wrong ([showErrors]).
 */
@Composable
internal fun PayerFields(
    method: PaymentMethod,
    payer: PayerForm,
    showErrors: Boolean,
    enabled: Boolean,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val card = method == PaymentMethod.Card
    Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
        BahrFormField(
            props =
                BahrFormField.Props(
                    label = stringResource(if (card) Res.string.booking_payer_card_name else Res.string.booking_guest_name),
                    value = payer.name,
                    onValueChange = onNameChange,
                    enabled = enabled,
                    error = if (showErrors && !payer.nameValid) stringResource(Res.string.account_name_invalid) else null,
                    keyboard = BahrFormField.Keyboard.Name,
                    testTag = PAYER_NAME_TAG,
                ),
        )
        BahrFormField(
            props =
                BahrFormField.Props(
                    label = stringResource(if (card) Res.string.booking_guest_phone else Res.string.booking_payer_vf_number),
                    value = payer.phone,
                    onValueChange = onPhoneChange,
                    enabled = enabled,
                    error = if (showErrors && !payer.phoneValid) stringResource(Res.string.booking_guest_phone_invalid) else null,
                    support = if (card) stringResource(Res.string.booking_payer_card_note) else null,
                    supportIsHelp = card,
                    leftToRight = true,
                    keyboard = BahrFormField.Keyboard.Phone,
                    imeAction = ImeAction.Done,
                    onImeAction = onDone,
                    testTag = PAYER_PHONE_TAG,
                ),
        )
    }
}

/**
 * Why the last try did not start or went through: the provider declined it (at its page, [declined], or when asked
 * to start), too little time was left, or any other failure in its localised words. Announced as it appears.
 */
@Composable
internal fun PaymentProblem(
    error: AppError?,
    declined: Boolean,
) {
    val text =
        when {
            error is AppError.Api && error.code == ApiErrorCodes.HOLD_EXPIRED -> stringResource(Res.string.booking_pay_too_late)
            error != null -> error.localizedMessage()
            declined -> stringResource(Res.string.error_payment_declined)
            else -> return
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(BahrSpacing.md)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(PAYMENT_PROBLEM_TAG),
    )
}

/** For tests. */
internal const val METHOD_VF_TAG = "checkout_method_vf"
internal const val METHOD_CARD_TAG = "checkout_method_card"
internal const val PAYER_NAME_TAG = "checkout_payer_name"
internal const val PAYER_PHONE_TAG = "checkout_payer_phone"
internal const val PAYMENT_PROBLEM_TAG = "checkout_payment_problem"
