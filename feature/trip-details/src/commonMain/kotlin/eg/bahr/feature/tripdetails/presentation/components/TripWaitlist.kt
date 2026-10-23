package eg.bahr.feature.tripdetails.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import eg.bahr.core.common.result.AppError
import eg.bahr.core.designsystem.components.BahrLottie
import eg.bahr.core.designsystem.components.BahrStepper
import eg.bahr.core.designsystem.components.BahrTextFields
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrBorder
import eg.bahr.core.designsystem.theme.BahrElevation
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrShadow
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.a11y_busy
import eg.bahr.core.localization.generated.resources.trip_waitlist_date_closed
import eg.bahr.core.localization.generated.resources.trip_waitlist_email
import eg.bahr.core.localization.generated.resources.trip_waitlist_email_invalid
import eg.bahr.core.localization.generated.resources.trip_waitlist_form_note
import eg.bahr.core.localization.generated.resources.trip_waitlist_full
import eg.bahr.core.localization.generated.resources.trip_waitlist_join
import eg.bahr.core.localization.generated.resources.trip_waitlist_joined_body
import eg.bahr.core.localization.generated.resources.trip_waitlist_joined_title
import eg.bahr.core.localization.generated.resources.trip_waitlist_party_count
import eg.bahr.core.localization.generated.resources.trip_waitlist_party_decrease
import eg.bahr.core.localization.generated.resources.trip_waitlist_party_increase
import eg.bahr.core.localization.generated.resources.trip_waitlist_party_size
import eg.bahr.core.localization.generated.resources.trip_waitlist_seats_opened
import eg.bahr.core.localization.generated.resources.trip_waitlist_submit
import eg.bahr.core.localization.localizedMessage
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.feature.tripdetails.presentation.TripDetailUiState
import eg.bahr.feature.tripdetails.presentation.WaitlistForm
import eg.bahr.feature.tripdetails.presentation.WaitlistOutcome
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/*
 * The waiting list under the sold-out notice (PLAN C10, D3: only a SOLD_OUT date has one). The
 * handoff draws just the "Join the waiting list" button; tapping it opens a short form in place
 * (email and party, the contract's `WaitlistRequest`), and a successful join replaces both with a
 * confirmation. Capture only: nothing here promises an automatic seat offer.
 */

/** What the waiting-list part of the notice shows and does. The callbacks are view-model methods. */
@Immutable
internal class WaitlistPanelState(
    /** The open form, or null while only the button shows. */
    val form: WaitlistForm?,
    /**
     * The email the date was joined under, or null when it was not joined. Remembered on the device
     * (M4-M5), so a join from an earlier visit or before a restart counts too.
     */
    val joinedEmail: String?,
    val maxPartySize: Int,
    val onOpen: () -> Unit = {},
    val onEmailChange: (String) -> Unit = {},
    val onDecrease: () -> Unit = {},
    val onIncrease: () -> Unit = {},
    val onJoin: () -> Unit = {},
)

/** The button, the form, or the confirmation, in that order of the user's progress. [fullDate] is "Sat 31 Oct". */
@Composable
internal fun WaitlistPanel(
    fullDate: String,
    panel: WaitlistPanelState,
) {
    val joined = panel.joinedEmail
    val form = panel.form
    when {
        joined != null -> JoinedNote(fullDate, joined)
        form != null -> WaitlistFormFields(form, panel)
        else ->
            NoticeButton(
                text = stringResource(Res.string.trip_waitlist_join),
                icon = BahrIcons.NotificationsActive.outlined(),
                onClick = panel.onOpen,
            )
    }
}

/**
 * The email, the party, the refusal if any, and "Add me to the list". The email field is typed and
 * shown left to right (an address reads LTR inside Arabic, like a time), on the email keyboard, with
 * no capitalisation or autocorrect to change what was typed, and offered to autofill as an email.
 * It is flagged once left with an address the server's rule refuses (`WaitlistEmail`): the server's
 * own refusal names no field, so this is where the user learns which one is wrong.
 */
@Composable
private fun WaitlistFormFields(
    form: WaitlistForm,
    panel: WaitlistPanelState,
) {
    val c = MaterialTheme.colorScheme
    var emailFocused by remember { mutableStateOf(false) }
    val emailError = form.email.isNotBlank() && !form.isEmailValid && !emailFocused
    Column(
        modifier = Modifier.testTag(WAITLIST_FORM_TAG),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        Text(
            text = stringResource(Res.string.trip_waitlist_form_note),
            style = MaterialTheme.typography.bodyMedium,
            color = c.onTertiaryContainer,
        )
        OutlinedTextField(
            value = form.email,
            onValueChange = panel.onEmailChange,
            label = { Text(stringResource(Res.string.trip_waitlist_email)) },
            singleLine = true,
            isError = emailError,
            supportingText = if (emailError) ({ Text(stringResource(Res.string.trip_waitlist_email_invalid)) }) else null,
            // Addresses stay left to right inside Arabic, like times.
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
            shape = BahrTheme.shapes.medium,
            // On the coral notice, the handoff's near-white fill already reads as a field.
            colors = BahrTextFields.colors(BahrTextFields.Ground.Tint),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { panel.onJoin() }),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.EmailAddress }
                    .testTag(WAITLIST_EMAIL_TAG)
                    .onFocusChanged { emailFocused = it.isFocused },
        )
        PartyRow(form.partySize, panel)
        // The notice's own text is already coral-dark, so the refusal carries an icon to stand apart.
        form.error?.let { error -> OutcomeRow(waitlistErrorText(error), BahrIcons.Info, c.error, Modifier) }
        NoticeButton(
            text = stringResource(Res.string.trip_waitlist_submit),
            icon = BahrIcons.NotificationsActive.outlined(),
            enabled = form.isEmailValid,
            loading = form.submitting,
            onClick = panel.onJoin,
        )
    }
}

/**
 * `RATE_LIMITED` on this endpoint means the date's list is full, not "too many tries", so it gets
 * its own sentence; everything else uses the app's message for the code.
 */
@Composable
private fun waitlistErrorText(error: AppError): String =
    if ((error as? AppError.Api)?.code == ApiErrorCodes.RATE_LIMITED) {
        stringResource(Res.string.trip_waitlist_full)
    } else {
        error.localizedMessage()
    }

/** "How many people": the label, then − count + on a pill track, bounded 1..[WaitlistPanelState.maxPartySize]. */
@Composable
private fun PartyRow(
    partySize: Int,
    panel: WaitlistPanelState,
) {
    val c = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
        Text(
            text = stringResource(Res.string.trip_waitlist_party_size),
            style = MaterialTheme.typography.labelLarge,
            color = c.onTertiaryContainer,
            modifier = Modifier.weight(1f),
        )
        BahrStepper(
            props =
                BahrStepper.Props(
                    value = partySize,
                    canDecrease = partySize > TripDetailUiState.MIN_PARTY_SIZE,
                    canIncrease = partySize < panel.maxPartySize,
                    decreaseLabel = stringResource(Res.string.trip_waitlist_party_decrease),
                    increaseLabel = stringResource(Res.string.trip_waitlist_party_increase),
                    valueDescription = pluralStringResource(Res.plurals.trip_waitlist_party_count, partySize, partySize),
                    // On the coral notice: a white track, a flat − button.
                    ground = BahrStepper.Ground.Tint,
                    onDecrease = panel.onDecrease,
                    onIncrease = panel.onIncrease,
                ),
        )
    }
}

/**
 * The handoff's waiting-list button: a `surface` pill with `error` text and a bell, lifted a little.
 * Not the coral button: that is the page's step forward (the sticky bar's), and this is a side path.
 * [loading] shows a spinner in the icon's place and announces "busy", as the primary button does.
 */
@Composable
private fun NoticeButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val c = MaterialTheme.colorScheme
    val x = BahrTheme.colors
    val shape = BahrTheme.shapes.full
    val active = enabled || loading
    val content = if (active) c.error else x.onSurfaceDisabled
    val busy = stringResource(Res.string.a11y_busy)
    Row(
        modifier =
            Modifier
                .heightIn(min = BahrSpacing.minTouch)
                .then(if (active) Modifier.bahrShadow(BahrElevation.Level1, shape, x) else Modifier)
                .clip(shape)
                .background(if (active) c.surface else x.track)
                .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
                .then(if (loading) Modifier.semantics { stateDescription = busy } else Modifier)
                .padding(horizontal = BahrSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(BahrSize.iconSmall), color = content, strokeWidth = BahrBorder.selected)
        } else {
            Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(BahrSize.iconSmall))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

/** After a join: a tick and "You're on the waiting list", with the date and the email it was made under. */
@Composable
private fun JoinedNote(
    fullDate: String,
    email: String,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.large)
                .background(c.surface)
                .padding(BahrSpacing.md)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        // The tick is drawn in once (M3-M8b): the join just went through, or it is the date's note on a later
        // visit. Decorative: the title beside it says it.
        BahrLottie(
            modifier = Modifier.width(BahrSize.lottieInline),
            props = BahrLottie.Props(BahrLottie.Animation.SuccessCheck),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
            Text(
                text = stringResource(Res.string.trip_waitlist_joined_title),
                style = MaterialTheme.typography.labelLarge,
                color = c.onSurface,
            )
            Text(
                text = stringResource(Res.string.trip_waitlist_joined_body, fullDate, BahrFormat.ltr(email)),
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )
        }
    }
}

/**
 * Said in the availability band after a refused join, outside the notice (which the re-read may have
 * removed): seats opened up on the date (it is now bookable), or the date closed.
 */
@Composable
internal fun WaitlistOutcomeNote(
    outcome: WaitlistOutcome,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    val date = BahrFormat.date(outcome.date)
    val (text, icon, tint) =
        when (outcome.kind) {
            WaitlistOutcome.Kind.SeatsOpened ->
                Triple(stringResource(Res.string.trip_waitlist_seats_opened, date), BahrIcons.EventSeat, BahrTheme.colors.success)
            WaitlistOutcome.Kind.DateClosed ->
                Triple(
                    stringResource(Res.string.trip_waitlist_date_closed, date),
                    BahrIcons.EventBusy,
                    c.error,
                )
        }
    OutcomeRow(text, icon, tint, modifier)
}

@Composable
private fun OutcomeRow(
    text: String,
    icon: BahrIcons,
    tint: Color,
    modifier: Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface)
                .padding(BahrSpacing.md)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        Icon(icon.filled(), contentDescription = null, tint = tint, modifier = Modifier.size(BahrSize.iconMedium))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** For tests: the open form and its email field. */
internal const val WAITLIST_FORM_TAG = "waitlist_form"
internal const val WAITLIST_EMAIL_TAG = "waitlist_email"
