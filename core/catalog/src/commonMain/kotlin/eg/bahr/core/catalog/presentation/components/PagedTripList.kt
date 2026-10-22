package eg.bahr.core.catalog.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import eg.bahr.core.catalog.data.appError
import eg.bahr.core.catalog.model.FacetDto
import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.catalog.presentation.WaitlistTags
import eg.bahr.core.common.result.AppError
import eg.bahr.core.designsystem.components.BahrErrorView
import eg.bahr.core.designsystem.components.BahrFilterChip
import eg.bahr.core.designsystem.components.BahrSkeleton
import eg.bahr.core.designsystem.theme.BahrAlpha
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrTween
import eg.bahr.core.designsystem.theme.bahrTweenOrNull
import eg.bahr.core.designsystem.theme.reducedMotion
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.a11y_busy
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.trips_count
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.sign

/*
 * The parts every paged trip list shares (M4-M1b): Home's "All trips", the category page, a row's
 * "See all" and search (M4-M3). Paging's load states drive the same loading / error / retry / empty UI the list had
 * before it was paged.
 *
 * M4-M6: a list whose query changes (a filter chip, a new search) keeps its cards on screen, dimmed
 * and not tappable, under a thin progress bar until the new first page answers; the new cards then
 * come in (sliding from the tapped chip's side), or the error with its retry replaces the old ones.
 * Items fade and move into place (`animateItem`); the loading, error and empty states crossfade.
 * Every animation is instant under reduce motion.
 */

/** Where a paged list's first page stands. */
sealed interface PagedListStatus {
    data object Loading : PagedListStatus

    /**
     * A new query's first page is loading while the previous query's cards are still held (M4-M6):
     * they stay on screen, dimmed and not tappable, under the progress bar. Never "the result".
     */
    data object Refreshing : PagedListStatus

    data class Failed(
        val error: AppError,
    ) : PagedListStatus

    data object Empty : PagedListStatus

    data object Loaded : PagedListStatus
}

/**
 * The first page's state, on Home's "All trips". Cards on screen win: a refresh with a list showing
 * keeps it (and returning to Home shows the cached cards on the first frame, so its scroll position
 * holds). Empty only once the first load has finished with nothing and there is nothing more to
 * append, so "no trips" never flashes before the first answer.
 */
internal val LazyPagingItems<*>.status: PagedListStatus
    get() {
        val refresh = loadState.refresh
        return when {
            itemCount > 0 -> PagedListStatus.Loaded
            refresh is LoadState.Error -> PagedListStatus.Failed(refresh.error.appError())
            refresh is LoadState.NotLoading && loadState.append.endOfPaginationReached -> PagedListStatus.Empty
            else -> PagedListStatus.Loading
        }
    }

/**
 * The first page's state on a list whose query can change under it (the category page's filter
 * chips, search). Paging's presenter keeps the previous query's cards until the new query's first
 * page arrives. While it loads, those cards show as [PagedListStatus.Refreshing] (dimmed, under the
 * progress bar, M4-M6); a failed first page wins over them, so a switch ends on the new cards or on
 * the error with its retry, never the old filter's cards under the new chip (M4-M1b review #1).
 *
 * [afterFailure]: the last first page shown was an error. Its Retry then shows the spinner, not the
 * cards from before the error coming back dimmed, which would read as if the error had gone.
 *
 * A cached list comes back with its refresh state settled, so returning to it still shows its cards at once.
 */
fun LazyPagingItems<*>.queryStatus(afterFailure: Boolean): PagedListStatus =
    when (val refresh = loadState.refresh) {
        is LoadState.Loading -> if (itemCount > 0 && !afterFailure) PagedListStatus.Refreshing else PagedListStatus.Loading
        is LoadState.Error -> PagedListStatus.Failed(refresh.error.appError())
        is LoadState.NotLoading -> status
    }

/** [queryStatus], remembering whether the last settled first page failed. */
@Composable
fun LazyPagingItems<*>.rememberQueryStatus(): PagedListStatus {
    var afterFailure by remember { mutableStateOf(false) }
    val refresh = loadState.refresh
    LaunchedEffect(refresh) {
        when (refresh) {
            is LoadState.Error -> afterFailure = true
            is LoadState.NotLoading -> afterFailure = false
            is LoadState.Loading -> Unit
        }
    }
    return queryStatus(afterFailure)
}

/**
 * How a query list's cards move (M4-M6): dimmed while [PagedListStatus.Refreshing], and when a new
 * query's cards land, sliding in from the side of the chip that was tapped while fading in. A chip
 * after the previous one (in reading order) brings the cards in from the end side, one before it
 * from the start side; a new search text, with no chip move, only fades. Applied in the draw phase
 * (`graphicsLayer`), so the animation does not recompose the cards.
 */
@Stable
class ListMotion internal constructor(
    private val dim: State<Float>,
    private val settle: Animatable<Float, *>,
    private val pending: State<Boolean>,
    private val direction: State<Int>,
    private val layoutSign: State<Int>,
) {
    /** For a card: its dim, and its slide and fade while new cards come in. */
    fun GraphicsLayerScope.applyTo() {
        val progress = if (pending.value) 0f else settle.value
        alpha = dim.value * progress
        translationX = (1f - progress) * BahrMotion.ListSlideFraction * size.width * direction.value * layoutSign.value
    }
}

/**
 * [ListMotion] for a list showing the query [switchKey]; [order] is the active chip's position
 * among the chips (any constant when the list has none).
 */
@Composable
fun rememberListMotion(
    status: PagedListStatus,
    switchKey: Any?,
    order: Int,
): ListMotion {
    val reduced = BahrTheme.reducedMotion
    val dim = animateFloatAsState(if (status == PagedListStatus.Refreshing) BahrAlpha.stale else 1f, bahrTween(BahrMotion.Short))
    val settle = remember { Animatable(1f) }

    // The query whose cards are on screen, and its chip's position.
    var shownKey by remember { mutableStateOf(switchKey) }
    var shownOrder by remember { mutableStateOf(order) }
    var direction by remember { mutableStateOf(0) }
    // The query whose first page was last seen loading. Right after a chip tap the presenter still
    // reports the old cards as loaded for a frame; only cards that follow the new query's own
    // loading are its result, so only those slide in.
    var loadedAfter by remember { mutableStateOf<Any?>(switchKey) }
    val landing = status == PagedListStatus.Loaded && switchKey != shownKey && loadedAfter == switchKey
    val incoming = (order - shownOrder).sign

    // Decided in this composition, so the new cards' first frame is already at the slide's start.
    val pending = rememberUpdatedState(landing && !reduced)
    val currentDirection = rememberUpdatedState(if (landing) incoming else direction)
    val layoutSign = rememberUpdatedState(if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1)

    // The slide runs in its own scope: a later status change must not cancel it half-way.
    val scope = rememberCoroutineScope()
    LaunchedEffect(status, switchKey) {
        when {
            status == PagedListStatus.Loading || status == PagedListStatus.Refreshing -> loadedAfter = switchKey

            landing -> {
                direction = incoming
                shownKey = switchKey
                shownOrder = order
                if (reduced) {
                    settle.snapTo(1f)
                } else {
                    settle.snapTo(0f)
                    scope.launch { settle.animateTo(1f, tween(BahrMotion.Medium, easing = BahrMotion.Standard)) }
                }
            }

            // An empty answer is shown too: the next landing slides relative to it.
            status == PagedListStatus.Empty && loadedAfter == switchKey -> {
                shownKey = switchKey
                shownOrder = order
            }

            else -> Unit
        }
    }
    return remember { ListMotion(dim, settle, pending, currentDirection, layoutSign) }
}

/**
 * `animateItem` with the design system's timing: an item fades in and out and slides to its new
 * place when the list around it changes. Instant under reduce motion. Public for feature:trips' reviews
 * list (M4-M11), the second paged list whose items are not trip cards.
 */
@Composable
fun LazyItemScope.bahrAnimateItem(): Modifier =
    Modifier.animateItem(
        fadeInSpec = bahrTweenOrNull(),
        placementSpec = bahrTweenOrNull(),
        fadeOutSpec = bahrTweenOrNull(),
    )

/**
 * The cards, keyed `trip:<slug>` (unique: the view models drop repeated cards), then a footer while
 * the next page loads or after it failed. Paging does not retry a failed page by itself, so the
 * footer's retry is the way on (it reloads only that page); [onAppendRetry] is that retry, throttled
 * by the caller.
 *
 * [stale]: the cards belong to the previous query (M4-M6): drawn through [motion], dimmed, and not tappable.
 */
internal fun LazyListScope.pagedTripCards(
    trips: LazyPagingItems<TripCardDto>,
    perPersonLabel: String,
    waitlistTags: WaitlistTags,
    onTripClick: (slug: String) -> Unit,
    onAppendRetry: () -> Unit = trips::retry,
    // Null: no motion (Home's "All trips", which keeps its cards through a refresh).
    motion: ListMotion? = null,
    stale: Boolean = false,
) {
    items(
        count = trips.itemCount,
        key = trips.itemKey { "$KEY_TRIP${it.slug}" },
        contentType = trips.itemContentType { CONTENT_TRIP_CARD },
    ) { index ->
        // Null only for a placeholder, and placeholders are off; nothing to draw then.
        val trip = trips[index] ?: return@items
        val moving = motion?.let { Modifier.graphicsLayer { with(it) { applyTo() } } } ?: Modifier
        Row(
            modifier =
                bahrAnimateItem()
                    .then(moving)
                    .testTag(tripRowTag(trip.slug))
                    // A stale card is not the result: screen readers skip it, and the refresh slot's
                    // live region says "busy" (M4-M6 review #3, M4-M4), as the stale count does.
                    .then(if (stale) Modifier.clearAndSetSemantics {} else Modifier)
                    .padding(horizontal = BahrSpacing.gutter),
        ) {
            TripCard(
                trip = trip,
                perPersonLabel = perPersonLabel,
                onClick = { onTripClick(trip.slug) },
                waitlisted = waitlistTags.shows(trip),
                enabled = !stale,
            )
        }
    }
    if (stale) return
    when (val append = trips.loadState.append) {
        is LoadState.Loading ->
            // The next page's first card, still loading (M3-M14), where the spinner used to be.
            item(key = KEY_APPEND_LOADING, contentType = CONTENT_LOADING) {
                TripListSkeleton(cards = APPEND_SKELETON_CARDS, modifier = bahrAnimateItem())
            }

        is LoadState.Error ->
            item(key = KEY_APPEND_ERROR, contentType = CONTENT_ERROR) {
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

/**
 * The thin progress bar of a refreshing list (M4-M6): drawn while [visible], in a slot that keeps its
 * height either way, so the cards under it do not move when it comes and goes.
 *
 * Screen readers (M4-M6 review #8): the stale cards are hidden from them, so without a word a refresh
 * would be silent. The slot is a polite live region that says "busy" while the bar shows: it is
 * always there, and its description changing is what gets announced (a live region that only just
 * appeared may not be), once per refresh, after whatever is being read.
 */
@Composable
fun RefreshBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val busy = stringResource(Res.string.a11y_busy)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(BahrSize.progressBar)
                .testTag(REFRESH_SLOT_TAG)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    if (visible) contentDescription = busy
                },
    ) {
        AnimatedVisibility(visible = visible, enter = fadeIn(bahrTween()), exit = fadeOut(bahrTween())) {
            LinearProgressIndicator(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(BahrSize.progressBar)
                        .testTag(REFRESH_BAR_TAG),
                color = MaterialTheme.colorScheme.primary,
                trackColor = BahrTheme.colors.track,
            )
        }
    }
}

/**
 * The trips under a list's header: its first page's loading, error (retry reloads it) or empty state,
 * else the paged cards. The states are list items, so the header and chips above stay put while a
 * new filter loads, and each has its own key so one fades into the next.
 *
 * [status] comes from [rememberQueryStatus]. The refresh bar is the screen's: under the field in
 * search, in the gap under the chips on the category page ([RefreshBarBelow]), so the list holds no
 * extra item and the cards sit where they did before M4-M6. [onRetry] reloads the first page (the view model's, throttled); [onAppendRetry] a failed next page.
 */
fun LazyListScope.listBody(
    trips: LazyPagingItems<TripCardDto>,
    status: PagedListStatus,
    motion: ListMotion?,
    perPersonLabel: String,
    waitlistTags: WaitlistTags,
    emptyMessage: @Composable () -> String,
    onRetry: () -> Unit,
    onAppendRetry: () -> Unit,
    onTripClick: (slug: String) -> Unit,
) {
    when (status) {
        PagedListStatus.Loading ->
            // One item for the whole skeleton: its cards come and go together (M3-M14).
            item(key = KEY_LIST_LOADING, contentType = CONTENT_LOADING) {
                TripListSkeleton(cards = FIRST_PAGE_SKELETON_CARDS, modifier = bahrAnimateItem())
            }

        is PagedListStatus.Failed ->
            item(key = KEY_LIST_ERROR, contentType = CONTENT_ERROR) {
                BahrErrorView(
                    message = status.error.localizedMessage(),
                    retryLabel = stringResource(Res.string.action_retry),
                    onRetry = if (status.error.isRetryable) onRetry else null,
                    modifier = bahrAnimateItem(),
                )
            }

        PagedListStatus.Empty ->
            item(key = KEY_LIST_EMPTY, contentType = CONTENT_EMPTY) {
                Text(
                    text = emptyMessage(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = bahrAnimateItem().padding(horizontal = BahrSpacing.gutter),
                )
            }

        PagedListStatus.Loaded, PagedListStatus.Refreshing ->
            pagedTripCards(
                trips = trips,
                perPersonLabel = perPersonLabel,
                waitlistTags = waitlistTags,
                onTripClick = onTripClick,
                onAppendRetry = onAppendRetry,
                motion = motion,
                stale = status == PagedListStatus.Refreshing,
            )
    }
}

/**
 * A list's title and, once the first page is in, how many trips it holds: under the title, or with
 * [countAtEnd] at the end of the title's line (HANDOFF Home: "All trips" with its result count at the
 * end, M4-M7; Home only, so the trip lists' headers and their M4-M6 morphs stay as they are).
 *
 * The count does not jump (M4-M6): a new count crossfades in, and while a new query loads the
 * previous count keeps its line, dimmed when [stale] (the old cards are still on screen) and hidden
 * otherwise, so the chips and cards under it stay where they are.
 */
@Composable
fun ListHeading(
    label: String?,
    totalItems: Long?,
    modifier: Modifier = Modifier,
    stale: Boolean = false,
    countAtEnd: Boolean = false,
) {
    val title: @Composable (Modifier) -> Unit = { titleModifier ->
        label?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = MaterialTheme.typography.titleLarge, modifier = titleModifier.semantics { heading() })
        }
    }
    if (countAtEnd) {
        Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { title(Modifier) }
            TripCount(totalItems, stale)
        }
    } else {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
            title(Modifier)
            TripCount(totalItems, stale)
        }
    }
}

/** [ListHeading]'s count: the last one shown stays (dimmed when [stale]) while a new query loads. */
@Composable
private fun TripCount(
    totalItems: Long?,
    stale: Boolean,
) {
    // The last count shown, recorded after the frame commits (a discarded composition records nothing).
    var last by remember { mutableStateOf(totalItems) }
    SideEffect { if (totalItems != null) last = totalItems }
    val shown = totalItems ?: last ?: return
    val alpha by animateFloatAsState(
        when {
            totalItems != null -> 1f
            stale -> BahrAlpha.stale
            else -> 0f
        },
        bahrTween(BahrMotion.Short),
    )
    val fade = bahrTween<Float>(BahrMotion.Short)
    AnimatedContent(
        targetState = shown.toInt(),
        transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) using SizeTransform(clip = false) },
        modifier =
            Modifier
                .graphicsLayer { this.alpha = alpha }
                // A count that is not the current query's is not read out.
                .then(if (totalItems == null) Modifier.clearAndSetSemantics {} else Modifier),
    ) { count ->
        Text(
            text = pluralStringResource(Res.plurals.trips_count, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A list's filter chips (`type == filter` facets) with their live counts, on the category page and
 * in search. They wrap onto a second line rather than scroll (the handoff's Home chips); a zero-count
 * chip is dimmed and cannot be tapped, unless it is the active one.
 */
@Composable
fun FacetFilterChips(
    chips: List<FacetDto>,
    isSelected: (FacetDto) -> Boolean,
    onSelect: (key: String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.padding(horizontal = BahrSpacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        chips.forEach { chip ->
            val key = chip.key ?: return@forEach
            BahrFilterChip(
                label = chip.label.orEmpty(),
                selected = isSelected(chip),
                onClick = { onSelect(key) },
                icon = chip.icon?.let { symbolIcon(it).filled() },
                count = chip.count,
            )
        }
    }
}

/** The active chip's position among [chips], for the direction a filter switch slides; 0 for none. */
fun selectedOrder(
    chips: List<FacetDto>,
    isSelected: (FacetDto) -> Boolean,
): Int = chips.indexOfFirst(isSelected).coerceAtLeast(0)

/**
 * [content] (a list item: the chips, or the header) with the refresh bar drawn in the list's gap just
 * under it, centred in [gap]. Drawn over the gap rather than laid out, so it takes no space and the
 * cards do not move when it comes and goes (M4-M6 review #2).
 */
@Composable
fun RefreshBarBelow(
    visible: Boolean,
    gap: Dp,
    content: @Composable () -> Unit,
) {
    Box {
        content()
        RefreshBar(
            visible = visible,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = BahrSpacing.gutter)
                    // From the item's bottom edge to the middle of the gap.
                    .offset(y = (gap + BahrSize.progressBar) / 2),
        )
    }
}

/** For tests: one trip card's row in a paged list (found even when its content is hidden from accessibility). */
fun tripRowTag(slug: String): String = "trip_row:$slug"

/** For tests: the thin progress bar of a refreshing list. */
const val REFRESH_BAR_TAG = "refresh_bar"

/** For tests: the refresh bar's slot, the live region that says "busy" (drawn whether or not the bar shows). */
const val REFRESH_SLOT_TAG = "refresh_slot"

/** Prefixed: a LazyColumn key must be unique across headings, chips, states and trips. */
internal const val KEY_TRIP = "trip:"

/**
 * A list of trip cards still loading (M3-M14): [cards] [TripCardSkeleton]s in the gutter, at the
 * list's own gap, read out as one "Loading".
 */
@Composable
fun TripListSkeleton(
    cards: Int,
    modifier: Modifier = Modifier,
) {
    BahrSkeleton(modifier = modifier.padding(horizontal = BahrSpacing.gutter)) {
        repeat(cards) { TripCardSkeleton() }
    }
}

/** Two cards fill a phone screen under a list's header; a third would only be cut off. */
private const val FIRST_PAGE_SKELETON_CARDS = 2

/** The next page shows its first card loading, under the last one loaded. */
private const val APPEND_SKELETON_CARDS = 1

private const val KEY_APPEND_LOADING = "append-loading"
private const val KEY_APPEND_ERROR = "append-error"
private const val KEY_LIST_LOADING = "list-loading"
private const val KEY_LIST_ERROR = "list-error"
private const val KEY_LIST_EMPTY = "list-empty"

/*
 * Content types (M3-M11, Compose audit M1): a lazy list reuses an item's composition only for an item
 * of the same type, so a scrolled-away card is rebuilt as the next card, never as a state view.
 */
private const val CONTENT_TRIP_CARD = "trip-card"
private const val CONTENT_LOADING = "loading"
private const val CONTENT_ERROR = "error"
private const val CONTENT_EMPTY = "empty"
