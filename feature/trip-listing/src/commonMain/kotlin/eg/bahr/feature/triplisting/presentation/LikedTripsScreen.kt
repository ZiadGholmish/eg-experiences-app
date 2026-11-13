package eg.bahr.feature.triplisting.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.catalog.presentation.components.ListHeading
import eg.bahr.core.catalog.presentation.components.ListTopBar
import eg.bahr.core.catalog.presentation.components.listBody
import eg.bahr.core.catalog.presentation.components.rememberListMotion
import eg.bahr.core.catalog.presentation.components.rememberQueryStatus
import eg.bahr.core.catalog.presentation.heartTap
import eg.bahr.core.catalog.presentation.rememberThrottled
import eg.bahr.core.designsystem.components.BahrPrimaryButton
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.lifecycle.RefreshesOnReturn
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.liked_trips_empty
import eg.bahr.core.localization.generated.resources.liked_trips_sign_in
import eg.bahr.core.localization.generated.resources.liked_trips_signed_out_body
import eg.bahr.core.localization.generated.resources.liked_trips_signed_out_title
import eg.bahr.core.localization.generated.resources.liked_trips_title
import eg.bahr.core.localization.generated.resources.trip_per_person
import eg.bahr.core.network.auth.SessionState
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Account → Liked trips (MP-M1): the title and how many, then the liked trips' cards, paged, newest like first, each
 * with its (filled) heart. Signed out (the account was signed out or deleted elsewhere while this page was on the stack),
 * a card offers the sign-in sheet ([onSignIn]); after signing in the list loads. Read again each time the page comes
 * back (a trip liked on its page joins, one taken back leaves).
 */
@Composable
internal fun LikedTripsScreen(
    onBack: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LikedTripsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val trips = viewModel.trips.collectAsLazyPagingItems()
    RefreshesOnReturn(viewModel::refresh)
    val onLike = remember(viewModel, onSignIn) { heartTap(viewModel::toggleLike, onSignIn) }
    LikedTripsContent(
        state = state,
        trips = trips,
        onBack = onBack,
        onTripClick = onTripClick,
        onSignIn = onSignIn,
        onLike = onLike,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

/** Stateless, for the screenshot tests. */
@Composable
internal fun LikedTripsContent(
    state: LikedTripsUiState,
    trips: LazyPagingItems<TripCardDto>,
    onBack: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    onSignIn: () -> Unit,
    onLike: (TripCardDto) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val perPersonLabel = stringResource(Res.string.trip_per_person)
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + BahrSpacing.xl
    val status = trips.rememberQueryStatus()
    // One list, never switched: no chip order to slide from.
    val motion = rememberListMotion(status, switchKey = Unit, order = 0)
    val appendRetry = rememberThrottled(trips::retry)
    val signedIn = state.session is SessionState.SignedIn

    Column(modifier = modifier.fillMaxSize()) {
        ListTopBar(onBack = onBack)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).testTag(LIKED_TRIPS_LIST_TAG),
            contentPadding = PaddingValues(bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(BahrSpacing.lg),
        ) {
            item(key = KEY_HEADER, contentType = KEY_HEADER) {
                ListHeading(
                    label = stringResource(Res.string.liked_trips_title),
                    totalItems = state.totalItems.takeIf { signedIn },
                    modifier = Modifier.padding(horizontal = BahrSpacing.gutter),
                )
            }
            when (state.session) {
                // Nothing until the secure store has answered, so a signed-in reader never sees the card flash.
                SessionState.Unknown -> Unit
                SessionState.SignedOut ->
                    item(key = KEY_SIGN_IN, contentType = KEY_SIGN_IN) {
                        SignInCard(onSignIn, Modifier.padding(horizontal = BahrSpacing.gutter))
                    }
                is SessionState.SignedIn ->
                    listBody(
                        trips = trips,
                        status = status,
                        motion = motion,
                        perPersonLabel = perPersonLabel,
                        waitlistTags = state.waitlistTags,
                        emptyMessage = { stringResource(Res.string.liked_trips_empty) },
                        emptyArt = { HeartDisc() },
                        onRetry = onRetry,
                        onAppendRetry = appendRetry,
                        onTripClick = onTripClick,
                        likes = state.likes,
                        onLike = onLike,
                    )
            }
        }
    }
}

/** The empty list's mark: an outlined heart on the primary tint, the size of the app's other state discs. */
@Composable
private fun HeartDisc() {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(BahrSize.stateDisc)
                .clip(BahrTheme.shapes.full)
                .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        Icon(
            BahrIcons.Favorite.outlined(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(BahrSize.iconState),
        )
    }
}

/** Signed out: why sign in, and the button that opens the sign-in sheet. */
@Composable
private fun SignInCard(
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.card)
                .background(BahrTheme.colors.surfaceLow)
                .padding(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeartDisc()
        Text(
            text = stringResource(Res.string.liked_trips_signed_out_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.liked_trips_signed_out_body),
            style = MaterialTheme.typography.bodyMedium,
            color = BahrTheme.colors.onSurfaceSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
        BahrPrimaryButton(
            text = stringResource(Res.string.liked_trips_sign_in),
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth().testTag(LIKED_TRIPS_SIGN_IN_TAG),
        )
    }
}

/** For tests. */
internal const val LIKED_TRIPS_LIST_TAG = "liked_trips_list"
internal const val LIKED_TRIPS_SIGN_IN_TAG = "liked_trips_sign_in"

// Keys of the single items, each also its item's content type (one drawing each).
private const val KEY_HEADER = "header"
private const val KEY_SIGN_IN = "sign-in"
