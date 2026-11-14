package eg.bahr.feature.hostworkspace.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import eg.bahr.core.designsystem.components.BahrBottomSheet
import eg.bahr.core.designsystem.components.BahrBottomSheetContent
import eg.bahr.core.designsystem.components.BahrFormField
import eg.bahr.core.designsystem.components.BahrStepper
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrBorder
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.host_broadcast_confirm
import eg.bahr.core.localization.generated.resources.host_broadcast_confirm_title
import eg.bahr.core.localization.generated.resources.host_broadcast_counter
import eg.bahr.core.localization.generated.resources.host_broadcast_how
import eg.bahr.core.localization.generated.resources.host_broadcast_message
import eg.bahr.core.localization.generated.resources.host_broadcast_no_recipients
import eg.bahr.core.localization.generated.resources.host_broadcast_not_open
import eg.bahr.core.localization.generated.resources.host_broadcast_once
import eg.bahr.core.localization.generated.resources.host_broadcast_rejected
import eg.bahr.core.localization.generated.resources.host_broadcast_required
import eg.bahr.core.localization.generated.resources.host_broadcast_send
import eg.bahr.core.localization.generated.resources.host_broadcast_title
import eg.bahr.core.localization.generated.resources.host_broadcast_too_long
import eg.bahr.core.localization.generated.resources.host_broadcast_too_soon
import eg.bahr.core.localization.generated.resources.host_broadcast_too_soon_unknown
import eg.bahr.core.localization.generated.resources.host_cancel
import eg.bahr.core.localization.generated.resources.host_edit
import eg.bahr.core.localization.generated.resources.host_manual_add
import eg.bahr.core.localization.generated.resources.host_manual_check_list
import eg.bahr.core.localization.generated.resources.host_manual_confirm_title
import eg.bahr.core.localization.generated.resources.host_manual_fewer
import eg.bahr.core.localization.generated.resources.host_manual_intro
import eg.bahr.core.localization.generated.resources.host_manual_key_reused
import eg.bahr.core.localization.generated.resources.host_manual_locale
import eg.bahr.core.localization.generated.resources.host_manual_more
import eg.bahr.core.localization.generated.resources.host_manual_name
import eg.bahr.core.localization.generated.resources.host_manual_name_missing
import eg.bahr.core.localization.generated.resources.host_manual_name_too_long
import eg.bahr.core.localization.generated.resources.host_manual_no_seats
import eg.bahr.core.localization.generated.resources.host_manual_not_open
import eg.bahr.core.localization.generated.resources.host_manual_paid
import eg.bahr.core.localization.generated.resources.host_manual_paid_hint
import eg.bahr.core.localization.generated.resources.host_manual_party
import eg.bahr.core.localization.generated.resources.host_manual_phone
import eg.bahr.core.localization.generated.resources.host_manual_phone_hint
import eg.bahr.core.localization.generated.resources.host_manual_phone_invalid
import eg.bahr.core.localization.generated.resources.host_manual_rejected
import eg.bahr.core.localization.generated.resources.host_manual_retry_safe
import eg.bahr.core.localization.generated.resources.host_manual_review
import eg.bahr.core.localization.generated.resources.host_manual_title
import eg.bahr.core.localization.generated.resources.host_manual_what
import eg.bahr.core.localization.generated.resources.host_mark_paid
import eg.bahr.core.localization.generated.resources.host_mark_paid_body
import eg.bahr.core.localization.generated.resources.host_mark_paid_title
import eg.bahr.core.localization.generated.resources.host_paid
import eg.bahr.core.localization.generated.resources.host_seats_count
import eg.bahr.core.localization.generated.resources.host_unpaid
import eg.bahr.core.localization.generated.resources.settings_language_arabic
import eg.bahr.core.localization.generated.resources.settings_language_english
import eg.bahr.core.localization.localizedMessage
import eg.bahr.feature.hostworkspace.presentation.components.cairoTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Host mode's sheets (M5-M1; bottom sheets, not dialogs, user decision 2026-10-14). Each is drawn in a real sheet on
 * screen ([inSheet]) or as the sheet's inside for the screenshot tests, which cannot draw a second window.
 */
@Composable
private fun HostSheet(
    inSheet: Boolean,
    props: BahrBottomSheet.Props,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (inSheet) BahrBottomSheet(props = props, content = content) else BahrBottomSheetContent(props, null, content)
}

internal class BroadcastActions(
    val onMessage: (String) -> Unit = {},
    val onReview: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onSend: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/**
 * The broadcast (M5-B2), in two steps so a message never goes by accident: write it, then read it back with who gets
 * it and send. The date's bookings count is [bookings]; the server reaches its CONFIRMED travellers, one per phone.
 */
@Composable
internal fun BroadcastSheet(
    form: BroadcastForm,
    dayLabel: String,
    bookings: Int,
    actions: BroadcastActions,
    inSheet: Boolean = true,
) {
    val confirming = form.step == BroadcastStep.Confirm
    val props =
        BahrBottomSheet.Props(
            title = stringResource(if (confirming) Res.string.host_broadcast_confirm_title else Res.string.host_broadcast_title),
            onDismiss = actions.onDismiss,
            actions =
                listOf(
                    if (confirming) {
                        BahrBottomSheet.Action(
                            stringResource(Res.string.host_broadcast_send),
                            onClick = actions.onSend,
                            loading = form.sending,
                            // Pressing again cannot help until the time comes or the date changes: the server would refuse.
                            enabled = form.problem.allowsResend(),
                            testTag = BROADCAST_SEND_TAG,
                        )
                    } else {
                        BahrBottomSheet.Action(
                            stringResource(Res.string.host_manual_review),
                            onClick = actions.onReview,
                            testTag = BROADCAST_REVIEW_TAG,
                        )
                    },
                ),
            cancel =
                if (confirming) {
                    BahrBottomSheet.Action(stringResource(Res.string.host_edit), onClick = actions.onEdit, enabled = !form.sending)
                } else {
                    BahrBottomSheet.Action(stringResource(Res.string.host_cancel), onClick = actions.onDismiss)
                },
            dismissible = !form.sending,
        )
    HostSheet(inSheet, props) {
        if (confirming) {
            Text(
                text = form.message.trim(),
                // The host's own words, in whichever language they wrote: their direction, not the app's.
                style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(
                            BahrTheme.shapes.large,
                        ).background(BahrTheme.colors.surfaceLow)
                        .padding(BahrSpacing.md),
            )
            SheetLine(stringResource(Res.string.host_broadcast_confirm, dayLabel, bookings))
            SheetLine(stringResource(Res.string.host_broadcast_once))
        } else {
            SheetLine(stringResource(Res.string.host_broadcast_how))
            MessageField(form, actions.onMessage)
        }
        form.problem?.let { SheetProblem(broadcastProblemText(it)) }
    }
}

/** A multi-line message box with its counter, drawn like the review page's text box. */
@Composable
private fun MessageField(
    form: BroadcastForm,
    onMessage: (String) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val length = form.message.trim().length
    val tooLong = length > HostDepartureViewModel.BROADCAST_MAX
    val label = stringResource(Res.string.host_broadcast_message)
    val shape = BahrTheme.shapes.medium
    Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = BahrTheme.colors.onSurfaceSecondary)
        BasicTextField(
            value = form.message,
            onValueChange = onMessage,
            enabled = !form.sending,
            minLines = MESSAGE_MIN_LINES,
            maxLines = MESSAGE_MAX_LINES,
            cursorBrush = SolidColor(c.primary),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface, textDirection = TextDirection.Content),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = label
                        if (tooLong) error(label)
                    }.testTag(BROADCAST_MESSAGE_TAG),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .clip(shape)
                        .background(c.surface)
                        .border(if (tooLong) BahrBorder.selected else BahrBorder.hairline, if (tooLong) c.error else c.outline, shape)
                        .padding(BahrSpacing.lg),
                ) { inner() }
            },
        )
        Text(
            text = stringResource(Res.string.host_broadcast_counter, length, HostDepartureViewModel.BROADCAST_MAX),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = if (tooLong) c.onErrorContainer else c.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun BroadcastProblem?.allowsResend(): Boolean =
    this !is BroadcastProblem.TooSoon && this != BroadcastProblem.NotOpen && this != BroadcastProblem.NoRecipients

@Composable
private fun broadcastProblemText(problem: BroadcastProblem): String =
    when (problem) {
        BroadcastProblem.Required -> stringResource(Res.string.host_broadcast_required)
        BroadcastProblem.TooLong -> stringResource(Res.string.host_broadcast_too_long, HostDepartureViewModel.BROADCAST_MAX)
        is BroadcastProblem.TooSoon ->
            problem.at?.let { stringResource(Res.string.host_broadcast_too_soon, BahrFormat.ltr(cairoTime(it))) }
                ?: stringResource(Res.string.host_broadcast_too_soon_unknown)
        BroadcastProblem.NoRecipients -> stringResource(Res.string.host_broadcast_no_recipients)
        BroadcastProblem.NotOpen -> stringResource(Res.string.host_broadcast_not_open)
        BroadcastProblem.Rejected -> stringResource(Res.string.host_broadcast_rejected, HostDepartureViewModel.BROADCAST_MAX)
        is BroadcastProblem.Other -> problem.error.localizedMessage()
    }

internal class ManualActions(
    val onName: (String) -> Unit = {},
    val onPhone: (String) -> Unit = {},
    val onParty: (Int) -> Unit = {},
    val onPaid: (Boolean) -> Unit = {},
    val onLocale: (String) -> Unit = {},
    val onReview: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/**
 * A manual booking (M5-B3): a booking the host took outside the app. Filled in, read back, then added; one
 * `Idempotency-Key` per opening (the view model's). After IDEMPOTENCY_KEY_REUSED only "Check the list" is offered.
 */
@Composable
internal fun ManualBookingSheet(
    form: ManualForm,
    dayLabel: String,
    actions: ManualActions,
    inSheet: Boolean = true,
) {
    val reviewing = form.step == ManualStep.Review
    val primary =
        when {
            !form.canAdd ->
                BahrBottomSheet.Action(
                    stringResource(Res.string.host_manual_check_list),
                    onClick = actions.onDismiss,
                    testTag = MANUAL_CHECK_LIST_TAG,
                )
            reviewing ->
                BahrBottomSheet.Action(
                    stringResource(Res.string.host_manual_add),
                    onClick = actions.onAdd,
                    loading = form.sending,
                    testTag = MANUAL_ADD_TAG,
                )
            else ->
                BahrBottomSheet.Action(
                    stringResource(Res.string.host_manual_review),
                    onClick = actions.onReview,
                    testTag = MANUAL_REVIEW_TAG,
                )
        }
    val cancel =
        when {
            !form.canAdd -> null
            reviewing -> BahrBottomSheet.Action(stringResource(Res.string.host_edit), onClick = actions.onEdit, enabled = !form.sending)
            else -> BahrBottomSheet.Action(stringResource(Res.string.host_cancel), onClick = actions.onDismiss)
        }
    val props =
        BahrBottomSheet.Props(
            title = stringResource(if (reviewing) Res.string.host_manual_confirm_title else Res.string.host_manual_title),
            onDismiss = actions.onDismiss,
            actions = listOf(primary),
            cancel = cancel,
            dismissible = !form.sending,
        )
    HostSheet(inSheet, props) {
        if (reviewing) ManualReview(form) else ManualFields(form, dayLabel, actions)
        form.problem?.let { SheetProblem(manualProblemText(it)) }
    }
}

@Composable
private fun ManualFields(
    form: ManualForm,
    dayLabel: String,
    actions: ManualActions,
) {
    SheetLine(stringResource(Res.string.host_manual_intro, dayLabel))
    BahrFormField(
        props =
            BahrFormField.Props(
                label = stringResource(Res.string.host_manual_name),
                value = form.name,
                onValueChange = actions.onName,
                enabled = !form.sending,
                error =
                    when {
                        !form.checked -> null
                        form.nameMissing -> stringResource(Res.string.host_manual_name_missing)
                        form.nameTooLong -> stringResource(Res.string.host_manual_name_too_long, ManualForm.NAME_MAX)
                        else -> null
                    },
                keyboard = BahrFormField.Keyboard.Name,
                testTag = MANUAL_NAME_TAG,
            ),
    )
    BahrFormField(
        props =
            BahrFormField.Props(
                label = stringResource(Res.string.host_manual_phone),
                value = form.phone,
                onValueChange = actions.onPhone,
                enabled = !form.sending,
                leadingIcon = BahrIcons.Smartphone,
                support = stringResource(Res.string.host_manual_phone_hint),
                supportIsHelp = true,
                error = if (form.checked && form.phoneInvalid) stringResource(Res.string.host_manual_phone_invalid) else null,
                leftToRight = true,
                keyboard = BahrFormField.Keyboard.Phone,
                imeAction = ImeAction.Done,
                testTag = MANUAL_PHONE_TAG,
            ),
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
        Text(
            text = stringResource(Res.string.host_manual_party),
            style = MaterialTheme.typography.labelLarge,
            color = BahrTheme.colors.onSurfaceSecondary,
            modifier = Modifier.weight(1f),
        )
        BahrStepper(
            props =
                BahrStepper.Props(
                    value = form.partySize,
                    canDecrease = form.partySize > 1 && !form.sending,
                    canIncrease = form.partySize < ManualForm.PARTY_MAX && !form.sending,
                    decreaseLabel = stringResource(Res.string.host_manual_fewer),
                    increaseLabel = stringResource(Res.string.host_manual_more),
                    valueDescription = pluralStringResource(Res.plurals.host_seats_count, form.partySize, form.partySize),
                    onDecrease = { actions.onParty(form.partySize - 1) },
                    onIncrease = { actions.onParty(form.partySize + 1) },
                ),
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = BahrSize.touchTarget)
                .toggleable(value = form.paid, enabled = !form.sending, role = Role.Switch, onValueChange = actions.onPaid)
                .testTag(MANUAL_PAID_TAG),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
            Text(
                stringResource(Res.string.host_manual_paid),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(Res.string.host_manual_paid_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = form.paid, onCheckedChange = null, enabled = !form.sending)
    }
    Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
        Text(
            stringResource(Res.string.host_manual_locale),
            style = MaterialTheme.typography.labelLarge,
            color = BahrTheme.colors.onSurfaceSecondary,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            GUEST_LOCALES.forEachIndexed { index, (tag, name) ->
                SegmentedButton(
                    selected = form.locale == tag,
                    onClick = { actions.onLocale(tag) },
                    enabled = !form.sending,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = GUEST_LOCALES.size),
                    colors =
                        SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            inactiveContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    modifier = Modifier.heightIn(min = BahrSize.touchTarget),
                ) { Text(stringResource(name), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
private fun ManualReview(form: ManualForm) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.large)
                .background(BahrTheme.colors.surfaceLow)
                .padding(BahrSpacing.md),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        ReviewFact(stringResource(Res.string.host_manual_name), form.name.trim())
        ReviewFact(stringResource(Res.string.host_manual_phone), BahrFormat.ltr(form.normalizedPhone))
        ReviewFact(
            stringResource(Res.string.host_manual_party),
            pluralStringResource(Res.plurals.host_seats_count, form.partySize, form.partySize),
        )
        ReviewFact(
            stringResource(Res.string.host_manual_paid),
            stringResource(if (form.paid) Res.string.host_paid else Res.string.host_unpaid),
        )
        val language = GUEST_LOCALES.firstOrNull { it.first == form.locale }?.second
        language?.let { ReviewFact(stringResource(Res.string.host_manual_locale), stringResource(it)) }
    }
    SheetLine(stringResource(Res.string.host_manual_what))
}

@Composable
private fun ReviewFact(
    label: String,
    value: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun manualProblemText(problem: ManualProblem): String =
    when (problem) {
        ManualProblem.NoSeats -> stringResource(Res.string.host_manual_no_seats)
        ManualProblem.NotOpen -> stringResource(Res.string.host_manual_not_open)
        ManualProblem.Rejected -> stringResource(Res.string.host_manual_rejected)
        ManualProblem.RetrySafe -> stringResource(Res.string.host_manual_retry_safe)
        ManualProblem.KeyReused -> stringResource(Res.string.host_manual_key_reused)
        is ManualProblem.Other -> problem.error.localizedMessage()
    }

internal class MarkPaidActions(
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/** Recording that a manual booking's guest has now paid the host (`markManualBookingPaid`): Bahr's commission is then due. */
@Composable
internal fun MarkPaidSheet(
    form: MarkPaidForm,
    actions: MarkPaidActions,
    inSheet: Boolean = true,
) {
    val props =
        BahrBottomSheet.Props(
            title = stringResource(Res.string.host_mark_paid_title),
            onDismiss = actions.onDismiss,
            actions =
                listOf(
                    BahrBottomSheet.Action(
                        stringResource(Res.string.host_mark_paid),
                        onClick = actions.onConfirm,
                        loading = form.sending,
                        testTag = MARK_PAID_CONFIRM_TAG,
                    ),
                ),
            cancel = BahrBottomSheet.Action(stringResource(Res.string.host_cancel), onClick = actions.onDismiss, enabled = !form.sending),
            dismissible = !form.sending,
        )
    HostSheet(inSheet, props) {
        SheetLine(stringResource(Res.string.host_mark_paid_body, form.name, BahrFormat.ltr(form.ref)))
        form.error?.let { SheetProblem(it.localizedMessage()) }
    }
}

@Composable
private fun SheetLine(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = BahrTheme.colors.onSurfaceSecondary)
}

@Composable
private fun SheetProblem(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.large)
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(BahrSpacing.md)
                .semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

/** The guest's language for messages about the booking: Arabic first, as everywhere. */
private val GUEST_LOCALES =
    listOf(
        "ar" to Res.string.settings_language_arabic,
        "en" to Res.string.settings_language_english,
    )

private const val MESSAGE_MIN_LINES = 4
private const val MESSAGE_MAX_LINES = 8

internal const val BROADCAST_MESSAGE_TAG = "host_broadcast_message"
internal const val BROADCAST_REVIEW_TAG = "host_broadcast_review"
internal const val BROADCAST_SEND_TAG = "host_broadcast_send"
internal const val MANUAL_NAME_TAG = "host_manual_name"
internal const val MANUAL_PHONE_TAG = "host_manual_phone"
internal const val MANUAL_PAID_TAG = "host_manual_paid"
internal const val MANUAL_REVIEW_TAG = "host_manual_review"
internal const val MANUAL_ADD_TAG = "host_manual_add"
internal const val MANUAL_CHECK_LIST_TAG = "host_manual_check_list"
internal const val MARK_PAID_CONFIRM_TAG = "host_mark_paid_confirm"
