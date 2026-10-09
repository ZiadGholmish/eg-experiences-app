package eg.bahr.feature.trips.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.designsystem.components.BahrEmptyView
import eg.bahr.core.designsystem.components.BahrErrorView
import eg.bahr.core.designsystem.components.BahrFilterChip
import eg.bahr.core.designsystem.components.BahrLoadingView
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.trip_per_person
import eg.bahr.core.localization.generated.resources.trips_empty
import eg.bahr.core.localization.generated.resources.trips_title
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import eg.bahr.feature.trips.presentation.components.TripCard
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun TripListScreen(
    onTripClick: (slug: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TripListViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val perPersonLabel = stringResource(Res.string.trip_per_person)

    // Paging trigger: fetch the next page once the user is within a screen's
    // worth of the end, rather than waiting for the very last item.
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - LoadMoreThreshold
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { shouldLoadMore }
            .collect { if (it) viewModel.loadMore() }
    }

    when {
        state.isLoading -> BahrLoadingView(modifier)

        state.error != null && state.trips.isEmpty() -> {
            val error = state.error!!
            BahrErrorView(
                message = error.localizedMessage(),
                retryLabel = stringResource(Res.string.action_retry),
                onRetry = if (error.isRetryable) viewModel::refresh else null,
                modifier = modifier,
            )
        }

        state.visibleTrips.isEmpty() ->
            BahrEmptyView(message = stringResource(Res.string.trips_empty), modifier = modifier)

        else ->
            LazyColumn(
                state = listState,
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = BahrSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(BahrSpacing.xl),
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = BahrSpacing.gutter),
                        verticalArrangement = Arrangement.spacedBy(BahrSpacing.lg),
                    ) {
                        Text(
                            text = stringResource(Res.string.trips_title),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                }

                if (state.categories.isNotEmpty()) {
                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = BahrSpacing.gutter),
                            horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
                        ) {
                            items(state.categories) { category ->
                                BahrFilterChip(
                                    label = category,
                                    selected = state.selectedCategory == category,
                                    onClick = {
                                        viewModel.selectCategory(
                                            if (state.selectedCategory == category) null else category,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                items(state.visibleTrips, key = { it.id }) { trip ->
                    Row(modifier = Modifier.padding(horizontal = BahrSpacing.gutter)) {
                        TripCard(
                            trip = trip,
                            perPersonLabel = perPersonLabel,
                            onClick = { onTripClick(trip.slug) },
                        )
                    }
                }
            }
    }
}

private const val LoadMoreThreshold = 3
