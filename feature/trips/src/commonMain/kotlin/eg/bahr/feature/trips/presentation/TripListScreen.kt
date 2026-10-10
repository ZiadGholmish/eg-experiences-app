package eg.bahr.feature.trips.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
import eg.bahr.core.designsystem.components.BahrLoadingView
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.trip_per_person
import eg.bahr.core.localization.generated.resources.trips_all_title
import eg.bahr.core.localization.generated.resources.trips_empty
import eg.bahr.core.localization.generated.resources.trips_title
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import eg.bahr.feature.trips.model.key
import eg.bahr.feature.trips.navigation.HomeAction
import eg.bahr.feature.trips.presentation.components.HomeSection
import eg.bahr.feature.trips.presentation.components.TripCard
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Home: the trips on sale. [header] is drawn under the title in the list, and at the top of the
 * loading, error and empty views (which have no title), so it is never hidden behind a failed list;
 * another feature fills it
 * through `tripListScreen(header = …)` (M2-M4: "Continue your booking"). It must draw nothing when
 * it has nothing to show, or the list's spacing leaves a gap.
 *
 * Under the header come Home's server-driven sections (M4-M1a), in the server's order, then the
 * full list under an "All trips" heading. With no sections the screen is the plain list it was.
 * A banner tap goes to [onAction]; a trip card in a row to [onTripClick], like one in the list.
 */
@Composable
internal fun TripListScreen(
    onTripClick: (slug: String) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    onAction: (HomeAction) -> Unit = {},
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

    // The app is edge-to-edge. Home is laid out below the status bar and clipped there: content
    // scrolled up stops at the bar instead of drawing under the clock and icons (M4-M1a: a scrolled
    // banner overlapped them). Applied here, not at the NavHost, because the trip detail hero is
    // meant to run full-bleed under the bar.
    val statusBar = WindowInsets.statusBars
    val stateModifier = modifier.windowInsetsPadding(statusBar)
    val listTop = BahrSpacing.xl
    // The last card scrolls clear of the gesture bar (M1-M1a review #10).
    val listBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + BahrSpacing.xl

    // The full-screen states keep the header above them; the list scrolls it with the trips. A list
    // that came back empty while Home has sections is not "no trips": the sections still show.
    val sections = state.sections
    if (state.showsLoading || state.trips.isEmpty() && (state.error != null || sections.isEmpty())) {
        Column(modifier = stateModifier) {
            // The zero-size anchor puts the gap above the header only when the header draws
            // something: spacedBy adds no space after a last child, and an empty header emits none.
            Column(
                modifier = Modifier.padding(horizontal = BahrSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(BahrSpacing.xl),
            ) {
                Spacer(Modifier)
                header()
            }
            val fill = Modifier.weight(1f)
            val error = state.error
            when {
                state.showsLoading -> BahrLoadingView(fill)

                error != null -> {
                    BahrErrorView(
                        message = error.localizedMessage(),
                        retryLabel = stringResource(Res.string.action_retry),
                        onRetry = if (error.isRetryable) viewModel::refresh else null,
                        modifier = fill,
                    )
                }

                else -> BahrEmptyView(message = stringResource(Res.string.trips_empty), modifier = fill)
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = stateModifier.fillMaxSize(),
        contentPadding = PaddingValues(top = listTop, bottom = listBottom),
        // The handoff's 16px gap between trip cards.
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.lg),
    ) {
        item(key = KEY_TOP) {
            Column(
                modifier = Modifier.padding(horizontal = BahrSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(BahrSpacing.lg),
            ) {
                Text(
                    text = stringResource(Res.string.trips_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                header()
            }
        }

        // Sections can arrive after the list (the view model caps how long the list waits for them).
        // Every item is keyed, so a user who has scrolled keeps the card they are on: LazyColumn
        // holds its position by the first visible item's key, and the sections go in above it.
        if (sections.isNotEmpty()) {
            items(sections, key = { "$KEY_SECTION${it.key}" }) { section ->
                HomeSection(
                    section = section,
                    perPersonLabel = perPersonLabel,
                    onTripClick = onTripClick,
                    onAction = onAction,
                )
            }
            // Only with sections above it: on its own the list is already under the title.
            item(key = KEY_ALL_TRIPS) {
                Text(
                    text = stringResource(Res.string.trips_all_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = BahrSpacing.gutter),
                )
            }
            if (state.trips.isEmpty()) {
                item(key = KEY_EMPTY) {
                    Text(
                        text = stringResource(Res.string.trips_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = BahrSpacing.gutter),
                    )
                }
            }
        }

        // Prefixed: a LazyColumn key must be unique across sections, headings and trips.
        items(state.trips, key = { "$KEY_TRIP${it.slug}" }) { trip ->
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

private const val LoadMoreThreshold = 3

private const val KEY_TOP = "top"
private const val KEY_ALL_TRIPS = "all-trips"
private const val KEY_EMPTY = "empty"
private const val KEY_SECTION = "section:"
private const val KEY_TRIP = "trip:"
