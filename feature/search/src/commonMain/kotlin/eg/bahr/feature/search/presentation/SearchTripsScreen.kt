package eg.bahr.feature.search.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import eg.bahr.core.catalog.model.HostCardDto
import eg.bahr.core.catalog.presentation.components.FacetFilterChips
import eg.bahr.core.catalog.presentation.components.HostDirectoryCard
import eg.bahr.core.catalog.presentation.components.ListHeading
import eg.bahr.core.catalog.presentation.components.PagedListStatus
import eg.bahr.core.catalog.presentation.components.RefreshBar
import eg.bahr.core.catalog.presentation.components.listBody
import eg.bahr.core.catalog.presentation.components.rememberListMotion
import eg.bahr.core.catalog.presentation.components.rememberQueryStatus
import eg.bahr.core.catalog.presentation.components.selectedOrder
import eg.bahr.core.catalog.presentation.heartTap
import eg.bahr.core.catalog.presentation.rememberThrottled
import eg.bahr.core.designsystem.components.BahrBackButton
import eg.bahr.core.designsystem.components.BahrLottie
import eg.bahr.core.designsystem.components.BahrSearchField
import eg.bahr.core.designsystem.components.navigationBarsSpacer
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.reducedMotion
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.search_clear
import eg.bahr.core.localization.generated.resources.search_hint
import eg.bahr.core.localization.generated.resources.search_hosts_see_all
import eg.bahr.core.localization.generated.resources.search_hosts_title
import eg.bahr.core.localization.generated.resources.search_no_results
import eg.bahr.core.localization.generated.resources.search_recent_clear
import eg.bahr.core.localization.generated.resources.search_recent_title
import eg.bahr.core.localization.generated.resources.search_start
import eg.bahr.core.localization.generated.resources.trip_per_person
import eg.bahr.core.localization.generated.resources.trips_filter_empty
import eg.bahr.feature.search.data.SearchLimits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Search (M4-M3), opened from Home's search entry. The top bar is Back and the field, focused with
 * the keyboard up on the way in. Under it: the recent searches until two letters are typed, then
 * the count, the filter chips (as on the category page) and the results, paged, best match first.
 *
 * The field's text lives here, in a [TextFieldState] (typing stays in step with the keyboard, Arabic
 * composition included); every change goes to the view model, which debounces it into a search.
 *
 * M4-M6: a new search or chip keeps the current results on screen, dimmed and not tappable, with a
 * thin progress bar under the field, until the new results come in (or the error replaces them).
 *
 * M4-M8: search is the Search tab's root, where there is nothing to go back to: [onBack] null draws
 * no Back. As a tab it does not take the focus on the way in ([focusOnEntry] false; switching tabs
 * must not throw the keyboard up); it takes it on each [focusRequests] signal instead (Home's search
 * entry). [scrollToTop] (a re-tap of the selected tab) scrolls the results back to the top.
 *
 * MV-M1: up to three hosts whose name matches sit above the trips; a card opens the host's page ([onHostClick]),
 * "See all hosts" the hosts directory with the same text ([onSeeAllHosts]).
 */
@Composable
internal fun SearchTripsScreen(
    onBack: (() -> Unit)?,
    onTripClick: (slug: String) -> Unit,
    modifier: Modifier = Modifier,
    onHostClick: (HostCardDto) -> Unit = {},
    onSeeAllHosts: (q: String) -> Unit = {},
    focusOnEntry: Boolean = true,
    focusRequests: Flow<Unit> = emptyFlow(),
    scrollToTop: Flow<Unit> = emptyFlow(),
    onSignIn: () -> Unit = {},
    viewModel: SearchTripsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // A heart tapped by a guest opens the sign-in sheet (MP-M1).
    val onLike = remember(viewModel, onSignIn) { heartTap(viewModel::toggleLike, onSignIn) }
    val trips = viewModel.trips.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val perPersonLabel = stringResource(Res.string.trip_per_person)
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // MV-M11: a result (a trip, a host, "See all hosts") opens another page over this one, and search stays on the back
    // stack. Let go of the field and put the keyboard away first rather than leave it to the field leaving the screen,
    // which let the keyboard stay up over the trip page (iOS ties the keyboard to the focus). Back closes search itself.
    val leave: (() -> Unit) -> Unit = { open ->
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        open()
    }
    val status = trips.rememberQueryStatus()
    val chips = state.filterChips
    val motion = rememberListMotion(status, switchKey = state.query to state.filter, order = selectedOrder(chips, state::isSelected))
    val appendRetry = rememberThrottled(trips::retry)

    // Seeded from the view model, so the text comes back after a configuration change or a process
    // restore. Not across a language switch: that rebuilds the whole AppNavHost (ProvideAppLanguage's
    // key), back stack and view model included, so search starts empty again.
    val field = rememberTextFieldState(initialText = state.text)
    LaunchedEffect(field) { snapshotFlow { field.text.toString() }.collect(viewModel::onTextChange) }

    val focus = remember { FocusRequester() }
    // Only on the way in: coming back from a trip page must not pop the keyboard over the results.
    var focusedOnEntry by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (focusOnEntry && !focusedOnEntry) {
            focus.requestFocus()
            keyboard?.show()
            focusedOnEntry = true
        }
    }
    LaunchedEffect(focusRequests) {
        focusRequests.collect {
            focus.requestFocus()
            keyboard?.show()
        }
    }
    val reduced = BahrTheme.reducedMotion
    LaunchedEffect(scrollToTop, reduced) {
        scrollToTop.collect { if (reduced) listState.scrollToItem(0) else listState.animateScrollToItem(0) }
    }

    // imePadding: the list ends above the keyboard, so the last result can be scrolled into view.
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        SearchBar(
            field = field,
            focus = focus,
            onBack = onBack,
            onSearch = {
                keyboard?.hide()
                viewModel.search()
            },
            onClear = {
                field.clearText()
                focus.requestFocus()
            },
        )
        // Under the field, where the eye is while typing; the list scrolls under it.
        RefreshBar(visible = state.query != null && status == PagedListStatus.Refreshing)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            // The last item ends xl above whatever of the navigation bar the keyboard or the tab bar
            // has left: the spacer item carries that inset, after its own LIST_GAP (Compose audit M3, L14).
            contentPadding = PaddingValues(top = BahrSpacing.sm, bottom = BahrSpacing.xl - LIST_GAP),
            verticalArrangement = Arrangement.spacedBy(LIST_GAP),
        ) {
            val query = state.query
            if (query == null) {
                recentSearches(
                    recents = state.recents,
                    onRecent = { recent ->
                        keyboard?.hide()
                        viewModel.searchRecent(recent)
                        field.setTextAndPlaceCursorAtEnd(recent)
                    },
                    onClearAll = viewModel::clearRecents,
                )
                navigationBarsSpacer()
                return@LazyColumn
            }
            // Nothing matches the words at all (no chip to blame): the sentence below says so, and a
            // "0 trips" line over four 0-count chips would only say it twice (M4-M3 review S1). With a
            // chip active they stay, so the search can be widened.
            val nothingMatches = state.filter == null && status == PagedListStatus.Empty
            // MV-M1: the hosts whose name matches, above the trips, with the whole list behind "See all hosts".
            if (state.hosts.isNotEmpty()) {
                item(key = KEY_HOSTS, contentType = KEY_HOSTS) {
                    SearchHosts(
                        hosts = state.hosts,
                        onHostClick = { host ->
                            viewModel.onResultOpened()
                            leave { onHostClick(host) }
                        },
                        onSeeAll = { leave { onSeeAllHosts(query) } },
                    )
                }
            }
            if (!nothingMatches) {
                item(key = KEY_COUNT, contentType = KEY_COUNT) {
                    ListHeading(
                        label = null,
                        totalItems = state.totalItems,
                        stale = status == PagedListStatus.Refreshing,
                        modifier = Modifier.padding(horizontal = BahrSpacing.gutter),
                    )
                }
            }
            if (chips.isNotEmpty() && !nothingMatches) {
                item(key = KEY_CHIPS, contentType = KEY_CHIPS) {
                    FacetFilterChips(chips, isSelected = state::isSelected, onSelect = viewModel::selectFilter)
                }
            }
            listBody(
                trips = trips,
                status = status,
                motion = motion,
                onRetry = viewModel::retry,
                onAppendRetry = appendRetry,
                perPersonLabel = perPersonLabel,
                waitlistTags = state.waitlistTags,
                likes = state.likes,
                onLike = onLike,
                emptyMessage = {
                    if (state.filter != null) {
                        stringResource(Res.string.trips_filter_empty)
                    } else {
                        stringResource(Res.string.search_no_results, query)
                    }
                },
                // Nothing matches the words at all (M3-M8b): a magnifier looking about while on screen. Not with a
                // chip active: there the line asks to widen the filter, and the list above stays.
                emptyArt =
                    if (nothingMatches) {
                        {
                            BahrLottie(
                                modifier = Modifier.width(BahrSize.lottieState),
                                props = BahrLottie.Props(BahrLottie.Animation.Magnifier, playback = BahrLottie.Playback.Loop),
                            )
                        }
                    } else {
                        null
                    },
                onTripClick = { slug ->
                    viewModel.onResultOpened()
                    leave { onTripClick(slug) }
                },
            )
            navigationBarsSpacer()
        }
    }
}

/**
 * Back (none on the tab root), then the field (core:designsystem's [BahrSearchField] since MV-M1: a search icon, the
 * placeholder, × once there is text), holding at most [SearchLimits.MAX_QUERY_LENGTH] characters, the contract's limit,
 * and no control characters. Carries the status-bar inset (edge-to-edge; each screen handles its own top inset).
 */
@Composable
private fun SearchBar(
    field: TextFieldState,
    focus: FocusRequester,
    onBack: (() -> Unit)?,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                // With no Back (the tab root), the field starts at the page's gutter like Home's entry.
                .padding(
                    start = if (onBack != null) BahrSpacing.lg else BahrSpacing.gutter,
                    end = BahrSpacing.gutter,
                    top = BahrSpacing.md,
                    bottom = BahrSpacing.sm,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        onBack?.let { BahrBackButton(props = BahrBackButton.Props(onClick = it)) }
        BahrSearchField(
            modifier = Modifier.weight(1f).focusRequester(focus).testTag(SEARCH_FIELD_TAG),
            props =
                BahrSearchField.Props(
                    state = field,
                    placeholder = stringResource(Res.string.search_hint),
                    clearLabel = stringResource(Res.string.search_clear),
                    maxLength = SearchLimits.MAX_QUERY_LENGTH,
                    onSearch = onSearch,
                    onClear = onClear,
                ),
        )
    }
}

/**
 * MV-M1: "Hosts" with "See all hosts" at the end of its line (primary, as Home's "See all": coral is for the
 * screen's primary action only), then up to three host cards. A plain column: three at most, never paged.
 */
@Composable
private fun SearchHosts(
    hosts: List<HostCardDto>,
    onHostClick: (HostCardDto) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
        Row(
            // The link's own touch padding stands in for most of the end gutter.
            modifier = Modifier.fillMaxWidth().padding(start = BahrSpacing.gutter, end = BahrSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.search_hosts_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = onSeeAll, modifier = Modifier.heightIn(min = BahrSpacing.minTouch).testTag(SEE_ALL_HOSTS_TAG)) {
                Text(
                    text = stringResource(Res.string.search_hosts_see_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = BahrSpacing.gutter),
            verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
        ) {
            hosts.forEach { host -> HostDirectoryCard(host = host, onClick = { onHostClick(host) }) }
        }
    }
}

/**
 * Before a search: the recent ones, newest first, each tappable to run again, under a heading with
 * "Clear all"; with none yet, a line on what can be searched.
 */
private fun LazyListScope.recentSearches(
    recents: List<String>,
    onRecent: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    if (recents.isEmpty()) {
        item(key = KEY_START, contentType = KEY_START) {
            Text(
                text = stringResource(Res.string.search_start),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = BahrSpacing.gutter),
            )
        }
        return
    }
    item(key = KEY_RECENT_TITLE, contentType = KEY_RECENT_TITLE) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = BahrSpacing.gutter, end = BahrSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.search_recent_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = onClearAll) {
                Text(
                    text = stringResource(Res.string.search_recent_clear),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    // One item, so the rows sit at touch height under each other rather than a card gap apart.
    item(key = KEY_RECENTS, contentType = KEY_RECENTS) {
        Column {
            recents.forEach { recent ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = BahrSpacing.minTouch)
                            .clickable(role = Role.Button) { onRecent(recent) }
                            .padding(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
                ) {
                    Icon(
                        BahrIcons.History.outlined(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(BahrSize.iconMedium),
                    )
                    Text(
                        text = recent,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** For tests: the search field. */
internal const val SEARCH_FIELD_TAG = "search_field"

/** For tests: "See all hosts" over the matching hosts (MV-M1). */
internal const val SEE_ALL_HOSTS_TAG = "see_all_hosts"

/** The gap between the list's items. */
private val LIST_GAP = BahrSpacing.lg

// Keys of the single items, each also its item's content type (one drawing each, M3-M11).
private const val KEY_COUNT = "count"
private const val KEY_HOSTS = "hosts"
private const val KEY_CHIPS = "chips"
private const val KEY_START = "start"
private const val KEY_RECENT_TITLE = "recent-title"
private const val KEY_RECENTS = "recents"
