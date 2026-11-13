package eg.bahr.feature.booking.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.common.result.AppError
import eg.bahr.core.designsystem.components.BahrBackButton
import eg.bahr.core.designsystem.components.BahrBadge
import eg.bahr.core.designsystem.components.BahrErrorView
import eg.bahr.core.designsystem.components.BahrLottie
import eg.bahr.core.designsystem.components.BahrPrimaryButton
import eg.bahr.core.designsystem.components.BahrSkeleton
import eg.bahr.core.designsystem.components.BahrSkeletonBlock
import eg.bahr.core.designsystem.components.BahrSkeletonLine
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.overlineCase
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.booking_method_card
import eg.bahr.core.localization.generated.resources.booking_method_test_badge
import eg.bahr.core.localization.generated.resources.booking_method_test_cash
import eg.bahr.core.localization.generated.resources.booking_method_vf
import eg.bahr.core.localization.generated.resources.booking_party_count
import eg.bahr.core.localization.generated.resources.booking_total
import eg.bahr.core.localization.generated.resources.mytrips_rate_trip
import eg.bahr.core.localization.generated.resources.ticket_not_found
import eg.bahr.core.localization.generated.resources.ticket_note_called_off
import eg.bahr.core.localization.generated.resources.ticket_note_cancelled
import eg.bahr.core.localization.generated.resources.ticket_note_completed
import eg.bahr.core.localization.generated.resources.ticket_note_expired
import eg.bahr.core.localization.generated.resources.ticket_note_unpaid
import eg.bahr.core.localization.generated.resources.ticket_overline_called_off
import eg.bahr.core.localization.generated.resources.ticket_overline_cancelled
import eg.bahr.core.localization.generated.resources.ticket_overline_completed
import eg.bahr.core.localization.generated.resources.ticket_overline_confirmed
import eg.bahr.core.localization.generated.resources.ticket_overline_expired
import eg.bahr.core.localization.generated.resources.ticket_overline_unknown
import eg.bahr.core.localization.generated.resources.ticket_overline_unpaid
import eg.bahr.core.localization.generated.resources.ticket_row_date
import eg.bahr.core.localization.generated.resources.ticket_row_departure
import eg.bahr.core.localization.generated.resources.ticket_row_host
import eg.bahr.core.localization.generated.resources.ticket_row_owed_to_host
import eg.bahr.core.localization.generated.resources.ticket_row_paid
import eg.bahr.core.localization.generated.resources.ticket_row_party
import eg.bahr.core.localization.generated.resources.ticket_row_return
import eg.bahr.core.localization.generated.resources.ticket_screenshot_note
import eg.bahr.core.localization.generated.resources.ticket_show_host
import eg.bahr.core.localization.generated.resources.ticket_show_host_unnamed
import eg.bahr.core.localization.generated.resources.ticket_title_confirmation
import eg.bahr.core.localization.generated.resources.ticket_title_details
import eg.bahr.core.localization.generated.resources.ticket_value_comma
import eg.bahr.core.localization.generated.resources.ticket_value_dash
import eg.bahr.core.localization.generated.resources.ticket_value_dot
import eg.bahr.core.localization.generated.resources.ticket_view_trip
import eg.bahr.core.localization.generated.resources.ticket_whatsapp_host
import eg.bahr.core.localization.generated.resources.ticket_whatsapp_host_unnamed
import eg.bahr.core.localization.generated.resources.ticket_where_to_stand
import eg.bahr.core.localization.generated.resources.trips_title
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.feature.booking.model.BookingDto
import eg.bahr.feature.booking.model.laterReturnDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The booking's ticket (HANDOFF §6, MV-M5), designed to be screenshotted: a teal header with the reference centred,
 * then the rows someone needs at the bus (date, departure, return, party, money, host), where to stand, the host's
 * WhatsApp and a note to take a screenshot. Opened after paying and by reference (a notification), so the header
 * follows the booking's status ([TicketState]): only a confirmed booking celebrates and says "Booked".
 *
 * Only what the contract serves is drawn: no "Add to calendar" (`Booking.calendar` is not served yet) and no map
 * crop (M3-M2).
 *
 * Since MV-M6 it is also a booking's details, opened from My trips ([TicketEntry.Details]): the bar names the entry
 * and has a way back in every state (loading, error, not found); the details offer the trip's page and, when [canRate]
 * (My trips' rule), "Rate your trip", instead of the "Trips" button, which there would only repeat Back.
 */
@Composable
internal fun BookingConfirmedScreen(
    ref: String,
    actions: TicketCallbacks,
    modifier: Modifier = Modifier,
    entry: TicketEntry = TicketEntry.Paid,
    canRate: Boolean = false,
    viewModel: BookingConfirmedViewModel = koinViewModel { parametersOf(ref) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val booking = state.booking

    when {
        state.isLoading -> TicketSkeleton(entry, actions.onBack, modifier)

        booking == null ->
            Column(modifier.fillMaxSize()) {
                // No header without a booking: the bar on the page's own ground, so a 404 or no network is never a dead end.
                TicketBar(
                    entry,
                    actions.onBack,
                    onHero = false,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = BahrSpacing.gutter),
                )
                val error = state.error
                BahrErrorView(
                    // Not found (an unknown reference, or a booking of another account) says so; anything else as usual.
                    message =
                        if ((error as? AppError.Api)?.code == ApiErrorCodes.NOT_FOUND) {
                            stringResource(Res.string.ticket_not_found)
                        } else {
                            error?.localizedMessage().orEmpty()
                        },
                    retryLabel = stringResource(Res.string.action_retry),
                    onRetry = if (state.error?.isRetryable == true) viewModel::load else null,
                    modifier = Modifier.weight(1f),
                )
            }

        else -> Ticket(booking, entry, canRate, actions, modifier)
    }
}

/** How the ticket was opened (MV-M6): what its bar says and which way out it offers. */
internal enum class TicketEntry {
    /** Right after paying: "Booking confirmation", and "Trips" back to the tab's root. */
    Paid,

    /** A booking looked up later (a My trips row, a notification): "Booking details", and the trip's page. */
    Details,
}

/**
 * Where the ticket's buttons lead, all wired in `:composeApp`: [onBack] the bar's back button, [onDone] "Trips" after
 * paying, [onViewTrip] the trip's page (feature:trip-details), [onRateTrip] the review page (feature:reviews).
 */
internal class TicketCallbacks(
    val onBack: () -> Unit = {},
    val onDone: () -> Unit = {},
    val onViewTrip: (slug: String) -> Unit = {},
    val onRateTrip: (ref: String, title: String?) -> Unit = { _, _ -> },
)

@Composable
private fun Ticket(
    booking: BookingDto,
    entry: TicketEntry,
    canRate: Boolean,
    actions: TicketCallbacks,
    modifier: Modifier,
) {
    val ticket = booking.ticketState
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TicketHeader(booking, ticket) { TicketBar(entry, actions.onBack, onHero = true) }
        Column(
            modifier = Modifier.padding(horizontal = BahrSpacing.gutter).padding(top = BahrSpacing.gutter),
            verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
        ) {
            booking.trip?.title?.let { title ->
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            }
            TicketRows(booking)
            // Where to stand matters only while there is a bus to catch; the server sends it on every status.
            booking.departure
                ?.whereToStand
                ?.takeIf { it.isNotBlank() && ticket in BUS_AHEAD }
                ?.let { WhereToStand(it) }
            TicketActions(booking, ticket, entry, canRate, actions)
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/**
 * The gradient header (`160deg`, the theme's hero stops, a 30dp bottom radius), its content centred (the user,
 * 2026-11-12): the celebration or the status's icon, the overline, the reference and one line under it. It runs under
 * the status bar, its content below it.
 */
@Composable
private fun TicketHeader(
    booking: BookingDto,
    ticket: TicketState,
    bar: @Composable () -> Unit,
) {
    val x = BahrTheme.colors
    val arabic = BahrTheme.locale.isArabic
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.header)
                .heroBackground()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = BahrSpacing.gutter)
                .padding(top = BahrSpacing.sm, bottom = BahrSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        bar()
        if (ticket == TicketState.Confirmed) {
            // The booked moment, drawn in once (MV-M5). Decorative: the overline under it says it.
            BahrLottie(
                modifier = Modifier.width(BahrSize.lottieCelebration),
                props = BahrLottie.Props(BahrLottie.Animation.BookingConfirmed),
            )
        } else {
            StatusDisc(ticket)
        }
        Text(
            text = stringResource(ticket.overline).overlineCase(arabic),
            style = BahrTheme.type.overline,
            color = x.onHero,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        // The one thing the traveller is asked to keep: the largest thing on screen, left to right in Arabic too (the
        // handoff's `dir="ltr"`), shrunk only if a long reference would not fit one line.
        BasicText(
            text = booking.ref,
            style = BahrTheme.type.display.copy(textAlign = TextAlign.Center, textDirection = TextDirection.Ltr),
            color = { x.onHero },
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(maxFontSize = BahrTheme.type.display.fontSize),
            modifier = Modifier.fillMaxWidth().testTag(TICKET_REF_TAG),
        )
        ticketNote(booking, ticket)?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                // Full white, not the handoff's translucent white: it is the instruction needed at the bus (MV-M5 review #1).
                color = x.onHero,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(TICKET_NOTE_TAG),
            )
        }
    }
}

/** The handoff's gradient and the soft disc in its top end corner, drawn behind the content (sizes from the width). */
@Composable
private fun Modifier.heroBackground(): Modifier {
    val x = BahrTheme.colors
    val start = x.heroStart
    val mid = x.heroMid
    val end = x.heroEnd
    val bubble = x.onHero.copy(alpha = HERO_BUBBLE_ALPHA)
    return drawWithCache {
        // CSS `linear-gradient(160deg, …)`: the line runs at 160° clockwise from "up", long enough that both corners
        // on it get the end colours. Not mirrored in RTL, as in the prototype.
        val radians = GRADIENT_ANGLE_DEGREES * PI / HALF_TURN_DEGREES
        val dx = sin(radians).toFloat()
        val dy = -cos(radians).toFloat()
        val half = (abs(size.width * dx) + abs(size.height * dy)) / 2
        val center = Offset(size.width / 2, size.height / 2)
        val brush =
            Brush.linearGradient(
                0f to start,
                GRADIENT_MID_STOP to mid,
                1f to end,
                start = center - Offset(dx * half, dy * half),
                end = center + Offset(dx * half, dy * half),
            )
        val radius = size.width * BUBBLE_RADIUS_SHARE
        val inset = size.width * BUBBLE_CENTER_INSET_SHARE
        val bubbleCenter =
            Offset(
                if (layoutDirection == LayoutDirection.Rtl) inset else size.width - inset,
                size.width * BUBBLE_CENTER_TOP_SHARE,
            )
        onDrawBehind {
            drawRect(brush)
            drawCircle(bubble, radius, bubbleCenter)
        }
    }
}

/** Instead of the celebration, for a booking that is not (or no longer) a ticket for a bus ahead: a still icon. */
@Composable
private fun StatusDisc(ticket: TicketState) {
    val x = BahrTheme.colors
    Box(
        modifier = Modifier.size(BahrSize.stateDisc).clip(BahrTheme.shapes.full).background(x.onHero),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = ticket.icon.filled(),
            // Decorative: the overline next to it says the status.
            contentDescription = null,
            tint = x.heroStart,
            modifier = Modifier.size(BahrSize.iconState),
        )
    }
}

/** The ticket's rows, each only when the server sent its value. */
@Composable
private fun TicketRows(booking: BookingDto) {
    val arabic = BahrTheme.locale.isArabic
    Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
        TicketRow(BahrIcons.Event, stringResource(Res.string.ticket_row_date), booking.dayLabel ?: BahrFormat.date(booking.date))
        departureValue(booking)?.let { TicketRow(BahrIcons.DirectionsBus, stringResource(Res.string.ticket_row_departure), it) }
        booking.returnTime?.takeIf { it.isNotBlank() }?.let { time ->
            val clock = BahrFormat.ltr(time)
            // A multi-day trip comes back on a later day: say which.
            val value =
                booking.laterReturnDate?.let { stringResource(Res.string.ticket_value_comma, BahrFormat.date(it), clock) } ?: clock
            TicketRow(BahrIcons.Schedule, stringResource(Res.string.ticket_row_return), value)
        }
        TicketRow(
            BahrIcons.Groups,
            stringResource(Res.string.ticket_row_party),
            pluralStringResource(Res.plurals.booking_party_count, booking.partySize, booking.partySize),
        )
        MoneyRow(booking, arabic)
        booking.host?.let { host ->
            val phone = host.phone?.takeIf { it.isNotBlank() }?.let(BahrFormat::ltr)
            val name = host.name?.takeIf { it.isNotBlank() }
            val value =
                when {
                    name != null && phone != null -> stringResource(Res.string.ticket_value_dot, name, phone)
                    else -> name ?: phone
                }
            value?.let { TicketRow(BahrIcons.Person, stringResource(Res.string.ticket_row_host), it) }
        }
    }
}

/** "05:00 — Cairo, Abdel Moneim Riad": the time (left to right), then the place; either alone if the other is missing. */
@Composable
private fun departureValue(booking: BookingDto): String? {
    val departure = booking.departure ?: return null
    val time = departure.timeLocal?.takeIf { it.isNotBlank() }?.let(BahrFormat::ltr)
    val placeName = departure.placeName?.takeIf { it.isNotBlank() }
    val city = departure.city?.takeIf { it.isNotBlank() }
    val place =
        when {
            city != null && placeName != null -> stringResource(Res.string.ticket_value_comma, city, placeName)
            else -> placeName ?: city
        }
    return when {
        time != null && place != null -> stringResource(Res.string.ticket_value_dash, time, place)
        else -> time ?: place
    }
}

/**
 * The money row: "Paid  900 EGP · Vodafone Cash" (with the Test badge for test cash, MV-M4), "To pay the host" on a
 * host's own unpaid booking, else "Total". The amount is the server's total; `Booking.paid` is not served.
 */
@Composable
private fun MoneyRow(
    booking: BookingDto,
    arabic: Boolean,
) {
    val payment = booking.ticketPayment
    val amount = BahrFormat.money(booking.total.amount, booking.total.currencyCode, arabic)
    val method = PaymentMethod.of(booking.method)
    val key =
        when (payment) {
            TicketPayment.Paid -> Res.string.ticket_row_paid
            TicketPayment.OwedToHost -> Res.string.ticket_row_owed_to_host
            TicketPayment.Unpaid -> Res.string.booking_total
        }
    val value =
        if (payment == TicketPayment.Paid && method != null) {
            stringResource(Res.string.ticket_value_dot, amount, stringResource(method.paidName))
        } else {
            amount
        }
    TicketRow(
        icon = BahrIcons.Payments,
        key = stringResource(key),
        value = value,
        valueTag = TICKET_MONEY_TAG,
        // A test booking says so where it is shown: no money moved for it.
        badge = if (method == PaymentMethod.TestCash) stringResource(Res.string.booking_method_test_badge) else null,
    )
}

/**
 * One row: the icon in its disc, the key as an overline, the value bold at the end. The handoff draws the rows on
 * `surfaceLow` over a white page; the app's page has been that off-white since M4-M12, so the rows are `surface`
 * (as cards on the page are) and the icon discs take `surfaceLow`, keeping the handoff's two steps apart.
 */
@Composable
private fun TicketRow(
    icon: BahrIcons,
    key: String,
    value: String,
    valueTag: String? = null,
    badge: String? = null,
) {
    val x = BahrTheme.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = BahrSpacing.md, vertical = BahrSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        Box(
            modifier = Modifier.size(BahrSize.listDisc).clip(BahrTheme.shapes.full).background(x.surfaceLow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon.outlined(),
                // The key next to it says what the row is.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(BahrSize.iconMedium),
            )
        }
        Text(
            text = key.overlineCase(BahrTheme.locale.isArabic),
            style = BahrTheme.type.overline,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The value takes the rest of the row and sits at its end (the handoff's `margin-inline-start: auto`).
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).then(if (valueTag != null) Modifier.testTag(valueTag) else Modifier),
        )
        badge?.let { BahrBadge(props = BahrBadge.Props(it, BahrBadge.Tone.Quaternary)) }
    }
}

/** The gold "Where to stand" panel: the host's meeting instructions, as served (`departure.whereToStand`). */
@Composable
private fun WhereToStand(text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.extraLarge)
                .background(scheme.secondaryContainer)
                .padding(horizontal = BahrSpacing.lg, vertical = BahrSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        Icon(
            imageVector = BahrIcons.PinDrop.filled(),
            contentDescription = null,
            tint = BahrTheme.colors.secondaryDim,
            modifier = Modifier.size(BahrSize.iconLarge),
        )
        Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
            Text(
                text = stringResource(Res.string.ticket_where_to_stand),
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSecondaryContainer,
            )
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSecondaryContainer)
        }
    }
}

/**
 * The host's WhatsApp (coral: the one step forward here) when the server sent the link; then after paying the way
 * back to the trips, or on a booking's details "Rate your trip" (when it may be rated) and the trip's page; and on a
 * confirmed ticket the note to take a screenshot.
 */
@Composable
private fun TicketActions(
    booking: BookingDto,
    ticket: TicketState,
    entry: TicketEntry,
    canRate: Boolean,
    actions: TicketCallbacks,
) {
    Column(
        modifier = Modifier.padding(vertical = BahrSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        val host = booking.host
        val whatsapp = host?.whatsappUrl?.takeIf { it.isNotBlank() }
        if (whatsapp != null) {
            val uriHandler = LocalUriHandler.current
            val name = host.name?.takeIf { it.isNotBlank() }
            BahrPrimaryButton(
                text =
                    if (name != null) {
                        stringResource(Res.string.ticket_whatsapp_host, name)
                    } else {
                        stringResource(Res.string.ticket_whatsapp_host_unnamed)
                    },
                onClick = { uriHandler.openUri(whatsapp) },
                leadingIcon = BahrIcons.Chat.outlined(),
                modifier = Modifier.fillMaxWidth().testTag(TICKET_WHATSAPP_TAG),
            )
        }
        when (entry) {
            TicketEntry.Paid -> SecondaryButton(BahrIcons.Sailing, stringResource(Res.string.trips_title), actions.onDone)
            TicketEntry.Details -> {
                if (canRate) {
                    SecondaryButton(
                        icon = BahrIcons.RateReview,
                        text = stringResource(Res.string.mytrips_rate_trip),
                        onClick = { actions.onRateTrip(booking.ref, booking.trip?.title) },
                        tag = TICKET_RATE_TAG,
                    )
                }
                // The trip's page needs its slug; a booking served without one simply has no way there.
                booking.trip?.slug?.takeIf { it.isNotBlank() }?.let { slug ->
                    SecondaryButton(
                        icon = BahrIcons.Sailing,
                        text = stringResource(Res.string.ticket_view_trip),
                        onClick = { actions.onViewTrip(slug) },
                        tag = TICKET_VIEW_TRIP_TAG,
                    )
                }
            }
        }
        if (ticket == TicketState.Confirmed) {
            Text(
                text = stringResource(Res.string.ticket_screenshot_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * A way on from the ticket (back to the trips, the trip's page, the review page) on `primaryContainer` (the handoff's
 * second action's look): never coral, so the WhatsApp button stays the screen's one coral action.
 */
@Composable
private fun SecondaryButton(
    icon: BahrIcons,
    text: String,
    onClick: () -> Unit,
    tag: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = BahrSize.touchTarget)
                .clip(BahrTheme.shapes.full)
                .background(scheme.primaryContainer)
                .clickable(role = Role.Button, onClick = onClick)
                .then(if (tag != null) Modifier.testTag(tag) else Modifier)
                .padding(horizontal = BahrSpacing.xl, vertical = BahrSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon.outlined(),
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(BahrSize.iconMedium),
        )
        Text(text = text, style = MaterialTheme.typography.titleSmall, color = scheme.primary)
    }
}

/**
 * The ticket's bar (MV-M6): the round back button and what the page is, "Booking confirmation" after paying or
 * "Booking details" from My trips. On the teal header ([onHero]) its title is white; on the page's ground (the error
 * view, which has no header) it is the ground's text colour. The title is the page's heading for screen readers.
 */
@Composable
private fun TicketBar(
    entry: TicketEntry,
    onBack: () -> Unit,
    onHero: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = BahrSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        BahrBackButton(modifier = Modifier.testTag(TICKET_BACK_TAG), props = BahrBackButton.Props(onClick = onBack))
        Text(
            text = stringResource(entry.title),
            style = MaterialTheme.typography.titleMedium,
            color = if (onHero) BahrTheme.colors.onHero else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
    }
}

private val TicketEntry.title: StringResource
    get() =
        when (this) {
            TicketEntry.Paid -> Res.string.ticket_title_confirmation
            TicketEntry.Details -> Res.string.ticket_title_details
        }

/**
 * While the booking is read (M3-M14): the header's ground with the reference's place shimmering, then the rows'
 * places. The status is not known yet, so no header text is drawn. The bar (MV-M6) sits outside the skeleton's
 * region: [BahrSkeleton] clears the semantics inside it, and the back button must stay reachable by screen readers.
 */
@Composable
private fun TicketSkeleton(
    entry: TicketEntry,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(BahrSpacing.gutter)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(BahrTheme.shapes.header)
                    .heroBackground()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = BahrSpacing.gutter)
                    .padding(top = BahrSpacing.sm, bottom = BahrSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
        ) {
            // The bar is drawn as it will be: the way back works while the booking is read.
            TicketBar(entry, onBack, onHero = true)
            // The one "Loading" for screen readers; the row places below carry no semantics of their own.
            BahrSkeleton(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BahrSkeletonBlock(Modifier.size(BahrSize.stateDisc), BahrSkeletonBlock.Props(BahrTheme.shapes.full))
                BahrSkeletonLine(props = BahrSkeletonLine.Props(widthFraction = SKELETON_OVERLINE_WIDTH))
                BahrSkeletonLine(props = BahrSkeletonLine.Props(widthFraction = SKELETON_REF_WIDTH))
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = BahrSpacing.gutter),
            verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
        ) {
            repeat(SKELETON_ROWS) {
                // About a loaded row's height (its disc plus padding), so the rows do not jump in.
                BahrSkeletonBlock(Modifier.fillMaxWidth().height(BahrSize.formField), BahrSkeletonBlock.Props(BahrTheme.shapes.large))
            }
        }
    }
}

/** The line under the reference: "show this screen to the host" on a ticket, else what became of the booking. */
@Composable
private fun ticketNote(
    booking: BookingDto,
    ticket: TicketState,
): String? =
    when (ticket) {
        TicketState.Confirmed ->
            booking.host
                ?.name
                ?.takeIf { it.isNotBlank() }
                ?.let { stringResource(Res.string.ticket_show_host, it) }
                ?: stringResource(Res.string.ticket_show_host_unnamed)
        TicketState.Completed -> stringResource(Res.string.ticket_note_completed)
        TicketState.CalledOff -> stringResource(Res.string.ticket_note_called_off)
        TicketState.Cancelled -> stringResource(Res.string.ticket_note_cancelled)
        TicketState.Expired -> stringResource(Res.string.ticket_note_expired)
        TicketState.Unpaid -> stringResource(Res.string.ticket_note_unpaid)
        TicketState.Unknown -> null
    }

private val TicketState.overline: StringResource
    get() =
        when (this) {
            TicketState.Confirmed -> Res.string.ticket_overline_confirmed
            TicketState.Completed -> Res.string.ticket_overline_completed
            TicketState.CalledOff -> Res.string.ticket_overline_called_off
            TicketState.Cancelled -> Res.string.ticket_overline_cancelled
            TicketState.Expired -> Res.string.ticket_overline_expired
            TicketState.Unpaid -> Res.string.ticket_overline_unpaid
            TicketState.Unknown -> Res.string.ticket_overline_unknown
        }

private val TicketState.icon: BahrIcons
    get() =
        when (this) {
            TicketState.Confirmed, TicketState.Completed -> BahrIcons.CheckCircle
            TicketState.CalledOff, TicketState.Cancelled -> BahrIcons.EventBusy
            TicketState.Expired -> BahrIcons.HourglassTop
            TicketState.Unpaid -> BahrIcons.Timer
            TicketState.Unknown -> BahrIcons.ConfirmationNumber
        }

/** The method's name on a paid booking: "Vodafone Cash / Card / Test cash". */
private val PaymentMethod.paidName: StringResource
    get() =
        when (this) {
            PaymentMethod.VodafoneCash -> Res.string.booking_method_vf
            PaymentMethod.Card -> Res.string.booking_method_card
            PaymentMethod.TestCash -> Res.string.booking_method_test_cash
        }

/** A bus still to catch: where to stand is worth showing. */
private val BUS_AHEAD = setOf(TicketState.Confirmed, TicketState.Unpaid)

/** For tests: the reference, the line under it, the money row's value and the WhatsApp button. */
internal const val TICKET_REF_TAG = "ticket_ref"
internal const val TICKET_NOTE_TAG = "ticket_note"
internal const val TICKET_MONEY_TAG = "ticket_money"
internal const val TICKET_WHATSAPP_TAG = "ticket_whatsapp"

/** For tests (MV-M6): the bar's back button, the details' "View trip" and "Rate your trip". */
internal const val TICKET_BACK_TAG = "ticket_back"
internal const val TICKET_VIEW_TRIP_TAG = "ticket_view_trip"
internal const val TICKET_RATE_TAG = "ticket_rate"

// The handoff's `linear-gradient(160deg, primary, teal-50 60%, teal-60)`.
private const val GRADIENT_ANGLE_DEGREES = 160.0
private const val HALF_TURN_DEGREES = 180.0
private const val GRADIENT_MID_STOP = .6f

// The handoff's 200px disc, 60px above and 40px past the header's top end corner, as shares of a 390px-wide header,
// so it scales with the screen. White at 4%, not the handoff's 12%: on a wide screen it reaches the overline, and at
// 4% white text over it still reads 4.6:1 or better in every theme (see BahrExtendedColors.heroStart; MV-M5 review #1).
private const val HERO_BUBBLE_ALPHA = .04f
private const val BUBBLE_RADIUS_SHARE = .256f
private const val BUBBLE_CENTER_INSET_SHARE = .154f
private const val BUBBLE_CENTER_TOP_SHARE = .103f

// The skeleton: the overline's and the reference's widths as shares of the header, and how many rows it draws.
private const val SKELETON_OVERLINE_WIDTH = .4f
private const val SKELETON_REF_WIDTH = .7f
private const val SKELETON_ROWS = 5
