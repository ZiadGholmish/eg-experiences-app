package eg.bahr.feature.tripdetails.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.catalog.presentation.media.ViewerMedia
import eg.bahr.core.designsystem.components.BahrErrorView
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import eg.bahr.feature.tripdetails.model.HostDto
import eg.bahr.feature.tripdetails.model.TripDetailDto
import eg.bahr.feature.tripdetails.presentation.components.TripAvailability
import eg.bahr.feature.tripdetails.presentation.components.TripFacts
import eg.bahr.feature.tripdetails.presentation.components.TripFactsSkeleton
import eg.bahr.feature.tripdetails.presentation.components.TripHeader
import eg.bahr.feature.tripdetails.presentation.components.TripHero
import eg.bahr.feature.tripdetails.presentation.components.TripHostCard
import eg.bahr.feature.tripdetails.presentation.components.TripInclusions
import eg.bahr.feature.tripdetails.presentation.components.TripInclusionsSkeleton
import eg.bahr.feature.tripdetails.presentation.components.TripItinerary
import eg.bahr.feature.tripdetails.presentation.components.TripItinerarySkeleton
import eg.bahr.feature.tripdetails.presentation.components.TripReviews
import eg.bahr.feature.tripdetails.presentation.components.TripStickyBar
import eg.bahr.feature.tripdetails.presentation.components.TripTitleBlock
import eg.bahr.feature.tripdetails.presentation.components.WaitlistPanelState
import eg.bahr.feature.tripdetails.presentation.components.factsSource
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The trip page (HANDOFF screen 3), in the handoff's order: slider, eyebrow, title and deck, facts,
 * included/excluded, the day in order, host and tips, reviews, availability, and the sticky bar.
 *
 * Text first: while the trip loads, whatever the list card already said (title, location, price, duration, its photo)
 * is drawn as it will stay, and only what is not known yet shimmers. Opened cold (a shared link), there is no card yet,
 * so the page is all skeleton until the trip arrives.
 *
 * Without jumping (MV-M7): every part still loading is a skeleton of its loaded size (the hero's box, the deck's lines,
 * the facts cells, the date cards, the bar's price line), the sections that follow the facts are in place from the first
 * frame, and those that may turn out empty (host, reviews) reserve nothing and appear far below the fold. The list is
 * keyed by section, so what the reader is looking at stays put when a section above it changes.
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
    onHostClick: ((HostDto) -> Unit)? = null,
    onSeeAllReviews: ((title: String) -> Unit)? = null,
    date: String? = null,
    placeOpened: Boolean = false,
    pushPrompt: (@Composable (onDone: () -> Unit) -> Unit)? = null,
    viewModel: TripDetailViewModel =
        koinViewModel { parametersOf(slug, TripLink(date?.let(::linkedDate), placeOpened)) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Right after a successful join (M3-M5): the push pre-prompt, until it says it is done.
    if (state.askForPush) {
        if (pushPrompt != null) pushPrompt(viewModel::pushPromptDone) else SideEffect(Unit) { viewModel.pushPromptDone() }
    }
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
    val joinedEmail = state.joinedEmail
    val maxPartySize = state.maxPartySize
    // Remembered so an unrelated recomposition hands the band the same instance (it compares by identity).
    val waitlist =
        remember(form, joinedEmail, maxPartySize, viewModel) {
            WaitlistPanelState(
                form = form,
                joinedEmail = joinedEmail,
                maxPartySize = maxPartySize,
                onOpen = viewModel::openWaitlist,
                onEmailChange = viewModel::setWaitlistEmail,
                onDecrease = viewModel::decreaseWaitlistParty,
                onIncrease = viewModel::increaseWaitlistParty,
                onJoin = viewModel::joinWaitlist,
            )
        }
    TripPage(
        state = state,
        waitlist = waitlist,
        onDatesRevealed = viewModel::datesRevealed,
        onBack = onBack,
        onSelectDeparture = viewModel::selectDeparture,
        onContinue = { departureId -> onContinue(slug, departureId) },
        onHostClick = onHostClick,
        onSeeAllReviews = onSeeAllReviews,
        modifier = modifier,
    )
}

/** The page's sections, in order. Their names are the list's item keys. */
internal enum class TripSection { Hero, Title, Facts, Inclusions, Itinerary, Host, Reviews, Availability }

@Composable
private fun TripPage(
    state: TripDetailUiState,
    waitlist: WaitlistPanelState,
    onDatesRevealed: () -> Unit,
    onBack: () -> Unit,
    onSelectDeparture: (String) -> Unit,
    onContinue: (String) -> Unit,
    onHostClick: ((HostDto) -> Unit)?,
    onSeeAllReviews: ((title: String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val trip = state.trip
    val header = state.header()
    val sections = sectionsFor(trip)
    val preview = state.preview
    // The gallery once the trip is here; until then the card's photo, the gallery's first item (MV-M7).
    val heroItems =
        remember(trip, preview) {
            trip?.galleryMedia() ?: listOfNotNull(preview?.heroImage ?: preview?.cardImage).map { ViewerMedia.Photo(it) }
        }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Whether the reader has moved the page themselves. Read only inside the effect below, so it never recomposes.
    val touched = remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) touched.value = true }
    }

    // Opened on a linked date (M3-M5): the dates, the picked one and its band come into view once, after the trip has
    // landed too (MV-M7): the band is on the page while it loads, but the sections the trip brings above it would push it
    // away again. The dates may land seconds after the page opened: once the reader has scrolled, the page is theirs and
    // is not moved under them.
    val reveal = state.revealDates && trip != null
    LaunchedEffect(reveal) {
        if (reveal) {
            if (!touched.value) listState.scrollTo(sections, TripSection.Availability)
            onDatesRevealed()
        }
    }

    // imePadding: the waiting-list email field sits low on the page; the list shrinks above the
    // keyboard (and the sticky bar rides on it) so the focused field can be scrolled into view.
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag(TRIP_PAGE_TAG),
                contentPadding = PaddingValues(bottom = BahrSpacing.xl),
            ) {
                // One content type per section (M3-M11, Compose audit M1): each draws differently.
                items(sections, key = { it.name }, contentType = { it }) { section ->
                    Section(section, state, header, trip, heroItems, waitlist, onBack, onSelectDeparture, onHostClick, onSeeAllReviews)
                }
            }
            StatusBarScrim(visible = { listState.firstVisibleItemIndex > 0 })
        }
        TripStickyBar(
            price = header?.price,
            selected = state.selectedDeparture,
            cta = state.cta,
            ctaEnabled = state.ctaEnabled,
            onCta = {
                val departureId = state.continueDepartureId
                when {
                    // The button is disabled then; a tap that slips through still does nothing (MV-M9).
                    !state.ctaEnabled -> Unit
                    departureId != null -> onContinue(departureId)
                    // "Choose a date" takes the user to the dates rather than doing nothing; while the trip loads it waits,
                    // as the reveal does (the trip's sections would push the band away again).
                    state.cta == TripCta.ChooseDate && trip != null ->
                        scope.launch { listState.scrollTo(sections, TripSection.Availability) }
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

/**
 * Sections the trip has data for. While it loads, the sections nearly every trip has, in their places (MV-M7), so the
 * ones that land are not inserted above what is on screen: included, the day and the dates. Host and reviews may be
 * empty, so they reserve nothing; they come in after the day, far below the fold.
 */
private fun sectionsFor(trip: TripDetailDto?): List<TripSection> =
    buildList {
        add(TripSection.Hero)
        add(TripSection.Title)
        add(TripSection.Facts)
        if (trip == null) {
            add(TripSection.Inclusions)
            add(TripSection.Itinerary)
            add(TripSection.Availability)
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
    heroItems: List<ViewerMedia>,
    waitlist: WaitlistPanelState,
    onBack: () -> Unit,
    onSelectDeparture: (String) -> Unit,
    onHostClick: ((HostDto) -> Unit)?,
    onSeeAllReviews: ((title: String) -> Unit)?,
) {
    val padded = Modifier.padding(start = BahrSpacing.gutter, end = BahrSpacing.gutter, top = BahrSpacing.xl)
    when (section) {
        TripSection.Hero -> TripHero(items = heroItems, loading = trip == null, onBack = onBack)

        TripSection.Title -> TripTitleBlock(header = header, deck = trip?.deck, deckPending = trip == null, modifier = padded)

        TripSection.Facts -> {
            val preview = state.preview
            when {
                trip != null -> TripFacts(trip.factsSource(), padded)
                preview != null -> TripFacts(preview.factsSource(), padded)
                else -> TripFactsSkeleton(padded)
            }
        }

        TripSection.Inclusions ->
            if (trip != null) TripInclusions(trip.included, trip.excluded, padded) else TripInclusionsSkeleton(padded)

        TripSection.Itinerary -> if (trip != null) TripItinerary(trip.itinerary, padded) else TripItinerarySkeleton(padded)

        TripSection.Host -> trip?.let { TripHostCard(it.host, it.tips, padded, onHostClick) }

        TripSection.Reviews ->
            trip?.reviews?.let { reviews ->
                TripReviews(reviews, padded, onSeeAll = onSeeAllReviews?.let { open -> { open(trip.title) } })
            }

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
                joinedIds = state.joinedWaitlists.keys,
                seatsLoading = state.seatsLoading,
                policyPending = trip == null,
            )
    }
}

/** The trip's own fields once loaded, else the list card's; null for a cold open. */
private fun TripDetailUiState.header(): TripHeader? {
    trip?.let { return TripHeader(it.title, it.subtitle, it.price, it.rating) }
    return preview?.let { TripHeader(it.title, it.subtitle, it.price, it.rating) }
}

/** Lets screenshot tests scroll the page to a section by key. */
internal const val TRIP_PAGE_TAG = "trip_page"

/** The sticky bar, for the keyboard test (the waiting-list email field must stay above it). */
internal const val TRIP_STICKY_BAR_TAG = "trip_sticky_bar"

/** A link's `?date=` (`YYYY-MM-DD`); anything else is no date, and the page opens with none picked. */
private fun linkedDate(text: String): LocalDate? =
    try {
        LocalDate.parse(text)
    } catch (_: IllegalArgumentException) {
        null
    }
