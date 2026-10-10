package eg.bahr.feature.trips.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.bahrSharedBounds
import eg.bahr.core.designsystem.theme.bahrTween
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.trip_per_person
import eg.bahr.core.localization.generated.resources.trips_all_title
import eg.bahr.core.localization.generated.resources.trips_empty
import eg.bahr.core.localization.generated.resources.trips_filter_empty
import eg.bahr.feature.trips.model.FacetDto
import eg.bahr.feature.trips.presentation.components.CategoryTile
import eg.bahr.feature.trips.presentation.components.FacetFilterChips
import eg.bahr.feature.trips.presentation.components.ListHeading
import eg.bahr.feature.trips.presentation.components.ListTopBar
import eg.bahr.feature.trips.presentation.components.PagedListStatus
import eg.bahr.feature.trips.presentation.components.RefreshBarBelow
import eg.bahr.feature.trips.presentation.components.listBody
import eg.bahr.feature.trips.presentation.components.rememberListMotion
import eg.bahr.feature.trips.presentation.components.rememberQueryStatus
import eg.bahr.feature.trips.presentation.components.selectedOrder
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
 *
 * M4-M6: a chip switch keeps the cards on screen, dimmed, under a thin progress bar until the new
 * ones slide in from the tapped chip's side; the header crossfades when the category changes (the
 * facets name it, or the fallback to "All trips"). [sharedKey] is the Home element (a chip, a row's
 * title) that morphs into the header on the way in.
 */
@Composable
internal fun CategoryTripsScreen(
    category: String,
    title: String?,
    onBack: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    modifier: Modifier = Modifier,
    sharedKey: String? = null,
    viewModel: CategoryTripsViewModel = koinViewModel(key = "category:$category") { parametersOf(category, title) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val trips = viewModel.trips.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val perPersonLabel = stringResource(Res.string.trip_per_person)
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + BahrSpacing.xl
    val status = trips.rememberQueryStatus()
    val chips = state.filterChips
    val motion =
        rememberListMotion(status, switchKey = state.category to state.filter, order = selectedOrder(chips, state::isSelected))
    val appendRetry = rememberThrottled(trips::retry)

    Column(modifier = modifier.fillMaxSize()) {
        ListTopBar(onBack = onBack)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(LIST_GAP),
        ) {
            val refreshing = status == PagedListStatus.Refreshing
            item(key = KEY_HEADER) {
                // The bar goes under the chips; with none yet, under the header.
                RefreshBarBelow(visible = refreshing && chips.isEmpty(), gap = LIST_GAP) {
                    CategoryHeader(
                        facet = state.categoryFacet,
                        label =
                            when {
                                state.category == null -> stringResource(Res.string.trips_all_title)
                                else -> state.categoryFacet?.label ?: state.fallbackTitle
                            },
                        totalItems = state.totalItems,
                        stale = status == PagedListStatus.Refreshing,
                        modifier = Modifier.padding(horizontal = BahrSpacing.gutter).bahrSharedBounds(sharedKey),
                    )
                }
            }
            if (chips.isNotEmpty()) {
                item(key = KEY_CHIPS) {
                    RefreshBarBelow(visible = refreshing, gap = LIST_GAP) {
                        FacetFilterChips(chips, isSelected = state::isSelected, onSelect = viewModel::selectFilter)
                    }
                }
            }
            listBody(
                trips = trips,
                status = status,
                motion = motion,
                perPersonLabel = perPersonLabel,
                waitlistTags = state.waitlistTags,
                emptyMessage = { stringResource(if (state.filter != null) Res.string.trips_filter_empty else Res.string.trips_empty) },
                onRetry = viewModel::retry,
                onAppendRetry = appendRetry,
                onTripClick = onTripClick,
            )
        }
    }
}

/**
 * The category's tile (its icon on its tone's tint, the handoff's category tile), its label and the
 * trip count. The tile waits for the facets; the label can come from the opener before them.
 *
 * When the category changes (its facet arrives, or the page falls back to "All trips"), the tile and
 * label crossfade into the new ones (M4-M6); the count crossfades on its own in [ListHeading].
 */
@Composable
private fun CategoryHeader(
    facet: FacetDto?,
    label: String?,
    totalItems: Long?,
    stale: Boolean,
    modifier: Modifier = Modifier,
) {
    val fade = bahrTween<Float>()
    AnimatedContent(
        targetState = CategoryTitle(facet, label),
        // The count changes on its own: only a new tile or label crossfades the header.
        contentKey = { it.facet?.key to it.label },
        transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) using SizeTransform(clip = false) },
        modifier = modifier,
    ) { title ->
        CategoryTitleRow(title.facet, title.label, totalItems, stale)
    }
}

/** What the header names: the category's facet (its tile) and label. */
private data class CategoryTitle(
    val facet: FacetDto?,
    val label: String?,
)

@Composable
private fun CategoryTitleRow(
    facet: FacetDto?,
    label: String?,
    totalItems: Long?,
    stale: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        if (facet != null) CategoryTile(icon = facet.icon, tone = facet.tone)
        ListHeading(label = label, totalItems = totalItems, stale = stale)
    }
}

/** The gap between the page's items; the refresh bar is drawn in the middle of the one under the chips. */
private val LIST_GAP = BahrSpacing.lg

private const val KEY_HEADER = "header"
private const val KEY_CHIPS = "chips"
