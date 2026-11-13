package eg.bahr.feature.mytrips.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import eg.bahr.core.catalog.data.appError
import eg.bahr.core.common.result.AppError
import eg.bahr.core.designsystem.components.BahrEmptyView
import eg.bahr.core.designsystem.components.BahrErrorView
import eg.bahr.core.designsystem.components.BahrLottie
import eg.bahr.core.designsystem.components.BahrPrimaryButton
import eg.bahr.core.designsystem.lifecycle.RefreshesOnReturn
import eg.bahr.core.designsystem.theme.BahrElevation
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrShadow
import eg.bahr.core.designsystem.theme.reducedMotion
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.mytrips_device_waitlists
import eg.bahr.core.localization.generated.resources.mytrips_device_waitlists_note
import eg.bahr.core.localization.generated.resources.mytrips_empty_upcoming
import eg.bahr.core.localization.generated.resources.mytrips_section_header
import eg.bahr.core.localization.generated.resources.mytrips_section_past
import eg.bahr.core.localization.generated.resources.mytrips_section_upcoming
import eg.bahr.core.localization.generated.resources.mytrips_section_waitlist
import eg.bahr.core.localization.generated.resources.mytrips_sign_in
import eg.bahr.core.localization.generated.resources.mytrips_signed_out_body
import eg.bahr.core.localization.generated.resources.mytrips_signed_out_title
import eg.bahr.core.localization.generated.resources.mytrips_title
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.mytrips.model.MyBookingDto
import eg.bahr.feature.mytrips.model.MyWaitlistEntryDto
import eg.bahr.feature.mytrips.presentation.components.BookingRow
import eg.bahr.feature.mytrips.presentation.components.DeviceJoinRow
import eg.bahr.feature.mytrips.presentation.components.TripRowsSkeleton
import eg.bahr.feature.mytrips.presentation.components.WaitlistRow
import eg.bahr.feature.mytrips.presentation.components.rateable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * My trips (M3-M0), the fifth tab's root. The handoff has no such screen; the layout is the steps' own (see the
 * M3-M0 and MV-M2 reports): the title, then signed out a sign-in card (a sailing-boat hero since M3-M8; the coral
 * button is the screen's one step forward) and the device's own waiting lists under it; signed in, since MV-M2 one
 * list of sections (Upcoming, Waiting list, Past), each under a sticky header with its count, pulled down to read
 * all three again.
 *
 * [onSignIn] opens the sign-in sheet; [onBookingClick] a booking's details (MV-M6: an Upcoming or Past row, with
 * whether it may be rated, the row's own rule); [onTripClick] the trip page (a waiting-list entry's trip); [onRateTrip]
 * the review page of a finished booking (M3-M4), whose status the row shows once it is back (the list is read again
 * on return).
 * [scrollToTop] fires when the selected tab is tapped again.
 */
@Composable
internal fun MyTripsScreen(
    onSignIn: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    onBookingClick: (ref: String, canRate: Boolean) -> Unit = { _, _ -> },
    onRateTrip: (ref: String, title: String?) -> Unit = { _, _ -> },
    scrollToTop: Flow<Unit> = emptyFlow(),
    viewModel: MyTripsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val past = viewModel.pastBookings.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    ScrollsToTop(listState, scrollToTop)
    RefreshesOnReturn(viewModel::refresh)
    MyTripsContent(
        state = state,
        past = past,
        actions =
            MyTripsActions(
                onSignIn = onSignIn,
                onTripClick = onTripClick,
                onBookingClick = onBookingClick,
                onRateTrip = onRateTrip,
                onRefresh = viewModel::pullToRefresh,
                onRetryUpcoming = viewModel::retryUpcoming,
                onRetryWaitlist = viewModel::retryWaitlist,
            ),
        listState = listState,
    )
}

internal class MyTripsActions(
    val onSignIn: () -> Unit = {},
    val onTripClick: (String) -> Unit = {},
    val onBookingClick: (ref: String, canRate: Boolean) -> Unit = { _, _ -> },
    val onRateTrip: (ref: String, title: String?) -> Unit = { _, _ -> },
    val onRefresh: () -> Unit = {},
    val onRetryUpcoming: () -> Unit = {},
    val onRetryWaitlist: () -> Unit = {},
)

/** Stateless, for the screenshot tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MyTripsContent(
    state: MyTripsUiState,
    past: LazyPagingItems<MyBookingDto>,
    actions: MyTripsActions,
    listState: LazyListState = rememberLazyListState(),
) {
    val signedIn = state.session is SessionState.SignedIn
    // Read here, in composition, once per change: the list's content below only lays the blocks out.
    val blocks =
        if (signedIn) sectionBlocks(state.upcoming.status(), state.waitlist.status(), past.status(state.pastTotal)) else emptyList()
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = actions.onRefresh,
        // Signed out there is nothing of the account's to read again.
        enabled = signedIn,
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag(MY_TRIPS_LIST_TAG),
            contentPadding = PaddingValues(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
        ) {
            item(key = "title", contentType = CONTENT_HEADING) {
                Text(
                    text = stringResource(Res.string.mytrips_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }.padding(bottom = BahrSpacing.sm),
                )
            }
            when (state.session) {
                // The stored sign-in is read in a moment; drawing neither state until then keeps the
                // signed-out prompt or a bookings skeleton from flashing (as the Alerts tab does, M3-M14).
                SessionState.Unknown -> Unit
                SessionState.SignedOut -> signedOut(state, actions)
                is SessionState.SignedIn -> signedIn(state, blocks, past, actions)
            }
        }
    }
}

private fun LazyListScope.signedOut(
    state: MyTripsUiState,
    actions: MyTripsActions,
) {
    item(key = "prompt", contentType = CONTENT_SIGN_IN) { SignInCard(actions.onSignIn) }
    if (state.deviceJoins.isNotEmpty()) {
        item(key = "device_title", contentType = CONTENT_DEVICE_HEADING) {
            Column(Modifier.padding(top = BahrSpacing.md), verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
                Text(stringResource(Res.string.mytrips_device_waitlists), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(Res.string.mytrips_device_waitlists_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.deviceJoins, key = { "device_${it.departureId}" }, contentType = { CONTENT_DEVICE_JOIN }) { join ->
            DeviceJoinRow(join, onClick = { actions.onTripClick(join.tripSlug) })
        }
    }
}

/** The sections in their order ([sectionBlocks]); none at all is the empty state. */
private fun LazyListScope.signedIn(
    state: MyTripsUiState,
    blocks: List<SectionBlock>,
    past: LazyPagingItems<MyBookingDto>,
    actions: MyTripsActions,
) {
    if (blocks.isEmpty()) {
        item(key = "empty", contentType = CONTENT_EMPTY) {
            // No trips yet (M3-M8b): a suitcase packing and unpacking itself while on screen. Decorative:
            // the line under it says it.
            Centered {
                BahrEmptyView(stringResource(Res.string.mytrips_empty_upcoming)) {
                    BahrLottie(
                        modifier = Modifier.width(BahrSize.lottieState),
                        props = BahrLottie.Props(BahrLottie.Animation.Suitcase, playback = BahrLottie.Playback.Loop),
                    )
                }
            }
        }
        return
    }
    blocks.forEach { block ->
        val section = block.section
        when (block) {
            is SectionBlock.Skeleton ->
                item(key = "${section.key}_loading", contentType = CONTENT_LOADING) { TripRowsSkeleton(FIRST_PAGE_SKELETON_ROWS) }
            is SectionBlock.Error -> {
                header(section, count = null)
                item(key = "${section.key}_error", contentType = CONTENT_ERROR) {
                    SectionError(block.error, onRetry = section.retry(actions, past))
                }
            }
            is SectionBlock.Rows -> {
                header(section, block.count)
                when (section) {
                    MyTripsSection.Upcoming -> upcomingRows(state.upcoming, actions)
                    MyTripsSection.Waitlist -> waitlistRows(state.waitlist, actions)
                    MyTripsSection.Past -> pastRows(past, actions)
                }
            }
        }
    }
}

/** The section's sticky header: it stays at the top while its rows scroll under it, read as a heading. */
private fun LazyListScope.header(
    section: MyTripsSection,
    count: Int?,
) {
    stickyHeader(key = "${section.key}_header", contentType = CONTENT_SECTION_HEADER) {
        val label = stringResource(section.label())
        Text(
            text = count?.let { stringResource(Res.string.mytrips_section_header, label, it) } ?: label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .fillMaxWidth()
                    // Opaque: rows scroll under a header that has stuck.
                    .background(MaterialTheme.colorScheme.background)
                    .padding(vertical = BahrSpacing.sm)
                    .semantics { heading() }
                    .testTag(sectionHeaderTag(section)),
        )
    }
}

private fun LazyListScope.upcomingRows(
    load: SectionLoad<MyBookingDto>,
    actions: MyTripsActions,
) {
    val bookings = (load as? SectionLoad.Loaded)?.items.orEmpty()
    // Keys carry the section: a booking that has just moved from Upcoming to Past may sit in both for a moment.
    items(bookings, key = { "upcoming_${it.ref}" }, contentType = { CONTENT_BOOKING }) { booking ->
        BookingRow(
            booking,
            onClick = { actions.onBookingClick(booking.ref, booking.rateable) },
            onRateTrip = { actions.onRateTrip(booking.ref, booking.trip?.title) },
        )
    }
}

private fun LazyListScope.waitlistRows(
    load: SectionLoad<MyWaitlistEntryDto>,
    actions: MyTripsActions,
) {
    val entries = (load as? SectionLoad.Loaded)?.items.orEmpty()
    items(entries, key = { "waitlist_${it.id}" }, contentType = { CONTENT_WAITLIST_ENTRY }) { entry ->
        WaitlistRow(entry, onClick = { entry.trip?.slug?.let(actions.onTripClick) })
    }
}

private fun LazyListScope.pastRows(
    past: LazyPagingItems<MyBookingDto>,
    actions: MyTripsActions,
) {
    // `ref` is never null (the contract requires it); Paging's itemKey covers the placeholder case.
    items(
        count = past.itemCount,
        key = past.itemKey { "past_${it.ref}" },
        contentType = past.itemContentType { CONTENT_BOOKING },
    ) { index ->
        past[index]?.let { booking ->
            BookingRow(
                booking,
                onClick = { actions.onBookingClick(booking.ref, booking.rateable) },
                onRateTrip = { actions.onRateTrip(booking.ref, booking.trip?.title) },
            )
        }
    }
    when (val append = past.loadState.append) {
        is LoadState.Loading -> item(key = "past_more", contentType = CONTENT_LOADING) { TripRowsSkeleton(APPEND_SKELETON_ROWS) }
        is LoadState.Error ->
            item(key = "past_more_error", contentType = CONTENT_ERROR) { SectionError(append.error.appError(), past::retry) }
        is LoadState.NotLoading -> Unit
    }
}

/** A section that could not be read: why, and its own "Try again" when trying again can help. */
@Composable
private fun SectionError(
    error: AppError,
    onRetry: () -> Unit,
) {
    Centered {
        BahrErrorView(
            message = error.localizedMessage(),
            retryLabel = stringResource(Res.string.action_retry),
            onRetry = if (error.isRetryable) onRetry else null,
        )
    }
}

/**
 * Past as the sections need it, from the paged list: rows shown win over any reload; empty only once a read has
 * reached the end, so the first frame (before the pager says it is loading) never draws the empty state.
 */
private fun LazyPagingItems<MyBookingDto>.status(total: Long?): SectionStatus {
    val refresh = loadState.refresh
    return when {
        itemCount > 0 -> SectionStatus.Shown(total?.takeIf { it in 1..Int.MAX_VALUE }?.toInt())
        refresh is LoadState.Error -> SectionStatus.Failed(refresh.error.appError())
        refresh is LoadState.NotLoading && loadState.append.endOfPaginationReached -> SectionStatus.Empty
        else -> SectionStatus.Loading
    }
}

private fun MyTripsSection.retry(
    actions: MyTripsActions,
    past: LazyPagingItems<MyBookingDto>,
): () -> Unit =
    when (this) {
        MyTripsSection.Upcoming -> actions.onRetryUpcoming
        MyTripsSection.Waitlist -> actions.onRetryWaitlist
        MyTripsSection.Past -> past::retry
    }

/** A state view in a list: a fixed band, not the whole screen (the list has no height to fill). */
@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = BahrSpacing.xxl * STATE_BAND)) { content() }
}

@Composable
private fun SignInCard(onSignIn: () -> Unit) {
    val x = BahrTheme.colors
    val shape = BahrTheme.shapes.card
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .bahrShadow(BahrElevation.Level1, shape, x)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(BahrSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        // The card's hero (M3-M8): a boat on the lake, rocking for as long as it is on screen (M3-M8b, the
        // user's call); still off screen, in the background and under reduced motion. Its water is drawn in
        // `surface`, the card's own ground. Decorative: the title says what the card is for. Kept to a
        // modest height so this phone's waiting lists stay in view under the card.
        BahrLottie(
            modifier = Modifier.align(Alignment.CenterHorizontally).height(BahrSize.lottieHero),
            props = BahrLottie.Props(BahrLottie.Animation.Sailboat, playback = BahrLottie.Playback.Loop),
        )
        Text(stringResource(Res.string.mytrips_signed_out_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(Res.string.mytrips_signed_out_body),
            style = MaterialTheme.typography.bodyMedium,
            color = x.onSurfaceSecondary,
        )
        BahrPrimaryButton(
            text = stringResource(Res.string.mytrips_sign_in),
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth().testTag(SIGN_IN_TAG),
        )
    }
}

/** Back to the top on a re-tap of the tab; at once when the system asks for less motion. */
@Composable
private fun ScrollsToTop(
    list: LazyListState,
    requests: Flow<Unit>,
) {
    val reduced = BahrTheme.reducedMotion
    LaunchedEffect(requests, reduced) {
        requests.collect { if (reduced) list.scrollToItem(0) else list.animateScrollToItem(0) }
    }
}

private fun MyTripsSection.label(): StringResource =
    when (this) {
        MyTripsSection.Upcoming -> Res.string.mytrips_section_upcoming
        MyTripsSection.Waitlist -> Res.string.mytrips_section_waitlist
        MyTripsSection.Past -> Res.string.mytrips_section_past
    }

/** The section's part of its items' keys. */
private val MyTripsSection.key: String get() = name.lowercase()

/** For tests. */
internal fun sectionHeaderTag(section: MyTripsSection) = "mytrips_header_${section.key}"

internal const val SIGN_IN_TAG = "mytrips_sign_in"
internal const val MY_TRIPS_LIST_TAG = "mytrips_list"

/** A state view's band in the list: this many of the largest spacing step. */
private const val STATE_BAND = 4

/*
 * Content types (M3-M11, Compose audit M1): the list reuses an item's composition only for an item
 * drawn the same way, e.g. a booking row for the next booking row, never for a state view.
 */
private const val CONTENT_HEADING = "heading"

/** Rows a first page or the waiting list shows while it loads: about a phone screen's worth. */
private const val FIRST_PAGE_SKELETON_ROWS = 4

/** The next page shows its first row loading. */
private const val APPEND_SKELETON_ROWS = 1

private const val CONTENT_LOADING = "loading"
private const val CONTENT_ERROR = "error"
private const val CONTENT_EMPTY = "empty"
private const val CONTENT_SIGN_IN = "sign-in"
private const val CONTENT_DEVICE_HEADING = "device-heading"
private const val CONTENT_DEVICE_JOIN = "device-join"
private const val CONTENT_SECTION_HEADER = "section-header"
private const val CONTENT_BOOKING = "booking"
private const val CONTENT_WAITLIST_ENTRY = "waitlist-entry"
