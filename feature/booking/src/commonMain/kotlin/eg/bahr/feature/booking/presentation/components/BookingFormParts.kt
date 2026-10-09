package eg.bahr.feature.booking.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import eg.bahr.core.designsystem.components.SeatBadge
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrBorder
import eg.bahr.core.designsystem.theme.BahrElevation
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrShadow
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.booking_guest_name
import eg.bahr.core.localization.generated.resources.booking_guest_phone
import eg.bahr.core.localization.generated.resources.booking_guest_phone_invalid
import eg.bahr.core.localization.generated.resources.booking_guest_title
import eg.bahr.core.localization.generated.resources.booking_party_count
import eg.bahr.core.localization.generated.resources.booking_party_decrease
import eg.bahr.core.localization.generated.resources.booking_party_increase
import eg.bahr.core.localization.generated.resources.booking_party_max
import eg.bahr.core.localization.generated.resources.booking_party_size
import eg.bahr.core.localization.generated.resources.booking_seats_left_cap
import eg.bahr.core.localization.generated.resources.booking_summary_party
import eg.bahr.core.localization.generated.resources.booking_summary_price
import eg.bahr.core.localization.generated.resources.booking_summary_total_note
import eg.bahr.core.localization.generated.resources.departure_cancelled
import eg.bahr.core.localization.generated.resources.departure_closed
import eg.bahr.core.localization.generated.resources.departure_sold_out
import eg.bahr.core.localization.generated.resources.departures_pick_a_date
import eg.bahr.core.localization.generated.resources.trip_dates_empty
import eg.bahr.core.network.MoneyDto
import eg.bahr.feature.booking.model.BookingDepartureDto
import eg.bahr.feature.booking.presentation.DateAvailability
import eg.bahr.feature.booking.presentation.availability
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * "Pick a date", then one row per date (HANDOFF screen 4): a radio, the date and its `05:00 → 22:00`
 * (always left to right), and a pill with the seats left or why the date cannot be booked. Only an
 * open date can be picked here.
 */
@Composable
internal fun DateRows(
    departures: List<BookingDepartureDto>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
        SectionLabel(stringResource(Res.string.departures_pick_a_date))
        if (departures.isEmpty()) {
            Text(
                text = stringResource(Res.string.trip_dates_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = BahrTheme.colors.onSurfaceSecondary,
            )
        } else {
            Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
                departures.forEach { departure ->
                    DateRow(departure, selected = departure.id == selectedId, onSelect = { onSelect(departure.id) })
                }
            }
        }
    }
}

/**
 * Open: `surface` with `elevation.level1`; selected: `primaryContainer` ringed in `primary`. Any date
 * that cannot be booked sits on muted `surfaceContainer` with a dashed radio (the handoff's sold-out
 * row); its reason is told by the pill (D3): "Sold out" on `track`, "Cancelled" in `error` with the
 * date struck through, "Booking closed" as an outlined pill.
 */
@Composable
private fun DateRow(
    departure: BookingDepartureDto,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val x = BahrTheme.colors
    val shape = BahrTheme.shapes.extraLarge
    val availability = departure.availability
    val open = availability == DateAvailability.Open
    val background =
        when {
            open && selected -> c.primaryContainer
            open -> c.surface
            else -> c.surfaceContainer
        }
    val content = if (open) c.onSurface else x.onSurfaceDisabled
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (open && !selected) Modifier.bahrShadow(BahrElevation.Level1, shape, x) else Modifier)
                .clip(shape)
                .background(background)
                .then(if (open && selected) Modifier.border(BahrBorder.selected, c.primary, shape) else Modifier)
                .selectable(selected = selected, enabled = open, role = Role.RadioButton, onClick = onSelect)
                .heightIn(min = BahrSpacing.minTouch)
                .padding(horizontal = BahrSpacing.lg, vertical = BahrSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        Radio(selected = selected, open = open)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
            Text(
                text = BahrFormat.date(departure.date),
                style = MaterialTheme.typography.titleMedium,
                color = content,
                textDecoration = if (availability == DateAvailability.Cancelled) TextDecoration.LineThrough else null,
                maxLines = 1,
            )
            timesOf(departure)?.let {
                Text(text = it, style = MaterialTheme.typography.labelMedium, color = if (open) c.onSurfaceVariant else content)
            }
        }
        if (open) SeatBadge(left = departure.seatsRemaining) else ReasonPill(availability)
    }
}

/** `05:00 → 22:00` in a left-to-right isolate; null when the server sent no usable times. */
private fun timesOf(departure: BookingDepartureDto): String? {
    val from = departure.departTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return null
    val to = departure.returnTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return null
    return BahrFormat.timeRange(from, to)
}

/** The handoff's custom radio: a `primary` ring with an inset dot when picked, dashed when it cannot be. */
@Composable
private fun Radio(
    selected: Boolean,
    open: Boolean,
) {
    val c = MaterialTheme.colorScheme
    val shape = BahrTheme.shapes.full
    val ring = if (selected) c.primary else c.outline
    Box(
        modifier =
            Modifier
                .size(BahrSize.radio)
                .then(
                    if (open) {
                        Modifier.border(BahrBorder.selected, ring, shape)
                    } else {
                        Modifier.drawBehind {
                            val stroke = BahrBorder.selected.toPx()
                            drawCircle(
                                color = c.outline,
                                radius = (size.minDimension - stroke) / 2,
                                style =
                                    Stroke(
                                        width = stroke,
                                        pathEffect =
                                            PathEffect.dashPathEffect(
                                                floatArrayOf(
                                                    stroke * DASH,
                                                    stroke * DASH,
                                                ),
                                            ),
                                    ),
                            )
                        }
                    },
                ).padding(BahrBorder.selected + BahrBorder.selected),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.fillMaxSize().clip(shape).background(c.primary))
    }
}

/** The dash and the gap of a closed date's radio, in stroke widths. */
private const val DASH = 1.5f

@Composable
private fun ReasonPill(availability: DateAvailability) {
    val c = MaterialTheme.colorScheme
    val x = BahrTheme.colors
    val shape = BahrTheme.shapes.full
    val (text, look) =
        when (availability) {
            DateAvailability.Cancelled -> stringResource(Res.string.departure_cancelled) to PillLook(c.errorContainer, c.error)
            DateAvailability.Closed ->
                stringResource(Res.string.departure_closed) to
                    PillLook(null, c.onSurfaceVariant, outline = c.outlineVariant)
            // Open never reaches here (it shows the seat badge); sold out is the muted default.
            DateAvailability.SoldOut, DateAvailability.Open ->
                stringResource(Res.string.departure_sold_out) to
                    PillLook(x.track, x.onSurfaceDisabled)
        }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = look.content,
        maxLines = 1,
        modifier =
            Modifier
                .clip(shape)
                .then(if (look.background != null) Modifier.background(look.background) else Modifier)
                .then(if (look.outline != null) Modifier.border(BahrBorder.hairline, look.outline, shape) else Modifier)
                .padding(horizontal = BahrSpacing.sm, vertical = BahrSpacing.xs),
    )
}

private class PillLook(
    val background: Color?,
    val content: Color,
    val outline: Color? = null,
)

/**
 * "How many people", a pill-shaped `surfaceContainer` track holding a white − button, the count and a
 * `primary` + button, then the limit: the selected date's seats left when that is lower than the
 * server's policy (a hint, the hold still decides), else the policy's maximum. Each end disables
 * itself at its bound; the count is announced when it changes.
 */
@Composable
internal fun PartyStepper(
    partySize: Int,
    maxPartySize: Int,
    seatsLeftCap: Int?,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
        SectionLabel(stringResource(Res.string.booking_party_size))
        Row(
            modifier =
                Modifier
                    .clip(BahrTheme.shapes.full)
                    .background(c.surfaceContainer)
                    .padding(horizontal = BahrSpacing.md, vertical = BahrSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BahrSpacing.lg),
        ) {
            StepperButton(
                icon = BahrIcons.Remove.outlined(),
                description = stringResource(Res.string.booking_party_decrease),
                enabled = canDecrease,
                background = c.surface,
                content = c.onSurface,
                elevated = true,
                onClick = onDecrease,
            )
            val spoken = pluralStringResource(Res.plurals.booking_party_count, partySize, partySize)
            Text(
                text = partySize.toString(),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier.widthIn(min = BahrSize.stepperValue).semantics {
                        contentDescription = spoken
                        liveRegion = LiveRegionMode.Polite
                    },
            )
            StepperButton(
                icon = BahrIcons.Add.outlined(),
                description = stringResource(Res.string.booking_party_increase),
                enabled = canIncrease,
                background = c.primary,
                content = c.onPrimary,
                elevated = false,
                onClick = onIncrease,
            )
        }
        // The tighter limit is the one worth saying: the date's seats left, else the policy's maximum.
        Text(
            text =
                if (seatsLeftCap != null) {
                    pluralStringResource(Res.plurals.booking_seats_left_cap, seatsLeftCap, seatsLeftCap)
                } else {
                    pluralStringResource(Res.plurals.booking_party_max, maxPartySize, maxPartySize)
                },
            style = MaterialTheme.typography.bodyMedium,
            color = c.onSurfaceVariant,
        )
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    background: Color,
    content: Color,
    elevated: Boolean,
    onClick: () -> Unit,
) {
    val x = BahrTheme.colors
    val shape = BahrTheme.shapes.full
    Box(
        modifier =
            Modifier
                .size(BahrSpacing.minTouch)
                .then(if (enabled && elevated) Modifier.bahrShadow(BahrElevation.Level1, shape, x) else Modifier)
                .clip(shape)
                .background(if (enabled) background else x.track)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = if (enabled) content else x.onSurfaceDisabled)
    }
}

/**
 * "Your details": the name and mobile number the hold is made under (`PlaceHoldRequest.guest`). The
 * number is typed and shown left to right in both languages, and flagged once the field is left with
 * something the contract's `Phone` pattern would refuse.
 */
@Composable
internal fun GuestDetails(
    name: String,
    phone: String,
    phoneValid: Boolean,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    var phoneFocused by remember { mutableStateOf(false) }
    val phoneError = phone.isNotEmpty() && !phoneValid && !phoneFocused
    Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
        SectionLabel(stringResource(Res.string.booking_guest_title))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(Res.string.booking_guest_name)) },
            singleLine = true,
            shape = BahrTheme.shapes.medium,
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().keptAboveKeyboard(),
        )
        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            label = { Text(stringResource(Res.string.booking_guest_phone)) },
            singleLine = true,
            isError = phoneError,
            supportingText = if (phoneError) ({ Text(stringResource(Res.string.booking_guest_phone_invalid)) }) else null,
            // Phone numbers stay left to right inside Arabic, like times.
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
            shape = BahrTheme.shapes.medium,
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth().keptAboveKeyboard().onFocusChanged { phoneFocused = it.isFocused },
        )
    }
}

/**
 * Scrolls a focused field back into view once the keyboard has opened. The field gets focus before
 * the keyboard has shrunk the form (the screen pads itself by the IME inset), so the text field's own
 * bring-into-view runs against the old, taller viewport and the field ends up under the sticky bar.
 * Asking again as the inset grows, while focused, settles it in view when the keyboard is fully up.
 */
@Composable
private fun Modifier.keptAboveKeyboard(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom) {
        if (focused && imeBottom > 0) requester.bringIntoView()
    }
    return bringIntoViewRequester(requester).onFocusEvent { focused = it.isFocused }
}

/** HANDOFF screen 5's inputs: `surfaceLowest` with an `outlineVariant` border. */
@Composable
private fun fieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = BahrTheme.colors.surfaceLowest,
        unfocusedContainerColor = BahrTheme.colors.surfaceLowest,
        errorContainerColor = BahrTheme.colors.surfaceLowest,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    )

/**
 * The price panel on `surfaceLow`: the per-person price and the party, side by side, and a note that
 * the total comes with the hold. The handoff shows `2 × 450` and a total of 900 here, but the client
 * never multiplies money: before the hold, the contract has no total to show (`HeldSeats.total` is
 * the first), and no booking fee is served in R1, so there is no fee row either.
 */
@Composable
internal fun PriceSummary(
    pricePerPerson: MoneyDto,
    partySize: Int,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.card)
                .background(BahrTheme.colors.surfaceLow)
                .padding(BahrSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        SummaryRow(
            label = stringResource(Res.string.booking_summary_price),
            value = BahrFormat.money(pricePerPerson.amount, pricePerPerson.currencyCode, BahrTheme.locale.isArabic),
        )
        SummaryRow(
            label = stringResource(Res.string.booking_summary_party),
            value = pluralStringResource(Res.plurals.booking_party_count, partySize, partySize),
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = BahrSpacing.sm), color = c.outlineVariant)
        Text(
            text = stringResource(Res.string.booking_summary_total_note),
            style = MaterialTheme.typography.bodyMedium,
            color = c.onSurfaceVariant,
        )
    }
}

/** A label and its value on the price panel; also the held-seats screen's party row. */
@Composable
internal fun SummaryRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}
