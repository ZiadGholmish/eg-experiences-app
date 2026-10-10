package eg.bahr.feature.trips.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import eg.bahr.core.designsystem.components.BahrFilterChip
import eg.bahr.core.designsystem.components.ImageGround
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.feature.trips.model.BannersSectionDto
import eg.bahr.feature.trips.model.CategoriesSectionDto
import eg.bahr.feature.trips.model.HomeBannerDto
import eg.bahr.feature.trips.model.HomeSectionDto
import eg.bahr.feature.trips.model.SkippedSectionDto
import eg.bahr.feature.trips.model.TripsSectionDto
import eg.bahr.feature.trips.navigation.HomeAction
import eg.bahr.feature.trips.presentation.parseAspectRatio
import eg.bahr.feature.trips.presentation.toHomeAction
import kotlinx.coroutines.delay

/*
 * Home's server-driven sections (PLAN §5c, M4-M1a): one composable per `type`. Rows scroll from the
 * start edge, so from the right in Arabic: LazyRow and HorizontalPager follow the layout direction.
 */

/** One section, drawn by its type, under its optional title. */
@Composable
internal fun HomeSection(
    section: HomeSectionDto,
    perPersonLabel: String,
    onTripClick: (slug: String) -> Unit,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (section) {
        is BannersSectionDto ->
            Titled(section.title, modifier) { BannerSection(section, onAction) }

        is TripsSectionDto ->
            Titled(section.title, modifier) { TripRow(section, perPersonLabel, onTripClick) }

        is CategoriesSectionDto ->
            Titled(section.title, modifier) { CategoryChips(section) }

        // Dropped (and logged) by the view model; nothing to draw.
        is SkippedSectionDto -> Unit
    }
}

@Composable
private fun Titled(
    title: String?,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
        title?.takeIf { it.isNotBlank() }?.let { SectionTitle(it, Modifier.padding(horizontal = BahrSpacing.gutter)) }
        content()
    }
}

/**
 * `banners`: a pager for `carousel` (the contract's default for banners), a scrolling row for `row`.
 * The banner's size comes from the section's `aspectRatio`, else from the first image's own shape.
 */
@Composable
private fun BannerSection(
    section: BannersSectionDto,
    onAction: (HomeAction) -> Unit,
) {
    val ratio = parseAspectRatio(section.aspectRatio) ?: section.items.first().imageRatio()
    if (section.layout == LAYOUT_ROW) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = BahrSpacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
        ) {
            items(section.items, key = { it.id }) { banner ->
                Banner(banner, ratio, onAction, Modifier.fillParentMaxWidth(ROW_ITEM_WIDTH_FRACTION))
            }
        }
    } else {
        BannerCarousel(section.items, ratio, onAction)
    }
}

/**
 * The carousel: one banner a page, advancing every [BahrMotion.CarouselAdvance] ms. A finger on it
 * holds it still (the timer starts over when it lifts), and a swipe restarts the timer from the page
 * it settles on. Dots only when there is more than one banner.
 */
@Composable
private fun BannerCarousel(
    banners: List<HomeBannerDto>,
    ratio: Float,
    onAction: (HomeAction) -> Unit,
) {
    val pager = rememberPagerState { banners.size }
    var touched by remember { mutableStateOf(false) }
    if (banners.size > 1) {
        LaunchedEffect(pager.settledPage, touched) {
            if (touched) return@LaunchedEffect
            delay(BahrMotion.CarouselAdvance)
            // Wraps back to the first, as the prototype's slider does.
            pager.animateScrollToPage((pager.settledPage + 1) % banners.size)
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = BahrSpacing.gutter),
            pageSpacing = BahrSpacing.sm,
            key = { banners[it].id },
            modifier =
                Modifier
                    .fillMaxWidth()
                    // Watches touches on the Initial pass without consuming them, so the pager
                    // still scrolls and a banner still takes its tap. A drag alone would miss a
                    // finger resting on a slide to read it.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            touched = true
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                            } while (event.changes.any { it.pressed })
                            touched = false
                        }
                    },
        ) { page ->
            Banner(banners[page], ratio, onAction)
        }
        if (banners.size > 1) PagerDots(count = banners.size, current = pager.currentPage)
    }
}

/**
 * One banner: the image at [ratio], LQIP first, with its title over a bottom scrim when it has one.
 * Tappable only when its action leads somewhere ([toHomeAction]); `none` is not a button.
 */
@Composable
private fun Banner(
    banner: HomeBannerDto,
    ratio: Float,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val action = banner.action.toHomeAction()
    val title = banner.title?.takeIf { it.isNotBlank() }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(BahrTheme.shapes.hero)
                .then(if (action != null) Modifier.clickable(role = Role.Button) { onAction(action) } else Modifier),
    ) {
        ImageGround(modifier = Modifier.matchParentSize(), scrimBottom = title != null) { Photo(banner.image) }
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = BANNER_TITLE_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(BahrSpacing.lg),
            )
        }
    }
}

/** The handoff's dot indicator: the current page a wide primary pill, the rest small track dots. */
@Composable
private fun PagerDots(
    count: Int,
    current: Int,
) {
    // Decorative: the pages themselves are what a screen reader walks through.
    Row(
        modifier = Modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        repeat(count) { index ->
            val active = index == current
            Box(
                Modifier
                    .size(width = if (active) BahrSize.pagerDotActive else BahrSize.pagerDot, height = BahrSize.pagerDot)
                    .clip(BahrTheme.shapes.full)
                    .background(if (active) MaterialTheme.colorScheme.primary else BahrTheme.colors.track),
            )
        }
    }
}

/** `trips`: compact trip cards in a row, in the order served (bookable, then sold out, then no date). */
@Composable
private fun TripRow(
    section: TripsSectionDto,
    perPersonLabel: String,
    onTripClick: (slug: String) -> Unit,
) {
    LazyRow(
        // Vertical room so the cards' shadows are not clipped by the row.
        contentPadding = PaddingValues(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        items(section.items, key = { it.slug }) { trip ->
            TripCard(
                trip = trip,
                perPersonLabel = perPersonLabel,
                onClick = { onTripClick(trip.slug) },
                modifier = Modifier.fillParentMaxWidth(ROW_ITEM_WIDTH_FRACTION),
                compact = true,
            )
        }
    }
}

/**
 * `categories`: one chip per category. Tapping does nothing yet: it is meant to filter the list, and
 * filters land with M4-B1 (`GET /trips?category=`). The chip has no tone of its own (`BahrFilterChip`
 * is primary-tinted), so the category's `tone` is not drawn.
 */
@Composable
private fun CategoryChips(section: CategoriesSectionDto) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = BahrSpacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        items(section.items, key = { it.key }) { category ->
            BahrFilterChip(
                label = category.label,
                selected = false,
                // No-op until M4-B1 adds the category filter to the list.
                onClick = {},
                icon = category.icon?.let { symbolIcon(it).filled() },
            )
        }
    }
}

/** The first banner's own shape, when the section has no usable `aspectRatio`. */
private fun HomeBannerDto.imageRatio(): Float =
    if (image.width > 0 && image.height > 0) image.width.toFloat() / image.height else FALLBACK_BANNER_RATIO

private const val LAYOUT_ROW = "row"

/** A row shows one item and a peek of the next, so it reads as scrollable. */
private const val ROW_ITEM_WIDTH_FRACTION = .72f
private const val BANNER_TITLE_LINES = 2

/** The handoff shoots hero slides at 16:10. */
private const val FALLBACK_BANNER_RATIO = 16f / 10f
