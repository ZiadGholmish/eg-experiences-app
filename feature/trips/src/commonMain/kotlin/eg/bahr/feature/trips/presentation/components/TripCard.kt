package eg.bahr.feature.trips.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import eg.bahr.core.designsystem.components.BahrBadge
import eg.bahr.core.designsystem.components.BahrCard
import eg.bahr.core.designsystem.components.ImageGround
import eg.bahr.core.designsystem.components.SeatBadge
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.feature.trips.model.NextDepartureDto
import eg.bahr.feature.trips.model.TripCardDto

/**
 * One trip in the list (handoff: "Trip cards"): photo with the trip's badge at the top start, then
 * title, the day's times with the next date, and the price with the next date's seats.
 *
 * A [BahrCard], so it gets the `card` radius, `level1` elevation and the HighContrast outline.
 * The text sits inside the card's padding: laid flush against the card's edge, the rounded
 * corner clipped the price's first digit (M0-M2 review).
 *
 * Laid out with `start`/`end` throughout, never `left`/`right`: Arabic is the default locale, so
 * this card is mirrored for most users.
 */
@Composable
internal fun TripCard(
    trip: TripCardDto,
    perPersonLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BahrCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Box {
            CoverImage(trip)
            trip.badge?.label?.let { label ->
                BahrBadge(
                    modifier = Modifier.align(Alignment.TopStart).padding(BahrSpacing.md),
                    props = BahrBadge.Props(text = label, tone = badgeTone(trip.badge.tone)),
                )
            }
        }

        Column(
            modifier = Modifier.padding(BahrSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
        ) {
            Text(
                text = trip.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            ScheduleRow(durationLabel = trip.durationLabel, next = trip.nextDeparture)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
                ) {
                    Text(
                        text = BahrFormat.money(trip.price.amount, trip.price.currencyCode, BahrTheme.locale.isArabic),
                        style = BahrTheme.type.price,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = perPersonLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                trip.nextDeparture?.seatsLeft()?.let { SeatBadge(left = it) }
            }
        }
    }
}

/**
 * The card photo, or its LQIP, or the bare ground. The size is reserved by the aspect ratio
 * before anything loads, so title and price never move when the photo arrives.
 */
@Composable
private fun CoverImage(trip: TripCardDto) {
    val image = trip.cardImage ?: trip.heroImage
    ImageGround(modifier = Modifier.fillMaxWidth().aspectRatio(CoverAspectRatio)) {
        // Two layers rather than Coil's `placeholder`: the LQIP is a ~1 KB `data:` URI that Coil
        // decodes locally, and it stays under the real photo, which simply draws over it.
        image?.lqip?.let { lqip ->
            AsyncImage(
                model = lqip,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        image?.let {
            AsyncImage(
                model = it.url,
                contentDescription = it.alt,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The day's times at the start, the next date at the end. Either may be missing. */
@Composable
private fun ScheduleRow(
    durationLabel: String,
    next: NextDepartureDto?,
) {
    val date = next?.date
    if (durationLabel.isBlank() && date == null) return
    val style = MaterialTheme.typography.labelLarge
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Wraps its content rather than taking the row's free space: an LTR text box aligns its
        // text to the left, which in an Arabic row is the far end, next to the date.
        Text(
            text = durationLabel,
            // "05:00 → 22:00" comes from the server in both languages. In an RTL line the bidi
            // algorithm would draw it as "22:00 → 05:00"; times stay LTR inside RTL.
            style = style.copy(textDirection = TextDirection.Ltr),
            color = color,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        date?.let {
            Text(text = BahrFormat.date(it, BahrTheme.locale.isArabic), style = style, color = color, maxLines = 1)
        }
    }
}

/** Seats to show on the pill: 0 when sold out, null (no pill) when the server sent no count. */
internal fun NextDepartureDto.seatsLeft(): Int? = if (soldOut == true) 0 else seatsRemaining

/**
 * The contract's lowercase `Tone` to the badge's tone. A tone this build does not know (or none)
 * is the primary tint rather than a decode failure.
 */
internal fun badgeTone(tone: String?): BahrBadge.Tone =
    when (tone) {
        "secondary" -> BahrBadge.Tone.Secondary
        "tertiary" -> BahrBadge.Tone.Tertiary
        "quaternary" -> BahrBadge.Tone.Quaternary
        "success" -> BahrBadge.Tone.Success
        else -> BahrBadge.Tone.Primary
    }

/**
 * The handoff's card photo is 186px tall on a 390px screen, with a gutter either side. A ratio, not
 * a height, so the reserved space scales with the screen width; derived from [BahrSpacing.gutter] so
 * it follows the token if the gutter changes.
 */
private const val HANDOFF_SCREEN_WIDTH = 390f
private const val HANDOFF_CARD_PHOTO_HEIGHT = 186f
private val CoverAspectRatio = (HANDOFF_SCREEN_WIDTH - 2 * BahrSpacing.gutter.value) / HANDOFF_CARD_PHOTO_HEIGHT
