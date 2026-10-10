package eg.bahr.feature.map.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import eg.bahr.core.designsystem.components.BahrBadge
import eg.bahr.core.designsystem.components.BahrCard
import eg.bahr.core.designsystem.components.SeatBadge
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrBorder
import eg.bahr.core.designsystem.theme.BahrElevation
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrShadow
import eg.bahr.core.designsystem.theme.bahrTween
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.departure_sold_out
import eg.bahr.core.localization.generated.resources.format_pair
import eg.bahr.core.localization.generated.resources.map_all_trips
import eg.bahr.core.localization.generated.resources.map_card_bus_time
import eg.bahr.core.localization.generated.resources.map_card_close
import eg.bahr.core.localization.generated.resources.map_legend_departure
import eg.bahr.core.localization.generated.resources.map_view_trip
import eg.bahr.core.localization.generated.resources.trip_fact_distance
import eg.bahr.core.localization.generated.resources.trip_nights_badge
import eg.bahr.core.localization.generated.resources.trip_per_person
import eg.bahr.feature.map.model.MapPinDto
import eg.bahr.feature.map.presentation.TripMapUiState
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Everything drawn over the map: the two toggles at the top (map.html `.ui`: "All trips" and "Show
 * the drive from Cairo", the pressed one filled), and at the bottom either the legend or, when a pin
 * is tapped, its card. The legend and card swap with a crossfade (instant under reduce motion).
 *
 * [onTopSize] and [onBottomSize] report the chrome's heights in pixels: the map keeps its camera and
 * Google's logo clear of them.
 */
@Composable
internal fun MapOverlay(
    state: TripMapUiState,
    driveLabel: String,
    onAllTrips: () -> Unit,
    onToggleDrive: () -> Unit,
    onCloseCard: () -> Unit,
    onTripClick: (String) -> Unit,
    onTopSize: (Int) -> Unit,
    onBottomSize: (Int) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .onSizeChanged { onTopSize(it.height) }
                    .padding(BahrSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
        ) {
            MapToggle(text = stringResource(Res.string.map_all_trips), selected = !state.showsDrive, onClick = onAllTrips)
            if (state.map?.departurePoints?.isNotEmpty() == true) {
                MapToggle(text = driveLabel, selected = state.showsDrive, onClick = onToggleDrive)
            }
        }

        val selected = state.selectedPin
        val fade = bahrTween<Float>()
        AnimatedContent(
            targetState = selected,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { onBottomSize(it.height) },
            transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
            contentAlignment = Alignment.BottomStart,
            contentKey = { it?.slug },
        ) { pin ->
            if (pin != null) {
                PinCard(pin = pin, onClick = { onTripClick(pin.slug) }, onClose = onCloseCard)
            } else {
                MapLegend(state)
            }
        }
    }
}

/** One of the two toggles: filled when it is the current view. */
@Composable
private fun MapToggle(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val shape = BahrTheme.shapes.full
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) c.onPrimary else c.primary,
        maxLines = 1,
        modifier =
            Modifier
                .bahrShadow(BahrElevation.Float, shape, BahrTheme.colors)
                .clip(shape)
                .background(if (selected) c.primary else c.surface)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .heightIn(min = BahrSpacing.minTouch)
                .padding(horizontal = BahrSpacing.lg, vertical = BahrSpacing.md),
    )
}

/**
 * What the pin colours mean: each category in the legend with its tone, "Sold out" when a pin is
 * grey, and the dark bus marker ("Bus departure", this app's own entry; the contract's legend lists
 * categories only). Chips that wrap, on the page colour slightly see-through (map.html `.legend`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapLegend(state: TripMapUiState) {
    val data = state.map ?: return
    val palette = rememberMapPalette()
    val shape = BahrTheme.shapes.medium
    FlowRow(
        modifier =
            Modifier
                .padding(BahrSpacing.md)
                .bahrShadow(BahrElevation.Float, shape, BahrTheme.colors)
                .clip(shape)
                .background(BahrTheme.colors.surfaceTranslucent)
                .padding(horizontal = BahrSpacing.md, vertical = BahrSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        data.legend.forEach { LegendEntry(it.label, palette.tone(it.tone)) }
        if (data.pins.any { it.isSoldOut }) LegendEntry(stringResource(Res.string.departure_sold_out), palette.soldOut)
        if (data.departurePoints.isNotEmpty()) LegendEntry(stringResource(Res.string.map_legend_departure), palette.departure)
    }
}

/** A dot in the pins' fill, outlined like the pin when it has one (the pale sold-out grey). */
@Composable
private fun LegendEntry(
    label: String,
    colors: PinColors,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
        val dot = Modifier.size(BahrSize.legendDot).clip(CircleShape).background(colors.fill)
        Box(colors.outline?.let { dot.border(BahrBorder.hairline, it, CircleShape) } ?: dot)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * A tapped pin's card (map.html's popup): title and place, the price per person with a nights badge
 * on a multi-day trip, the next date with its seats (or "Sold out"), the bus time and the drive's
 * length, and "View trip". The whole card opens the trip; the × closes it.
 */
@Composable
private fun PinCard(
    pin: MapPinDto,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    BahrCard(modifier = Modifier.fillMaxWidth().padding(BahrSpacing.md), onClick = onClick) {
        Column(
            modifier = Modifier.padding(start = BahrSpacing.lg, top = BahrSpacing.sm, end = BahrSpacing.xs, bottom = BahrSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(top = BahrSpacing.sm)) {
                    Text(pin.title, style = MaterialTheme.typography.titleMedium, maxLines = TITLE_LINES, overflow = TextOverflow.Ellipsis)
                    (pin.subtitle ?: pin.placeName)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.Top)) {
                    Icon(
                        BahrIcons.Close.outlined(),
                        contentDescription = stringResource(Res.string.map_card_close),
                        tint = c.onSurfaceVariant,
                        modifier = Modifier.size(BahrSize.iconMedium),
                    )
                }
            }
            Column(Modifier.padding(end = BahrSpacing.md), verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
                PriceRow(pin)
                DateRow(pin)
                BusRow(pin)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    ViewTripPill(onClick)
                }
            }
        }
    }
}

@Composable
private fun PriceRow(pin: MapPinDto) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
        Text(
            text = BahrFormat.money(pin.price.amount, pin.price.currencyCode, BahrTheme.locale.isArabic),
            style = BahrTheme.type.price,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = stringResource(Res.string.trip_per_person),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).alignByBaseline(),
        )
        if (pin.nights > 0) {
            BahrBadge(
                props =
                    BahrBadge.Props(
                        text = pluralStringResource(Res.plurals.trip_nights_badge, pin.nights, pin.nights),
                        tone = BahrBadge.Tone.Secondary,
                    ),
            )
        }
    }
}

/**
 * The next date and its seats; nothing with no open date. A full date is `SeatBadge(0)`, which is the
 * one place "Sold out" is drawn; with no seat count at all, just the date.
 */
@Composable
private fun DateRow(pin: MapPinDto) {
    val next = pin.nextDeparture ?: return
    val seats = if (next.soldOut == true) 0 else next.seatsRemaining
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
        next.date?.let {
            Text(BahrFormat.date(it), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        seats?.let { SeatBadge(left = it) }
    }
}

/** "Bus leaves at 05:00 · 150 km": this pin's own bus time and drive, whichever are known. */
@Composable
private fun BusRow(pin: MapPinDto) {
    val time = pin.departTime?.let { stringResource(Res.string.map_card_bus_time, BahrFormat.ltr(it)) }
    val distance = pin.distanceKm?.let { stringResource(Res.string.trip_fact_distance, it) }
    val text =
        when {
            time != null && distance != null -> stringResource(Res.string.format_pair, time, distance)
            else -> time ?: distance
        } ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
        Icon(
            BahrIcons.DirectionsBus.outlined(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(BahrSize.iconSmall),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** map.html's "View trip →" (`.pp`): teal, not coral; the card is the tap target, this says so. */
@Composable
private fun ViewTripPill(onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .clip(BahrTheme.shapes.full)
                .background(c.primary)
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = BahrSpacing.minTouch)
                .padding(horizontal = BahrSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        Text(stringResource(Res.string.map_view_trip), style = MaterialTheme.typography.labelLarge, color = c.onPrimary)
        Icon(BahrIcons.ArrowForward.outlined(), contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(BahrSize.iconSmall))
    }
}

private const val TITLE_LINES = 2
