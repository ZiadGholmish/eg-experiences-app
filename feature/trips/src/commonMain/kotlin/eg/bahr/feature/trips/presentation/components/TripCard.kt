package eg.bahr.feature.trips.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import eg.bahr.core.designsystem.components.BahrBadge
import eg.bahr.core.designsystem.components.ImageGround
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.feature.trips.model.TripSummaryDto

/**
 * One trip in the home list.
 *
 * Laid out with `start`/`end` throughout, never `left`/`right`: Arabic is the
 * default locale, so this card is mirrored for most users.
 */
@Composable
fun TripCard(
    trip: TripSummaryDto,
    perPersonLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.card)
                .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        // surfaceDim is painted first, so title and price never wait on the photo.
        Box {
            ImageGround(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(CoverAspectRatio)
                        .clip(BahrTheme.shapes.card),
            ) {
                AsyncImage(
                    model = trip.coverPhotoUrl,
                    contentDescription = trip.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(CoverAspectRatio),
                )
            }
            trip.category?.let { category ->
                BahrBadge(
                    modifier = Modifier.align(Alignment.TopStart).padding(BahrSpacing.md),
                    props = BahrBadge.Props(text = category),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
            trip.kicker?.let { kicker ->
                Text(
                    text = kicker,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = trip.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
                Text(
                    text =
                        BahrFormat.money(
                            trip.pricePerPerson.amount,
                            trip.pricePerPerson.currencyCode,
                            BahrTheme.locale.isArabic,
                        ),
                    style = BahrTheme.type.price,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.alignByBaseline(),
                )
                // Baseline alignment replaces the old 2dp bottom nudge, which has no token.
                Text(
                    text = perPersonLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}

/** The canvas crops every cover photo to this ratio. */
private const val CoverAspectRatio = 1.5f
