package eg.bahr.feature.home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import eg.bahr.core.catalog.presentation.components.ListHeading
import eg.bahr.core.catalog.presentation.components.TripCardSkeleton
import eg.bahr.core.designsystem.components.BahrSkeleton
import eg.bahr.core.designsystem.components.BahrSkeletonBlock
import eg.bahr.core.designsystem.components.BahrSkeletonLine
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.trips_all_title
import eg.bahr.feature.home.presentation.HomeLayout
import org.jetbrains.compose.resources.stringResource

/**
 * Home while its sections and first page load (M3-M14), under the app bar, search and header, which
 * are already drawn for real. It mirrors the usual loaded Home, top to bottom: a hero (MH-M1: at the hero
 * layout's 2:1), a titled card rail with the next card peeking in, "All trips" (a fixed label, so drawn as
 * text: the text-first rule) and the list's first card. Sections are server-driven, so this is the common
 * order, not a promise of what comes.
 *
 * Taller than the screen on purpose and clipped at its bottom, as the loaded list is, rather than
 * squeezed to fit.
 */
@Composable
internal fun HomeSkeleton(modifier: Modifier = Modifier) {
    Box(modifier.clipToBounds()) {
        BahrSkeleton(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .padding(top = BahrSpacing.lg),
        ) {
            HomeLayoutSkeleton(HomeLayout.Hero)
            HomeLayoutSkeleton(HomeLayout.CardRail)
            ListHeading(
                label = stringResource(Res.string.trips_all_title),
                totalItems = null,
                countAtEnd = true,
                modifier = Modifier.padding(horizontal = BahrSpacing.gutter),
            )
            TripCardSkeleton(modifier = Modifier.padding(horizontal = BahrSpacing.gutter))
        }
    }
}

/**
 * One section still loading, drawn as [layout] lays out its loaded items (MH-M1), with a title line above it
 * for the layouts whose sections usually have one. Exhaustive over [HomeLayout], like the renderer registry,
 * so a new layout gets a skeleton too. Use it inside a `BahrSkeleton`, which says "Loading" for the group.
 */
@Composable
internal fun HomeLayoutSkeleton(
    layout: HomeLayout,
    modifier: Modifier = Modifier,
    columns: Int = HomeLayout.MIN_GRID_COLUMNS,
) {
    val titled = layout != HomeLayout.Hero && layout != HomeLayout.NoteCard
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
        if (titled) {
            BahrSkeletonLine(
                modifier = Modifier.padding(horizontal = BahrSpacing.gutter),
                props = BahrSkeletonLine.Props(widthFraction = ROW_TITLE_WIDTH),
            )
        }
        when (layout) {
            HomeLayout.Hero -> ImageSkeleton(layout, Modifier.padding(horizontal = BahrSpacing.gutter).fillMaxWidth(), hero = true)
            HomeLayout.Strip ->
                Column(Modifier.padding(horizontal = BahrSpacing.gutter), verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
                    repeat(STRIP_SKELETONS) { ImageSkeleton(layout, Modifier.fillMaxWidth()) }
                }

            HomeLayout.PosterRail ->
                StillRail {
                    rail(
                        RAIL_SKELETONS,
                    ) { ImageSkeleton(layout, Modifier.fillParentMaxWidth(POSTER_WIDTH_FRACTION)) }
                }
            HomeLayout.TileRail ->
                StillRail {
                    rail(
                        RAIL_SKELETONS,
                    ) { ImageSkeleton(layout, Modifier.fillParentMaxWidth(TILE_WIDTH_FRACTION)) }
                }
            HomeLayout.TileGrid -> SkeletonGrid(columns) { ImageSkeleton(layout, it) }
            HomeLayout.Circles ->
                StillRail {
                    rail(CIRCLE_SKELETONS) {
                        Column(
                            modifier = Modifier.fillParentMaxWidth(CIRCLE_WIDTH_FRACTION).padding(BahrSpacing.xs),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
                        ) {
                            BahrSkeletonBlock(
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                props = BahrSkeletonBlock.Props(shape = BahrTheme.shapes.full),
                            )
                            BahrSkeletonLine(props = BahrSkeletonLine.Props(widthFraction = CAPTION_WIDTH))
                        }
                    }
                }

            HomeLayout.CardRail ->
                StillRail(cards = true) {
                    rail(RAIL_CARD_SKELETONS) {
                        TripCardSkeleton(modifier = Modifier.fillParentMaxWidth(ROW_ITEM_WIDTH_FRACTION), compact = true)
                    }
                }

            HomeLayout.CardGrid ->
                SkeletonGrid(columns, Modifier.padding(vertical = BahrSpacing.sm)) { TripCardSkeleton(modifier = it, compact = true) }

            HomeLayout.CardList ->
                Column(
                    modifier = Modifier.padding(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
                ) {
                    repeat(LIST_SKELETONS) { TripCardSkeleton() }
                }

            HomeLayout.Chips ->
                Row(
                    modifier = Modifier.padding(horizontal = BahrSpacing.gutter),
                    horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
                ) {
                    repeat(CHIP_SKELETONS) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
                        ) {
                            BahrSkeletonBlock(
                                modifier = Modifier.size(BahrSize.categoryTile),
                                props = BahrSkeletonBlock.Props(shape = BahrTheme.shapes.extraLarge),
                            )
                            Box(Modifier.width(BahrSize.categoryTile)) { BahrSkeletonLine() }
                        }
                    }
                }

            HomeLayout.NoteCard ->
                BahrSkeletonBlock(
                    modifier = Modifier.padding(horizontal = BahrSpacing.gutter).fillMaxWidth().aspectRatio(NOTE_SKELETON_RATIO),
                    props = BahrSkeletonBlock.Props(shape = BahrTheme.shapes.extraLarge),
                )
        }
    }
}

/** An image of an asset layout, at the layout's own ratio and its loaded shape. */
@Composable
private fun ImageSkeleton(
    layout: HomeLayout,
    modifier: Modifier,
    hero: Boolean = false,
) {
    BahrSkeletonBlock(
        modifier = modifier.aspectRatio(layout.ratio ?: 1f),
        props = BahrSkeletonBlock.Props(shape = if (hero) BahrTheme.shapes.hero else BahrTheme.shapes.large),
    )
}

/** A rail as the loaded one is laid out (a lazy row's parent-width share), but still. */
@Composable
private fun StillRail(
    cards: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    LazyRow(
        userScrollEnabled = false,
        contentPadding =
            if (cards) {
                PaddingValues(
                    horizontal = BahrSpacing.gutter,
                    vertical = BahrSpacing.sm,
                )
            } else {
                PaddingValues(horizontal = BahrSpacing.gutter)
            },
        horizontalArrangement = Arrangement.spacedBy(if (cards) BahrSpacing.md else BahrSpacing.sm),
        content = content,
    )
}

private fun LazyListScope.rail(
    count: Int,
    item: @Composable LazyItemScope.() -> Unit,
) {
    items(count, contentType = { CONTENT_SKELETON }) { item() }
}

/** Two rows of [columns] cells inside the gutters. */
@Composable
private fun SkeletonGrid(
    columns: Int,
    modifier: Modifier = Modifier,
    cell: @Composable (Modifier) -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = BahrSpacing.gutter),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        repeat(GRID_SKELETON_ROWS) {
            Row(horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
                repeat(columns) { cell(Modifier.weight(1f)) }
            }
        }
    }
}

/** A row's title line, as a share of the width inside the gutters. */
private const val ROW_TITLE_WIDTH = .45f

/** A circle's caption line, as a share of the circle's width. */
private const val CAPTION_WIDTH = .7f

/** Enough items to fill the row past its end, as the loaded rails do. */
private const val RAIL_SKELETONS = 3
private const val RAIL_CARD_SKELETONS = 2
private const val CIRCLE_SKELETONS = 5
private const val CHIP_SKELETONS = 4
private const val STRIP_SKELETONS = 2
private const val LIST_SKELETONS = 2
private const val GRID_SKELETON_ROWS = 2

/** A one-paragraph note panel is about four times as wide as it is tall on a phone. */
private const val NOTE_SKELETON_RATIO = 4f

private const val CONTENT_SKELETON = "skeleton"
