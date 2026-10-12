package eg.bahr.feature.home.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import eg.bahr.core.catalog.presentation.WaitlistTags
import eg.bahr.core.catalog.presentation.components.CategoryTile
import eg.bahr.core.catalog.presentation.components.Photo
import eg.bahr.core.catalog.presentation.components.SectionTitle
import eg.bahr.core.catalog.presentation.components.TripCard
import eg.bahr.core.catalog.presentation.components.badgeTone
import eg.bahr.core.catalog.presentation.components.knownSymbolIcon
import eg.bahr.core.designsystem.components.BahrBadge
import eg.bahr.core.designsystem.components.ImageGround
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrAlpha
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrSharedBounds
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.home_banner_price_from
import eg.bahr.core.localization.generated.resources.trips_see_all
import eg.bahr.feature.home.model.BannersSectionDto
import eg.bahr.feature.home.model.CategoriesSectionDto
import eg.bahr.feature.home.model.HomeBannerDto
import eg.bahr.feature.home.model.HomeNoteDto
import eg.bahr.feature.home.model.HomeSectionDto
import eg.bahr.feature.home.model.NoteSectionDto
import eg.bahr.feature.home.model.SeeAllType
import eg.bahr.feature.home.model.SkippedSectionDto
import eg.bahr.feature.home.model.TripsSectionDto
import eg.bahr.feature.home.navigation.HomeAction
import eg.bahr.feature.home.presentation.parseAspectRatio
import eg.bahr.feature.home.presentation.showsForward
import eg.bahr.feature.home.presentation.toHomeAction
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

        // Where the server put it (M4-M7): its body replaced the footer copy the app used to carry.
        is NoteSectionDto ->
            section.items.firstOrNull()?.let { note ->
                Titled(section.title, modifier) { NotePanel(note, Modifier.padding(horizontal = BahrSpacing.gutter)) }
            }

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
 *
 * Endless (M4-M9): the pager holds [CAROUSEL_LAPS] laps of the banners and starts mid-way, so after
 * the last slide the next one is the first again, reached going forward like every other step, not
 * by scrolling back across every slide. Either way a swipe also wraps. A page's banner is its index
 * modulo the banner count; the dots and their "Slide n of N" labels count banners, not pages.
 */
@Composable
private fun BannerCarousel(
    banners: List<HomeBannerDto>,
    ratio: Float,
    onAction: (HomeAction) -> Unit,
) {
    val count = banners.size
    // A new banner count is a new pager: the laps are counted in banners.
    key(count) {
        val laps = carouselLaps(count)
        val pager = rememberPagerState(initialPage = carouselStartPage(count)) { count * laps }
        val scope = rememberCoroutineScope()
        var touched by remember { mutableStateOf(false) }
        if (count > 1) {
            LaunchedEffect(pager.settledPage, touched) {
                if (touched) return@LaunchedEffect
                delay(BahrMotion.CarouselAdvance)
                val next = pager.settledPage + 1
                // Hours of idle advancing reach the last lap: back to the same banner mid-way, unseen.
                if (next >= pager.pageCount) pager.scrollToPage(carouselStartPage(count) + pager.settledPage % count)
                pager.animateScrollToPage(pager.currentPage + 1)
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
                // The same banner shows once a lap, so the lap is part of the key.
                key = { page -> "${banners[page % count].id}#${page / count}" },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(HOME_CAROUSEL_TAG)
                        // Screen readers hear the banners, not the laps: the pager's own collection info
                        // counts every page of every lap (M4-M9 review #3). Outermost on this node, so it
                        // wins over the pager's.
                        .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = count) }
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
                Banner(banners[page % count], ratio, onAction)
            }
            if (count > 1) {
                // A tap on a dot goes to its slide in the current lap; the timer starts over from there
                // (keyed on settledPage).
                // The dots read the live scroll position only while drawing (M4-M14), so a swipe does
                // not recompose the carousel frame by frame.
                PagerDots(
                    count = count,
                    current = pager.currentPage % count,
                    page = { pager.currentPage },
                    offset = { pager.currentPageOffsetFraction },
                ) { banner ->
                    scope.launch { pager.animateScrollToPage(pager.currentPage - pager.currentPage % count + banner) }
                }
            }
        }
    }
}

/** How many laps of [count] banners the pager holds: one when there is nothing to wrap. */
internal fun carouselLaps(count: Int): Int = if (count > 1) CAROUSEL_LAPS else 1

/** The first page shown: the first banner, half-way through the laps, so either way has room. */
internal fun carouselStartPage(count: Int): Int = count * (carouselLaps(count) / 2)

/**
 * One banner: the image at [ratio], LQIP first, with its title over a bottom scrim when it has one.
 * Tappable only when its action leads somewhere ([toHomeAction]); `none` is not a button.
 *
 * A hero slide (M4-B4 fields, HANDOFF Home → hero slider) adds its tinted badge at the top start and,
 * under the title, a white "from" price pill, the meta line and the coral forward circle at the end.
 * Each is drawn only when the server sent it; a slide without any is the plain picture with its
 * title. The forward circle is drawn only on a slide that is a button: it does what a tap on the
 * slide does, so it is part of the slide's one target rather than a second, smaller one.
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
    val meta = banner.meta?.takeIf { it.isNotBlank() }
    val forward = banner.showsForward()
    val footer = banner.price != null || meta != null || forward
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(BahrTheme.shapes.hero)
                .then(if (action != null) Modifier.clickable(role = Role.Button) { onAction(action) } else Modifier),
    ) {
        ImageGround(modifier = Modifier.matchParentSize(), scrimBottom = title != null || footer) { Photo(banner.image) }
        banner.badge?.label?.takeIf { it.isNotBlank() }?.let { label ->
            BahrBadge(
                modifier = Modifier.align(Alignment.TopStart).padding(BahrSpacing.md),
                props = BahrBadge.Props(text = label, tone = badgeTone(banner.badge.tone)),
            )
        }
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(BahrSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = BANNER_TITLE_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (footer) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
                    banner.price?.let { PricePill(BahrFormat.money(it.amount, it.currencyCode, BahrTheme.locale.isArabic)) }
                    if (meta != null) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = BahrAlpha.onImageSecondary),
                            maxLines = BANNER_META_LINES,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        // Keeps the forward circle at the end edge.
                        Spacer(Modifier.weight(1f))
                    }
                    if (forward) ForwardCircle()
                }
            }
        }
    }
}

/** The hero slide's white price pill: "From 450 EGP" (the server's "from" price, formatted only). */
@Composable
private fun PricePill(money: String) {
    Text(
        text = stringResource(Res.string.home_banner_price_from, money),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = BANNER_PRICE_LINES,
        modifier =
            Modifier
                .clip(BahrTheme.shapes.full)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = BahrSpacing.md, vertical = BahrSpacing.xs),
    )
}

/**
 * The hero slide's coral forward circle. Decorative: the whole slide is the button, and the arrow
 * mirrors itself in RTL (BahrIcons.ArrowForward is auto-mirrored).
 */
@Composable
private fun ForwardCircle() {
    Box(
        modifier =
            Modifier
                .size(BahrSize.heroForward)
                .clip(BahrTheme.shapes.full)
                .background(MaterialTheme.colorScheme.tertiary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            BahrIcons.ArrowForward.outlined(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiary,
            modifier = Modifier.size(BahrSize.iconMedium),
        )
    }
}

/**
 * `note` (M4-M7, HANDOFF Home footer note): a primary-container panel with the server's text, after
 * the note's icon when this build has it. An unknown or absent icon is left out and the text takes
 * the full width: a stand-in dot would read as a bullet.
 */
@Composable
private fun NotePanel(
    note: HomeNoteDto,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        knownSymbolIcon(note.icon)?.let { icon ->
            Icon(
                icon.filled(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(BahrSize.iconLarge),
            )
        }
        Text(
            text = note.body,
            style = MaterialTheme.typography.bodyMedium,
            color = BahrTheme.colors.onSurfaceSecondary,
        )
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

/** For tests: Home's banner carousel (the pager). */
internal const val HOME_CAROUSEL_TAG = "home_carousel"

/**
 * Laps of the banners in the endless carousel. At one slide per [BahrMotion.CarouselAdvance] ms, half of
 * them lasts hours even with two banners; a lap is cheap, the pager only composes the pages on screen.
 */
private const val CAROUSEL_LAPS = 1000

/** A row shows one item and a peek of the next, so it reads as scrollable. */
private const val ROW_ITEM_WIDTH_FRACTION = .72f
private const val BANNER_TITLE_LINES = 2

/** A hero slide's meta line and price pill stay on one line each, so the forward circle keeps its place. */
private const val BANNER_META_LINES = 1
private const val BANNER_PRICE_LINES = 1

/** The handoff shoots hero slides at 16:10. */
private const val FALLBACK_BANNER_RATIO = 16f / 10f
