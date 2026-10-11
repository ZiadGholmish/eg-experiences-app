package eg.bahr.feature.home.navigation

import kotlinx.serialization.Serializable

/**
 * Home: the trips on sale, under the server-driven sections. Named for the list it was before Home
 * had sections (and before it left feature:trips, M4-M1c); kept so the app's back-stack checks
 * (`popUpTo<TripListRoute>`) read as before.
 */
@Serializable
data object TripListRoute
