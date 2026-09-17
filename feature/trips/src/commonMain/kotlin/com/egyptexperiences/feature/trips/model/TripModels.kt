package com.egyptexperiences.feature.trips.model

import com.egyptexperiences.core.network.MoneyDto
import kotlinx.serialization.Serializable

/**
 * Wire shapes for `/api/v1/trips`.
 *
 * Field-for-field with `be/trip/src/main/java/com/egyptexperiences/trip/dto/`.
 * When the backend adds a field, add it here as nullable with a default — the
 * JSON parser ignores unknown keys, so an old app keeps working, and a
 * non-nullable addition here would break it instead.
 */
@Serializable
data class TripSummaryDto(
    val id: Long,
    val slug: String,
    val category: String? = null,
    val title: String,
    val kicker: String? = null,
    val pricePerPerson: MoneyDto,
    val durationMinutes: Int? = null,
    val coverPhotoUrl: String? = null,
    val host: HostDto? = null,
)

@Serializable
data class TripDetailDto(
    val id: Long,
    val slug: String,
    val category: String? = null,
    val title: String,
    val kicker: String? = null,
    val deck: String? = null,
    val description: String? = null,
    val pricePerPerson: MoneyDto,
    val capacity: Int = 0,
    val durationMinutes: Int? = null,
    val departurePoint: String? = null,
    val meetingPoint: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val host: HostDto? = null,
    val knowledge: HostKnowledgeDto? = null,
    val photoUrls: List<String> = emptyList(),
    val itinerary: List<ItineraryEntryDto> = emptyList(),
    val inclusions: List<InclusionDto> = emptyList(),
)

@Serializable
data class HostDto(
    val id: Long,
    val displayName: String,
    val verified: Boolean = false,
    val bio: String? = null,
)

/** The four host-knowledge fields the requirements doc asks every host for. */
@Serializable
data class HostKnowledgeDto(
    val whatToBring: String? = null,
    val bestTimeOfYear: String? = null,
    val whatToLookOutFor: String? = null,
    val whatToKnow: String? = null,
)

/** `included = false` renders as a struck-through "not included" row. */
@Serializable
data class InclusionDto(
    val included: Boolean,
    val text: String,
)

@Serializable
data class ItineraryEntryDto(
    val time: String? = null,
    val icon: String? = null,
    val text: String,
)

/**
 * A dated instance of a trip — and the seat inventory of record.
 *
 * [seatsRemaining] is a display hint only. It is read at list time and can be
 * stale by the time the user taps; the authoritative check is the conditional
 * update the backend runs when placing a hold, which is why a hold can still
 * fail with `SEATS_UNAVAILABLE` against a departure that looked bookable.
 */
@Serializable
data class DepartureDto(
    val id: Long,
    val date: String,
    val departTime: String? = null,
    val returnTime: String? = null,
    val seatsRemaining: Int = 0,
    val soldOut: Boolean = false,
    val bookable: Boolean = false,
    val status: String? = null,
)
