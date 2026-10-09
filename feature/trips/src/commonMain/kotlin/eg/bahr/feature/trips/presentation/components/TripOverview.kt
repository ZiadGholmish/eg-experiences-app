package eg.bahr.feature.trips.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.overlineCase
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.format_pair
import eg.bahr.core.localization.generated.resources.format_rating_count
import eg.bahr.core.localization.generated.resources.trip_fact_bus
import eg.bahr.core.localization.generated.resources.trip_fact_bus_be_there
import eg.bahr.core.localization.generated.resources.trip_fact_day
import eg.bahr.core.localization.generated.resources.trip_fact_distance
import eg.bahr.core.localization.generated.resources.trip_fact_place
import eg.bahr.core.localization.generated.resources.trip_fact_price
import eg.bahr.core.localization.generated.resources.trip_fact_price_note
import eg.bahr.core.localization.generated.resources.trip_hours_door_to_door
import eg.bahr.core.localization.generated.resources.trip_whats_included
import eg.bahr.core.network.MoneyDto
import eg.bahr.feature.trips.model.RatingDto
import eg.bahr.feature.trips.model.TripDetailDto
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What the trip page can show before the full trip arrives: the list card's fields. The handoff's
 * loading state renders exactly these as text (location chip, title, price) while the rest shimmers.
 */
internal data class TripHeader(
    val title: String,
    val subtitle: String?,
    val price: MoneyDto,
    val rating: RatingDto?,
)

/** Eyebrow row (location chip + rating), title and deck (HANDOFF screen 3, items 2–3). */
@Composable
internal fun TripTitleBlock(
    header: TripHeader,
    deck: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            header.subtitle?.let { LocationChip(it) }
            Spacer(Modifier.weight(1f))
            header.rating?.value?.let { value -> RatingLabel(value, header.rating.count) }
        }
        Text(text = header.title, style = MaterialTheme.typography.headlineMedium)
        deck?.let {
            Text(text = it, style = MaterialTheme.typography.bodyLarge, color = BahrTheme.colors.onSurfaceSecondary)
        }
    }
}

@Composable
private fun LocationChip(text: String) {
    Row(
        modifier =
            Modifier
                .clip(BahrTheme.shapes.full)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = BahrSpacing.md, vertical = BahrSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        Icon(
            imageVector = BahrIcons.Place.filled(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(BahrSize.iconSmall),
        )
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}

/** "★ 4.8 (37)". The count is left off when the server has none. */
@Composable
private fun RatingLabel(
    value: Double,
    count: Int?,
) {
    val rating = BahrFormat.rating(value)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
        Icon(
            imageVector = BahrIcons.Star.filled(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(BahrSize.iconSmall),
        )
        Text(
            text = if (count != null) stringResource(Res.string.format_rating_count, rating, count) else rating,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** One cell of the facts grid. */
private data class Fact(
    val tint: Tint,
    val icon: BahrIcons,
    val label: String,
    val value: String,
    val note: String?,
)

/**
 * The 2×2 facts grid (item 4), one tint per cell. Departure and destination are separate cells:
 * that is how the page answers "where does it leave from, and where is it" (the handoff's
 * two-locations problem). A cell whose data the server did not send is left out.
 */
@Composable
internal fun TripFacts(
    trip: TripDetailDto,
    modifier: Modifier = Modifier,
) {
    val arabic = BahrTheme.locale.isArabic
    val facts =
        buildList {
            add(
                Fact(
                    Tint.Teal,
                    BahrIcons.Payments,
                    stringResource(Res.string.trip_fact_price),
                    BahrFormat.money(trip.price.amount, trip.price.currencyCode, arabic),
                    stringResource(Res.string.trip_fact_price_note),
                ),
            )
            if (trip.durationLabel.isNotBlank()) {
                val hours = trip.durationMinutes?.takeIf { it > 0 && it % MINUTES_PER_HOUR == 0 }?.div(MINUTES_PER_HOUR)
                add(
                    Fact(
                        Tint.Gold,
                        BahrIcons.Schedule,
                        stringResource(Res.string.trip_fact_day),
                        // Times stay LTR inside Arabic (mobile rule 2), as on the trip card.
                        BahrFormat.ltr(trip.durationLabel),
                        hours?.let { pluralStringResource(Res.plurals.trip_hours_door_to_door, it, it) },
                    ),
                )
            }
            trip.departure?.let { from ->
                val place = from.placeName ?: from.city
                if (place != null) {
                    val beThere = from.arriveBy?.let { stringResource(Res.string.trip_fact_bus_be_there, it) }
                    add(
                        Fact(
                            Tint.Coral,
                            BahrIcons.DirectionsBus,
                            stringResource(Res.string.trip_fact_bus),
                            place,
                            pair(from.city.takeIf { from.placeName != null }, beThere),
                        ),
                    )
                }
            }
            trip.destination?.let { at ->
                val place = at.placeName ?: at.city
                if (place != null) {
                    val distance = at.distanceKm?.let { stringResource(Res.string.trip_fact_distance, it) }
                    add(
                        Fact(
                            Tint.Magenta,
                            BahrIcons.Place,
                            stringResource(Res.string.trip_fact_place),
                            place,
                            pair(at.governorate, distance),
                        ),
                    )
                }
            }
        }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
        facts.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
            ) {
                row.forEach { FactCell(it, Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** "a · b", either part optional. */
@Composable
private fun pair(
    first: String?,
    second: String?,
): String? =
    when {
        first != null && second != null -> stringResource(Res.string.format_pair, first, second)
        else -> first ?: second
    }

@Composable
private fun FactCell(
    fact: Fact,
    modifier: Modifier = Modifier,
) {
    val colors = fact.tint.colors()
    Column(
        modifier =
            modifier
                .clip(BahrTheme.shapes.extraLarge)
                .background(colors.container)
                .padding(BahrSpacing.md),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        Icon(
            imageVector = fact.icon.filled(),
            contentDescription = null,
            tint = colors.icon,
            modifier = Modifier.size(BahrSize.iconLarge),
        )
        Text(
            text = fact.label.overlineCase(BahrTheme.locale.isArabic),
            style = BahrTheme.type.overline,
            color = colors.content,
        )
        Text(text = fact.value, style = MaterialTheme.typography.labelLarge, color = colors.content)
        fact.note?.let { Text(text = it, style = MaterialTheme.typography.labelMedium, color = colors.content) }
    }
}

/** While the trip loads: the grid's shape, so nothing below it jumps when it arrives. */
@Composable
internal fun TripFactsSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
                repeat(2) {
                    Skeleton(
                        Modifier
                            .weight(1f)
                            .aspectRatio(FactSkeletonRatio)
                            .clip(BahrTheme.shapes.extraLarge),
                    )
                }
            }
        }
    }
}

/** What's included (item 5): a green check per included line, a grey cross per excluded one. */
@Composable
internal fun TripInclusions(
    included: List<String>,
    excluded: List<String>,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    val x = BahrTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
        SectionTitle(stringResource(Res.string.trip_whats_included))
        included.forEach { InclusionRow(it, BahrIcons.Check, x.successContainer, x.success, c.onSurface) }
        // An excluded line is shown, not dropped: knowing lunch is *not* included is exactly as
        // useful as knowing it is.
        excluded.forEach { InclusionRow(it, BahrIcons.Close, c.surfaceContainer, x.onSurfaceDisabled, x.onSurfaceDisabled) }
    }
}

@Composable
private fun InclusionRow(
    text: String,
    icon: BahrIcons,
    container: Color,
    iconColor: Color,
    textColor: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
        Marker(icon.outlined(), container, iconColor)
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = textColor, modifier = Modifier.fillMaxWidth())
    }
}

/** Skeleton text lines, for the deck and the itinerary while they load. */
@Composable
internal fun TextSkeleton(
    lines: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
        repeat(lines) { line ->
            // The last line is shorter, as a paragraph's is.
            val width = if (line == lines - 1) LAST_LINE_FRACTION else 1f
            Skeleton(
                Modifier
                    .fillMaxWidth(width)
                    .height(BahrSize.skeletonLine)
                    .clip(BahrTheme.shapes.extraSmall),
            )
        }
    }
}

private const val MINUTES_PER_HOUR = 60
private const val LAST_LINE_FRACTION = .6f

/** The prototype's 84px-tall skeleton cell in a half-width column (≈176px). */
private const val FactSkeletonRatio = 176f / 84f
