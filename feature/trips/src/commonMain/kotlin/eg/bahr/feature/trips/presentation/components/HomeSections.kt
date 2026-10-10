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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import eg.bahr.core.designsystem.components.ImageGround
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrSharedBounds
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.home_slide_of
import eg.bahr.core.localization.generated.resources.trips_see_all
import eg.bahr.feature.trips.model.BannersSectionDto
import eg.bahr.feature.trips.model.CategoriesSectionDto
import eg.bahr.feature.trips.model.HomeBannerDto
import eg.bahr.feature.trips.model.HomeSectionDto
import eg.bahr.feature.trips.model.SeeAllType
import eg.bahr.feature.trips.model.SkippedSectionDto
import eg.bahr.feature.trips.model.TripsSectionDto
import eg.bahr.feature.trips.navigation.HomeAction
import eg.bahr.feature.trips.presentation.WaitlistTags
import eg.bahr.feature.trips.presentation.parseAspectRatio
import eg.bahr.feature.trips.presentation.toHomeAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/*
 * Home's server-driven sections (PLAN §5c, M4-M1a): one composable per `type`. Rows scroll from the
 * start edge, so from the right in Arabic: LazyRow and HorizontalPager follow the layout direction.
 */

/** One section, drawn by its type, under its optional title. */
@Composable
internal fun HomeSection(
    section: HomeSectionDto,
    perPersonLabel: String,
    waitlistTags: WaitlistTags,
    onTripClick: (slug: String) -> Unit,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (section) {
        is BannersSectionDto ->
            Titled(section.title, modifier) { BannerSection(section, onAction) }

        is TripsSectionDto -> {
            val seeAll = section.seeAllAction()
            Titled(
                section.title,
                modifier,
                seeAll = seeAll?.let { action -> { onAction(action) } },
                // Only a title that leads somewhere morphs into a page header (M4-M6).
                titleSharedKey = seeAll?.let { rowTitleSharedKey(section.id) },
            ) {
                TripRow(section, perPersonLabel, waitlistTags, onTripClick)
            }
        }

        is CategoriesSectionDto ->
            Titled(section.title, modifier) { CategoryTiles(section, onAction) }

        // Dropped (and logged) by the view model; nothing to draw.
        is SkippedSectionDto -> Unit
    }
}

/**
 * A section under its title. [seeAll], when there is one, is a "See all" link at the end of the
 * title line (primary, not coral: coral is for the screen's primary action only). [titleSharedKey]
 * lets the title morph into the opened page's heading (M4-M6).
 */
@Composable
private fun Titled(
    title: String?,
    modifier: Modifier,
    seeAll: (() -> Unit)? = null,
    titleSharedKey: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
        val heading = title?.takeIf { it.isNotBlank() }
        if (heading != null || seeAll != null) {
            Row(
                // The link's own touch padding stands in for most of the end gutter.
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = BahrSpacing.gutter, end = if (seeAll != null) BahrSpacing.sm else BahrSpacing.gutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { heading?.let { SectionTitle(it, Modifier.bahrSharedBounds(titleSharedKey)) } }
                seeAll?.let { SeeAllLink(it) }
            }
        }
        content()
    }
}

/** "See all", at the 44dp touch minimum. */
@Composable
private fun SeeAllLink(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = BahrSpacing.minTouch)) {
        Text(
            text = stringResource(Res.string.trips_see_all),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Where this row's "See all" leads, or null when there is no more to see: only when the whole list
 * ([TripsSectionDto.totalItems]) is longer than the row, and the server said where it leads in a way
 * this build knows. A category row opens the category page; any other row its own list.
 */
internal fun TripsSectionDto.seeAllAction(): HomeAction? {
    val target = seeAll ?: return null
    if ((totalItems ?: 0) <= items.size || target.value.isBlank()) return null
    return when (target.type) {
        SeeAllType.CATEGORY -> HomeAction.OpenCategory(target.value, title, rowTitleSharedKey(id))
        SeeAllType.SECTION -> HomeAction.OpenSection(target.value, title, rowTitleSharedKey(id))
        else -> null
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
    val scope = rememberCoroutineScope()
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
        if (banners.size > 1) {
            // A tap on a dot goes to its slide; the timer starts over from there (keyed on settledPage).
            PagerDots(count = banners.size, current = pager.currentPage) { page -> scope.launch { pager.animateScrollToPage(page) } }
        }
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

/**
 * The handoff's dot indicator: the current page a wide primary pill, the rest small track dots. Each
 * dot is a tab that goes to its slide ([onSelect], HANDOFF Home: "tappable").
 *
 * The dots keep the handoff's size and spacing. Each sits in a cell as tall as the touch minimum, and
 * Compose widens a target narrower than the minimum for touch on its own (the nearest dot wins where
 * two overlap), so a dot is easy to hit without spreading the row out.
 */
@Composable
private fun PagerDots(
    count: Int,
    current: Int,
    onSelect: (page: Int) -> Unit,
) {
    Row(modifier = Modifier.selectableGroup()) {
        repeat(count) { index ->
            val active = index == current
            val label = stringResource(Res.string.home_slide_of, index + 1, count)
            Box(
                modifier =
                    Modifier
                        .heightIn(min = BahrSpacing.minTouch)
                        .selectable(selected = active, role = Role.Tab, onClick = { onSelect(index) })
                        .semantics { contentDescription = label }
                        // Half the handoff's gap on each side, so neighbours sit the full gap apart.
                        .padding(horizontal = BahrSpacing.xs / 2),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = if (active) BahrSize.pagerDotActive else BahrSize.pagerDot, height = BahrSize.pagerDot)
                        .clip(BahrTheme.shapes.full)
                        .background(if (active) MaterialTheme.colorScheme.primary else BahrTheme.colors.track),
                )
            }
        }
    }
}

/** `trips`: compact trip cards in a row, in the order served (bookable, then sold out, then no date). */
@Composable
private fun TripRow(
    section: TripsSectionDto,
    perPersonLabel: String,
    waitlistTags: WaitlistTags,
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
                waitlisted = waitlistTags.shows(trip),
            )
        }
    }
}

/**
 * `categories`: the handoff's category row, one tinted tile per category with its caption under it,
 * scrolling from the start edge. The tile's colour is the category's `tone`. A tap opens that
 * category's page (M4-M1b), with the caption as its title until the page's facets arrive, and the
 * tile grows into the page's header (M4-M6).
 */
@Composable
private fun CategoryTiles(
    section: CategoriesSectionDto,
    onAction: (HomeAction) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = BahrSpacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        items(section.items, key = { it.key }) { category ->
            val sharedKey = categoryTileSharedKey(section.id, category.key)
            Column(
                modifier =
                    Modifier
                        .clip(BahrTheme.shapes.medium)
                        .clickable(role = Role.Button) { onAction(HomeAction.OpenCategory(category.key, category.label, sharedKey)) }
                        .padding(BahrSpacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
            ) {
                CategoryTile(icon = category.icon, tone = category.tone, modifier = Modifier.bahrSharedBounds(sharedKey))
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Shared-element keys (M4-M6): one per tappable element on Home, so two elements that open the same
 * page (a category's tile and a category row's title) never claim the same key.
 */
internal fun rowTitleSharedKey(sectionId: String): String = "home-row-title:$sectionId"

internal fun categoryTileSharedKey(
    sectionId: String,
    category: String,
): String = "home-category-tile:$sectionId:$category"

/** The first banner's own shape, when the section has no usable `aspectRatio`. */
private fun HomeBannerDto.imageRatio(): Float =
    if (image.width > 0 && image.height > 0) image.width.toFloat() / image.height else FALLBACK_BANNER_RATIO

private const val LAYOUT_ROW = "row"

/** A row shows one item and a peek of the next, so it reads as scrollable. */
private const val ROW_ITEM_WIDTH_FRACTION = .72f
private const val BANNER_TITLE_LINES = 2

/** The handoff shoots hero slides at 16:10. */
private const val FALLBACK_BANNER_RATIO = 16f / 10f
