package eg.bahr.feature.home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import eg.bahr.core.catalog.presentation.components.InitialAvatar
import eg.bahr.core.catalog.presentation.components.Photo
import eg.bahr.core.catalog.presentation.components.ReviewStars
import eg.bahr.core.catalog.presentation.components.Tint
import eg.bahr.core.catalog.presentation.components.TripCard
import eg.bahr.core.catalog.presentation.components.VerifiedTrip
import eg.bahr.core.catalog.presentation.components.toneTint
import eg.bahr.core.designsystem.components.BahrCard
import eg.bahr.core.designsystem.components.ImageGround
import eg.bahr.core.designsystem.components.SeatBadge
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.format_pair
import eg.bahr.core.localization.generated.resources.home_departure_bus
import eg.bahr.core.localization.generated.resources.home_rating_count
import eg.bahr.core.localization.generated.resources.host_rating_average
import eg.bahr.core.localization.generated.resources.host_reviews_count
import eg.bahr.core.localization.generated.resources.trip_host_trips_run
import eg.bahr.core.localization.generated.resources.trip_host_verified
import eg.bahr.feature.home.model.HomeDepartureDto
import eg.bahr.feature.home.model.HomeHostDto
import eg.bahr.feature.home.model.HomeHostRatingDto
import eg.bahr.feature.home.model.HomeReviewDto
import eg.bahr.feature.home.model.HomeTripRefDto
import eg.bahr.feature.home.navigation.HomeAction
import eg.bahr.feature.home.presentation.HomeCards
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/*
 * The cards of Home's card layouts (MH-M1). Trips are core:catalog's TripCard; hosts, departures and reviews
 * are new, built from design-system parts (BahrCard, the avatar, the seats pill, the review stars). The handoff
 * has none of the three: they follow the trip page's host card, the trip card and the review card, so each reads
 * as a smaller copy of what it opens. Private to Home until a second screen draws one.
 */

/** How a card layout sizes its cards: [Compact] in a rail or grid (the layout gives the width), [Wide] in a list. */
internal enum class CardStyle { Compact, Wide }

/** The card at [index] of [cards], opening what it names through the [context]. */
@Composable
internal fun HomeCard(
    cards: HomeCards,
    index: Int,
    style: CardStyle,
    context: HomeSectionContext,
    modifier: Modifier = Modifier,
) {
    val compact = style == CardStyle.Compact
    when (cards) {
        is HomeCards.Trips -> {
            val trip = cards.items[index]
            TripCard(
                trip = trip,
                perPersonLabel = context.perPersonLabel,
                onClick = { context.onTripClick(trip.slug) },
                modifier = modifier,
                compact = compact,
                waitlisted = context.waitlistTags.shows(trip),
                like = context.likes?.stateOf(trip),
                onLike = { context.onLike(trip) },
            )
        }

        is HomeCards.Hosts -> {
            val host = cards.items[index]
            HostCard(host, compact, modifier) { context.onAction(HomeAction.OpenHost(host.slug, host.name, host.avatar?.url)) }
        }

        is HomeCards.Departures -> {
            val departure = cards.items[index]
            DepartureCard(departure, context.perPersonLabel, compact, modifier) {
                context.onAction(HomeAction.OpenTrip(departure.trip.slug, departure.date.toString()))
            }
        }

        is HomeCards.Reviews -> {
            val review = cards.items[index]
            HomeReviewCard(review, compact, modifier) { context.onAction(HomeAction.OpenTrip(review.trip.slug)) }
        }
    }
}

/**
 * A host: photo over their initial, name with the verified mark, rating ("★ 4.8 (12)") when they have reviews,
 * and the trips they have run when any. [compact]: a small centred tile; otherwise a row, photo at the start.
 */
@Composable
private fun HostCard(
    host: HomeHostDto,
    compact: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val tripsRun = host.tripsRun.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.trip_host_trips_run, it, it) }
    BahrCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        if (compact) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(BahrSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
            ) {
                InitialAvatar(name = host.name, tint = Tint.Teal, size = BahrSize.avatar, image = host.avatar?.url)
                Spacer(Modifier.size(BahrSpacing.xs))
                HostName(host)
                // Both lines are always there in a tile, empty or not, so a rail's or grid's hosts line up whatever
                // they have to show (MH-M1 review #3).
                host.rating?.let { HostRating(it) } ?: ReservedLine(MaterialTheme.typography.labelLarge, withIcon = true)
                tripsRun?.let { Secondary(it) } ?: ReservedLine(MaterialTheme.typography.labelMedium)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(BahrSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
            ) {
                InitialAvatar(name = host.name, tint = Tint.Teal, size = BahrSize.avatar, image = host.avatar?.url)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
                    HostName(host)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
                        host.rating?.let { HostRating(it) }
                        tripsRun?.let { Secondary(it) }
                    }
                }
            }
        }
    }
}

/** The host's name, one line, and the verified mark after it (spoken as "Verified host"). */
@Composable
private fun HostName(host: HomeHostDto) {
    Row(
        // As tall with the verified mark as without, so tiles with and without one line up.
        modifier = Modifier.heightIn(min = BahrSize.iconSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        Text(
            text = host.name,
            style = MaterialTheme.typography.labelLarge.byContent(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (host.verified) {
            Icon(
                imageVector = BahrIcons.Verified.filled(),
                contentDescription = stringResource(Res.string.trip_host_verified),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(BahrSize.iconSmall),
            )
        }
    }
}

/** "★ 4.8 (12)", read out once as "Rated 4.8 out of 5 · 12 reviews". The mean is the server's, formatted only. */
@Composable
private fun HostRating(rating: HomeHostRatingDto) {
    val shown = BahrFormat.rating(rating.value)
    val spoken =
        stringResource(
            Res.string.format_pair,
            stringResource(Res.string.host_rating_average, shown),
            pluralStringResource(Res.plurals.host_reviews_count, rating.count, rating.count),
        )
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        Icon(
            BahrIcons.Star.filled(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(BahrSize.iconSmall),
        )
        Text(text = shown, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        Secondary(stringResource(Res.string.home_rating_count, rating.count))
    }
}

/**
 * A date leaving soon: the trip's photo, its title, the date with the bus's Cairo time ([WhenLine]), and the price with the
 * seats left (a hint: the hold decides). Laid out as a [compact] trip card: the seats pill under the price
 * where a narrow card would crowd it, beside it in a list.
 */
@Composable
private fun DepartureCard(
    departure: HomeDepartureDto,
    perPersonLabel: String,
    compact: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    BahrCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        // The size is reserved before anything loads, so the text under it never moves.
        ImageGround(modifier = Modifier.fillMaxWidth().aspectRatio(DEPARTURE_PHOTO_RATIO)) {
            departure.trip.image?.let { Photo(it) }
        }
        Column(modifier = Modifier.padding(BahrSpacing.lg), verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
            Text(
                text = departure.trip.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = TITLE_LINES,
                minLines = if (compact) TITLE_LINES else 1,
                overflow = TextOverflow.Ellipsis,
            )
            WhenLine(departure, compact)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // A narrow grid card puts "per person" under the price rather than breaking it mid-word.
                FlowRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
                    Text(
                        text = BahrFormat.money(departure.price.amount, departure.price.currencyCode, BahrTheme.locale.isArabic),
                        style = BahrTheme.type.price,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = perPersonLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                if (!compact) SeatBadge(left = departure.seatsRemaining)
            }
            if (compact) SeatBadge(left = departure.seatsRemaining)
        }
    }
}

/**
 * When the bus leaves. Wide: "Sat 14 Nov … (bus) 05:00" on one line, the date ellipsised only if it must be. [compact]
 * (a rail or grid card, MH-M1 review #1): the date takes its own line, so a narrow card never cuts the month (the
 * card's main fact, and long in Arabic), and the bus icon with its time goes under it. The time is never cut, and
 * the whole is read out once as "Sat 14 Nov · Bus 05:00".
 */
@Composable
private fun WhenLine(
    departure: HomeDepartureDto,
    compact: Boolean,
) {
    val date = BahrFormat.date(departure.date)
    val time = BahrFormat.time(departure.departTime)
    val spoken = stringResource(Res.string.format_pair, date, stringResource(Res.string.home_departure_bus, time))
    val semantics = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }
    if (compact) {
        Column(modifier = semantics, verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
            WhenText(date)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
                BusIcon()
                WhenText(time, ltr = true)
            }
        }
    } else {
        Row(
            modifier = semantics,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
        ) {
            WhenText(date, Modifier.weight(1f))
            BusIcon()
            WhenText(time, ltr = true)
        }
    }
}

/** One line of [WhenLine]. [ltr]: a time, which stays LTR inside an Arabic line. */
@Composable
private fun WhenText(
    text: String,
    modifier: Modifier = Modifier,
    ltr: Boolean = false,
) {
    val style = MaterialTheme.typography.labelLarge
    Text(
        text = text,
        style = if (ltr) style.copy(textDirection = TextDirection.Ltr) else style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun BusIcon() {
    Icon(
        BahrIcons.DirectionsBus.outlined(),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(BahrSize.iconSmall),
    )
}

/**
 * A review: the author's initial, name and date, the stars, the text (a few lines; the trip page has it whole),
 * "Verified trip" when the server says so, and the trip it is about, which a tap opens.
 */
@Composable
private fun HomeReviewCard(
    review: HomeReviewDto,
    compact: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    BahrCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(BahrSpacing.lg), verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
                InitialAvatar(name = review.author, tint = toneTint(review.tone), size = BahrSize.avatarSmall)
                Text(
                    text = review.author,
                    style = MaterialTheme.typography.labelLarge.byContent(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Secondary(BahrFormat.dayMonth(review.dateISO))
            }
            ReviewStars(review.rating)
            review.body?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BahrTheme.colors.onSurfaceSecondary,
                    maxLines = if (compact) REVIEW_LINES_COMPACT else REVIEW_LINES_WIDE,
                    // A rail's reviews line up whatever their length.
                    minLines = if (compact) REVIEW_LINES_COMPACT else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (review.verified) VerifiedTrip()
            TripLine(review.trip)
        }
    }
}

/** The trip a card leads to: its thumbnail (the ground while it loads) and title. */
@Composable
private fun TripLine(trip: HomeTripRefDto) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
        ImageGround(modifier = Modifier.size(BahrSize.thumbnailSmall).clip(BahrTheme.shapes.small)) { trip.image?.let { Photo(it) } }
        Text(
            text = trip.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The room one line of [style] takes (and, [withIcon], beside a small icon, as the rating row), with nothing drawn
 * and nothing read out: a host tile's missing rating or trips line.
 */
@Composable
private fun ReservedLine(
    style: TextStyle,
    withIcon: Boolean = false,
) {
    Row(modifier = Modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
        if (withIcon) Spacer(Modifier.size(BahrSize.iconSmall))
        // An empty line, never shown: it only measures the style's line height (fonts and Arabic leading included).
        Text(text = "", style = style, maxLines = 1)
    }
}

/** A line of secondary text. */
@Composable
private fun Secondary(
    text: String,
    style: TextStyle = MaterialTheme.typography.labelMedium,
) {
    Text(text = text, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
}

/** Names arrive as typed: a Latin name inside an Arabic card reads in its own direction (see ReviewCard). */
private fun TextStyle.byContent() = copy(textDirection = TextDirection.Content)

private const val TITLE_LINES = 2
private const val REVIEW_LINES_COMPACT = 3
private const val REVIEW_LINES_WIDE = 5

/** A departure's photo, 16:9: shorter than a trip card's, since the date and seats are the news. */
private const val DEPARTURE_PHOTO_RATIO = 16f / 9f
