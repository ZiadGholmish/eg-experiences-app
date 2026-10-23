package eg.bahr.feature.triplisting.model

import eg.bahr.core.catalog.model.TripCardDto
import kotlinx.serialization.Serializable

/*
 * Wire shapes for `GET /api/v1/home/sections/{id}/trips`, field for field with `../docs/api/openapi.yaml`. The
 * category page's `TripPage` (`GET /trips`) is core:catalog's, shared with Home, search and the map (M4-M1c).
 */

/**
 * openapi `TripCardPage` (`listHomeSectionTrips`): a page of a Home row's whole list, with no chips.
 */
@Serializable
internal data class TripCardPageDto(
    val items: List<TripCardDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalItems: Long = 0,
    val totalPages: Int = 0,
)
