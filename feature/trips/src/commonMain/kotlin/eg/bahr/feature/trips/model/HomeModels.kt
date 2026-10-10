package eg.bahr.feature.trips.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
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
) : HomeSectionDto

/** `type: trips`: a titled row of trip cards, served in display order (bookable, sold out, no date). */
@Serializable
internal data class TripsSectionDto(
    val id: String,
    val type: String,
    val title: String? = null,
    val layout: String,
    val aspectRatio: String? = null,
    val items: List<TripCardDto>,
) : HomeSectionDto

/** `type: categories`: category chips. */
@Serializable
internal data class CategoriesSectionDto(
    val id: String,
    val type: String,
    val title: String? = null,
    val layout: String,
    val aspectRatio: String? = null,
    val items: List<HomeCategoryDto>,
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
                HomeSectionType.BANNERS -> json.decodeFromJsonElement(BannersSectionDto.serializer(), element)
                HomeSectionType.TRIPS -> json.decodeFromJsonElement(TripsSectionDto.serializer(), element)
                HomeSectionType.CATEGORIES -> json.decodeFromJsonElement(CategoriesSectionDto.serializer(), element)
                else -> SkippedSectionDto(type = type, reason = "unknown type")
            }
        } catch (unreadable: IllegalArgumentException) {
            SkippedSectionDto(type = type, reason = "unreadable: ${unreadable.message}")
        }
    }
}
