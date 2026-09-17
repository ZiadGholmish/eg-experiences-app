package com.egyptexperiences.feature.booking.navigation

import kotlinx.serialization.Serializable

@Serializable
data class BookingRoute(
    val departureId: Long,
)

/**
 * The confirmation screen keys on the booking reference — the thing the user is
 * told to keep, and the thing a "where is my booking" deep link will carry.
 */
@Serializable
data class BookingConfirmedRoute(
    val ref: String,
)
