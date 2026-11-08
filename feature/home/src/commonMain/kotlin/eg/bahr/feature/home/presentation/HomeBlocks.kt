package eg.bahr.feature.home.presentation

import eg.bahr.core.catalog.model.HomeCategoryDto
import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.feature.home.model.BannersSectionDto
import eg.bahr.feature.home.model.CategoriesSectionDto
import eg.bahr.feature.home.model.DeparturesSectionDto
import eg.bahr.feature.home.model.HomeBannerDto
import eg.bahr.feature.home.model.HomeDepartureDto
import eg.bahr.feature.home.model.HomeHostDto
import eg.bahr.feature.home.model.HomeNoteDto
import eg.bahr.feature.home.model.HomeReviewDto
import eg.bahr.feature.home.model.HomeSectionDto
import eg.bahr.feature.home.model.HostsSectionDto
import eg.bahr.feature.home.model.NoteSectionDto
import eg.bahr.feature.home.model.ReviewsSectionDto
import eg.bahr.feature.home.model.SkippedSectionDto
import eg.bahr.feature.home.model.TripsSectionDto
import eg.bahr.feature.home.model.key
import eg.bahr.feature.home.navigation.HomeAction
import eg.bahr.feature.home.presentation.components.seeAllAction

/*
 * Home's sections as they are drawn (MH-M1): each decoded section ([HomeSectionDto]) with its layout resolved and
 * checked against its content, its grid columns clamped and its image ratio settled. One block per shape, so a
 * renderer gets the items it can draw and nothing else ([HomeShape]).
 */

/** One section ready to draw. [key] is unique on the page (the section's id). */
internal sealed interface HomeBlock {
    val id: String
    val title: String?
    val layout: HomeLayout

    val key: String get() = id

    /**
     * The section's content type in Home's list (M3-M11): one per drawing, so a scrolled-away section's
     * composition is reused only for one drawn the same way. A rail of hosts is not a rail of trips.
     */
    val contentType: String
}

/** `banners`: images at [ratio] (width / height), [columns] across for a grid. */
internal data class AssetsBlock(
    override val id: String,
    override val title: String?,
    override val layout: HomeLayout,
    val banners: List<HomeBannerDto>,
    val ratio: Float,
    val columns: Int,
) : HomeBlock {
    override val contentType: String get() = "section-${layout.key}"
}

/** Card sections. [seeAll]: where the title's "See all" leads, only ever for trips (null: none). */
internal data class CardsBlock(
    override val id: String,
    override val title: String?,
    override val layout: HomeLayout,
    val cards: HomeCards,
    val columns: Int,
    val seeAll: HomeAction? = null,
) : HomeBlock {
    override val contentType: String get() = "section-${layout.key}-${cards.kind}"
}

/** `categories`. */
internal data class ChipsBlock(
    override val id: String,
    override val title: String?,
    override val layout: HomeLayout,
    val categories: List<HomeCategoryDto>,
) : HomeBlock {
    override val contentType: String get() = "section-${layout.key}"
}

/** `note`: the first item only, should a server ever send more (the contract sends exactly one). */
internal data class TextBlock(
    override val id: String,
    override val title: String?,
    override val layout: HomeLayout,
    val note: HomeNoteDto,
) : HomeBlock {
    override val contentType: String get() = "section-${layout.key}"
}

/** A card section's items, one list per card type. [key] is the lazy-row key of the card at an index. */
internal sealed interface HomeCards {
    val size: Int
    val kind: String

    fun key(index: Int): String

    data class Trips(
        val items: List<TripCardDto>,
    ) : HomeCards {
        override val size: Int get() = items.size
        override val kind: String get() = "trip"

        override fun key(index: Int): String = items[index].slug
    }

    data class Hosts(
        val items: List<HomeHostDto>,
    ) : HomeCards {
        override val size: Int get() = items.size
        override val kind: String get() = "host"

        override fun key(index: Int): String = items[index].slug
    }

    data class Departures(
        val items: List<HomeDepartureDto>,
    ) : HomeCards {
        override val size: Int get() = items.size
        override val kind: String get() = "departure"

        override fun key(index: Int): String = items[index].id
    }

    data class Reviews(
        val items: List<HomeReviewDto>,
    ) : HomeCards {
        override val size: Int get() = items.size
        override val kind: String get() = "review"

        override fun key(index: Int): String = items[index].id
    }
}

/** The shape of a decoded section's items; null for one this build skipped. */
internal val HomeSectionDto.shape: HomeShape?
    get() =
        when (this) {
            is BannersSectionDto -> HomeShape.Assets
            is TripsSectionDto, is HostsSectionDto, is DeparturesSectionDto, is ReviewsSectionDto -> HomeShape.Cards
            is CategoriesSectionDto -> HomeShape.Chips
            is NoteSectionDto -> HomeShape.Text
            is SkippedSectionDto -> null
        }

/**
 * The sections this build draws, in the server's order (PetMarket's `SectionsParser`, trimmed). A section is
 * dropped and [log]ged, never guessed at, when its `type` is unknown or it did not decode, when its `layout`
 * is unknown, when the layout cannot draw its content's shape (a banner as a card rail), or when it has
 * nothing to show (the server omits those, so one would be a server bug). Items that did not decode and
 * repeated item keys are dropped too (a repeated key would crash a lazy row) and logged.
 */
internal fun parseHomeSections(
    sections: List<HomeSectionDto>,
    log: (String) -> Unit,
): List<HomeBlock> = sections.mapNotNull { parseSection(it, log) }

private fun parseSection(
    section: HomeSectionDto,
    log: (String) -> Unit,
): HomeBlock? {
    if (section is SkippedSectionDto) {
        log("Dropped a Home section of type ${section.type}: ${section.reason}")
        return null
    }
    val shape = section.shape ?: return null
    val rawLayout = section.rawLayout
    val layout = HomeLayout.resolve(rawLayout, shape)
    if (layout == null) {
        log("Dropped Home section ${section.key}: unknown layout '$rawLayout'")
        return null
    }
    if (layout.shape != shape) {
        log("Dropped Home section ${section.key}: layout ${layout.key} cannot draw $shape")
        return null
    }
    val unreadable = section.droppedItems
    if (unreadable > 0) log("Dropped $unreadable unreadable item(s) from Home section ${section.key}")
    val block =
        section.toBlock(layout) { kept, served ->
            if (kept < served) log("Dropped ${served - kept} repeated item(s) from Home section ${section.key}")
        }
    if (block == null) log("Dropped an empty Home section ${section.key}")
    return block
}

private fun HomeSectionDto.toBlock(
    layout: HomeLayout,
    onDistinct: (kept: Int, served: Int) -> Unit,
): HomeBlock? {
    fun <T> List<T>.distinct(key: (T) -> String): List<T>? = distinctBy(key).also { onDistinct(it.size, size) }.ifEmpty { null }
    val columns = HomeLayout.gridColumns(columnsOrNull)
    return when (this) {
        is BannersSectionDto ->
            items.distinct { it.id }?.let {
                AssetsBlock(id, title, layout, it, parseAspectRatio(aspectRatio) ?: layout.ratio ?: 1f, columns)
            }

        is TripsSectionDto ->
            items.distinct { it.slug }?.let { CardsBlock(id, title, layout, HomeCards.Trips(it), columns, seeAllAction()) }

        is HostsSectionDto -> items.distinct { it.slug }?.let { CardsBlock(id, title, layout, HomeCards.Hosts(it), columns) }
        is DeparturesSectionDto -> items.distinct { it.id }?.let { CardsBlock(id, title, layout, HomeCards.Departures(it), columns) }
        is ReviewsSectionDto -> items.distinct { it.id }?.let { CardsBlock(id, title, layout, HomeCards.Reviews(it), columns) }
        is CategoriesSectionDto -> items.distinct { it.key }?.let { ChipsBlock(id, title, layout, it) }
        is NoteSectionDto -> items.firstOrNull()?.let { TextBlock(id, title, layout, it) }
        is SkippedSectionDto -> null
    }
}

private val HomeSectionDto.rawLayout: String?
    get() =
        when (this) {
            is BannersSectionDto -> layout
            is TripsSectionDto -> layout
            is CategoriesSectionDto -> layout
            is NoteSectionDto -> layout
            is HostsSectionDto -> layout
            is DeparturesSectionDto -> layout
            is ReviewsSectionDto -> layout
            is SkippedSectionDto -> null
        }

private val HomeSectionDto.columnsOrNull: Int?
    get() =
        when (this) {
            is BannersSectionDto -> columns
            is TripsSectionDto -> columns
            is CategoriesSectionDto -> columns
            is NoteSectionDto -> columns
            is HostsSectionDto -> columns
            is DeparturesSectionDto -> columns
            is ReviewsSectionDto -> columns
            is SkippedSectionDto -> null
        }

private val HomeSectionDto.droppedItems: Int
    get() =
        when (this) {
            is BannersSectionDto -> droppedItems
            is TripsSectionDto -> droppedItems
            is CategoriesSectionDto -> droppedItems
            is NoteSectionDto -> droppedItems
            is HostsSectionDto -> droppedItems
            is DeparturesSectionDto -> droppedItems
            is ReviewsSectionDto -> droppedItems
            is SkippedSectionDto -> 0
        }

/**
 * Where Home draws its sections (user decision 2026-10-11, M4-M7). The run of `note` sections that ends
 * `/home` (every note after the last section of another type) is Home's footer: the handoff puts that
 * panel under the last trip card, so [trailing] is drawn after the paged "All trips" list, once its last
 * page is in. Everything else, a note in the middle included, is [inline]: above the chips, in the
 * server's order. Client-only: the contract only orders the sections and has no place after the list.
 */
internal data class HomePlacement(
    val inline: List<HomeBlock>,
    val trailing: List<HomeBlock>,
) {
    companion object {
        fun of(blocks: List<HomeBlock>): HomePlacement {
            val footerStart = blocks.indexOfLast { it !is TextBlock } + 1
            return HomePlacement(inline = blocks.take(footerStart), trailing = blocks.drop(footerStart))
        }
    }
}
