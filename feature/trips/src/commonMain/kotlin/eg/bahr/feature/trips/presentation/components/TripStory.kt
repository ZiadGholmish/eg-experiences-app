package eg.bahr.feature.trips.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import eg.bahr.core.designsystem.components.BahrCard
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.format_pair
import eg.bahr.core.localization.generated.resources.trip_best_time
import eg.bahr.core.localization.generated.resources.trip_day_note
import eg.bahr.core.localization.generated.resources.trip_host_trips_run
import eg.bahr.core.localization.generated.resources.trip_host_verified
import eg.bahr.core.localization.generated.resources.trip_itinerary
import eg.bahr.core.localization.generated.resources.trip_look_out_for
import eg.bahr.core.localization.generated.resources.trip_reviews
import eg.bahr.core.localization.generated.resources.trip_time_approximate
import eg.bahr.core.localization.generated.resources.trip_what_to_bring
import eg.bahr.core.localization.generated.resources.trip_what_to_know
import eg.bahr.feature.trips.model.HostDto
import eg.bahr.feature.trips.model.ItineraryStopDto
import eg.bahr.feature.trips.model.ReviewDto
import eg.bahr.feature.trips.model.ReviewsDto
import eg.bahr.feature.trips.model.TipDto
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The day, in order (item 6): `time | dot | text` rows on a `surfaceLow` panel, the dots joined by a
 * line. A stop the server marks `approximate` prints its time as "≈12:15", and the note above says
 * what the mark means. The prototype's note ("times after 12:00…") was this trip's copy; the
 * contract carries the flag per stop instead.
 */
@Composable
internal fun TripItinerary(
    stops: List<ItineraryStopDto>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
        SectionTitle(stringResource(Res.string.trip_itinerary))
        if (stops.any { it.approximate }) {
            Text(
                text = stringResource(Res.string.trip_day_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            modifier =
                Modifier
                    .padding(top = BahrSpacing.sm)
                    .fillMaxWidth()
                    .clip(BahrTheme.shapes.card)
                    .background(BahrTheme.colors.surfaceLow)
                    .padding(start = BahrSpacing.lg, end = BahrSpacing.lg, top = BahrSpacing.lg),
        ) {
            stops.forEachIndexed { index, stop -> StopRow(stop, isLast = index == stops.lastIndex) }
        }
    }
}

@Composable
private fun StopRow(
    stop: ItineraryStopDto,
    isLast: Boolean,
) {
    val (dot, onDot) = stop.kindColors()
    Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
        val time = stop.time?.let { if (stop.approximate) stringResource(Res.string.trip_time_approximate, it) else it }
        Text(
            text = time.orEmpty(),
            // Clock times stay left-to-right inside Arabic.
            style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Ltr),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(BahrSize.timeColumn).padding(top = BahrSpacing.xs),
        )
        Column(modifier = Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Marker(symbolIcon(stop.icon).filled(), dot, onDot)
            // The line runs into the next stop's dot; it is the spine of the day.
            Box(
                Modifier
                    .width(BahrSize.connector)
                    .weight(1f)
                    .background(if (isLast) BahrTheme.colors.surfaceLow else MaterialTheme.colorScheme.outlineVariant),
            )
        }
        Text(
            text = stop.text.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(top = BahrSpacing.xs, bottom = BahrSpacing.lg),
        )
    }
}

/**
 * Dot colours by the stop's kind, the seed's rule: transit stops (leaving, arriving) in the coral
 * family, meals gold, the rest teal. Coral itself is reserved for actions, so transit uses the coral
 * container's ink, `onTertiaryContainer`.
 */
@Composable
private fun ItineraryStopDto.kindColors() =
    when (kind) {
        "transit" -> MaterialTheme.colorScheme.onTertiaryContainer to MaterialTheme.colorScheme.onPrimary
        "meal" -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
        else -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    }

/**
 * The host card (item 8): avatar, name with the verified mark, "role · N trips run", then the host's
 * tips as tinted panels. Name and photo only: the host's phone is never on the public page, it comes
 * with a paid booking (product decision D4), so there is no call or chat button here.
 */
@Composable
internal fun TripHostCard(
    host: HostDto?,
    tips: List<TipDto>,
    modifier: Modifier = Modifier,
) {
    BahrCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(BahrSpacing.lg), verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
            host?.let { HostRow(it) }
            tips.forEach { TipPanel(it) }
        }
    }
}

@Composable
private fun HostRow(host: HostDto) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
        InitialAvatar(
            name = host.name,
            tint = Tint.Teal,
            size = BahrSize.avatar,
            image = host.avatar?.url,
        )
        Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
                host.name?.let { Text(text = it, style = MaterialTheme.typography.titleMedium.byContent()) }
                if (host.verified) {
                    Icon(
                        imageVector = BahrIcons.Verified.filled(),
                        contentDescription = stringResource(Res.string.trip_host_verified),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(BahrSize.iconSmall),
                    )
                }
            }
            val tripsRun = host.tripsRun?.let { pluralStringResource(Res.plurals.trip_host_trips_run, it, it) }
            val meta =
                when {
                    host.role != null && tripsRun != null -> stringResource(Res.string.format_pair, host.role, tripsRun)
                    else -> host.role ?: tripsRun
                }
            meta?.let {
                Text(text = it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One tip, tinted and iconed by its key, as in the prototype. An unknown key is a teal "know". */
@Composable
private fun TipPanel(tip: TipDto) {
    val (tint, icon, fallbackTitle) = tipStyle(tip.key)
    val colors = tint.colors()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.large)
                .background(colors.container)
                .padding(horizontal = BahrSpacing.lg, vertical = BahrSpacing.md),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
            Icon(icon.filled(), contentDescription = null, tint = colors.icon, modifier = Modifier.size(BahrSize.iconMedium))
            Text(
                text = tip.title ?: stringResource(fallbackTitle),
                style = MaterialTheme.typography.labelLarge,
                color = colors.content,
            )
        }
        tip.body?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium, color = colors.content) }
    }
}

private fun tipStyle(key: String?): Triple<Tint, BahrIcons, StringResource> =
    when (key) {
        "bring" -> Triple(Tint.Teal, BahrIcons.Backpack, Res.string.trip_what_to_bring)
        "best_time" -> Triple(Tint.Gold, BahrIcons.Sunny, Res.string.trip_best_time)
        "look_out" -> Triple(Tint.Magenta, BahrIcons.Visibility, Res.string.trip_look_out_for)
        else -> Triple(Tint.Green, BahrIcons.Info, Res.string.trip_what_to_know)
    }

/**
 * Reviews (item 9): a "★ 4.8 · 37" pill, then one card per review with a tinted initial, the
 * author, the date and the text. The server sends the average and count it wants shown.
 */
@Composable
internal fun TripReviews(
    reviews: ReviewsDto,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringResource(Res.string.trip_reviews))
            Spacer(Modifier.weight(1f))
            reviews.average?.let { average -> ReviewsPill(BahrFormat.rating(average), reviews.count) }
        }
        reviews.items.forEach { ReviewCard(it) }
    }
}

@Composable
private fun ReviewsPill(
    average: String,
    count: Int?,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .clip(BahrTheme.shapes.full)
                .background(c.secondaryContainer)
                .padding(horizontal = BahrSpacing.md, vertical = BahrSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        Icon(BahrIcons.Star.filled(), contentDescription = null, tint = c.secondary, modifier = Modifier.size(BahrSize.iconSmall))
        Text(
            text = if (count != null) stringResource(Res.string.format_pair, average, count.toString()) else average,
            style = MaterialTheme.typography.labelLarge,
            color = c.onSecondaryContainer,
        )
    }
}

@Composable
private fun ReviewCard(review: ReviewDto) {
    BahrCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(BahrSpacing.lg), verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
                InitialAvatar(name = review.author, tint = toneTint(review.tone), size = BahrSize.avatarSmall)
                review.author?.let { Text(text = it, style = MaterialTheme.typography.labelLarge.byContent()) }
                Spacer(Modifier.weight(1f))
                review.dateISO?.let {
                    Text(
                        text = BahrFormat.dayMonthYear(it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            review.body?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = BahrTheme.colors.onSurfaceSecondary)
            }
            review.partyLabel?.let {
                Text(text = it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * A round avatar: the photo when there is one, over the person's initial on a tinted ground (which
 * is also what shows while the photo loads, or when the backend serves none, as today for hosts).
 */
@Composable
private fun InitialAvatar(
    name: String?,
    tint: Tint,
    size: Dp,
    image: String? = null,
) {
    val colors = tint.colors()
    Box(
        modifier = Modifier.size(size).clip(BahrTheme.shapes.full).background(colors.container),
        contentAlignment = Alignment.Center,
    ) {
        name?.firstOrNull()?.let {
            Text(text = it.uppercase(), style = MaterialTheme.typography.labelLarge, color = colors.content)
        }
        image?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * Names arrive as typed ("Mahmoud A."): a Latin name inside an Arabic page must read in its own
 * direction, or the bidi algorithm moves its trailing period to the front.
 */
private fun TextStyle.byContent() = copy(textDirection = TextDirection.Content)
