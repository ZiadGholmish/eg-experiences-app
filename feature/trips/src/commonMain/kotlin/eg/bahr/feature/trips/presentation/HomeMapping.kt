package eg.bahr.feature.trips.presentation

import eg.bahr.feature.trips.model.HomeActionDto
import eg.bahr.feature.trips.navigation.HomeAction

/**
 * What a banner tap does, or null when it does nothing, and then the banner is not a button at all:
 * `none`, an action type this build does not know, or a missing value.
 *
 * `url` is null too, for now: the contract wants an https link opened in an in-app browser, and the
 * app has none yet. A banner that rippled and then did nothing would read as broken (M4-M1a review
 * #5b), so it is drawn as a picture until the browser lands; then this maps https links (only) to an
 * action the NavHost opens.
 */
internal fun HomeActionDto.toHomeAction(): HomeAction? {
    val target = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when (type) {
        "trip" -> HomeAction.OpenTrip(target)
        "category" -> HomeAction.OpenCategory(target)
        else -> null
    }
}

/**
 * The contract's `aspectRatio` (`"16:9"`, `^[1-9][0-9]*:[1-9][0-9]*$`) as width / height, or null when
 * it is absent or malformed. The server never sends a size in dp; the width is the screen's.
 */
internal fun parseAspectRatio(ratio: String?): Float? {
    val parts = ratio?.split(':')?.takeIf { it.size == 2 } ?: return null
    val width = parts[0].toIntOrNull()?.takeIf { it > 0 } ?: return null
    val height = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
    return width.toFloat() / height
}
