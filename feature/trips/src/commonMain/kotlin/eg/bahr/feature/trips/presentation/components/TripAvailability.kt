package eg.bahr.feature.trips.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import eg.bahr.core.designsystem.components.BahrPrimaryButton
import eg.bahr.core.designsystem.components.StickyActionBar
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrBorder
import eg.bahr.core.designsystem.theme.BahrElevation
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrShadow
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.departure_back_on
import eg.bahr.core.localization.generated.resources.departure_cancelled
import eg.bahr.core.localization.generated.resources.departure_closed
import eg.bahr.core.localization.generated.resources.departure_seats_left
import eg.bahr.core.localization.generated.resources.departure_sold_out
import eg.bahr.core.localization.generated.resources.departures_pick_a_date
import eg.bahr.core.localization.generated.resources.format_pair
import eg.bahr.core.localization.generated.resources.seat_count
import eg.bahr.core.localization.generated.resources.trip_bar_per_person
import eg.bahr.core.localization.generated.resources.trip_cta_choose_date
import eg.bahr.core.localization.generated.resources.trip_cta_continue
import eg.bahr.core.localization.generated.resources.trip_cta_sold_out
import eg.bahr.core.localization.generated.resources.trip_dates_empty
import eg.bahr.core.localization.generated.resources.trip_policy_cancellation
import eg.bahr.core.localization.generated.resources.trip_policy_units
import eg.bahr.core.localization.generated.resources.trip_sold_out_alternative
import eg.bahr.core.localization.generated.resources.trip_sold_out_alternative_same_price
import eg.bahr.core.localization.generated.resources.trip_sold_out_body
import eg.bahr.core.localization.generated.resources.trip_sold_out_title
import eg.bahr.core.localization.generated.resources.waitlist_tag
import eg.bahr.core.network.MoneyDto
import eg.bahr.feature.trips.model.DepartureDto
import eg.bahr.feature.trips.model.PolicyDto
import eg.bahr.feature.trips.model.laterReturnDate
import eg.bahr.feature.trips.presentation.DateAvailability
import eg.bahr.feature.trips.presentation.TripCta
import eg.bahr.feature.trips.presentation.WaitlistOutcome
import eg.bahr.feature.trips.presentation.availability
import eg.bahr.feature.trips.presentation.isSelectable
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Availability (item 10): a `primaryContainer` band with a row of date cards, the sold-out notice
 * with its waiting list when a full date is picked, what a refused join turned out to mean, and the
 * policy in small print.
 */
@Composable
internal fun TripAvailability(
    departures: List<DepartureDto>,
    loading: Boolean,
    selected: DepartureDto?,
    alternative: DepartureDto?,
    policy: PolicyDto?,
    waitlist: WaitlistPanelState,
    waitlistOutcome: WaitlistOutcome?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    joinedIds: Set<String> = emptySet(),
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(vertical = BahrSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        val gutter = Modifier.padding(horizontal = BahrSpacing.gutter)
        SectionTitle(stringResource(Res.string.departures_pick_a_date), gutter)
        when {
            loading ->
                Row(gutter, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
                    repeat(SKELETON_CARDS) {
                        Skeleton(
                            Modifier
                                .width(BahrSize.dateCard)
                                .heightIn(min = BahrSize.dateCard)
                                .clip(BahrTheme.shapes.extraLarge),
                        )
                    }
                }

            departures.isEmpty() ->
                Text(
                    text = stringResource(Res.string.trip_dates_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BahrTheme.colors.onSurfaceSecondary,
                    modifier = gutter,
                )

            else ->
                LazyRow(
                    contentPadding = PaddingValues(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
                ) {
                    items(departures, key = { it.id }) { departure ->
                        DateCard(
                            departure = departure,
                            selected = departure.id == selected?.id,
                            joined = departure.id in joinedIds,
                            onSelect = { onSelect(departure.id) },
                        )
                    }
                }
        }
        waitlistOutcome?.let { WaitlistOutcomeNote(it, gutter) }
        if (selected?.availability == DateAvailability.SoldOut) SoldOutNotice(selected, alternative, waitlist, gutter)
        policy?.let { PolicyNote(it, gutter) }
    }
}

/**
 * One date: weekday, day and month (and the day it is back, on a multi-day trip), and the seats left
 * or why it cannot be booked. Selected = primary
 * fill with `elevation.raised`. A date that cannot be booked is drawn by its reason (D3), so the three
 * never look alike:
 * - sold out: muted `surfaceContainer` with a `block` icon; it can be picked (it shows the sold-out
 *   notice), and when picked a coral-family ground ringed in `error` rather than the prototype's
 *   coral, which is for actions;
 * - cancelled: the same muted ground, but label and `event_busy` icon in `error`: the host called the
 *   date off, which is news, not just "full";
 * - booking closed: no fill at all, only an `outlineVariant` hairline and a `lock` icon, so it reads
 *   as past rather than taken.
 *
 * A sold-out date the device is on the waiting list of ([joined], M4-M5) keeps the sold-out look but
 * reads "Waiting list" with a bell (the list cards' tag wording, short enough for one line on the
 * narrow card), so the user sees they need not join again. Only a sold-out
 * date can say so (D3: only it has a list).
 */
@Composable
private fun DateCard(
    departure: DepartureDto,
    selected: Boolean,
    joined: Boolean,
    onSelect: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val x = BahrTheme.colors
    val shape = BahrTheme.shapes.extraLarge
    val availability = departure.availability
    val waiting = joined && availability == DateAvailability.SoldOut
    val look =
        when (availability) {
            DateAvailability.Open ->
                if (selected) {
                    DateCardLook(c.primary, c.onPrimary, BahrIcons.EventSeat, BahrElevation.Raised)
                } else {
                    DateCardLook(c.surface, c.onSurface, BahrIcons.EventSeat, BahrElevation.Level1)
                }
            DateAvailability.SoldOut ->
                if (selected) {
                    DateCardLook(c.tertiaryContainer, c.onTertiaryContainer, BahrIcons.Block, ring = c.error)
                } else {
                    DateCardLook(c.surfaceContainer, x.onSurfaceDisabled, BahrIcons.Block)
                }
            DateAvailability.Cancelled -> DateCardLook(c.surfaceContainer, c.error, BahrIcons.EventBusy)
            DateAvailability.Closed -> DateCardLook(null, c.onSurfaceVariant, BahrIcons.Lock, hairline = c.outlineVariant)
        }
    Column(
        modifier =
            Modifier
                .width(BahrSize.dateCard)
                .then(if (look.elevation != null) Modifier.bahrShadow(look.elevation, shape, x) else Modifier)
                .clip(shape)
                .then(if (look.background != null) Modifier.background(look.background) else Modifier)
                .then(if (look.ring != null) Modifier.border(BahrBorder.selected, look.ring, shape) else Modifier)
                .then(if (look.hairline != null) Modifier.border(BahrBorder.hairline, look.hairline, shape) else Modifier)
                .selectable(
                    selected = selected,
                    enabled = departure.isSelectable(),
                    role = Role.RadioButton,
                    onClick = onSelect,
                ).heightIn(min = BahrSpacing.minTouch)
                .padding(BahrSpacing.md),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        val content = look.content
        Text(text = BahrFormat.weekday(departure.date), style = MaterialTheme.typography.labelMedium, color = content)
        Text(text = BahrFormat.dayMonth(departure.date), style = MaterialTheme.typography.titleMedium, color = content, maxLines = 1)
        // A multi-day trip (M4-B0b) is back on a later day; a day trip's card is unchanged.
        departure.laterReturnDate?.let { back ->
            Text(
                text = stringResource(Res.string.departure_back_on, BahrFormat.dayMonth(back)),
                style = MaterialTheme.typography.labelMedium,
                color = content,
            )
        }
        Row(
            modifier = Modifier.padding(top = BahrSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
        ) {
            Icon(
                imageVector = if (waiting) BahrIcons.NotificationsActive.outlined() else look.icon.outlined(),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(BahrSize.iconSmall),
            )
            // Wraps rather than clips: "11 seats left" is wider than the 100dp card in English.
            Text(
                text = if (waiting) stringResource(Res.string.waitlist_tag) else seatsText(departure),
                style = MaterialTheme.typography.labelMedium,
                color = content,
            )
        }
    }
}

/** A date card's colours, icon and edge for one availability × selection. */
private class DateCardLook(
    val background: Color?,
    val content: Color,
    val icon: BahrIcons,
    val elevation: BahrElevation? = null,
    val ring: Color? = null,
    val hairline: Color? = null,
)

/** "6 seats left" / "Sold out" / "Cancelled" / "Booking closed", from the date's [availability]. */
@Composable
internal fun seatsText(departure: DepartureDto): String =
    when (departure.availability) {
        DateAvailability.Open -> pluralStringResource(Res.plurals.departure_seats_left, departure.seatsRemaining, departure.seatsRemaining)
        DateAvailability.SoldOut -> stringResource(Res.string.departure_sold_out)
        DateAvailability.Cancelled -> stringResource(Res.string.departure_cancelled)
        DateAvailability.Closed -> stringResource(Res.string.departure_closed)
    }

/**
 * "Sat 17 Oct is full. All 18 seats are booked. Sat 24 Oct has 11 seats left, same price." The
 * price sentence is only said when the two prices are equal (a comparison, never arithmetic). Then
 * the waiting list: the button, its form, or the confirmation ([WaitlistPanel]).
 */
@Composable
private fun SoldOutNotice(
    full: DepartureDto,
    alternative: DepartureDto?,
    waitlist: WaitlistPanelState,
    modifier: Modifier,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.extraLarge)
                .background(c.tertiaryContainer)
                .padding(BahrSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
            Icon(BahrIcons.EventBusy.filled(), contentDescription = null, tint = c.error, modifier = Modifier.size(BahrSize.iconMedium))
            Text(
                text = stringResource(Res.string.trip_sold_out_title, BahrFormat.date(full.date)),
                style = MaterialTheme.typography.labelLarge,
                color = c.onTertiaryContainer,
            )
        }
        Text(
            text = stringResource(Res.string.trip_sold_out_body, full.capacity),
            style = MaterialTheme.typography.bodyMedium,
            color = c.onTertiaryContainer,
        )
        alternative?.let {
            val seats = pluralStringResource(Res.plurals.seat_count, it.seatsRemaining, it.seatsRemaining)
            val sentence =
                if (it.price == full.price) Res.string.trip_sold_out_alternative_same_price else Res.string.trip_sold_out_alternative
            Text(
                text = stringResource(sentence, BahrFormat.date(it.date), seats),
                style = MaterialTheme.typography.bodyMedium,
                color = c.onTertiaryContainer,
            )
        }
        Box(Modifier.padding(top = BahrSpacing.sm)) {
            WaitlistPanel(fullDate = BahrFormat.date(full.date), panel = waitlist)
        }
    }
}

/** The cancellation rule from the server's policy, then the units line. No child fare (D1): every seat is charged. */
@Composable
private fun PolicyNote(
    policy: PolicyDto,
    modifier: Modifier,
) {
    val color = BahrTheme.colors.onSurfaceSecondary
    val style = MaterialTheme.typography.labelMedium
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
        policy.freeCancellationHours?.let { Text(stringResource(Res.string.trip_policy_cancellation, it), style = style, color = color) }
        Text(stringResource(Res.string.trip_policy_units), style = style, color = color)
    }
}

/**
 * The sticky bottom bar (item 11): what the price is for ("Per person", or the picked date and its
 * seats) and the price, then the one coral button. Its label follows the selection: "Choose a date"
 * → "Continue" → a disabled "Sold out".
 */
@Composable
internal fun TripStickyBar(
    price: MoneyDto?,
    selected: DepartureDto?,
    cta: TripCta,
    onCta: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StickyActionBar(modifier = modifier) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
            Text(
                text =
                    selected?.let { stringResource(Res.string.format_pair, BahrFormat.date(it.date), seatsText(it)) }
                        ?: stringResource(Res.string.trip_bar_per_person),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            // The picked date's own price when there is one: a date may be priced differently.
            (selected?.price ?: price)?.let {
                Text(
                    text = BahrFormat.money(it.amount, it.currencyCode, BahrTheme.locale.isArabic),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
        BahrPrimaryButton(
            text =
                stringResource(
                    when (cta) {
                        TripCta.ChooseDate -> Res.string.trip_cta_choose_date
                        TripCta.Continue -> Res.string.trip_cta_continue
                        TripCta.SoldOut -> Res.string.trip_cta_sold_out
                    },
                ),
            onClick = onCta,
            // Only "Sold out" is disabled. While the trip loads the button keeps its normal look, so a
            // slow page never reads as unavailable; the tap waits for the dates (see TripDetailScreen).
            enabled = cta != TripCta.SoldOut,
            leadingIcon = if (cta == TripCta.Continue) BahrIcons.ArrowForward.outlined() else null,
        )
    }
}

/** Placeholder date cards while the live seat counts load. */
private const val SKELETON_CARDS = 4
