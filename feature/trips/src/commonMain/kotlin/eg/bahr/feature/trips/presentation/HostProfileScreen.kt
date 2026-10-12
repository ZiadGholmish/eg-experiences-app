package eg.bahr.feature.trips.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.catalog.presentation.components.ListHeading
import eg.bahr.core.catalog.presentation.components.listBody
import eg.bahr.core.catalog.presentation.components.rememberListMotion
import eg.bahr.core.catalog.presentation.components.rememberQueryStatus
import eg.bahr.core.catalog.presentation.rememberThrottled
import eg.bahr.core.designsystem.components.BahrEmptyView
import eg.bahr.core.designsystem.components.BahrErrorView
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.bahrTween
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.host_not_available
import eg.bahr.core.localization.generated.resources.host_trips_empty
import eg.bahr.core.localization.generated.resources.host_trips_heading
import eg.bahr.core.localization.generated.resources.trip_per_person
import eg.bahr.core.localization.generated.resources.trips_title
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import eg.bahr.feature.trips.presentation.components.HostHeader
import eg.bahr.feature.trips.presentation.components.ListTopBar
import eg.bahr.feature.trips.presentation.components.RatingSummary
import eg.bahr.feature.trips.presentation.components.ReviewCard
import eg.bahr.feature.trips.presentation.components.SeeAllReviewsLink
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The host page (M4-M10), opened from the trip page's host card: the header (photo, name, verified
 * mark, person or company, member since, trips run), the rating summary and the newest reviews, then
 * the host's trips, paged (tap → the trip page). The handoff has no host page; it follows the trip
 * page's host card and reviews and the category page's list.
 *
 * M4-M11: "See all reviews" under the previews ([onSeeAllReviews], with the host's name) when the summary
 * counts more reviews than they show.
 *
 * Text first: the name and photo the card showed are drawn at once; the facts and the rating shimmer
 * until `getHost` answers, and the trips load beside it. The back button stays in every state, the
 * error and "not available" included: the tab bar is hidden here, and iOS has no system Back.
 */
@Composable
internal fun HostProfileScreen(
    slug: String,
    name: String?,
    avatar: String?,
    onBack: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    modifier: Modifier = Modifier,
    onSeeAllReviews: ((name: String) -> Unit)? = null,
    viewModel: HostProfileViewModel = koinViewModel(key = "host:$slug") { parametersOf(slug, name, avatar) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val trips = viewModel.trips.collectAsLazyPagingItems()
    val error = state.error
    val page =
        when {
            state.notAvailable -> HostPage.NotAvailable
            error != null && state.profile == null -> HostPage.Failed
            else -> HostPage.Shown
        }

    Column(modifier = modifier.fillMaxSize()) {
        ListTopBar(onBack = onBack)
        // The states crossfade, so a 404 landing after the card's name was drawn does not jump.
        val fade = bahrTween<Float>()
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
            modifier = Modifier.weight(1f),
        ) { shown ->
            when (shown) {
                HostPage.NotAvailable ->
                    BahrEmptyView(
                        message = stringResource(Res.string.host_not_available),
                        modifier = Modifier.testTag(HOST_NOT_AVAILABLE_TAG),
                    )

                HostPage.Failed ->
                    error?.let {
                        BahrErrorView(
                            message = it.localizedMessage(),
                            retryLabel = stringResource(Res.string.action_retry),
                            onRetry = if (it.isRetryable) viewModel::retry else null,
                        )
                    } ?: Box(Modifier.fillMaxSize())

                HostPage.Shown -> HostPageList(slug, state, trips, onTripClick, viewModel::retryTrips, onSeeAllReviews)
            }
        }
    }
}

/** What fills the page under the back button. */
private enum class HostPage { Shown, Failed, NotAvailable }

@Composable
private fun HostPageList(
    slug: String,
    state: HostProfileUiState,
    trips: LazyPagingItems<TripCardDto>,
    onTripClick: (slug: String) -> Unit,
    onRetryTrips: () -> Unit,
    onSeeAllReviews: ((name: String) -> Unit)?,
) {
    val listState = rememberLazyListState()
    val perPersonLabel = stringResource(Res.string.trip_per_person)
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + BahrSpacing.xl
    val status = trips.rememberQueryStatus()
    // One list, never switched: no chip order to slide from.
    val motion = rememberListMotion(status, switchKey = slug, order = 0)
    val appendRetry = rememberThrottled(trips::retry)
    val padded = Modifier.padding(horizontal = BahrSpacing.gutter)
    val profile = state.profile

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag(HOST_PAGE_TAG),
        contentPadding = PaddingValues(bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.lg),
    ) {
        item(key = KEY_HEADER) { HostHeader(slug, state.name, state.avatar, profile, padded) }
        if (profile != null) {
            item(key = KEY_RATING) { RatingSummary(profile.rating, padded.padding(top = BahrSpacing.sm)) }
            items(profile.reviews.withIndex().toList(), key = { "$KEY_REVIEW${it.index}" }) { (_, review) ->
                Box(padded) { ReviewCard(review) }
            }
            // M4-M11: only when the summary counts more reviews than the preview shows.
            if (onSeeAllReviews != null && profile.rating.count > profile.reviews.size) {
                item(key = KEY_SEE_ALL_REVIEWS) { SeeAllReviewsLink(onClick = { onSeeAllReviews(profile.name) }, modifier = padded) }
            }
        }
        item(key = KEY_TRIPS_HEADING) {
            ListHeading(
                label = state.name?.let { stringResource(Res.string.host_trips_heading, it) } ?: stringResource(Res.string.trips_title),
                totalItems = state.totalTrips,
                modifier = padded.padding(top = BahrSpacing.lg),
            )
        }
        listBody(
            trips = trips,
            status = status,
            motion = motion,
            perPersonLabel = perPersonLabel,
            waitlistTags = state.waitlistTags,
            emptyMessage = { stringResource(Res.string.host_trips_empty) },
            onRetry = onRetryTrips,
            onAppendRetry = appendRetry,
            onTripClick = onTripClick,
        )
    }
}

/** For tests: the host page's list, and its "not available" state. */
internal const val HOST_PAGE_TAG = "host_page"
internal const val HOST_NOT_AVAILABLE_TAG = "host_not_available"

/** Prefixed: a LazyColumn key must be unique across the header, the reviews and the trips (`trip:<slug>`). */
private const val KEY_HEADER = "host-header"
private const val KEY_RATING = "host-rating"
private const val KEY_REVIEW = "host-review:"
private const val KEY_TRIPS_HEADING = "host-trips-heading"
private const val KEY_SEE_ALL_REVIEWS = "host-see-all-reviews"
