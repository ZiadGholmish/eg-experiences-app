package eg.bahr.feature.trips.data

/**
 * The last search, kept for the whole run of the app (a Koin single): since M3-M0b search is a page
 * opened from Home's field or the Map header rather than a tab, and the user decided (2026-10-14) that
 * reopening it shows the last query and its results, as the M4-M8 Search tab did. In memory only;
 * [text] is what the field held and [filter] the active chip.
 */
internal class LastSearch {
    var text: String = ""
    var filter: String? = null
}
