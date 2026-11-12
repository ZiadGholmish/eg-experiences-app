package eg.bahr.feature.host.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import eg.bahr.core.catalog.data.appError
import eg.bahr.core.catalog.model.HostCardDto
import eg.bahr.core.catalog.presentation.components.HostDirectoryCard
import eg.bahr.core.catalog.presentation.components.HostDirectoryCardSkeleton
import eg.bahr.core.catalog.presentation.components.ListMotion
import eg.bahr.core.catalog.presentation.components.PagedListStatus
import eg.bahr.core.catalog.presentation.components.RefreshBarBelow
import eg.bahr.core.catalog.presentation.components.bahrAnimateItem
import eg.bahr.core.catalog.presentation.components.rememberListMotion
import eg.bahr.core.catalog.presentation.components.rememberQueryStatus
import eg.bahr.core.catalog.presentation.components.symbolIcon
import eg.bahr.core.catalog.presentation.rememberThrottled
import eg.bahr.core.designsystem.components.BahrBackButton
import eg.bahr.core.designsystem.components.BahrErrorView
import eg.bahr.core.designsystem.components.BahrFilterChip
import eg.bahr.core.designsystem.components.BahrSearchField
import eg.bahr.core.designsystem.components.BahrSkeleton
import eg.bahr.core.designsystem.components.navigationBarsSpacer
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrAlpha
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.reducedMotion
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.hosts_clear_filters
import eg.bahr.core.localization.generated.resources.hosts_count
import eg.bahr.core.localization.generated.resources.hosts_empty
import eg.bahr.core.localization.generated.resources.hosts_empty_search
import eg.bahr.core.localization.generated.resources.hosts_kind_companies
import eg.bahr.core.localization.generated.resources.hosts_kind_people
import eg.bahr.core.localization.generated.resources.hosts_search_hint
import eg.bahr.core.localization.generated.resources.hosts_sort
import eg.bahr.core.localization.generated.resources.hosts_sort_most_trips
import eg.bahr.core.localization.generated.resources.hosts_sort_newest
import eg.bahr.core.localization.generated.resources.hosts_sort_top_rated
import eg.bahr.core.localization.generated.resources.search_clear
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The hosts directory (MV-M1): the Hosts tab's root, and the same list pushed from Home's hosts "See all" and
 * search's "See all hosts". The handoff has no such page; it is built from the trip lists' parts.
 *
 * At the top, Back (pushed only) and the name search. Under it, in the list: the count with the sort menu at its
 * end, then the filter chips (kinds of trip, regions with their host counts, individuals or companies, and "Clear
 * all" once one is on), then the host cards, paged. A change of search, order or filter keeps the cards on screen,
 * dimmed under a thin bar, until the new first page answers (as the category page, M4-M6).
 *
 * The field's text lives here in a TextFieldState and goes to the view model on each change, which debounces it.
 * Nothing takes the focus on the way in: as a tab it must not throw the keyboard up, and pushed it shows a list.
 */
@Composable
internal fun HostsScreen(
    initialQ: String?,
    initialSort: String?,
    onBack: (() -> Unit)?,
    onHostClick: (slug: String, name: String, avatar: String?) -> Unit,
    modifier: Modifier = Modifier,
    scrollToTop: Flow<Unit> = emptyFlow(),
    viewModel: HostsViewModel = koinViewModel { parametersOf(initialQ, initialSort) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hosts = viewModel.hosts.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val status = hosts.rememberQueryStatus()
    val motion = rememberListMotion(status, switchKey = state.query, order = 0)
    val appendRetry = rememberThrottled(hosts::retry)

    // Seeded from the view model, so the text comes back after a configuration change or a process restore.
    val field = rememberTextFieldState(initialText = state.text)
    LaunchedEffect(field) { snapshotFlow { field.text.toString() }.collect(viewModel::onTextChange) }

    val reduced = BahrTheme.reducedMotion
    LaunchedEffect(scrollToTop, reduced) {
        scrollToTop.collect { if (reduced) listState.scrollToItem(0) else listState.animateScrollToItem(0) }
    }

    // imePadding: the list ends above the keyboard, so the last card can be scrolled into view.
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    // With no Back (the tab root), the field starts at the page's gutter.
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
                modifier = Modifier.weight(1f).testTag(HOSTS_FIELD_TAG),
                props =
                    BahrSearchField.Props(
                        state = field,
                        placeholder = stringResource(Res.string.hosts_search_hint),
                        clearLabel = stringResource(Res.string.search_clear),
                        maxLength = HOSTS_MAX_QUERY_LENGTH,
                        // A longer paste is cut to the contract's 80, not dropped (MV-M1 review #1).
                        overflow = BahrSearchField.Overflow.Cut,
                        onSearch = {
                            keyboard?.hide()
                            viewModel.search()
                        },
                        onClear = field::clearText,
                    ),
            )
        }
        val refreshing = status == PagedListStatus.Refreshing
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = BahrSpacing.sm, bottom = BahrSpacing.xl - LIST_GAP),
            verticalArrangement = Arrangement.spacedBy(LIST_GAP),
        ) {
            item(key = KEY_HEADER, contentType = KEY_HEADER) {
                CountAndSort(totalItems = state.totalItems, stale = refreshing, sort = state.sort, onSort = viewModel::selectSort)
            }
            item(key = KEY_CHIPS, contentType = KEY_CHIPS) {
                // The bar is drawn in the gap under the chips, so the cards do not move when it comes and goes.
                RefreshBarBelow(visible = refreshing, gap = LIST_GAP) {
                    HostFilters(
                        state = state,
                        onCategory = viewModel::selectCategory,
                        onGovernorate = viewModel::selectGovernorate,
                        onKind = viewModel::selectKind,
                        onClearAll = viewModel::clearFilters,
                    )
                }
            }
            hostsBody(
                hosts = hosts,
                status = status,
                motion = motion,
                narrowed = state.narrowed,
                onRetry = viewModel::retry,
                onAppendRetry = appendRetry,
                onHostClick = { host -> onHostClick(host.slug, host.name, host.avatar?.url) },
            )
            navigationBarsSpacer()
        }
    }
}

/**
 * How many hosts the query holds (the last count stays, dimmed, while a new query loads; none before the first),
 * and the sort menu at the end of the line: its button names the order, the menu lists the three.
 */
@Composable
private fun CountAndSort(
    totalItems: Long?,
    stale: Boolean,
    sort: HostSort,
    onSort: (HostSort) -> Unit,
) {
    Row(
        // The sort button's own touch padding stands in for most of the end gutter.
        modifier = Modifier.fillMaxWidth().padding(start = BahrSpacing.gutter, end = BahrSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            // None says it twice: the empty line below already says nothing matches.
            totalItems?.takeIf { it > 0 }?.let { count ->
                Text(
                    text = pluralStringResource(Res.plurals.hosts_count, count.toInt(), count.toInt()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier =
                        Modifier
                            .graphicsLayer { alpha = if (stale) BahrAlpha.stale else 1f }
                            // A count that is not the current query's is not read out.
                            .then(if (stale) Modifier.clearAndSetSemantics {} else Modifier.semantics { heading() }),
                )
            }
        }
        SortMenu(sort, onSort)
    }
}

@Composable
private fun SortMenu(
    sort: HostSort,
    onSort: (HostSort) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, modifier = Modifier.heightIn(min = BahrSpacing.minTouch).testTag(SORT_BUTTON_TAG)) {
            Icon(
                BahrIcons.Sort.outlined(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(BahrSize.iconSmall),
            )
            Spacer(Modifier.size(BahrSpacing.xs))
            Text(
                text = stringResource(Res.string.hosts_sort, stringResource(sort.label)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            HostSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    trailingIcon =
                        if (option == sort) {
                            { Icon(BahrIcons.Check.outlined(), contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        } else {
                            null
                        },
                    onClick = {
                        open = false
                        onSort(option)
                    },
                    modifier = Modifier.testTag(sortOptionTag(option)),
                )
            }
        }
    }
}

private val HostSort.label: StringResource
    get() =
        when (this) {
            HostSort.TopRated -> Res.string.hosts_sort_top_rated
            HostSort.MostTrips -> Res.string.hosts_sort_most_trips
            HostSort.Newest -> Res.string.hosts_sort_newest
        }

/**
 * The filter chips, one scrolling line per group so the list starts high: the kinds of trip (Home's categories,
 * with their icons; no count, theirs would be trips), the regions with how many hosts each would show (a zero one
 * dimmed and not tappable unless chosen), then individuals or companies, with "Clear all" at the end once a chip
 * is on. A group with nothing to offer yet is left out.
 */
@Composable
private fun HostFilters(
    state: HostsUiState,
    onCategory: (key: String) -> Unit,
    onGovernorate: (key: String) -> Unit,
    onKind: (HostKindFilter) -> Unit,
    onClearAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
        if (state.categories.isNotEmpty()) {
            ChipLine {
                state.categories.forEach { chip ->
                    val key = chip.key ?: return@forEach
                    BahrFilterChip(
                        label = chip.label.orEmpty(),
                        selected = key == state.category,
                        onClick = { onCategory(key) },
                        icon = chip.icon?.let { symbolIcon(it).filled() },
                    )
                }
            }
        }
        if (state.governorates.isNotEmpty()) {
            ChipLine {
                state.governorates.forEach { chip ->
                    BahrFilterChip(
                        label = chip.label,
                        selected = chip.key == state.governorate,
                        onClick = { onGovernorate(chip.key) },
                        count = chip.count,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = BahrSpacing.gutter, end = BahrSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
        ) {
            HostKindFilter.entries.forEach { kind ->
                BahrFilterChip(
                    label = stringResource(kind.label),
                    selected = kind == state.kind,
                    onClick = { onKind(kind) },
                )
            }
            Spacer(Modifier.weight(1f))
            if (state.hasFilters) {
                TextButton(
                    onClick = onClearAll,
                    modifier = Modifier.heightIn(min = BahrSpacing.minTouch).testTag(CLEAR_FILTERS_TAG),
                ) {
                    Text(
                        text = stringResource(Res.string.hosts_clear_filters),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private val HostKindFilter.label: StringResource
    get() =
        when (this) {
            HostKindFilter.Person -> Res.string.hosts_kind_people
            HostKindFilter.Company -> Res.string.hosts_kind_companies
        }

/** One line of chips that scrolls sideways, starting at the gutter (a few chips: no lazy row). */
@Composable
private fun ChipLine(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = BahrSpacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/**
 * The hosts under the filters: the first page's loading, error (retry reloads it) or empty state, else the cards
 * and, under them, the next page loading or its error. Each state is its own keyed item, so one fades into the next.
 * [narrowed]: a search or a filter is on, so an empty answer is "no hosts match", not "no hosts yet".
 */
private fun LazyListScope.hostsBody(
    hosts: LazyPagingItems<HostCardDto>,
    status: PagedListStatus,
    motion: ListMotion,
    narrowed: Boolean,
    onRetry: () -> Unit,
    onAppendRetry: () -> Unit,
    onHostClick: (HostCardDto) -> Unit,
) {
    when (status) {
        PagedListStatus.Loading ->
            item(key = KEY_LOADING, contentType = KEY_LOADING) {
                BahrSkeleton(modifier = bahrAnimateItem().padding(horizontal = BahrSpacing.gutter)) {
                    repeat(FIRST_PAGE_SKELETON_CARDS) { HostDirectoryCardSkeleton() }
                }
            }

        is PagedListStatus.Failed ->
            item(key = KEY_ERROR, contentType = KEY_ERROR) {
                BahrErrorView(
                    message = status.error.localizedMessage(),
                    retryLabel = stringResource(Res.string.action_retry),
                    onRetry = if (status.error.isRetryable) onRetry else null,
                    modifier = bahrAnimateItem(),
                )
            }

        PagedListStatus.Empty ->
            item(key = KEY_EMPTY, contentType = KEY_EMPTY) {
                Text(
                    text = stringResource(if (narrowed) Res.string.hosts_empty_search else Res.string.hosts_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = bahrAnimateItem().fillMaxWidth().padding(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.xl),
                )
            }

        PagedListStatus.Loaded, PagedListStatus.Refreshing -> {
            val stale = status == PagedListStatus.Refreshing
            items(
                count = hosts.itemCount,
                key = hosts.itemKey { "$KEY_HOST${it.slug}" },
                contentType = hosts.itemContentType { CONTENT_HOST },
            ) { index ->
                // Null only for a placeholder, and placeholders are off.
                val host = hosts[index] ?: return@items
                Box(
                    modifier =
                        bahrAnimateItem()
                            .graphicsLayer { with(motion) { applyTo() } }
                            .testTag(hostRowTag(host.slug))
                            // A stale card is not the result: screen readers skip it (the bar's slot says "busy").
                            .then(if (stale) Modifier.clearAndSetSemantics {} else Modifier)
                            .padding(horizontal = BahrSpacing.gutter),
                ) {
                    HostDirectoryCard(host = host, onClick = { onHostClick(host) }, enabled = !stale)
                }
            }
            if (!stale) appendFooter(hosts, onAppendRetry)
        }
    }
}

/** The next page: its first card loading, or its error with a retry (Paging does not retry a page by itself). */
private fun LazyListScope.appendFooter(
    hosts: LazyPagingItems<HostCardDto>,
    onAppendRetry: () -> Unit,
) {
    when (val append = hosts.loadState.append) {
        is LoadState.Loading ->
            item(key = KEY_APPEND_LOADING, contentType = KEY_LOADING) {
                BahrSkeleton(modifier = bahrAnimateItem().padding(horizontal = BahrSpacing.gutter)) { HostDirectoryCardSkeleton() }
            }

        is LoadState.Error ->
            item(key = KEY_APPEND_ERROR, contentType = KEY_ERROR) {
                val error = append.error.appError()
                BahrErrorView(
                    message = error.localizedMessage(),
                    retryLabel = stringResource(Res.string.action_retry),
                    onRetry = if (error.isRetryable) onAppendRetry else null,
                    modifier = bahrAnimateItem(),
                )
            }

        is LoadState.NotLoading -> Unit
    }
}

/** For tests: the search field. */
internal const val HOSTS_FIELD_TAG = "hosts_field"

/** For tests: the sort menu's button. */
internal const val SORT_BUTTON_TAG = "hosts_sort"

/** For tests: "Clear all". */
internal const val CLEAR_FILTERS_TAG = "hosts_clear_filters"

/** For tests: one order in the sort menu. */
internal fun sortOptionTag(sort: HostSort): String = "hosts_sort:${sort.key}"

/** For tests: one host's row in the list. */
internal fun hostRowTag(slug: String): String = "host_row:$slug"

/** The gap between the list's items; the refresh bar is drawn in the middle of the one under the chips. */
private val LIST_GAP = BahrSpacing.lg

/** Three cards fill a phone screen under the filters. */
private const val FIRST_PAGE_SKELETON_CARDS = 3

// Keys of the single items, each also its item's content type (one drawing each, M3-M11). Prefixed host keys: a
// LazyColumn key must be unique across the header, chips, states and cards.
private const val KEY_HEADER = "header"
private const val KEY_CHIPS = "chips"
private const val KEY_LOADING = "loading"
private const val KEY_ERROR = "error"
private const val KEY_EMPTY = "empty"
private const val KEY_APPEND_LOADING = "append-loading"
private const val KEY_APPEND_ERROR = "append-error"
private const val KEY_HOST = "host:"
private const val CONTENT_HOST = "host-card"
