package eg.bahr.feature.home.presentation

/*
 * How a Home section is drawn (MH-M1, PLAN "MH — Home builder"): the contract's `HomeSection.layout`, each
 * with the shape of content it can draw. The renderer for each lives in `components/HomeLayouts.kt`, whose
 * `when` over [HomeLayout] has no `else`: a layout added here does not compile until it has a renderer and
 * a skeleton.
 */

/** What a section's items are, which decides the layouts that can draw it (the server's `SectionContentType` shape). */
internal enum class HomeShape {
    /** Images: `banners`. */
    Assets,

    /** Cards: `trips`, `hosts`, `departures`, `reviews`. */
    Cards,

    /** Chips: `categories`. */
    Chips,

    /** One panel of text: `note`. */
    Text,
}

/**
 * The contract's layouts (lowercase [key]s). [ratio] is the layout's own image ratio (width / height), the
 * one the server sends as `aspectRatio` (openapi `HomeSection.aspectRatio`); used only when that is absent
 * or malformed. Null for layouts that do not size an image. [usesColumns]: `columns` means something.
 */
internal enum class HomeLayout(
    val key: String,
    val shape: HomeShape,
    val ratio: Float? = null,
    val usesColumns: Boolean = false,
) {
    /** A full-width pager, 2:1, auto-advancing, with dots when there is more than one image. */
    Hero("hero", HomeShape.Assets, ratio = 2f),

    /** One full-width slim banner per row, 4:1, stacked. */
    Strip("strip", HomeShape.Assets, ratio = 4f),

    /** A horizontal row of 4:5 posters. */
    PosterRail("poster_rail", HomeShape.Assets, ratio = 4f / 5f),

    /** A horizontal row of square tiles. */
    TileRail("tile_rail", HomeShape.Assets, ratio = 1f),

    /** Square tiles, `columns` across, wrapping into rows. */
    TileGrid("tile_grid", HomeShape.Assets, ratio = 1f, usesColumns = true),

    /** A horizontal row of round images, each with its caption under it. */
    Circles("circles", HomeShape.Assets, ratio = 1f),

    /** A horizontal row of cards. */
    CardRail("card_rail", HomeShape.Cards),

    /** Cards, `columns` across, wrapping into rows. */
    CardGrid("card_grid", HomeShape.Cards, usesColumns = true),

    /** Cards one under another, full width. */
    CardList("card_list", HomeShape.Cards),

    /** A horizontal row of category chips (the handoff's category row). */
    Chips("chips", HomeShape.Chips),

    /** One panel of text. */
    NoteCard("note_card", HomeShape.Text),
    ;

    companion object {
        private val byKey = entries.associateBy { it.key }

        /**
         * The layout [key] names for a section of [shape], or null for one this build does not know (the
         * section is then dropped). `carousel` and `row`, which servers before MH-B1 sent, and an absent
         * layout (a note never needed one) map as the server's V420 migration did: by shape.
         */
        fun resolve(
            key: String?,
            shape: HomeShape,
        ): HomeLayout? {
            val normalised = key?.trim()?.lowercase()
            return when (normalised) {
                null, "", LEGACY_CAROUSEL, LEGACY_ROW -> legacy(normalised, shape)
                else -> byKey[normalised]
            }
        }

        private fun legacy(
            key: String?,
            shape: HomeShape,
        ): HomeLayout? =
            when (shape) {
                HomeShape.Assets ->
                    when (key) {
                        LEGACY_CAROUSEL -> Hero
                        LEGACY_ROW -> TileRail
                        // A banners section always named its layout; one with none is not guessed at.
                        else -> null
                    }

                HomeShape.Cards -> if (key.isNullOrEmpty()) null else CardRail
                // The old app drew categories and notes one way whatever the layout said.
                HomeShape.Chips -> Chips
                HomeShape.Text -> NoteCard
            }

        private const val LEGACY_CAROUSEL = "carousel"
        private const val LEGACY_ROW = "row"

        const val MIN_GRID_COLUMNS = 2
        const val MAX_GRID_COLUMNS = 3

        /** `columns` as a grid draws it: clamped to 2–3, and 2 when absent (the server's default too). */
        fun gridColumns(columns: Int?): Int = (columns ?: MIN_GRID_COLUMNS).coerceIn(MIN_GRID_COLUMNS, MAX_GRID_COLUMNS)
    }
}
