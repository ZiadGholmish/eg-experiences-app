package eg.bahr.feature.trips.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/*
 * Wire shapes for `GET /api/v1/home` (`getHome`), field for field with `../docs/api/openapi.yaml`:
 * `Home`, `HomeSection`, `HomeBanner`, `HomeAction`, `HomeCategory` (PLAN §5c). Trip rows reuse
 * [TripCardDto].
 *
 * `HomeSection.items` is a `oneOf` with no discriminator of its own: the item shape follows the
 * section's `type`. So a section is decoded in two steps by [HomeSectionsSerializer], which reads
 * `type` first and then the whole section with that type's serializer.
 *
 * Every enum-like value (`type`, `layout`, `action.type`, `tone`) stays a string: the contract keeps
 * `type` open on purpose, and a value added later must not fail the whole Home decode.
 */

/** openapi `Home`. */
@Serializable
internal data class HomeDto(
    @Serializable(with = HomeSectionsSerializer::class)
    val sections: List<HomeSectionDto> = emptyList(),
)

/** openapi `HomeSection`, one subtype per `type` this build knows, plus [SkippedSectionDto]. */
internal sealed interface HomeSectionDto

/** `type: banners`: a carousel (or row) of tappable images. */
@Serializable
internal data class BannersSectionDto(
    val id: String,
    val type: String,
    val title: String? = null,
    val layout: String,
    val aspectRatio: String? = null,
    val items: List<HomeBannerDto>,
    /** How many of the served items did not decode and were left out (see [HomeSectionsSerializer]); logged. */
    @Transient
    val droppedItems: Int = 0,
) : HomeSectionDto

/**
 * `type: trips`: a titled row of trip cards, served in display order (bookable, sold out, no date).
 * [items] is page 0 of the section's whole list; [totalItems] is that list's length and [seeAll] where
 * "See all" leads (both since M4-B1b, absent from older servers, so no "See all" then).
 */
@Serializable
internal data class TripsSectionDto(
    val id: String,
    val type: String,
    val title: String? = null,
    val layout: String,
    val aspectRatio: String? = null,
    val items: List<TripCardDto>,
    val totalItems: Int? = null,
    val seeAll: HomeSeeAllDto? = null,
    /** How many of the served items did not decode and were left out (see [HomeSectionsSerializer]); logged. */
    @Transient
    val droppedItems: Int = 0,
) : HomeSectionDto

/**
 * openapi `HomeSeeAll`. [type] is `category` ([value] a category key: the category page) or `section`
 * ([value] the section id: `GET /home/sections/{id}/trips`); kept a string so a new type only hides the link.
 */
@Serializable
internal data class HomeSeeAllDto(
    val type: String,
    val value: String,
)

/** openapi `HomeSeeAll.type` values. */
internal object SeeAllType {
    const val CATEGORY = "category"
    const val SECTION = "section"
}

/** `type: categories`: category chips. */
@Serializable
internal data class CategoriesSectionDto(
    val id: String,
    val type: String,
    val title: String? = null,
    val layout: String,
    val aspectRatio: String? = null,
    val items: List<HomeCategoryDto>,
    /** How many of the served items did not decode and were left out (see [HomeSectionsSerializer]); logged. */
    @Transient
    val droppedItems: Int = 0,
) : HomeSectionDto

/**
 * A section this build does not draw: a `type` it does not know (the contract adds types additively),
 * or a known type whose payload did not decode. Either way the rest of Home still shows; the view
 * model drops these and logs [type] and [reason].
 */
internal data class SkippedSectionDto(
    val type: String?,
    val reason: String,
) : HomeSectionDto

/** A list key for [this] section: its id (skipped sections are never drawn). */
internal val HomeSectionDto.key: String
    get() =
        when (this) {
            is BannersSectionDto -> id
            is TripsSectionDto -> id
            is CategoriesSectionDto -> id
            is SkippedSectionDto -> "skipped:$type"
        }

/** openapi `HomeBanner`. [title] is absent on an image-only banner. */
@Serializable
internal data class HomeBannerDto(
    val id: String,
    val title: String? = null,
    val image: ImageDto,
    val action: HomeActionDto,
)

/**
 * openapi `HomeAction`. [type] is the contract's lowercase `trip`/`category`/`url`/`none`; [value] is a
 * slug, a category key or an https URL, and absent for `none`.
 */
@Serializable
internal data class HomeActionDto(
    val type: String,
    val value: String? = null,
)

/** openapi `HomeCategory`. [icon] is a Material Symbols name; [tone] the contract's `Tone`. */
@Serializable
internal data class HomeCategoryDto(
    val key: String,
    val label: String,
    val icon: String? = null,
    val tone: String,
)

/** The `type` values this build draws (openapi `HomeSection.type`). */
internal object HomeSectionType {
    const val BANNERS = "banners"
    const val TRIPS = "trips"
    const val CATEGORIES = "categories"
}

/**
 * Decodes `sections` one at a time, so one section this build cannot read costs that section, not the
 * whole Home. Uses the decoder's own [Json] (the app client's, with `ignoreUnknownKeys`), never
 * `Json.Default`, which would fail on the first field a newer server adds.
 *
 * Decode only: the app never sends a Home.
 */
internal object HomeSectionsSerializer : KSerializer<List<HomeSectionDto>> {
    private val raw = ListSerializer(JsonElement.serializer())

    override val descriptor = raw.descriptor

    override fun deserialize(decoder: Decoder): List<HomeSectionDto> {
        val input = decoder as? JsonDecoder ?: throw SerializationException("Home sections decode from JSON only")
        return input.decodeSerializableValue(raw).map { decodeSection(input.json, it) }
    }

    override fun serialize(
        encoder: Encoder,
        value: List<HomeSectionDto>,
    ): Unit = throw SerializationException("GET /home is read-only: the app never sends a section")

    internal fun decodeSection(
        json: Json,
        element: JsonElement,
    ): HomeSectionDto {
        // SerializationException is an IllegalArgumentException, as is `jsonObject` on a non-object.
        val type =
            try {
                element.jsonObject["type"]?.jsonPrimitive?.contentOrNull
            } catch (notAnObject: IllegalArgumentException) {
                return SkippedSectionDto(type = null, reason = "not an object: ${notAnObject.message}")
            }
        return try {
            when (type) {
                HomeSectionType.BANNERS ->
                    decodeKeepingItems(json, element, BannersSectionDto.serializer(), HomeBannerDto.serializer()) {
                        copy(droppedItems = it)
                    }

                HomeSectionType.TRIPS ->
                    decodeKeepingItems(json, element, TripsSectionDto.serializer(), TripCardDto.serializer()) {
                        copy(droppedItems = it)
                    }

                HomeSectionType.CATEGORIES ->
                    decodeKeepingItems(json, element, CategoriesSectionDto.serializer(), HomeCategoryDto.serializer()) {
                        copy(droppedItems = it)
                    }

                else -> SkippedSectionDto(type = type, reason = "unknown type")
            }
        } catch (unreadable: IllegalArgumentException) {
            SkippedSectionDto(type = type, reason = "unreadable: ${unreadable.message}")
        }
    }

    /**
     * Decodes a section of a known type item by item (M4-M1a review #2): an item that does not decode
     * (a trip card missing its slug, say) costs that item, not the row. The section itself must still
     * decode (its own fields, and an `items` array), or the caller skips it. A section left with no
     * items is dropped later as empty, like one served empty.
     */
    private fun <S : HomeSectionDto, I> decodeKeepingItems(
        json: Json,
        element: JsonElement,
        section: KSerializer<S>,
        item: KSerializer<I>,
        withDropped: S.(dropped: Int) -> S,
    ): S {
        val fields = element.jsonObject
        val items = fields[ITEMS] as? JsonArray ?: return json.decodeFromJsonElement(section, element)
        val readable =
            items.filter { candidate ->
                try {
                    json.decodeFromJsonElement(item, candidate)
                    true
                } catch (_: IllegalArgumentException) {
                    false
                }
            }
        val decoded = json.decodeFromJsonElement(section, JsonObject(fields + (ITEMS to JsonArray(readable))))
        val dropped = items.size - readable.size
        return if (dropped > 0) decoded.withDropped(dropped) else decoded
    }

    private const val ITEMS = "items"
}
