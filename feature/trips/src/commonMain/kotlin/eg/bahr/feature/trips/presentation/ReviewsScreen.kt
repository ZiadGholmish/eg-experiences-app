package eg.bahr.feature.trips.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import eg.bahr.core.catalog.data.appError
import eg.bahr.core.catalog.presentation.components.ListMotion
import eg.bahr.core.catalog.presentation.components.PagedListStatus
import eg.bahr.core.catalog.presentation.components.RefreshBarBelow
import eg.bahr.core.catalog.presentation.components.bahrAnimateItem
import eg.bahr.core.catalog.presentation.components.rememberListMotion
import eg.bahr.core.catalog.presentation.components.rememberQueryStatus
import eg.bahr.core.catalog.presentation.rememberThrottled
import eg.bahr.core.designsystem.components.BahrEmptyView
import eg.bahr.core.designsystem.components.BahrErrorView
import eg.bahr.core.designsystem.components.BahrFilterChip
import eg.bahr.core.designsystem.components.BahrLoadingView
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.bahrTween
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.host_no_reviews
import eg.bahr.core.localization.generated.resources.host_rating_bar
import eg.bahr.core.localization.generated.resources.reviews_all_trips
import eg.bahr.core.localization.generated.resources.reviews_filter_empty
import eg.bahr.core.localization.generated.resources.reviews_not_available
import eg.bahr.core.localization.generated.resources.reviews_sort_highest
import eg.bahr.core.localization.generated.resources.reviews_sort_lowest
import eg.bahr.core.localization.generated.resources.reviews_sort_newest
import eg.bahr.core.localization.generated.resources.trip_reviews
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import eg.bahr.feature.trips.model.RatingSummaryDto
import eg.bahr.feature.trips.model.ReviewDto
import eg.bahr.feature.trips.model.ReviewSort
import eg.bahr.feature.trips.model.stars
import eg.bahr.feature.trips.presentation.components.ListTopBar
import eg.bahr.feature.trips.presentation.components.RatingSummary
import eg.bahr.feature.trips.presentation.components.ReviewCard
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * "See all reviews" (M4-M11), opened under the review previews of a trip page ([trip]) or a host page
 * ([host]). The handoff has no such page: it follows the category page (M4-M6 chips and list motion)
 * and the host page (its rating summary and review cards).
 *
 * Under the back button: "Reviews" with the trip's title or the host's name, the rating summary of what
 * is listed, the Trip filter (a host's reviews with more than one trip), the sort chips (Most recent,
 * Highest rated, Lowest rated), the star chips (5★ … 1★ with their counts, one at a time, tap again to
 * clear), then the reviews, paged. A chip switch keeps the reviews on screen, dimmed under a thin
 * progress bar, until the new ones slide in from the tapped chip's side (M4-M6). A NOT_FOUND is the one
 * "not available" state, with the back button still there (the tab bar is hidden here).
 */
@Composable
internal fun ReviewsScreen(
    trip: String?,
    host: String?,
    title: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReviewsViewModel =
        koinViewModel(key = host?.let { "reviews:host:$it" } ?: "reviews:trip:$trip") { parametersOf(trip, host, title) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reviews = viewModel.reviews.collectAsLazyPagingItems()

    Column(modifier = modifier.fillMaxSize()) {
        ListTopBar(onBack = onBack)
        // The states crossfade, so a 404 landing after the heading was drawn does not jump.
        val fade = bahrTween<Float>()
        AnimatedContent(
            targetState = state.notAvailable,
            transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
            modifier = Modifier.weight(1f),
        ) { notAvailable ->
            if (notAvailable) {
                BahrEmptyView(
                    message = stringResource(Res.string.reviews_not_available),
                    modifier = Modifier.testTag(REVIEWS_NOT_AVAILABLE_TAG),
                )
            } else {
                ReviewsList(state, reviews, viewModel)
            }
        }
    }
}

@Composable
private fun ReviewsList(
    state: ReviewsUiState,
    reviews: LazyPagingItems<ReviewDto>,
    viewModel: ReviewsViewModel,
) {
    val listState = rememberLazyListState()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + BahrSpacing.xl
    val status = reviews.rememberQueryStatus()
    val motion = rememberListMotion(status, switchKey = state.query, order = state.chipOrder)
    val appendRetry = rememberThrottled(reviews::retry)
    val padded = Modifier.padding(horizontal = BahrSpacing.gutter)
    val rating = state.rating
    val refreshing = status == PagedListStatus.Refreshing

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag(REVIEWS_PAGE_TAG),
        contentPadding = PaddingValues(bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(LIST_GAP),
    ) {
        item(key = KEY_HEADING, contentType = KEY_HEADING) {
            // With no chips yet (the first page is still out), the refresh bar has nowhere else to go.
            RefreshBarBelow(visible = refreshing && rating == null, gap = LIST_GAP) { ReviewsHeading(state.subtitle, padded) }
        }
        // The summary and the chips wait for the first answer: the star chips count from its summary.
        if (rating != null) {
            item(key = KEY_RATING, contentType = KEY_RATING) { RatingHeader(rating, padded) }
            if (state.tripFilter.isNotEmpty()) {
                item(key = KEY_TRIP_FILTER, contentType = KEY_TRIP_FILTER) { TripFilter(state, viewModel::selectTrip) }
            }
            item(key = KEY_CHIPS, contentType = KEY_CHIPS) {
                RefreshBarBelow(visible = refreshing, gap = LIST_GAP) {
                    ReviewChips(state, rating, viewModel::selectSort, viewModel::selectStars)
                }
            }
        }
        reviewsBody(
            reviews = reviews,
            status = status,
            motion = motion,
            emptyMessage = if (state.isFiltered) Res.string.reviews_filter_empty else Res.string.host_no_reviews,
            onRetry = viewModel::retry,
            onAppendRetry = appendRetry,
        )
    }
}

/** "Reviews", and under it the trip's title or the host's name (the filtered trip's, on a host's reviews). */
@Composable
private fun ReviewsHeading(
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
        Text(
            text = stringResource(Res.string.trip_reviews),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        // A new trip picked in the Trip filter crossfades its title in, without a jump.
        val fade = bahrTween<Float>()
        AnimatedContent(
            targetState = subtitle,
            transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) using SizeTransform(clip = false) },
        ) { shown ->
            shown?.let {
                Text(
                    text = it,
                    // A Latin name inside an Arabic page reads in its own direction (see TripStory).
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The summary of what is listed: a Trip filter switch crossfades it into the new trip's (M4-M6). */
@Composable
private fun RatingHeader(
    rating: RatingSummaryDto,
    modifier: Modifier = Modifier,
) {
    val fade = bahrTween<Float>()
    AnimatedContent(
        targetState = rating,
        transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) using SizeTransform(clip = false) },
        modifier = modifier,
    ) { shown ->
        RatingSummary(shown, showTitle = false)
    }
}

/**
 * The host's trips, one chip each after "All trips", in one row that scrolls sideways: trip titles are
 * long, and wrapped they would push the reviews far down.
 */
@Composable
private fun TripFilter(
    state: ReviewsUiState,
    onSelect: (slug: String?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().testTag(REVIEWS_TRIP_FILTER_TAG),
        contentPadding = PaddingValues(horizontal = BahrSpacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        item(key = KEY_ALL_TRIPS, contentType = CONTENT_TRIP_CHIP) {
            BahrFilterChip(
                label = stringResource(Res.string.reviews_all_trips),
                selected = state.tripSlug == null,
                onClick = { onSelect(null) },
            )
        }
        items(state.tripFilter, key = { it.slug }, contentType = { CONTENT_TRIP_CHIP }) { trip ->
            BahrFilterChip(label = trip.title, selected = trip.slug == state.tripSlug, onClick = { onSelect(trip.slug) })
        }
    }
}

/**
 * The sort chips, then the star chips (5 down to 1, each with how many reviews gave that many: the
 * summary's buckets, which a star filter does not narrow). A bucket of 0 is dimmed and cannot be tapped
 * unless it is the active one ([BahrFilterChip]).
 */
@Composable
private fun ReviewChips(
    state: ReviewsUiState,
    rating: RatingSummaryDto,
    onSort: (ReviewSort) -> Unit,
    onStars: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = BahrSpacing.gutter),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm), verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
            ReviewSort.entries.forEach { sort ->
                BahrFilterChip(label = stringResource(sort.label), selected = sort == state.sort, onClick = { onSort(sort) })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm), verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
            rating.stars.forEach { bucket ->
                // Read out as "5 stars: 4", not the bare digits on the chip.
                val spoken = pluralStringResource(Res.plurals.host_rating_bar, bucket.stars, bucket.stars, bucket.count)
                BahrFilterChip(
                    label = bucket.stars.toString(),
                    selected = bucket.stars == state.stars,
                    onClick = { onStars(bucket.stars) },
                    icon = BahrIcons.Star.filled(),
                    count = bucket.count,
                    modifier = Modifier.semantics { contentDescription = spoken }.testTag(starChipTag(bucket.stars)),
                )
            }
        }
    }
}

private val ReviewSort.label: StringResource
    get() =
        when (this) {
            ReviewSort.NEWEST -> Res.string.reviews_sort_newest
            ReviewSort.HIGHEST -> Res.string.reviews_sort_highest
            ReviewSort.LOWEST -> Res.string.reviews_sort_lowest
        }

/**
 * The reviews under the chips: the first page's loading, error (retry reloads it) or empty state, else
 * the paged cards and a footer while the next page loads or after it failed. Core:catalog's `listBody`
 * does this for trip cards; reviews are another item type, so the same states are laid out here, with
 * the same keys' pattern so one state fades into the next.
 */
private fun LazyListScope.reviewsBody(
    reviews: LazyPagingItems<ReviewDto>,
    status: PagedListStatus,
    motion: ListMotion,
    emptyMessage: StringResource,
    onRetry: () -> Unit,
    onAppendRetry: () -> Unit,
) {
    when (status) {
        PagedListStatus.Loading ->
            item(key = KEY_LIST_LOADING, contentType = CONTENT_LOADING) { BahrLoadingView(bahrAnimateItem().padding(BahrSpacing.xl)) }

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
                    text = stringResource(emptyMessage),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = bahrAnimateItem().padding(horizontal = BahrSpacing.gutter).testTag(REVIEWS_EMPTY_TAG),
                )
            }

        PagedListStatus.Loaded, PagedListStatus.Refreshing -> {
            val stale = status == PagedListStatus.Refreshing
            // By position: a review has no id in the contract, and its fields can repeat (two rating-only
            // reviews on one day by the same first name), while a repeated key crashes a LazyColumn.
            // A review id on the shared endpoint would fix this; asked for in M3-M11's report.
            items(
                count = reviews.itemCount,
                key = { "$KEY_REVIEW$it" },
                contentType = reviews.itemContentType { CONTENT_REVIEW },
            ) { index ->
                // Null only for a placeholder, and placeholders are off.
                val review = reviews[index] ?: return@items
                Box(
                    modifier =
                        bahrAnimateItem()
                            .graphicsLayer { with(motion) { applyTo() } }
                            // Stale reviews are not the result: screen readers skip them while the refresh
                            // slot says "busy" (as the trip lists do, M4-M6 review #3).
                            .then(if (stale) Modifier.clearAndSetSemantics {} else Modifier)
                            .padding(horizontal = BahrSpacing.gutter),
                ) {
                    ReviewCard(review)
                }
            }
            if (!stale) appendFooter(reviews, onAppendRetry)
        }
    }
}

/** The next page's spinner, or its error with a retry (Paging does not retry a failed page by itself). */
private fun LazyListScope.appendFooter(
    reviews: LazyPagingItems<ReviewDto>,
    onAppendRetry: () -> Unit,
) {
    when (val append = reviews.loadState.append) {
        is LoadState.Loading ->
            item(key = KEY_APPEND_LOADING, contentType = CONTENT_LOADING) { BahrLoadingView(bahrAnimateItem().padding(BahrSpacing.lg)) }

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

/** For tests: the reviews page's list, its "not available" state, the Trip filter, the empty state and a star chip. */
internal const val REVIEWS_PAGE_TAG = "reviews_page"
internal const val REVIEWS_NOT_AVAILABLE_TAG = "reviews_not_available"
internal const val REVIEWS_TRIP_FILTER_TAG = "reviews_trip_filter"
internal const val REVIEWS_EMPTY_TAG = "reviews_empty"

internal fun starChipTag(stars: Int): String = "reviews_stars:$stars"

/** The gap between the page's items; the refresh bar is drawn in the middle of the one under the chips. */
private val LIST_GAP = BahrSpacing.lg

/** Prefixed: a LazyColumn key must be unique across the heading, the chips, the states and the reviews. */
private const val KEY_HEADING = "reviews-heading"
private const val KEY_RATING = "reviews-rating"
private const val KEY_TRIP_FILTER = "reviews-trip-filter"
private const val KEY_CHIPS = "reviews-chips"
private const val KEY_ALL_TRIPS = "reviews-all-trips"
private const val KEY_REVIEW = "review:"
private const val KEY_LIST_LOADING = "reviews-loading"
private const val KEY_LIST_ERROR = "reviews-error"
private const val KEY_LIST_EMPTY = "reviews-empty"
private const val KEY_APPEND_LOADING = "reviews-append-loading"
private const val KEY_APPEND_ERROR = "reviews-append-error"

/*
 * Content types (M3-M11, Compose audit M1): a single item's is its key (one drawing each); these are
 * the ones several items share, so a scrolled-away review is rebuilt as the next review.
 */
private const val CONTENT_REVIEW = "review"
private const val CONTENT_TRIP_CHIP = "trip-chip"
private const val CONTENT_LOADING = "loading"
private const val CONTENT_ERROR = "error"
private const val CONTENT_EMPTY = "empty"
