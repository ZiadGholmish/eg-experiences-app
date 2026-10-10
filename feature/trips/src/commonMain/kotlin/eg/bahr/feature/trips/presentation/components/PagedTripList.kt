package eg.bahr.feature.trips.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import eg.bahr.core.common.result.AppError
import eg.bahr.core.designsystem.components.BahrBackButton
import eg.bahr.core.designsystem.components.BahrErrorView
import eg.bahr.core.designsystem.components.BahrFilterChip
import eg.bahr.core.designsystem.components.BahrLoadingView
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.trips_count
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import eg.bahr.feature.trips.data.appError
import eg.bahr.feature.trips.model.FacetDto
import eg.bahr.feature.trips.model.TripCardDto
import eg.bahr.feature.trips.presentation.WaitlistTags
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/*
 * The parts every paged trip list shares (M4-M1b): Home's "All trips", the category page, a row's
 * "See all" and search (M4-M3). Paging's load states drive the same loading / error / retry / empty UI the list had
 * before it was paged.
 */

/** Where a paged list's first page stands. */
internal sealed interface PagedListStatus {
    data object Loading : PagedListStatus

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
 * chips). Paging's presenter keeps the previous query's cards until the new query's first page
 * arrives, so here a first page that is loading or has failed wins over the cards still held: a
 * switch shows the spinner, then the new cards or the error with its retry, never the old filter's
 * cards under the new chip (M4-M1b review #1). A cached list comes back with its refresh state
 * settled, so returning to it still shows its cards at once.
 */
internal val LazyPagingItems<*>.queryStatus: PagedListStatus
    get() =
        when (val refresh = loadState.refresh) {
            is LoadState.Loading -> PagedListStatus.Loading
            is LoadState.Error -> PagedListStatus.Failed(refresh.error.appError())
            is LoadState.NotLoading -> status
        }

/**
 * The cards, keyed `trip:<slug>` (unique: the view models drop repeated cards), then a footer while
 * the next page loads or after it failed. Paging does not retry a failed page by itself, so the
 * footer's retry is the way on (it reloads only that page).
 */
internal fun LazyListScope.pagedTripCards(
    trips: LazyPagingItems<TripCardDto>,
    perPersonLabel: String,
    waitlistTags: WaitlistTags,
    onTripClick: (slug: String) -> Unit,
) {
    items(count = trips.itemCount, key = trips.itemKey { "$KEY_TRIP${it.slug}" }) { index ->
        // Null only for a placeholder, and placeholders are off; nothing to draw then.
        val trip = trips[index] ?: return@items
        Row(modifier = Modifier.padding(horizontal = BahrSpacing.gutter)) {
            TripCard(
                trip = trip,
                perPersonLabel = perPersonLabel,
                onClick = { onTripClick(trip.slug) },
                waitlisted = waitlistTags.shows(trip),
            )
        }
    }
    when (val append = trips.loadState.append) {
        is LoadState.Loading -> item(key = KEY_APPEND_LOADING) { BahrLoadingView(Modifier.padding(BahrSpacing.lg)) }

        is LoadState.Error ->
            item(key = KEY_APPEND_ERROR) {
                val error = append.error.appError()
                BahrErrorView(
                    message = error.localizedMessage(),
                    retryLabel = stringResource(Res.string.action_retry),
                    onRetry = if (error.isRetryable) trips::retry else null,
                )
            }

        is LoadState.NotLoading -> Unit
    }
}

/**
 * The app bar of a list opened from Home: a round back button. The list's title sits in the larger
 * heading under it. Carries the status-bar inset itself (edge-to-edge; each screen handles its own
 * top inset).
 */
@Composable
internal fun ListTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = BahrSpacing.lg, vertical = BahrSpacing.md),
    ) {
        BahrBackButton(props = BahrBackButton.Props(onClick = onBack))
    }
}

/**
 * The trips under a list's header: its first page's loading, error (retry reloads it) or empty state,
 * else the paged cards. The states are list items, so the header and chips above stay put while a
 * new filter loads.
 */
internal fun LazyListScope.listBody(
    trips: LazyPagingItems<TripCardDto>,
    perPersonLabel: String,
    waitlistTags: WaitlistTags,
    emptyMessage: @Composable () -> String,
    onTripClick: (slug: String) -> Unit,
) {
    when (val status = trips.queryStatus) {
        PagedListStatus.Loading -> item(key = KEY_LIST_STATE) { BahrLoadingView(Modifier.padding(BahrSpacing.xl)) }

        is PagedListStatus.Failed ->
            item(key = KEY_LIST_STATE) {
                BahrErrorView(
                    message = status.error.localizedMessage(),
                    retryLabel = stringResource(Res.string.action_retry),
                    onRetry = if (status.error.isRetryable) trips::retry else null,
                )
            }

        PagedListStatus.Empty ->
            item(key = KEY_LIST_STATE) {
                Text(
                    text = emptyMessage(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = BahrSpacing.gutter),
                )
            }

        PagedListStatus.Loaded -> pagedTripCards(trips, perPersonLabel, waitlistTags, onTripClick)
    }
}

/** A list's title and, once the first page is in, how many trips it holds. */
@Composable
internal fun ListHeading(
    label: String?,
    totalItems: Long?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
        label?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }
        totalItems?.let { total ->
            val count = total.toInt()
            Text(
                text = pluralStringResource(Res.plurals.trips_count, count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A list's filter chips (`type == filter` facets) with their live counts, on the category page and
 * in search. They wrap onto a second line rather than scroll (the handoff's Home chips); a zero-count
 * chip is dimmed and cannot be tapped, unless it is the active one.
 */
@Composable
internal fun FacetFilterChips(
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

/** Prefixed: a LazyColumn key must be unique across headings, chips, states and trips. */
internal const val KEY_TRIP = "trip:"
private const val KEY_APPEND_LOADING = "append-loading"
private const val KEY_APPEND_ERROR = "append-error"
private const val KEY_LIST_STATE = "list-state"
