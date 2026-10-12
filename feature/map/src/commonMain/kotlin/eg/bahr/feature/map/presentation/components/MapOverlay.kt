package eg.bahr.feature.map.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import eg.bahr.core.catalog.presentation.components.seatsLeft
import eg.bahr.core.designsystem.components.BahrBadge
import eg.bahr.core.designsystem.components.BahrCard
import eg.bahr.core.designsystem.components.SeatBadge
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrBorder
import eg.bahr.core.designsystem.theme.BahrElevation
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrShadow
import eg.bahr.core.designsystem.theme.reducedMotion
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.departure_sold_out
import eg.bahr.core.localization.generated.resources.format_pair
import eg.bahr.core.localization.generated.resources.map_card_bus_time
import eg.bahr.core.localization.generated.resources.map_card_close
import eg.bahr.core.localization.generated.resources.map_view_trip
import eg.bahr.core.localization.generated.resources.trip_fact_distance
import eg.bahr.core.localization.generated.resources.trip_nights_badge
import eg.bahr.core.localization.generated.resources.trip_per_person
import eg.bahr.feature.map.model.MapPinDto
import eg.bahr.feature.map.presentation.TripMapUiState
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * What is drawn over the bottom of the map: the legend, or, when a pin is tapped, its card. The card
 * slides in and out, and between pins ([cardTransition], M4-M9). (The prototype's toggles over the
 * map, "Show the drive" and "All trips", were removed by user decision 2026-10-12.)
 *
 * [onBottomSize] reports each card's height in pixels with the pin whose card it is (null for the
 * legend): the map keeps its camera and Google's logo clear of it.
 */
@Composable
internal fun MapOverlay(
    state: TripMapUiState,
    onCloseCard: () -> Unit,
    onTripClick: (String) -> Unit,
    onBottomSize: (height: Int, forSlug: String?) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        val selected = state.selectedPin
        val reduced = BahrTheme.reducedMotion
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        AnimatedContent(
            targetState = selected,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            transitionSpec = { cardTransition(reduced, rtl) },
            contentAlignment = Alignment.BottomStart,
            contentKey = { it?.slug },
            label = "pinCard",
        ) { pin ->
            // Each content's own height, by the pin it shows (null: the legend), as soon as it is
            // measured: the screen looks up the current selection's, so the map's padding changes once
            // (not frame by frame as the box grows), and quick taps cannot leave a stale height behind
            // (M4-M9).
            Box(Modifier.onSizeChanged { onBottomSize(it.height, pin?.slug) }) {
                if (pin != null) {
                    PinCard(pin = pin, onClick = { onTripClick(pin.slug) }, onClose = onCloseCard)
                } else {
                    MapLegend(state)
                }
            }
        }
    }
}

/**
 * How the bottom of the map changes (M4-M9, user request 2026-10-11), on the M4-M6 tokens
 * ([BahrMotion.Medium], [BahrMotion.Standard]):
 * - a pin picked: its card slides up from the panel's bottom edge, fading in, as the legend fades;
 * - another pin picked: the card's content slides a short way ([BahrMotion.CardSlideFraction]) in from
 *   the new pin's side of the old one, fading, and the old content out the other way;
 * - the card closed: it slides down out of the panel, fading, as the legend fades back in.
 *
 * The space the card takes grows or shrinks with it and is not clipped, so the card is seen sliding,
 * not uncovered. Under reduce motion everything changes at once.
 */
private fun AnimatedContentTransitionScope<MapPinDto?>.cardTransition(
    reduced: Boolean,
    rtl: Boolean,
): ContentTransform {
    if (reduced) return EnterTransition.None togetherWith ExitTransition.None using SizeTransform(clip = false) { _, _ -> snap() }
    val slide = tween<IntOffset>(BahrMotion.Medium, easing = BahrMotion.Standard)
    val fade = tween<Float>(BahrMotion.Medium, easing = BahrMotion.Standard)
    val from = initialState
    val to = targetState
    val change =
        when {
            from == null && to != null -> (slideInVertically(slide) { it } + fadeIn(fade)) togetherWith fadeOut(fade)
            from != null && to == null -> fadeIn(fade) togetherWith (slideOutVertically(slide) { it } + fadeOut(fade))
            from != null && to != null -> {
                val side = cardEntrySide(from, to, rtl)
                (slideInHorizontally(slide) { side * (it * BahrMotion.CardSlideFraction).roundToInt() } + fadeIn(fade)) togetherWith
                    (slideOutHorizontally(slide) { -side * (it * BahrMotion.CardSlideFraction).roundToInt() } + fadeOut(fade))
            }
            else -> fadeIn(fade) togetherWith fadeOut(fade)
        }
    return change using SizeTransform(clip = false) { _, _ -> tween(BahrMotion.Medium, easing = BahrMotion.Standard) }
}

/**
 * Which side the new pin's card comes in from, as a sign on the x axis: +1 from the right, -1 from the
 * left. Geographic, the same in both languages (PLAN M4-M9, clarified 2026-10-11): a pin east of the
 * old one brings its card in from the right, a pin west of it from the left, in English and in Arabic
 * alike, because the map is not mirrored in Arabic and cannot be rotated. Right-to-left only breaks a
 * tie (two pins on the same meridian): the card then comes in from the end side, as a step forward in
 * the reading direction does: from the right in English, from the left in Arabic.
 */
internal fun cardEntrySide(
    from: MapPinDto,
    to: MapPinDto,
    rtl: Boolean,
): Int =
    when {
        to.lng > from.lng -> 1
        to.lng < from.lng -> -1
        rtl -> -1
        else -> 1
    }

/**
 * What the pin colours mean: each category in the legend with its tone, and "Sold out" when a pin is
 * grey. Chips that wrap, on the page colour slightly see-through (map.html `.legend`).
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
 * on a multi-day trip, the next date with its seats (or "Sold out"), the bus time and the road
 * distance, and "View trip". The whole card opens the trip; the × closes it.
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
    val seats = next.seatsLeft()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
        next.date?.let {
            Text(BahrFormat.date(it), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        seats?.let { SeatBadge(left = it) }
    }
}

/** "Bus leaves at 05:00 · 150 km": this pin's own bus time and road distance, whichever are known. */
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
