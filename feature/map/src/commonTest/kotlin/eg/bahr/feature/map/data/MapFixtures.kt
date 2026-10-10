package eg.bahr.feature.map.data

import eg.bahr.core.network.MoneyDto
import eg.bahr.feature.map.model.MapLegendCategoryDto
import eg.bahr.feature.map.model.MapNextDepartureDto
import eg.bahr.feature.map.model.MapPinDto
import eg.bahr.feature.map.model.MapPlaceDto
import eg.bahr.feature.map.model.TripMapDto
import kotlinx.datetime.LocalDate

/** Map data built in code, for the view-model and screenshot tests. Values follow the seed. */
internal object MapFixtures {
    const val CAIRO_ID = "0199c3a0-5eed-7000-8000-000000000201"
    const val ALEX_ID = "0199c3a0-5eed-7000-8000-000000000202"
    private val SATURDAY = LocalDate(2026, 10, 17)

    fun cairo(arabic: Boolean = false) =
        MapPlaceDto(
            id = CAIRO_ID,
            placeName = if (arabic) "موقف عبد المنعم رياض" else "Abdel Moneim Riad",
            city = if (arabic) "القاهرة" else "Cairo",
            governorate = if (arabic) "القاهرة" else "Cairo",
            lat = 30.0566,
            lng = 31.2288,
        )

    fun pin(
        slug: String,
        lat: Double,
        lng: Double,
        title: String = slug,
        amount: Long = 450,
        tone: String = "primary",
        category: String? = "on_the_boat",
        nights: Int = 0,
        departTime: String? = "05:00",
        departurePointId: String? = CAIRO_ID,
        distanceKm: Int? = 150,
        seats: Int? = 6,
        soldOut: Boolean = false,
        subtitle: String? = null,
    ) = MapPinDto(
        slug = slug,
        title = title,
        subtitle = subtitle,
        price = MoneyDto(amount, "EGP"),
        durationLabel = "05:00 → 22:00",
        nights = nights,
        lat = lat,
        lng = lng,
        placeName = null,
        category = category,
        tone = tone,
        nextDeparture =
            seats?.let {
                MapNextDepartureDto(
                    id = "dep-$slug",
                    date = SATURDAY,
                    returnDate = LocalDate(2026, 10, 17 + nights),
                    seatsRemaining = if (soldOut) 0 else it,
                    capacity = 18,
                    soldOut = soldOut,
                )
            },
        departTime = departTime,
        departurePointId = departurePointId,
        distanceKm = distanceKm,
    )

    /** The Burullus trips plus a sold-out overnight one and a beach day, all leaving Cairo. */
    fun map(arabic: Boolean = false): TripMapDto {
        fun t(
            en: String,
            ar: String,
        ) = if (arabic) ar else en
        return TripMapDto(
            pins =
                listOf(
                    pin(
                        "burullus-dawn",
                        31.503,
                        30.804,
                        title = t("Dawn on Lake Burullus", "الفجر على بحيرة البرلس"),
                        subtitle = t("Lake Burullus · Kafr El Sheikh", "بحيرة البرلس · كفر الشيخ"),
                    ),
                    pin(
                        "boughaz-fish-market",
                        31.586,
                        30.981,
                        title = t("The boughaz and the morning fish market", "البوغاز وسوق السمك الصبح"),
                        amount = 380,
                        seats = 14,
                    ),
                    pin(
                        "burullus-murals",
                        31.5905,
                        30.9905,
                        title = t("Murals of Burg El Burullus, on foot", "جداريات برج البرلس، مشي"),
                        amount = 220,
                        tone = "quaternary",
                        category = "murals",
                    ),
                    pin(
                        "ain-sokhna-beach-day",
                        29.6,
                        32.34,
                        title = t("A Red Sea beach day at Ain Sokhna", "يوم على البحر الأحمر في العين السخنة"),
                        amount = 550,
                        tone = "success",
                        category = "beach",
                        departTime = "06:00",
                        distanceKm = 130,
                    ),
                    pin(
                        "white-desert-overnight",
                        27.36,
                        28.17,
                        title = t("A night in the White Desert", "ليلة في الصحراء البيضاء"),
                        subtitle = t("Farafra · New Valley", "الفرافرة · الوادي الجديد"),
                        amount = 2900,
                        category = "night_trips",
                        nights = 1,
                        departTime = "06:00",
                        distanceKm = 500,
                        soldOut = true,
                    ),
                ),
            departurePoints = listOf(cairo(arabic)),
            legend =
                listOf(
                    MapLegendCategoryDto("on_the_boat", t("On the boat", "في القارب"), "sailing", "primary"),
                    MapLegendCategoryDto("murals", t("Murals", "جداريات"), "palette", "quaternary"),
                    MapLegendCategoryDto("beach", t("Beach", "شاطئ"), "beach_access", "success"),
                    MapLegendCategoryDto("night_trips", t("Night trips", "رحلات ليلية"), "bedtime", "primary"),
                ),
        )
    }
}
