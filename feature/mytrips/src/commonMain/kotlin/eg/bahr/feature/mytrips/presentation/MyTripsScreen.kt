package eg.bahr.feature.mytrips.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import eg.bahr.core.catalog.data.appError
import eg.bahr.core.designsystem.components.BahrEmptyView
import eg.bahr.core.designsystem.components.BahrErrorView
import eg.bahr.core.designsystem.components.BahrLoadingView
import eg.bahr.core.designsystem.components.BahrPrimaryButton
import eg.bahr.core.designsystem.lifecycle.RefreshesOnReturn
import eg.bahr.core.designsystem.theme.BahrElevation
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrShadow
import eg.bahr.core.designsystem.theme.reducedMotion
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.mytrips_device_waitlists
import eg.bahr.core.localization.generated.resources.mytrips_device_waitlists_note
import eg.bahr.core.localization.generated.resources.mytrips_empty_past
import eg.bahr.core.localization.generated.resources.mytrips_empty_upcoming
import eg.bahr.core.localization.generated.resources.mytrips_empty_waitlist
import eg.bahr.core.localization.generated.resources.mytrips_segment_past
import eg.bahr.core.localization.generated.resources.mytrips_segment_upcoming
import eg.bahr.core.localization.generated.resources.mytrips_segment_waitlist
import eg.bahr.core.localization.generated.resources.mytrips_sign_in
import eg.bahr.core.localization.generated.resources.mytrips_signed_out_body
import eg.bahr.core.localization.generated.resources.mytrips_signed_out_title
import eg.bahr.core.localization.generated.resources.mytrips_title
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.mytrips.model.MyBookingDto
import eg.bahr.feature.mytrips.presentation.components.BookingRow
import eg.bahr.feature.mytrips.presentation.components.DeviceJoinRow
import eg.bahr.feature.mytrips.presentation.components.WaitlistRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * My trips (M3-M0), the fifth tab's root. The handoff has no such screen; the layout is this step's
 * (see the step report): the title, then signed out a sign-in card (the coral button is the screen's
 * one step forward) and the device's own waiting lists under it; signed in, the three segments as a
 * segmented control (as Settings' language) over one list.
 *
 * [onSignIn] opens the sign-in sheet; [onTripClick] the trip page (a booking or entry's trip).
 * [scrollToTop] fires when the selected tab is tapped again.
 */
@Composable
internal fun MyTripsScreen(
    onSignIn: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    scrollToTop: Flow<Unit> = emptyFlow(),
    viewModel: MyTripsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bookings = viewModel.bookings.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    ScrollsToTop(listState, scrollToTop)
    RefreshesOnReturn(viewModel::refresh)
    MyTripsContent(
        state = state,
        bookings = bookings,
        actions =
            MyTripsActions(
                onSignIn = onSignIn,
                onTripClick = onTripClick,
                onSegment = viewModel::selectSegment,
                onRetryWaitlist = viewModel::retryWaitlist,
            ),
        listState = listState,
    )
}

internal class MyTripsActions(
    val onSignIn: () -> Unit = {},
    val onTripClick: (String) -> Unit = {},
    val onSegment: (MyTripsSegment) -> Unit = {},
    val onRetryWaitlist: () -> Unit = {},
)

/** Stateless, for the screenshot tests. */
@Composable
internal fun MyTripsContent(
    state: MyTripsUiState,
    bookings: LazyPagingItems<MyBookingDto>,
    actions: MyTripsActions,
    listState: LazyListState = rememberLazyListState(),
) {
    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.statusBars)
                .testTag(MY_TRIPS_LIST_TAG),
        contentPadding = PaddingValues(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        item(key = "title") {
            Text(
                text = stringResource(Res.string.mytrips_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }.padding(bottom = BahrSpacing.sm),
            )
        }
        when (state.session) {
            SessionState.Unknown -> item(key = "loading") { BahrLoadingView(Modifier.heightIn(min = BahrSpacing.xxl)) }
            SessionState.SignedOut -> signedOut(state, actions)
            is SessionState.SignedIn -> signedIn(state, bookings, actions)
        }
    }
}

private fun LazyListScope.signedOut(
    state: MyTripsUiState,
    actions: MyTripsActions,
) {
    item(key = "prompt") { SignInCard(actions.onSignIn) }
    if (state.deviceJoins.isNotEmpty()) {
        item(key = "device_title") {
            Column(Modifier.padding(top = BahrSpacing.md), verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
                Text(stringResource(Res.string.mytrips_device_waitlists), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(Res.string.mytrips_device_waitlists_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.deviceJoins, key = { "device_${it.departureId}" }) { join ->
            DeviceJoinRow(join, onClick = { actions.onTripClick(join.tripSlug) })
        }
    }
}

private fun LazyListScope.signedIn(
    state: MyTripsUiState,
    bookings: LazyPagingItems<MyBookingDto>,
    actions: MyTripsActions,
) {
    item(key = "segments") { Segments(state.segment, actions.onSegment) }
    when (state.segment) {
        MyTripsSegment.Upcoming -> bookingList(bookings, Res.string.mytrips_empty_upcoming, actions)
        MyTripsSegment.Past -> bookingList(bookings, Res.string.mytrips_empty_past, actions)
        MyTripsSegment.Waitlist -> waitlist(state.waitlist, actions)
    }
}

private fun LazyListScope.bookingList(
    bookings: LazyPagingItems<MyBookingDto>,
    empty: StringResource,
    actions: MyTripsActions,
) {
    when (val refresh = bookings.loadState.refresh) {
        is LoadState.Loading -> if (bookings.itemCount == 0) item(key = "bookings_loading") { Centered { BahrLoadingView() } }
        is LoadState.Error ->
            if (bookings.itemCount == 0) {
                item(key = "bookings_error") {
                    val error = refresh.error.appError()
                    Centered {
                        BahrErrorView(
                            message = error.localizedMessage(),
                            retryLabel = stringResource(Res.string.action_retry),
                            onRetry = if (error.isRetryable) bookings::retry else null,
                        )
                    }
                }
            }
        is LoadState.NotLoading ->
            if (bookings.itemCount == 0) item(key = "bookings_empty") { Centered { BahrEmptyView(stringResource(empty)) } }
    }
    items(count = bookings.itemCount, key = { index -> bookings.peek(index)?.ref ?: "booking_$index" }) { index ->
        bookings[index]?.let { booking ->
            BookingRow(booking, onClick = { booking.trip?.slug?.let(actions.onTripClick) })
        }
    }
    if (bookings.loadState.append is LoadState.Loading) {
        item(key = "bookings_more") {
            BahrLoadingView(Modifier.heightIn(min = BahrSpacing.xxl))
        }
    }
}

private fun LazyListScope.waitlist(
    load: WaitlistLoad,
    actions: MyTripsActions,
) {
    when (load) {
        WaitlistLoad.Loading -> item(key = "waitlist_loading") { Centered { BahrLoadingView() } }
        is WaitlistLoad.Failed ->
            item(key = "waitlist_error") {
                Centered {
                    BahrErrorView(
                        message = load.error.localizedMessage(),
                        retryLabel = stringResource(Res.string.action_retry),
                        onRetry = if (load.error.isRetryable) actions.onRetryWaitlist else null,
                    )
                }
            }
        is WaitlistLoad.Loaded ->
            if (load.entries.isEmpty()) {
                item(key = "waitlist_empty") { Centered { BahrEmptyView(stringResource(Res.string.mytrips_empty_waitlist)) } }
            } else {
                items(load.entries, key = { "waitlist_${it.id}" }) { entry ->
                    WaitlistRow(entry, onClick = { entry.trip?.slug?.let(actions.onTripClick) })
                }
            }
    }
}

/** A state view in a list: a fixed band, not the whole screen (the list has no height to fill). */
@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = BahrSpacing.xxl * STATE_BAND)) { content() }
}

@Composable
private fun Segments(
    selected: MyTripsSegment,
    onSelect: (MyTripsSegment) -> Unit,
) {
    val segments = MyTripsSegment.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        segments.forEachIndexed { index, segment ->
            SegmentedButton(
                selected = segment == selected,
                onClick = { onSelect(segment) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = segments.size),
                colors =
                    SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        inactiveContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                // The tick would crowd three Arabic labels; the fill says which is picked.
                icon = {},
                modifier = Modifier.heightIn(min = BahrSpacing.minTouch).testTag(segmentTag(segment)),
            ) {
                Text(text = stringResource(segment.label()), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
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

private fun MyTripsSegment.label(): StringResource =
    when (this) {
        MyTripsSegment.Upcoming -> Res.string.mytrips_segment_upcoming
        MyTripsSegment.Waitlist -> Res.string.mytrips_segment_waitlist
        MyTripsSegment.Past -> Res.string.mytrips_segment_past
    }

/** For tests. */
internal fun segmentTag(segment: MyTripsSegment) = "mytrips_segment_${segment.name.lowercase()}"

internal const val SIGN_IN_TAG = "mytrips_sign_in"
internal const val MY_TRIPS_LIST_TAG = "mytrips_list"

/** A state view's band in the list: this many of the largest spacing step. */
private const val STATE_BAND = 4
