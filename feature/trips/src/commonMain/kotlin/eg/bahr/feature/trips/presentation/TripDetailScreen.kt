package eg.bahr.feature.trips.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.designsystem.components.BahrErrorView
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.trip_per_person
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import eg.bahr.feature.trips.model.TripDetailDto
import eg.bahr.feature.trips.presentation.components.TextSkeleton
import eg.bahr.feature.trips.presentation.components.TripAvailability
import eg.bahr.feature.trips.presentation.components.TripFacts
import eg.bahr.feature.trips.presentation.components.TripFactsSkeleton
import eg.bahr.feature.trips.presentation.components.TripHeader
import eg.bahr.feature.trips.presentation.components.TripHero
import eg.bahr.feature.trips.presentation.components.TripHostCard
import eg.bahr.feature.trips.presentation.components.TripInclusions
import eg.bahr.feature.trips.presentation.components.TripItinerary
import eg.bahr.feature.trips.presentation.components.TripReviews
import eg.bahr.feature.trips.presentation.components.TripStickyBar
import eg.bahr.feature.trips.presentation.components.TripTitleBlock
import eg.bahr.feature.trips.presentation.components.WaitlistPanelState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The trip page (HANDOFF screen 3), in the handoff's order: slider, eyebrow, title and deck, facts,
 * included/excluded, the day in order, host and tips, reviews, availability, and the sticky bar.
 *
 * Text first: while the trip loads, whatever the list card already said (title, location, price)
 * is drawn as text and only photos, the body and seat counts shimmer. Opened cold (a shared link),
 * there is no card yet, so the page is all skeleton until the trip arrives.
 *
 * The page is edge-to-edge: the hero runs under the status bar and carries its own inset for the
 * back button; the sticky bar takes the navigation-bar inset.
 */
@Composable
internal fun TripDetailScreen(
    slug: String,
    onBack: () -> Unit,
    onContinue: (slug: String, departureId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TripDetailViewModel = koinViewModel { parametersOf(slug) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val error = state.error
    if (error != null && state.trip == null) {
        BahrErrorView(
            message = error.localizedMessage(),
            retryLabel = stringResource(Res.string.action_retry),
            onRetry = if (error.isRetryable) viewModel::load else null,
            modifier = modifier.windowInsetsPadding(WindowInsets.statusBars),
        )
        return
    }
    val form = state.openWaitlist
    val joinedPhone = state.joinedPhone
    val maxPartySize = state.maxPartySize
    // Remembered so an unrelated recomposition hands the band the same instance (it compares by identity).
    val waitlist =
        remember(form, joinedPhone, maxPartySize, viewModel) {
            WaitlistPanelState(
                form = form,
                joinedPhone = joinedPhone,
                maxPartySize = maxPartySize,
                onOpen = viewModel::openWaitlist,
                onPhoneChange = viewModel::setWaitlistPhone,
                onDecrease = viewModel::decreaseWaitlistParty,
                onIncrease = viewModel::increaseWaitlistParty,
                onJoin = viewModel::joinWaitlist,
            )
        }
    TripPage(
        state = state,
        waitlist = waitlist,
        onBack = onBack,
        onSelectDeparture = viewModel::selectDeparture,
        onContinue = { departureId -> onContinue(slug, departureId) },
        modifier = modifier,
    )
}

/** The page's sections, in order. Their names are the list's item keys. */
internal enum class TripSection { Hero, Title, Facts, Inclusions, Itinerary, Host, Reviews, Availability }

@Composable
private fun TripPage(
    state: TripDetailUiState,
    waitlist: WaitlistPanelState,
    onBack: () -> Unit,
    onSelectDeparture: (String) -> Unit,
    onContinue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trip = state.trip
    val header = state.header()
    val sections = sectionsFor(trip)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // imePadding: the waiting-list phone field sits low on the page; the list shrinks above the
    // keyboard (and the sticky bar rides on it) so the focused field can be scrolled into view.
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag(TRIP_PAGE_TAG),
                contentPadding = PaddingValues(bottom = BahrSpacing.xl),
            ) {
                items(sections, key = { it.name }) { section ->
                    Section(section, state, header, trip, waitlist, onBack, onSelectDeparture)
                }
            }
            StatusBarScrim(visible = { listState.firstVisibleItemIndex > 0 })
        }
        TripStickyBar(
            price = header?.price,
            selected = state.selectedDeparture,
            cta = state.cta,
            onCta = {
                val departureId = state.continueDepartureId
                when {
                    departureId != null -> onContinue(departureId)
                    // "Choose a date" takes the user to the dates rather than doing nothing.
                    state.cta == TripCta.ChooseDate -> scope.launch { listState.scrollTo(sections, TripSection.Availability) }
                }
            },
            modifier = Modifier.testTag(TRIP_STICKY_BAR_TAG),
        )
    }
}

/**
 * The hero runs full-bleed under the status bar, with its own top scrim. Once it has scrolled away,
 * text and cards would pass under the clock with nothing behind it, so a status-bar-high strip of the
 * sticky bar's translucent surface fades in over the list (M1-M1 review #2, option a).
 */
@Composable
private fun StatusBarScrim(visible: () -> Boolean) {
    val shown by remember { derivedStateOf(visible) }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(BahrMotion.Short, easing = BahrMotion.Standard),
    )
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .graphicsLayer { this.alpha = alpha }
            .background(BahrTheme.colors.surfaceTranslucent),
    )
}

private suspend fun LazyListState.scrollTo(
    sections: List<TripSection>,
    section: TripSection,
) {
    val index = sections.indexOf(section)
    if (index >= 0) animateScrollToItem(index)
}

/** Sections the trip has data for; while it loads, the skeleton's sections. */
private fun sectionsFor(trip: TripDetailDto?): List<TripSection> =
    buildList {
        add(TripSection.Hero)
        add(TripSection.Title)
        add(TripSection.Facts)
        if (trip == null) {
            add(TripSection.Itinerary)
            return@buildList
        }
        if (trip.included.isNotEmpty() || trip.excluded.isNotEmpty()) add(TripSection.Inclusions)
        if (trip.itinerary.isNotEmpty()) add(TripSection.Itinerary)
        if (trip.host != null || trip.tips.isNotEmpty()) add(TripSection.Host)
        val reviews = trip.reviews
        if (reviews != null && (reviews.items.isNotEmpty() || reviews.average != null)) add(TripSection.Reviews)
        add(TripSection.Availability)
    }

@Composable
private fun Section(
    section: TripSection,
    state: TripDetailUiState,
    header: TripHeader?,
    trip: TripDetailDto?,
    waitlist: WaitlistPanelState,
    onBack: () -> Unit,
    onSelectDeparture: (String) -> Unit,
) {
    val padded = Modifier.padding(start = BahrSpacing.gutter, end = BahrSpacing.gutter, top = BahrSpacing.xl)
    when (section) {
        TripSection.Hero -> TripHero(images = trip?.photos().orEmpty(), loading = trip == null, onBack = onBack)

        TripSection.Title ->
            if (header != null) {
                Column(padded) {
                    TripTitleBlock(header = header, deck = trip?.deck)
                    if (trip == null) LoadingPrice(header)
                }
            } else {
                TextSkeleton(lines = TITLE_SKELETON_LINES, modifier = padded)
            }

        TripSection.Facts -> if (trip != null) TripFacts(trip, padded) else TripFactsSkeleton(padded)

        TripSection.Inclusions -> trip?.let { TripInclusions(it.included, it.excluded, padded) }

        TripSection.Itinerary ->
            if (trip != null) TripItinerary(trip.itinerary, padded) else TextSkeleton(ITINERARY_SKELETON_LINES, padded)

        TripSection.Host -> trip?.let { TripHostCard(it.host, it.tips, padded) }

        TripSection.Reviews -> trip?.reviews?.let { TripReviews(it, padded) }

        TripSection.Availability ->
            TripAvailability(
                departures = state.departures,
                loading = state.departuresLoading,
                selected = state.selectedDeparture,
                alternative = state.alternative,
                policy = trip?.policy,
                waitlist = waitlist,
                waitlistOutcome = state.waitlistOutcome,
                onSelect = onSelectDeparture,
                modifier = Modifier.padding(top = BahrSpacing.xxl),
            )
    }
}

/** While loading, the price the card showed sits under the title, as in the handoff's skeleton. */
@Composable
private fun LoadingPrice(header: TripHeader) {
    Row(
        modifier = Modifier.padding(top = BahrSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        Text(
            text = BahrFormat.money(header.price.amount, header.price.currencyCode, BahrTheme.locale.isArabic),
            style = BahrTheme.type.price,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = stringResource(Res.string.trip_per_person),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/** The trip's own fields once loaded, else the list card's; null for a cold open. */
private fun TripDetailUiState.header(): TripHeader? {
    trip?.let { return TripHeader(it.title, it.subtitle, it.price, it.rating) }
    return preview?.let { TripHeader(it.title, it.subtitle, it.price, it.rating) }
}

/** The slider's photos: the gallery, else the hero or card image alone. */
private fun TripDetailDto.photos() = gallery.ifEmpty { listOfNotNull(heroImage ?: cardImage) }

/** Lets screenshot tests scroll the page to a section by key. */
internal const val TRIP_PAGE_TAG = "trip_page"

/** The sticky bar, for the keyboard test (the waiting-list phone field must stay above it). */
internal const val TRIP_STICKY_BAR_TAG = "trip_sticky_bar"

private const val TITLE_SKELETON_LINES = 3
private const val ITINERARY_SKELETON_LINES = 5
