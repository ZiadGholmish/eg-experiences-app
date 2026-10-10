package eg.bahr.feature.trips.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import eg.bahr.core.designsystem.components.BahrEmptyView
import eg.bahr.core.designsystem.components.BahrErrorView
import eg.bahr.core.designsystem.components.BahrLoadingView
import eg.bahr.core.designsystem.components.BahrPillButton
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrBorder
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.home_footer_note
import eg.bahr.core.localization.generated.resources.home_wordmark
import eg.bahr.core.localization.generated.resources.map_open
import eg.bahr.core.localization.generated.resources.search_hint
import eg.bahr.core.localization.generated.resources.trip_per_person
import eg.bahr.core.localization.generated.resources.trips_all_title
import eg.bahr.core.localization.generated.resources.trips_empty
import eg.bahr.core.localization.generated.resources.trips_filter_empty
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import eg.bahr.feature.trips.model.key
import eg.bahr.feature.trips.navigation.HomeAction
import eg.bahr.feature.trips.presentation.components.FacetFilterChips
import eg.bahr.feature.trips.presentation.components.HomeSection
import eg.bahr.feature.trips.presentation.components.ListHeading
import eg.bahr.feature.trips.presentation.components.PagedListStatus
import eg.bahr.feature.trips.presentation.components.RefreshBarBelow
import eg.bahr.feature.trips.presentation.components.listBody
import eg.bahr.feature.trips.presentation.components.rememberListMotion
import eg.bahr.feature.trips.presentation.components.rememberQueryStatus
import eg.bahr.feature.trips.presentation.components.selectedOrder
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Home: the trips on sale. [header] is drawn under the search entry in the list, and at the top of the
 * loading, error and empty views, so it is never hidden behind a failed list; another feature fills
 * it through `tripListScreen(header = …)` (M2-M4: "Continue your booking").
 * It is handed the gap that separates it from what is above, as a modifier for its content: drawn
 * inside the slot's own enter/exit animation, the gap grows and shrinks with it (M4-M4), where a gap
 * the list kept would make everything below jump at the end of an exit. With nothing to show it
 * must draw nothing at all, gap included.
 *
 * Top to bottom (HANDOFF Home, M4-M1): the app bar (logo, wordmark, the Map pill), the search entry
 * (M4-M3, not in the handoff), the header, Home's server-driven sections in the server's order
 * (M4-M1a), the filter chips (wrapping, with live counts), "All trips" with its count, the cards, and
 * once the last page is in, the footer note. A banner tap goes to [onAction]; a trip card in a row to
 * [onTripClick], like one in the list.
 *
 * A filter tap reloads only the list (M4-M6 rules, as on the category page): the cards stay, dimmed,
 * until the new ones slide in from the tapped chip's side, or the error with its retry replaces them.
 * The whole screen is a spinner, an error or "no trips" only until the list has first answered.
 */
@Composable
internal fun TripListScreen(
    onTripClick: (slug: String) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable (gap: Modifier) -> Unit = {},
    onAction: (HomeAction) -> Unit = {},
    onSearch: () -> Unit = {},
    onMap: (() -> Unit)? = null,
    viewModel: TripListViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val trips = viewModel.trips.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val perPersonLabel = stringResource(Res.string.trip_per_person)
    val status = trips.rememberQueryStatus()
    val chips = state.filterChips
    val motion = rememberListMotion(status, switchKey = state.filter, order = selectedOrder(chips, state::isSelected))
    val appendRetry = rememberThrottled(trips::retry)

    // The app is edge-to-edge. Home is laid out below the status bar and clipped there: content
    // scrolled up stops at the bar instead of drawing under the clock and icons (M4-M1a: a scrolled
    // banner overlapped them). Applied here, not at the NavHost, because the trip detail hero is
    // meant to run full-bleed under the bar.
    val statusBar = WindowInsets.statusBars
    val stateModifier = modifier.windowInsetsPadding(statusBar)
    // The last card scrolls clear of the gesture bar (M1-M1a review #10).
    val listBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + BahrSpacing.xl

    val sections = state.sections
    // One loading state for the list and Home's sections (see HOME_WAIT_MILLIS), and only until the
    // list has answered once: after that, a reload happens under the chips (they must stay, or a
    // filter that failed or found nothing could not be changed back).
    val showsLoading = state.awaitingHome || !state.listAnswered && status !is PagedListStatus.Failed
    val firstFailure = (status as? PagedListStatus.Failed)?.takeIf { !state.listAnswered && !showsLoading }
    // Nothing on sale at all (no filter to blame). With sections, those still show above "no trips".
    val nothingOnSale = !showsLoading && state.filter == null && status == PagedListStatus.Empty && sections.isEmpty()
    if (showsLoading || firstFailure != null || nothingOnSale) {
        Column(modifier = stateModifier) {
            // The app bar, the map and search stay one tap away while the list loads or fails (M4-M2
            // review #10).
            Column(modifier = Modifier.padding(start = BahrSpacing.gutter, top = BahrSpacing.lg, end = BahrSpacing.gutter)) {
                Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.lg)) {
                    HomeAppBar(onMap = onMap)
                    SearchEntry(onClick = onSearch)
                }
                // The gap above the header is the header's, so it comes and goes with it.
                header(Modifier.padding(top = BahrSpacing.lg))
            }
            val fill = Modifier.weight(1f)
            when {
                showsLoading -> BahrLoadingView(fill)

                firstFailure != null -> {
                    val error = firstFailure.error
                    BahrErrorView(
                        message = error.localizedMessage(),
                        retryLabel = stringResource(Res.string.action_retry),
                        // Reads Home again too, and starts the list over from its first page.
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
        contentPadding = PaddingValues(top = BahrSpacing.lg, bottom = listBottom),
        // The handoff's 16px gap between trip cards.
        verticalArrangement = Arrangement.spacedBy(LIST_GAP),
    ) {
        item(key = KEY_TOP) {
            Column(modifier = Modifier.padding(horizontal = BahrSpacing.gutter)) {
                Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.lg)) {
                    HomeAppBar(onMap = onMap)
                    SearchEntry(onClick = onSearch)
                }
                // The header grows and shrinks inside this item (M4-M4), and the items below follow
                // it frame by frame; its gap is given to it so that shrinks too.
                header(Modifier.padding(top = BahrSpacing.lg))
            }
        }

        // Sections can arrive after the list (the view model caps how long the list waits for them).
        // Every item is keyed, so a user who has scrolled keeps the card they are on: LazyColumn
        // holds its position by the first visible item's key, and the sections go in above it.
        items(sections, key = { "$KEY_SECTION${it.key}" }) { section ->
            HomeSection(
                section = section,
                perPersonLabel = perPersonLabel,
                waitlistTags = state.waitlistTags,
                onTripClick = onTripClick,
                onAction = onAction,
            )
        }

        val refreshing = status == PagedListStatus.Refreshing
        // No trip on sale at all, with no chip to blame (the sections above still show): the sentence
        // under "All trips" says so, and a "0 trips" line over four 0-count chips would only say it
        // twice (as in search, M4-M3 review S1; M4-M1 review #1). With a chip active they stay, so
        // the filter can be changed back.
        val nothingOnSaleHere = state.filter == null && status == PagedListStatus.Empty
        // HANDOFF Home: the filter chips wrap onto a second line rather than scroll; a zero-count chip
        // is dimmed. Only once the server has sent them (an older server sends none).
        if (chips.isNotEmpty() && !nothingOnSaleHere) {
            item(key = KEY_CHIPS) {
                RefreshBarBelow(visible = refreshing, gap = LIST_GAP) {
                    FacetFilterChips(chips, isSelected = state::isSelected, onSelect = viewModel::selectFilter)
                }
            }
        }
        item(key = KEY_ALL_TRIPS) {
            ListHeading(
                label = stringResource(Res.string.trips_all_title),
                totalItems = state.totalItems.takeUnless { nothingOnSaleHere },
                stale = refreshing,
                modifier = Modifier.padding(horizontal = BahrSpacing.gutter),
            )
        }

        // Paged (M4-M1b): the next page loads as the end comes near, with a footer while it does.
        listBody(
            trips = trips,
            status = status,
            motion = motion,
            perPersonLabel = perPersonLabel,
            waitlistTags = state.waitlistTags,
            emptyMessage = { stringResource(if (state.filter != null) Res.string.trips_filter_empty else Res.string.trips_empty) },
            onRetry = viewModel::refresh,
            onAppendRetry = appendRetry,
            onTripClick = onTripClick,
        )

        // Under the last card only: while more pages can load, the note would sit between them.
        if (status == PagedListStatus.Loaded && trips.loadState.append.endOfPaginationReached) {
            item(key = HOME_FOOTER_KEY) { FooterNote(Modifier.padding(horizontal = BahrSpacing.gutter)) }
        }
    }
}

/**
 * HANDOFF Home app bar: the teal logo tile with the `sailing` icon, the "bahr" wordmark in primary,
 * and the Map pill at the end. The wordmark is the screen's heading for screen readers.
 */
@Composable
private fun HomeAppBar(onMap: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        Box(
            modifier =
                Modifier
                    .size(BahrSize.logoTile)
                    .clip(BahrTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                BahrIcons.Sailing.filled(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(BahrSize.iconMedium),
            )
        }
        Text(
            text = stringResource(Res.string.home_wordmark),
            style = BahrTheme.type.wordmark,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        // HANDOFF Home app bar: the "Map" pill at the end (M4-M2).
        onMap?.let { MapPill(onClick = it) }
    }
}

/**
 * HANDOFF Home footer note: a primary-container panel with the bus icon. The handoff's text named one
 * stop and one time for every trip, which the catalogue no longer bears out, so it says where to look.
 */
@Composable
private fun FooterNote(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            BahrIcons.DirectionsBus.filled(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(BahrSize.iconLarge),
        )
        Text(
            text = stringResource(Res.string.home_footer_note),
            style = MaterialTheme.typography.bodyMedium,
            color = BahrTheme.colors.onSurfaceSecondary,
        )
    }
}

/** Home's way into the map (M4-M2, HANDOFF Home app bar: `map` + "Map" on primary-container). */
@Composable
private fun MapPill(onClick: () -> Unit) {
    BahrPillButton(props = BahrPillButton.Props(text = stringResource(Res.string.map_open), icon = BahrIcons.Map, onClick = onClick))
}

/**
 * Home's way into search (M4-M3): looks like a search field, opens the search screen, where the real
 * field takes the keyboard. A button rather than a field here, so Home never brings the keyboard up.
 * Not coral: coral is for the primary action, and search is a way around, not the goal.
 */
@Composable
private fun SearchEntry(onClick: () -> Unit) {
    val shape = BahrTheme.shapes.full
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = BahrSpacing.minTouch)
                .clip(shape)
                .background(BahrTheme.colors.surfaceLowest)
                .border(BahrBorder.hairline, MaterialTheme.colorScheme.outlineVariant, shape)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = BahrSpacing.lg, vertical = BahrSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        Icon(
            BahrIcons.Search.outlined(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(BahrSize.iconLarge),
        )
        Text(
            text = stringResource(Res.string.search_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The gap between Home's items; the refresh bar is drawn in the middle of the one under the chips. */
private val LIST_GAP = BahrSpacing.lg

private const val KEY_TOP = "top"
private const val KEY_CHIPS = "chips"
private const val KEY_ALL_TRIPS = "all-trips"
private const val KEY_SECTION = "section:"

/** The footer note's list key; tests scroll to it. */
internal const val HOME_FOOTER_KEY = "footer"
