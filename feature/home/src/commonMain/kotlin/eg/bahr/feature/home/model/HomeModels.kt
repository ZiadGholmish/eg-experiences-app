package eg.bahr.feature.home.model

import eg.bahr.core.catalog.model.BadgeDto
import eg.bahr.core.catalog.model.HomeCategoryDto
import eg.bahr.core.catalog.model.ImageDto
import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.network.MoneyDto
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
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
 * `Home`, `HomeSection`, `HomeBanner`, `HomeAction`, `HomeNote` (PLAN §5c), and since MH-B2 `HomeHost`,
 * `HomeDeparture`, `HomeReview`, `HomeTripRef`. Trip rows reuse core:catalog's
 * [TripCardDto], category tiles its [HomeCategoryDto] (shared with the map's legend, M4-M1c).
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
    /** Grid layouts only (since MH-B1): items across, clamped to 2–3 when drawn. */
    val columns: Int? = null,
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
    /** Grid layouts only (since MH-B1): items across, clamped to 2–3 when drawn. */
    val columns: Int? = null,
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
    /** Grid layouts only (since MH-B1): items across, clamped to 2–3 when drawn. */
    val columns: Int? = null,
    val items: List<HomeCategoryDto>,
    /** How many of the served items did not decode and were left out (see [HomeSectionsSerializer]); logged. */
    @Transient
    val droppedItems: Int = 0,
) : HomeSectionDto

/**
 * `type: note` (since M4-B4): a panel of text the server states, such as where the buses leave, under
 * the section's optional [title]. The contract sends exactly one item; [layout] is ignored. Only the
 * first item is drawn should a server ever send more.
 */
@Serializable
internal data class NoteSectionDto(
    val id: String,
    val type: String,
    val title: String? = null,
    val layout: String? = null,
    val aspectRatio: String? = null,
    /** Grid layouts only (since MH-B1): items across, clamped to 2–3 when drawn. */
    val columns: Int? = null,
    val items: List<HomeNoteDto>,
    /** How many of the served items did not decode and were left out (see [HomeSectionsSerializer]); logged. */
    @Transient
    val droppedItems: Int = 0,
) : HomeSectionDto

/**
 * openapi `HomeNote`. [body] is localised and never empty; a blank one fails the item's decode, so the
 * section is dropped as empty rather than drawn as an empty panel. [icon] is a Material Symbols name.
 */
@Serializable
internal data class HomeNoteDto(
    val body: String,
    val icon: String? = null,
) {
    init {
        require(body.isNotBlank()) { "a note's body is never empty" }
    }
}

/** `type: hosts` (since MH-B2): featured hosts, each card opening the host's public page. No "See all". */
@Serializable
internal data class HostsSectionDto(
    val id: String,
    val type: String,
    val title: String? = null,
    val layout: String,
    val aspectRatio: String? = null,
    val columns: Int? = null,
    val items: List<HomeHostDto>,
    @Transient
    val droppedItems: Int = 0,
) : HomeSectionDto

/**
 * `type: departures` (since MH-B2): "leaving soon", the soonest dates with seats left across trips, one per
 * trip, soonest first. Each card opens its trip's page on that date. No "See all".
 */
@Serializable
internal data class DeparturesSectionDto(
    val id: String,
    val type: String,
    val title: String? = null,
    val layout: String,
    val aspectRatio: String? = null,
    val columns: Int? = null,
    val items: List<HomeDepartureDto>,
    @Transient
    val droppedItems: Int = 0,
) : HomeSectionDto

/** `type: reviews` (since MH-B2): the newest approved reviews of published trips, each opening its trip. No "See all". */
@Serializable
internal data class ReviewsSectionDto(
    val id: String,
    val type: String,
    val title: String? = null,
    val layout: String,
    val aspectRatio: String? = null,
    val columns: Int? = null,
    val items: List<HomeReviewDto>,
    @Transient
    val droppedItems: Int = 0,
) : HomeSectionDto

/**
 * openapi `HomeHost`: an approved host. [kind] is the contract's `HostKind`, kept a string. [rating] is
 * absent when the host has no reviews yet. No phone, as `HostProfile`.
 */
@Serializable
internal data class HomeHostDto(
    val slug: String,
    val kind: String,
    val name: String,
    val avatar: ImageDto? = null,
    val verified: Boolean = false,
    val tripsRun: Int = 0,
    val rating: HomeHostRatingDto? = null,
)

/** openapi `HomeHost.rating`: the mean (one decimal, from the server) over [count] reviews. */
@Serializable
internal data class HomeHostRatingDto(
    val value: Double,
    val count: Int,
)

/**
 * openapi `HomeDeparture`: one bookable date. [id] is the departure; [departTime] is the bus's Cairo
 * wall clock. [seatsRemaining] is a display hint (the hold decides). A date or time that does not parse
 * costs this card only (see [HomeSectionsSerializer]).
 */
@Serializable
internal data class HomeDepartureDto(
    val id: String,
    val date: LocalDate,
    val departTime: LocalTime,
    val seatsRemaining: Int,
    val price: MoneyDto,
    val trip: HomeTripRefDto,
)

/** openapi `HomeReview`: an approved review of [trip]. [body] is absent for a rating only. */
@Serializable
internal data class HomeReviewDto(
    val id: String,
    val rating: Int,
    val author: String,
    val tone: String? = null,
    val dateISO: LocalDate,
    val verified: Boolean = false,
    val body: String? = null,
    val trip: HomeTripRefDto,
)

/** openapi `HomeTripRef`: the trip a home card leads to (`getTrip` with [slug]). */
@Serializable
internal data class HomeTripRefDto(
    val slug: String,
    val title: String,
    val image: ImageDto? = null,
)

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
            is NoteSectionDto -> id
            is HostsSectionDto -> id
            is DeparturesSectionDto -> id
            is ReviewsSectionDto -> id
            is SkippedSectionDto -> "skipped:$type"
        }

/**
 * openapi `HomeBanner`. [title] is absent on an image-only banner. [badge], [price] (a "from" price),
 * [meta] and [showForward] are the hero-slide extras (M4-B4), each optional on its own and absent from
 * older servers: a slide without them is drawn as before, a picture with its title.
 */
@Serializable
internal data class HomeBannerDto(
    val id: String,
    val title: String? = null,
    val image: ImageDto,
    val action: HomeActionDto,
    val badge: BadgeDto? = null,
    val price: MoneyDto? = null,
    val meta: String? = null,
    val showForward: Boolean = false,
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

/** The `type` values this build draws (openapi `HomeSection.type`). */
internal object HomeSectionType {
    const val BANNERS = "banners"
    const val TRIPS = "trips"
    const val CATEGORIES = "categories"
    const val NOTE = "note"
    const val HOSTS = "hosts"
    const val DEPARTURES = "departures"
    const val REVIEWS = "reviews"
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

                HomeSectionType.NOTE ->
                    decodeKeepingItems(json, element, NoteSectionDto.serializer(), HomeNoteDto.serializer()) {
                        copy(droppedItems = it)
                    }

                HomeSectionType.HOSTS ->
                    decodeKeepingItems(json, element, HostsSectionDto.serializer(), HomeHostDto.serializer()) {
                        copy(droppedItems = it)
                    }

                HomeSectionType.DEPARTURES ->
                    decodeKeepingItems(json, element, DeparturesSectionDto.serializer(), HomeDepartureDto.serializer()) {
                        copy(droppedItems = it)
                    }

                HomeSectionType.REVIEWS ->
                    decodeKeepingItems(json, element, ReviewsSectionDto.serializer(), HomeReviewDto.serializer()) {
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
