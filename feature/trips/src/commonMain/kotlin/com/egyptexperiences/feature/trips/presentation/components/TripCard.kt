package com.egyptexperiences.feature.trips.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.egyptexperiences.core.common.money.MoneyFormatter
import com.egyptexperiences.core.designsystem.component.EgBadge
import com.egyptexperiences.core.designsystem.theme.EgInkFaint
import com.egyptexperiences.core.designsystem.theme.EgInkSubtle
import com.egyptexperiences.core.designsystem.theme.EgSurfaceSunken
import com.egyptexperiences.core.designsystem.theme.EgTeal
import com.egyptexperiences.core.designsystem.theme.EgTheme
import com.egyptexperiences.feature.trips.model.TripSummaryDto

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
                .clip(EgTheme.shapes.card)
                .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(EgTheme.spacings.lg),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(CoverAspectRatio)
                    .clip(EgTheme.shapes.card)
                    .background(EgSurfaceSunken),
        ) {
            AsyncImage(
                model = trip.coverPhotoUrl,
                contentDescription = trip.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(CoverAspectRatio),
            )
            trip.category?.let { category ->
                EgBadge(
                    text = category,
                    modifier = Modifier.align(Alignment.TopStart).padding(EgTheme.spacings.xl),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(EgTheme.spacings.xs)) {
            trip.kicker?.let { kicker ->
                Text(text = kicker, style = EgTheme.typography.kicker, color = EgInkFaint)
            }
            Text(
                text = trip.title,
                style = EgTheme.typography.cardTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(EgTheme.spacings.sm),
            ) {
                Text(
                    text = MoneyFormatter.format(trip.pricePerPerson.toMoney()),
                    style = EgTheme.typography.price,
                    color = EgTeal,
                )
                Text(
                    text = perPersonLabel,
                    style = EgTheme.typography.meta,
                    color = EgInkSubtle,
                    modifier = Modifier.padding(bottom = EgTheme.spacings.xxs),
                )
            }
        }
    }
}

/** The canvas crops every cover photo to this ratio. */
private const val CoverAspectRatio = 1.5f
