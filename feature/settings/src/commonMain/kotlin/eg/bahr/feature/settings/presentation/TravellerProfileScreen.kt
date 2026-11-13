package eg.bahr.feature.settings.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.catalog.presentation.components.ListTopBar
import eg.bahr.core.catalog.presentation.components.Photo
import eg.bahr.core.catalog.presentation.components.ReviewCard
import eg.bahr.core.catalog.presentation.components.SectionTitle
import eg.bahr.core.catalog.presentation.components.TextSkeleton
import eg.bahr.core.catalog.presentation.components.TripCard
import eg.bahr.core.designsystem.components.BahrEmptyView
import eg.bahr.core.designsystem.components.BahrErrorView
import eg.bahr.core.designsystem.components.BahrSkeleton
import eg.bahr.core.designsystem.components.BahrSkeletonCircle
import eg.bahr.core.designsystem.components.ImageGround
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrTween
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.host_no_reviews
import eg.bahr.core.localization.generated.resources.traveller_liked_trips
import eg.bahr.core.localization.generated.resources.traveller_member_since
import eg.bahr.core.localization.generated.resources.traveller_no_liked_trips
import eg.bahr.core.localization.generated.resources.traveller_no_past_trips
import eg.bahr.core.localization.generated.resources.traveller_not_public
import eg.bahr.core.localization.generated.resources.traveller_nothing_shared
import eg.bahr.core.localization.generated.resources.traveller_past_trips
import eg.bahr.core.localization.generated.resources.trip_per_person
import eg.bahr.core.localization.generated.resources.trip_reviews
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import eg.bahr.feature.settings.model.TravellerProfileDto
import eg.bahr.feature.settings.model.TravellerTripRefDto
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A traveller's public profile (MP-M2), opened from a reviewer's name (trip page, host page, all reviews, Home) or from
 * Account → Public profile → "View my public profile". The handoff has no such page: it follows the host page (back
 * button, header, sections in one list) and Home's rails for the trip cards.
 *
 * Header: the default avatar (no account photo exists yet; the slot is the avatar's), the name the owner chose to show
 * and "Member since <month year>". Then only the sections the server sent, in this order: trips taken and liked trips
 * (each a sideways row of trip cards, tap → the trip page), reviews (each with the trip it is about). A section that
 * is on but empty says so in a line; if none is sent, one line says nothing else is shared.
 *
 * The cards carry no heart: a profile's cards come without `likedByMe` (openapi), so a heart there could not say
 * whether the reader likes the trip. Past trips come without `nextDeparture`, so their cards show no date or seats.
 * Review names are not links here: every review on the page is this traveller's.
 *
 * Any 404 reads "This profile isn't public" (private, unknown and deleted are one answer). The back button stays in
 * every state: the tab bar is hidden here, and iOS has no system Back.
 */
@Composable
internal fun TravellerProfileScreen(
    publicId: String,
    onBack: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TravellerProfileViewModel = koinViewModel(key = "traveller:$publicId") { parametersOf(publicId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TravellerProfileContent(state = state, onBack = onBack, onTripClick = onTripClick, onRetry = viewModel::retry, modifier = modifier)
}

/** What fills the page under the back button. */
private enum class TravellerPage { Loading, Shown, Failed, NotPublic }

/** Stateless, for the screenshot tests. */
@Composable
internal fun TravellerProfileContent(
    state: TravellerProfileUiState,
    onBack: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = state.profile
    val error = state.error
    val page =
        when {
            state.notAvailable -> TravellerPage.NotPublic
            profile != null -> TravellerPage.Shown
            error != null -> TravellerPage.Failed
            else -> TravellerPage.Loading
        }
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ListTopBar(onBack = onBack)
        // The states crossfade, so the profile landing over its skeleton does not jump.
        val fade = bahrTween<Float>()
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
            modifier = Modifier.weight(1f),
        ) { shown ->
            when (shown) {
                TravellerPage.Loading -> HeaderSkeleton()

                TravellerPage.NotPublic ->
                    BahrEmptyView(
                        message = stringResource(Res.string.traveller_not_public),
                        modifier = Modifier.testTag(TRAVELLER_NOT_PUBLIC_TAG),
                    )

                TravellerPage.Failed ->
                    error?.let {
                        BahrErrorView(
                            message = it.localizedMessage(),
                            retryLabel = stringResource(Res.string.action_retry),
                            onRetry = if (it.isRetryable) onRetry else null,
                        )
                    } ?: Box(Modifier.fillMaxSize())

                TravellerPage.Shown -> profile?.let { ProfileList(it, onTripClick) } ?: Box(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ProfileList(
    profile: TravellerProfileDto,
    onTripClick: (slug: String) -> Unit,
) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + BahrSpacing.xl
    val perPersonLabel = stringResource(Res.string.trip_per_person)
    val padded = Modifier.padding(horizontal = BahrSpacing.gutter)
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(TRAVELLER_PAGE_TAG),
        contentPadding = PaddingValues(bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.lg),
    ) {
        item(key = KEY_HEADER, contentType = KEY_HEADER) { Header(profile, padded) }
        profile.pastTrips?.let { trips ->
            tripsSection(KEY_PAST, Res.string.traveller_past_trips, Res.string.traveller_no_past_trips, trips, perPersonLabel, onTripClick)
        }
        profile.likedTrips?.let { trips ->
            tripsSection(
                KEY_LIKED,
                Res.string.traveller_liked_trips,
                Res.string.traveller_no_liked_trips,
                trips,
                perPersonLabel,
                onTripClick,
            )
        }
        profile.reviews?.let { reviews ->
            item(key = "$KEY_REVIEWS-title", contentType = CONTENT_TITLE) { Title(Res.string.trip_reviews, padded) }
            if (reviews.isEmpty()) {
                item(key = "$KEY_REVIEWS-empty", contentType = CONTENT_EMPTY) { EmptyLine(Res.string.host_no_reviews, padded) }
            }
            // By position: a review has no id in the contract (see ReviewsScreen).
            itemsIndexed(reviews, key = { index, _ -> "$KEY_REVIEWS:$index" }, contentType = { _, _ -> CONTENT_REVIEW }) { _, item ->
                Box(padded) {
                    // No author link: every review here is this traveller's.
                    ReviewCard(item.review, footer = { ReviewedTrip(item.trip, onTripClick) })
                }
            }
        }
        if (profile.pastTrips == null && profile.likedTrips == null && profile.reviews == null) {
            item(key = KEY_NOTHING, contentType = CONTENT_EMPTY) {
                EmptyLine(Res.string.traveller_nothing_shared, padded.testTag(TRAVELLER_NOTHING_SHARED_TAG))
            }
        }
    }
}

/** A trip section: its title, then its cards in a sideways row (at most the server's 20), or a line when empty. */
private fun LazyListScope.tripsSection(
    key: String,
    title: StringResource,
    empty: StringResource,
    trips: List<TripCardDto>,
    perPersonLabel: String,
    onTripClick: (slug: String) -> Unit,
) {
    val padded = Modifier.padding(horizontal = BahrSpacing.gutter)
    item(key = "$key-title", contentType = CONTENT_TITLE) { Title(title, padded.padding(top = BahrSpacing.sm)) }
    item(key = "$key-cards", contentType = if (trips.isEmpty()) CONTENT_EMPTY else CONTENT_RAIL) {
        if (trips.isEmpty()) {
            EmptyLine(empty, padded)
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth().testTag("$TRAVELLER_RAIL_TAG_PREFIX$key"),
                contentPadding = PaddingValues(horizontal = BahrSpacing.gutter),
                horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
            ) {
                items(trips, key = { it.slug }, contentType = { CONTENT_CARD }) { trip ->
                    TripCard(
                        trip = trip,
                        perPersonLabel = perPersonLabel,
                        onClick = { onTripClick(trip.slug) },
                        modifier = Modifier.fillParentMaxWidth(RAIL_CARD_WIDTH_FRACTION),
                        compact = true,
                    )
                }
            }
        }
    }
}

/** The default avatar beside the name and "Member since". The name is the page's heading. */
@Composable
private fun Header(
    profile: TravellerProfileDto,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.lg),
    ) {
        DefaultAvatar()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
            Text(
                text = profile.name,
                // A Latin name inside an Arabic page reads in its own direction (see ReviewCard).
                style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content),
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(Res.string.traveller_member_since, BahrFormat.monthYear(profile.memberSince)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The default avatar: no account has a photo yet (openapi `TravellerProfile.name`), and with `showFullNameAndPhoto`
 * off none will be shown. When accounts get one, the photo goes in this disc.
 */
@Composable
private fun DefaultAvatar() {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(BahrSize.avatarLarge)
                .clip(BahrTheme.shapes.full)
                .background(BahrTheme.colors.quaternaryContainer)
                // The name beside it says who it is.
                .clearAndSetSemantics {},
    ) {
        Icon(
            BahrIcons.Person.filled(),
            contentDescription = null,
            tint = BahrTheme.colors.onQuaternaryContainer,
            modifier = Modifier.size(BahrSize.iconState),
        )
    }
}

/** The header's skeleton while `getTraveller` is out: the avatar's disc and two lines. */
@Composable
private fun HeaderSkeleton() {
    BahrSkeleton(modifier = Modifier.fillMaxSize().padding(horizontal = BahrSpacing.gutter).testTag(TRAVELLER_LOADING_TAG)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BahrSpacing.lg),
        ) {
            BahrSkeletonCircle(props = BahrSkeletonCircle.Props(size = BahrSize.avatarLarge))
            TextSkeleton(lines = HEADER_SKELETON_LINES, modifier = Modifier.weight(1f))
        }
        TextSkeleton(lines = BODY_SKELETON_LINES)
    }
}

/** The trip a review is about, under it: thumbnail and title, a tap opens its page. */
@Composable
private fun ReviewedTrip(
    trip: TravellerTripRefDto,
    onTripClick: (slug: String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = BahrSpacing.minTouch)
                .clip(BahrTheme.shapes.small)
                .clickable(role = Role.Button) { onTripClick(trip.slug) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        ImageGround(modifier = Modifier.size(BahrSize.thumbnailSmall).clip(BahrTheme.shapes.small)) { trip.image?.let { Photo(it) } }
        Text(
            text = trip.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Title(
    text: StringResource,
    modifier: Modifier,
) = SectionTitle(stringResource(text), modifier.semantics { heading() })

@Composable
private fun EmptyLine(
    text: StringResource,
    modifier: Modifier,
) = Text(
    text = stringResource(text),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier,
)

/** For tests: the page's list, its states and a section's row of cards (the prefix + `past` or `liked`). */
internal const val TRAVELLER_PAGE_TAG = "traveller_page"
internal const val TRAVELLER_NOT_PUBLIC_TAG = "traveller_not_public"
internal const val TRAVELLER_LOADING_TAG = "traveller_loading"
internal const val TRAVELLER_NOTHING_SHARED_TAG = "traveller_nothing_shared"
internal const val TRAVELLER_RAIL_TAG_PREFIX = "traveller_rail_"

/** Keys, unique across the sections. */
private const val KEY_HEADER = "traveller-header"
internal const val KEY_PAST = "past"
internal const val KEY_LIKED = "liked"
private const val KEY_REVIEWS = "reviews"
private const val KEY_NOTHING = "nothing-shared"

private const val CONTENT_TITLE = "title"
private const val CONTENT_EMPTY = "empty"
private const val CONTENT_RAIL = "rail"
private const val CONTENT_CARD = "card"
private const val CONTENT_REVIEW = "review"

/** A rail card's share of the row, so the next card peeks in and says the row scrolls (Home's rails do the same). */
private const val RAIL_CARD_WIDTH_FRACTION = .72f

private const val HEADER_SKELETON_LINES = 2
private const val BODY_SKELETON_LINES = 3
