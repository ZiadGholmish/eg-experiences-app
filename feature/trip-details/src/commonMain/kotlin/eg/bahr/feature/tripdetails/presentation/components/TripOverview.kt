package eg.bahr.feature.tripdetails.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.testTag
import eg.bahr.core.catalog.model.RatingDto
import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.catalog.presentation.components.SectionTitle
import eg.bahr.core.catalog.presentation.components.Tint
import eg.bahr.core.catalog.presentation.components.colors
import eg.bahr.core.designsystem.components.BahrSkeletonCircle
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrShimmer
import eg.bahr.core.designsystem.theme.overlineCase
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.format_pair
import eg.bahr.core.localization.generated.resources.format_rating_count
import eg.bahr.core.localization.generated.resources.trip_fact_bus
import eg.bahr.core.localization.generated.resources.trip_fact_bus_be_there
import eg.bahr.core.localization.generated.resources.trip_fact_day
import eg.bahr.core.localization.generated.resources.trip_fact_distance
import eg.bahr.core.localization.generated.resources.trip_fact_duration
import eg.bahr.core.localization.generated.resources.trip_fact_place
import eg.bahr.core.localization.generated.resources.trip_fact_price
import eg.bahr.core.localization.generated.resources.trip_fact_price_note
import eg.bahr.core.localization.generated.resources.trip_hours_door_to_door
import eg.bahr.core.localization.generated.resources.trip_whats_included
import eg.bahr.core.network.MoneyDto
import eg.bahr.feature.tripdetails.model.DeparturePointDto
import eg.bahr.feature.tripdetails.model.DestinationDto
import eg.bahr.feature.tripdetails.model.TripDetailDto
import eg.bahr.feature.tripdetails.model.isMultiDay
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

/**
 * Eyebrow row (location chip + rating), title and deck (HANDOFF screen 3, items 2–3).
 *
 * While the trip loads (MV-M7) the known part stays text: the list card's chip, rating and title. What is not known yet
 * shimmers in the room it will take: the deck ([deckPending], [DECK_SKELETON_LINES] lines of its own style), and on a
 * cold open with no card ([header] null) the chip and the title too. The price is not repeated under the title, as the
 * prototype's skeleton did: it would vanish when the trip lands and pull everything under it up. The price is in the
 * facts grid and the sticky bar from the first frame instead.
 */
@Composable
internal fun TripTitleBlock(
    header: TripHeader?,
    deck: String?,
    deckPending: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                header == null -> LocationChip(text = null)
                header.subtitle != null -> LocationChip(header.subtitle)
            }
            Spacer(Modifier.weight(1f))
            header?.rating?.value?.let { value -> RatingLabel(value, header.rating.count) }
        }
        val titleStyle = MaterialTheme.typography.headlineMedium
        if (header != null) {
            Text(text = header.title, style = titleStyle, modifier = Modifier.testTag(TRIP_TITLE_TAG))
        } else {
            LinesSkeleton(titleStyle, Modifier.testTag(TRIP_TITLE_TAG), widthFraction = TITLE_SKELETON_FRACTION)
        }
        val deckStyle = MaterialTheme.typography.bodyLarge
        when {
            deck != null -> Text(text = deck, style = deckStyle, color = BahrTheme.colors.onSurfaceSecondary)
            deckPending -> LinesSkeleton(deckStyle, lines = DECK_SKELETON_LINES)
        }
    }
}

/** The location chip; [text] null draws the chip's own shape and height, shimmering (a cold open: no card yet). */
@Composable
private fun LocationChip(text: String?) {
    val shape = BahrTheme.shapes.full
    val ground =
        if (text != null) {
            Modifier.background(MaterialTheme.colorScheme.primaryContainer)
        } else {
            Modifier.fillMaxWidth(CHIP_SKELETON_FRACTION).bahrShimmer(shape)
        }
    Row(
        modifier = Modifier.clip(shape).then(ground).padding(horizontal = BahrSpacing.md, vertical = BahrSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        if (text == null) {
            Spacer(Modifier.size(BahrSize.iconSmall))
            ReservedText(MaterialTheme.typography.labelLarge)
            return@Row
        }
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

/**
 * What the facts grid is drawn from: the trip, or (MV-M7) the list card while the trip loads. The card knows the price
 * and the duration but not where the bus leaves from or where the trip goes ([placesPending]): those two cells shimmer
 * in a cell of their final size until the trip lands.
 */
internal data class TripFactsSource(
    val price: MoneyDto,
    val durationLabel: String,
    val durationMinutes: Int?,
    val multiDay: Boolean,
    val returnLabel: String?,
    val departure: DeparturePointDto?,
    val destination: DestinationDto?,
    val placesPending: Boolean = false,
)

internal fun TripDetailDto.factsSource() =
    TripFactsSource(price, durationLabel, durationMinutes, isMultiDay, returnLabel, departure, destination)

/** The card has no `returnLabel`: an overnight's note says the hours until the trip brings "Back Fri 22:00". */
internal fun TripCardDto.factsSource() =
    TripFactsSource(
        price,
        durationLabel,
        durationMinutes,
        nights > 0,
        returnLabel = null,
        departure = null,
        destination = null,
        placesPending = true,
    )

/** One cell of the facts grid. */
private data class Fact(
    val tint: Tint,
    val icon: BahrIcons,
    val label: String,
    val value: String,
    val note: String?,
    val testTag: String? = null,
)

/**
 * The 2×2 facts grid (item 4), one tint per cell. Departure and destination are separate cells:
 * that is how the page answers "where does it leave from, and where is it" (the handoff's
 * two-locations problem). A cell whose data the server did not send is left out; a cell not known yet
 * ([TripFactsSource.placesPending]) shimmers in its place.
 */
@Composable
internal fun TripFacts(
    source: TripFactsSource,
    modifier: Modifier = Modifier,
) {
    val arabic = BahrTheme.locale.isArabic
    // null = a cell still loading.
    val facts: List<Fact?> =
        buildList {
            add(
                Fact(
                    Tint.Teal,
                    BahrIcons.Payments,
                    stringResource(Res.string.trip_fact_price),
                    BahrFormat.money(source.price.amount, source.price.currencyCode, arabic),
                    stringResource(Res.string.trip_fact_price_note),
                    testTag = TRIP_PRICE_FACT_TAG,
                ),
            )
            if (source.durationLabel.isNotBlank()) {
                val hours = source.durationMinutes?.takeIf { it > 0 && it % MINUTES_PER_HOUR == 0 }?.div(MINUTES_PER_HOUR)
                val multiDay = source.multiDay
                add(
                    Fact(
                        Tint.Gold,
                        BahrIcons.Schedule,
                        stringResource(if (multiDay) Res.string.trip_fact_duration else Res.string.trip_fact_day),
                        // Times stay LTR inside Arabic (mobile rule 2), as on the trip card. A multi-day
                        // label (`يومان · ليلة واحدة`) is words, and an LTR isolate would swap its halves.
                        if (multiDay) source.durationLabel else BahrFormat.ltr(source.durationLabel),
                        // The server's "Back Fri 22:00" says more than an hour count on an overnight trip.
                        source.returnLabel?.takeIf { it.isNotBlank() }
                            ?: hours?.let { pluralStringResource(Res.plurals.trip_hours_door_to_door, it, it) },
                    ),
                )
            }
            if (source.placesPending) {
                add(null)
                add(null)
                return@buildList
            }
            source.departure?.let { from ->
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
            source.destination?.let { at ->
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
    FactsGrid(facts, modifier)
}

/** While nothing about the trip is known (a cold open): four cells of the grid's final size, shimmering. */
@Composable
internal fun TripFactsSkeleton(modifier: Modifier = Modifier) {
    FactsGrid(List(FACTS_CELLS) { null }, modifier)
}

@Composable
private fun FactsGrid(
    facts: List<Fact?>,
    modifier: Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
        facts.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
            ) {
                row.forEach { fact ->
                    val cell = Modifier.weight(1f).fillMaxHeight()
                    if (fact != null) FactCell(fact, cell) else FactCellSkeleton(cell)
                }
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
                .then(if (fact.testTag != null) Modifier.testTag(fact.testTag) else Modifier)
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

/**
 * A cell still loading: [FactCell]'s own padding, icon and three lines (label, value, note) laid out but not drawn, so
 * the shimmering tile is the loaded cell's height (MV-M7), not a guessed aspect ratio.
 */
@Composable
private fun FactCellSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.bahrShimmer(BahrTheme.shapes.extraLarge).padding(BahrSpacing.md),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        Spacer(Modifier.size(BahrSize.iconLarge))
        ReservedText(BahrTheme.type.overline)
        ReservedText(MaterialTheme.typography.labelLarge)
        ReservedText(MaterialTheme.typography.labelMedium)
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

/**
 * What's included while the trip loads (MV-M7): its heading is fixed copy, so it is text; the lines shimmer in rows of
 * a loaded line's height. Only the number of lines is a guess, and it sits under the heading, below the fold.
 */
@Composable
internal fun TripInclusionsSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
        SectionTitle(stringResource(Res.string.trip_whats_included))
        repeat(INCLUSION_SKELETON_ROWS) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
                BahrSkeletonCircle(props = BahrSkeletonCircle.Props(size = BahrSize.marker))
                LinesSkeleton(MaterialTheme.typography.bodyMedium, Modifier.weight(1f), widthFraction = INCLUSION_SKELETON_FRACTION)
            }
        }
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

/** For tests (MV-M7): the title, and the facts grid's price cell, whose bounds must not move while the page loads. */
internal const val TRIP_TITLE_TAG = "trip_title"
internal const val TRIP_PRICE_FACT_TAG = "trip_price_fact"

private const val MINUTES_PER_HOUR = 60

/** The grid's four cells: price, duration, bus, place. */
private const val FACTS_CELLS = 4

/**
 * The deck is not on the card, so its length is a guess: two lines, what the seeded Arabic decks take on a 412–448dp
 * phone (on a 360dp phone most take three, and 3 of the 15 seeded trips have none). A deck of another length moves what
 * is under the title by the difference, until the card carries the deck (`TripCard` has no `deck`: MV-M7 report).
 */
private const val DECK_SKELETON_LINES = 2

private const val TITLE_SKELETON_FRACTION = 0.7f
private const val CHIP_SKELETON_FRACTION = 0.45f
private const val INCLUSION_SKELETON_ROWS = 3
private const val INCLUSION_SKELETON_FRACTION = 0.6f
