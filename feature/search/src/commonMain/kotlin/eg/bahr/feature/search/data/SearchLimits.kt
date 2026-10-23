package eg.bahr.feature.search.data

/** What the search field may send (moved from feature:trips' `TripApiService` when it was split, M3-M16). */
internal object SearchLimits {
    /**
     * `listTrips`' `q` `maxLength`, in UTF-16 units (`String.length`): a longer one is answered 400
     * VALIDATION_FAILED, so the search field never lets the text grow past it.
     */
    const val MAX_QUERY_LENGTH = 80
}
