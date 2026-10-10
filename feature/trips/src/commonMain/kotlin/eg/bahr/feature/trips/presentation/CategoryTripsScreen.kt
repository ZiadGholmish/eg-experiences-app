package eg.bahr.feature.trips.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.trip_per_person
import eg.bahr.core.localization.generated.resources.trips_all_title
import eg.bahr.core.localization.generated.resources.trips_empty
import eg.bahr.core.localization.generated.resources.trips_filter_empty
import eg.bahr.feature.trips.model.FacetDto
import eg.bahr.feature.trips.presentation.components.FacetFilterChips
import eg.bahr.feature.trips.presentation.components.ListHeading
import eg.bahr.feature.trips.presentation.components.ListTopBar
import eg.bahr.feature.trips.presentation.components.colors
import eg.bahr.feature.trips.presentation.components.listBody
import eg.bahr.feature.trips.presentation.components.symbolIcon
import eg.bahr.feature.trips.presentation.components.toneTint
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The category page (M4-M1b), opened from a Home category chip, a `category` banner or a category
 * row's "See all": a header with the category's tinted tile, label and trip count, the filter chips
 * (wrapping, as the handoff's Home chips do), then the trips, paged.
 *
 * [title] is the opener's label for the category, shown until the facets name it. If the server
 * turns the category down (a stale key) the page becomes "All trips" rather than an error.
 */
@Composable
internal fun CategoryTripsScreen(
    category: String,
    title: String?,
    onBack: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryTripsViewModel = koinViewModel(key = "category:$category") { parametersOf(category, title) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val trips = viewModel.trips.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val perPersonLabel = stringResource(Res.string.trip_per_person)
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + BahrSpacing.xl

    Column(modifier = modifier.fillMaxSize()) {
        ListTopBar(onBack = onBack)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(BahrSpacing.lg),
        ) {
            item(key = KEY_HEADER) {
                CategoryHeader(
                    facet = state.categoryFacet,
                    label =
                        when {
                            state.category == null -> stringResource(Res.string.trips_all_title)
                            else -> state.categoryFacet?.label ?: state.fallbackTitle
                        },
                    totalItems = state.totalItems,
                    modifier = Modifier.padding(horizontal = BahrSpacing.gutter),
                )
            }
            val chips = state.filterChips
            if (chips.isNotEmpty()) {
                item(key = KEY_CHIPS) {
                    FacetFilterChips(chips, isSelected = state::isSelected, onSelect = viewModel::selectFilter)
                }
            }
            listBody(
                trips = trips,
                perPersonLabel = perPersonLabel,
                emptyMessage = { stringResource(if (state.filter != null) Res.string.trips_filter_empty else Res.string.trips_empty) },
                onTripClick = onTripClick,
            )
        }
    }
}

/**
 * The category's tile (its icon on its tone's tint, the handoff's category tile), its label and the
 * trip count. The tile waits for the facets; the label can come from the opener before them.
 */
@Composable
private fun CategoryHeader(
    facet: FacetDto?,
    label: String?,
    totalItems: Long?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        if (facet != null) {
            val tint = toneTint(facet.tone).colors()
            Box(
                modifier =
                    Modifier
                        .size(BahrSize.categoryTile)
                        .clip(BahrTheme.shapes.extraLarge)
                        .background(tint.container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = symbolIcon(facet.icon).filled(),
                    contentDescription = null,
                    tint = tint.icon,
                    modifier = Modifier.size(BahrSize.iconLarge),
                )
            }
        }
        ListHeading(label = label, totalItems = totalItems)
    }
}

private const val KEY_HEADER = "header"
private const val KEY_CHIPS = "chips"
