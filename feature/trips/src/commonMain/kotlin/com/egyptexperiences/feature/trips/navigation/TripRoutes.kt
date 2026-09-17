package com.egyptexperiences.feature.trips.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe routes for the trips graph.
 *
 * [TripDetailRoute] keys on the slug rather than the id because the slug is
 * what appears in a shared link — a deep link and an in-app tap then land on
 * exactly the same destination with the same argument.
 */
@Serializable
data object TripListRoute

@Serializable
data class TripDetailRoute(
    val slug: String,
)
