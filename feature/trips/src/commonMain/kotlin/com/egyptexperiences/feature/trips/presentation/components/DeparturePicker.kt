package com.egyptexperiences.feature.trips.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.egyptexperiences.core.designsystem.theme.EgInkFaint
import com.egyptexperiences.core.designsystem.theme.EgInkSubtle
import com.egyptexperiences.core.designsystem.theme.EgOnBrand
import com.egyptexperiences.core.designsystem.theme.EgStroke
import com.egyptexperiences.core.designsystem.theme.EgSurfaceSunken
import com.egyptexperiences.core.designsystem.theme.EgTeal
import com.egyptexperiences.core.designsystem.theme.EgTheme
import com.egyptexperiences.core.localization.generated.resources.Res
import com.egyptexperiences.core.localization.generated.resources.departure_seats_left
import com.egyptexperiences.core.localization.generated.resources.departure_sold_out
import com.egyptexperiences.feature.trips.model.DepartureDto
import org.jetbrains.compose.resources.stringResource

/**
 * The dates a customer can pick.
 *
 * A departure that is not [DepartureDto.bookable] still renders — sold out is
 * information, and hiding those dates makes a half-empty picker look like the
 * trip barely runs. It is simply not selectable.
 */
@Composable
fun DeparturePicker(
    departures: List<DepartureDto>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val soldOutLabel = stringResource(Res.string.departure_sold_out)
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = EgTheme.spacings.screenGutter),
        horizontalArrangement = Arrangement.spacedBy(EgTheme.spacings.lg),
    ) {
        items(departures, key = { it.id }) { departure ->
            val selected = departure.id == selectedId
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(EgTheme.spacings.xxs),
                modifier =
                    Modifier
                        .widthIn(min = 96.dp)
                        .clip(EgTheme.shapes.lg)
                        .background(
                            if (selected) EgTeal else EgSurfaceSunken,
                        ).border(
                            width = if (selected) 0.dp else 1.dp,
                            color = if (selected) EgTeal else EgStroke,
                            shape = EgTheme.shapes.lg,
                        ).clickable(enabled = departure.bookable) { onSelect(departure.id) }
                        .padding(
                            horizontal = EgTheme.spacings.xl,
                            vertical = EgTheme.spacings.xxl,
                        ),
            ) {
                Text(
                    text = departure.date,
                    style = EgTheme.typography.chip,
                    color =
                        when {
                            selected -> EgOnBrand
                            departure.bookable -> EgInkSubtle
                            else -> EgInkFaint
                        },
                )
                departure.departTime?.let { time ->
                    Text(
                        text = time,
                        style = EgTheme.typography.badge,
                        color = if (selected) EgOnBrand else EgInkFaint,
                    )
                }
                Text(
                    text =
                        if (!departure.bookable || departure.soldOut) {
                            soldOutLabel
                        } else {
                            stringResource(Res.string.departure_seats_left, departure.seatsRemaining)
                        },
                    style = EgTheme.typography.badge,
                    color = if (selected) EgOnBrand else EgInkFaint,
                )
            }
        }
    }
}
