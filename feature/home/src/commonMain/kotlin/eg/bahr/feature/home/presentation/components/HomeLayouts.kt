package eg.bahr.feature.home.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import eg.bahr.core.catalog.presentation.WaitlistTags
import eg.bahr.core.catalog.presentation.components.Photo
import eg.bahr.core.designsystem.components.ImageGround
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.feature.home.model.HomeBannerDto
import eg.bahr.feature.home.navigation.HomeAction
import eg.bahr.feature.home.presentation.AssetsBlock
import eg.bahr.feature.home.presentation.CardsBlock
import eg.bahr.feature.home.presentation.ChipsBlock
import eg.bahr.feature.home.presentation.HomeBlock
import eg.bahr.feature.home.presentation.HomeCards
import eg.bahr.feature.home.presentation.HomeLayout
import eg.bahr.feature.home.presentation.HomeShape
import eg.bahr.feature.home.presentation.TextBlock
import eg.bahr.feature.home.presentation.toHomeAction

/*
 * The layout registry (MH-M1, PetMarket's `SectionLayouts`): one renderer per [HomeLayout], picked by a `when`
 * with no `else`, so a layout cannot ship without one. A renderer is typed by the shape it draws; the parser
 * only lets a section through when its layout's shape is its content's, and HomeLayoutsTest checks that each
 * renderer's shape is its layout's.
 *
 * Every renderer lays out its own gutters, so rails can scroll under them edge to edge. Vertical layouts (a
 * strip, a grid, a list) are plain Columns of Rows: they sit inside Home's LazyColumn, where a lazy vertical
 * layout cannot be measured, and a section holds a few items (the server caps cards at 10).
 */

/** What every renderer may need from the screen, bundled once per change (see [HomeSection]). */
internal class HomeSectionContext(
    val perPersonLabel: String,
    val waitlistTags: WaitlistTags,
    val onTripClick: (slug: String) -> Unit,
    val onAction: (HomeAction) -> Unit,
)

/** A layout's renderer, one kind per [HomeShape]. */
internal sealed interface HomeLayoutRenderer {
    val shape: HomeShape

    class Assets(
        val draw: @Composable (AssetsBlock, HomeSectionContext) -> Unit,
    ) : HomeLayoutRenderer {
        override val shape: HomeShape get() = HomeShape.Assets
    }

    class Cards(
        val draw: @Composable (CardsBlock, HomeSectionContext) -> Unit,
    ) : HomeLayoutRenderer {
        override val shape: HomeShape get() = HomeShape.Cards
    }

    class Chips(
        val draw: @Composable (ChipsBlock, HomeSectionContext) -> Unit,
    ) : HomeLayoutRenderer {
        override val shape: HomeShape get() = HomeShape.Chips
    }

    class Text(
        val draw: @Composable (TextBlock, HomeSectionContext) -> Unit,
    ) : HomeLayoutRenderer {
        override val shape: HomeShape get() = HomeShape.Text
    }
}

/** The registry: exhaustive over [HomeLayout], no `else`. */
internal fun homeLayoutRenderer(layout: HomeLayout): HomeLayoutRenderer =
    when (layout) {
        HomeLayout.Hero -> HomeLayoutRenderer.Assets { block, context -> BannerCarousel(block.banners, block.ratio, context.onAction) }
        HomeLayout.Strip -> HomeLayoutRenderer.Assets { block, context -> StripLayout(block, context) }
        HomeLayout.PosterRail -> HomeLayoutRenderer.Assets { block, context -> AssetRail(block, context, POSTER_WIDTH_FRACTION) }
        HomeLayout.TileRail -> HomeLayoutRenderer.Assets { block, context -> AssetRail(block, context, TILE_WIDTH_FRACTION) }
        HomeLayout.TileGrid -> HomeLayoutRenderer.Assets { block, context -> TileGridLayout(block, context) }
        HomeLayout.Circles -> HomeLayoutRenderer.Assets { block, context -> CirclesLayout(block, context) }
        HomeLayout.CardRail -> HomeLayoutRenderer.Cards { block, context -> CardRailLayout(block, context) }
        HomeLayout.CardGrid -> HomeLayoutRenderer.Cards { block, context -> CardGridLayout(block, context) }
        HomeLayout.CardList -> HomeLayoutRenderer.Cards { block, context -> CardListLayout(block, context) }
        HomeLayout.Chips -> HomeLayoutRenderer.Chips { block, context -> CategoryTiles(block, context.onAction) }
        HomeLayout.NoteCard ->
            HomeLayoutRenderer.Text { block, _ -> NotePanel(block.note, Modifier.padding(horizontal = BahrSpacing.gutter)) }
    }

/** Built once: the renderers hold no state, so one per layout serves every section. */
private val Renderers: Map<HomeLayout, HomeLayoutRenderer> = HomeLayout.entries.associateWith(::homeLayoutRenderer)

/**
 * [block] drawn by its layout's renderer. A renderer whose shape is not the block's draws nothing; the
 * parser never lets one through, so that would be a registry bug (HomeLayoutsTest).
 */
@Composable
internal fun HomeLayoutContent(
    block: HomeBlock,
    context: HomeSectionContext,
) {
    val renderer = Renderers.getValue(block.layout)
    when (block) {
        is AssetsBlock -> if (renderer is HomeLayoutRenderer.Assets) renderer.draw(block, context)
        is CardsBlock -> if (renderer is HomeLayoutRenderer.Cards) renderer.draw(block, context)
        is ChipsBlock -> if (renderer is HomeLayoutRenderer.Chips) renderer.draw(block, context)
        is TextBlock -> if (renderer is HomeLayoutRenderer.Text) renderer.draw(block, context)
    }
}

/** `strip`: one full-width slim banner per row, stacked (not a pager: the contract's strip is a list). */
@Composable
private fun StripLayout(
    block: AssetsBlock,
    context: HomeSectionContext,
) {
    Column(
        modifier = Modifier.padding(horizontal = BahrSpacing.gutter),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        block.banners.forEach { banner ->
            key(banner.id) {
                BannerTile(
                    banner = banner,
                    ratio = block.ratio,
                    onAction = context.onAction,
                    shape = BahrTheme.shapes.extraLarge,
                    titleStyle = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** `poster_rail` and `tile_rail`: a row of images at the layout's ratio, [widthFraction] of the row wide each. */
@Composable
private fun AssetRail(
    block: AssetsBlock,
    context: HomeSectionContext,
    widthFraction: Float,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = BahrSpacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        items(block.banners, key = { it.id }, contentType = { CONTENT_BANNER }) { banner ->
            BannerTile(
                banner = banner,
                ratio = block.ratio,
                onAction = context.onAction,
                shape = BahrTheme.shapes.large,
                titleStyle = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillParentMaxWidth(widthFraction),
            )
        }
    }
}

/** `tile_grid`: square tiles, `columns` across. */
@Composable
private fun TileGridLayout(
    block: AssetsBlock,
    context: HomeSectionContext,
) {
    GridRows(
        count = block.banners.size,
        columns = block.columns,
        key = { block.banners[it].id },
        gap = BahrSpacing.sm,
    ) { index, modifier ->
        BannerTile(
            banner = block.banners[index],
            ratio = block.ratio,
            onAction = context.onAction,
            shape = BahrTheme.shapes.large,
            titleStyle = MaterialTheme.typography.labelLarge,
            modifier = modifier,
        )
    }
}

/**
 * `circles`: round images with the caption under each (not over: a circle's scrim would hide most of it). Always
 * round, whatever ratio a server sends.
 */
@Composable
private fun CirclesLayout(
    block: AssetsBlock,
    context: HomeSectionContext,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = BahrSpacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        items(block.banners, key = { it.id }, contentType = { CONTENT_CIRCLE }) { banner ->
            val action = banner.action.toHomeAction()
            Column(
                modifier =
                    Modifier
                        .fillParentMaxWidth(CIRCLE_WIDTH_FRACTION)
                        .clip(BahrTheme.shapes.medium)
                        .then(if (action != null) Modifier.clickable(role = Role.Button) { context.onAction(action) } else Modifier)
                        .padding(BahrSpacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
            ) {
                ImageGround(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(BahrTheme.shapes.full)) { Photo(banner.image) }
                banner.title?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = TILE_TITLE_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * One image of a strip, a rail or a grid: the picture at [ratio], LQIP first, with its title over a bottom
 * scrim when it has one. The hero's extras (badge, price pill, forward circle) are the hero's only: a tile
 * is too small for them. Tappable only when its action leads somewhere, as a hero slide.
 */
@Composable
private fun BannerTile(
    banner: HomeBannerDto,
    ratio: Float,
    onAction: (HomeAction) -> Unit,
    shape: Shape,
    titleStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val action = banner.action.toHomeAction()
    val title = banner.title?.takeIf { it.isNotBlank() }
    Box(
        modifier =
            modifier
                .aspectRatio(ratio)
                .clip(shape)
                .then(if (action != null) Modifier.clickable(role = Role.Button) { onAction(action) } else Modifier),
    ) {
        ImageGround(modifier = Modifier.matchParentSize(), scrimBottom = title != null) { Photo(banner.image) }
        title?.let {
            Text(
                text = it,
                style = titleStyle,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = TILE_TITLE_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(BahrSpacing.md),
            )
        }
    }
}

/** `card_rail`: a row of cards, each [railWidthFraction] of the row wide so the next one peeks in. */
@Composable
private fun CardRailLayout(
    block: CardsBlock,
    context: HomeSectionContext,
) {
    val cards = block.cards
    val width = railWidthFraction(cards)
    LazyRow(
        // Vertical room so the cards' shadows are not clipped by the row.
        contentPadding = PaddingValues(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        items(cards.size, key = cards::key, contentType = { cards.kind }) { index ->
            HomeCard(cards, index, CardStyle.Compact, context, Modifier.fillParentMaxWidth(width))
        }
    }
}

/** `card_grid`: compact cards, `columns` across. */
@Composable
private fun CardGridLayout(
    block: CardsBlock,
    context: HomeSectionContext,
) {
    val cards = block.cards
    GridRows(
        count = cards.size,
        columns = block.columns,
        key = cards::key,
        gap = BahrSpacing.md,
        modifier = Modifier.padding(vertical = BahrSpacing.sm),
    ) { index, modifier ->
        HomeCard(cards, index, CardStyle.Compact, context, modifier)
    }
}

/** `card_list`: full-width cards one under another. */
@Composable
private fun CardListLayout(
    block: CardsBlock,
    context: HomeSectionContext,
) {
    val cards = block.cards
    Column(
        modifier = Modifier.padding(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        repeat(cards.size) { index ->
            key(cards.key(index)) { HomeCard(cards, index, CardStyle.Wide, context, Modifier.fillMaxWidth()) }
        }
    }
}

/**
 * [count] items, [columns] to a row inside the gutters, each cell an equal share ([cell]'s modifier). A short
 * last row keeps its cells at the same width, so the grid's columns line up.
 */
@Composable
private fun GridRows(
    count: Int,
    columns: Int,
    key: (index: Int) -> Any,
    gap: Dp,
    modifier: Modifier = Modifier,
    cell: @Composable RowScope.(index: Int, modifier: Modifier) -> Unit,
) {
    val rows = remember(count, columns) { (0 until count).chunked(columns) }
    Column(
        modifier = modifier.padding(horizontal = BahrSpacing.gutter),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { index -> key(key(index)) { cell(index, Modifier.weight(1f)) } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** How wide a card is in a rail, as a share of the row: a host is a small tile, a review reads as a quote. */
private fun railWidthFraction(cards: HomeCards): Float =
    when (cards) {
        is HomeCards.Trips, is HomeCards.Departures -> ROW_ITEM_WIDTH_FRACTION
        is HomeCards.Hosts -> HOST_WIDTH_FRACTION
        is HomeCards.Reviews -> REVIEW_WIDTH_FRACTION
    }

private const val CONTENT_CIRCLE = "circle"

/** A poster rail shows about 2.6 posters, a tile rail about 2.3 tiles (PetMarket's rails), circles about 4.5. */
internal const val POSTER_WIDTH_FRACTION = .38f
internal const val TILE_WIDTH_FRACTION = .42f
internal const val CIRCLE_WIDTH_FRACTION = .22f

/** A rail of hosts shows two and a peek; one of reviews, one and a peek, like a trip row. */
internal const val HOST_WIDTH_FRACTION = .44f
internal const val REVIEW_WIDTH_FRACTION = .8f

private const val TILE_TITLE_LINES = 2
