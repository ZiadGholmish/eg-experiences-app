package eg.bahr.feature.trips.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.departure_seats_left
import eg.bahr.core.localization.generated.resources.departure_sold_out
import eg.bahr.feature.trips.model.DepartureDto
import org.jetbrains.compose.resources.stringResource

/**
 * The dates a customer can pick.
 *
 * A departure that is not [DepartureDto.bookable] still renders — sold out is
 * information, and hiding those dates makes a half-empty picker look like the
 * trip barely runs. It is simply not selectable.
 */
@Composable
internal fun DeparturePicker(
    departures: List<DepartureDto>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val soldOutLabel = stringResource(Res.string.departure_sold_out)
    val colors = MaterialTheme.colorScheme
    val muted = BahrTheme.colors.onSurfaceDisabled
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = BahrSpacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        items(departures, key = { it.id }) { departure ->
            val selected = departure.id == selectedId
            // Same fills as a filter chip (tokens.json: surfaceContainer = "unselected chips"), so the
            // strip needs no border and no fixed width of its own. The date row layout lands in M2.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .clip(BahrTheme.shapes.medium)
                        .background(if (selected) colors.primary else colors.surfaceContainer)
                        .clickable(enabled = departure.bookable) { onSelect(departure.id) }
                        .padding(horizontal = BahrSpacing.md, vertical = BahrSpacing.md),
            ) {
                Text(
                    text = departure.date,
                    style = MaterialTheme.typography.labelLarge,
                    color =
                        when {
                            selected -> colors.onPrimary
                            departure.bookable -> colors.onSurfaceVariant
                            else -> muted
                        },
                )
                departure.departTime?.let { time ->
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) colors.onPrimary else muted,
                    )
                }
                Text(
                    text =
                        if (!departure.bookable || departure.soldOut) {
                            soldOutLabel
                        } else {
                            stringResource(Res.string.departure_seats_left, departure.seatsRemaining)
                        },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) colors.onPrimary else muted,
                )
            }
        }
    }
}
