package eg.bahr.feature.trips.presentation

import eg.bahr.feature.trips.model.HomeActionDto
import eg.bahr.feature.trips.navigation.HomeAction

/**
 * What a banner tap does, or null when it does nothing: `none`, an action type this build does not
 * know, a missing value, or a `url` that is not https (the contract promises https; anything else is
 * not opened, whatever the server says).
 */
internal fun HomeActionDto.toHomeAction(): HomeAction? {
    val target = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when (type) {
        "trip" -> HomeAction.OpenTrip(target)
        "category" -> HomeAction.OpenCategory(target)
        "url" -> if (target.startsWith(HTTPS_PREFIX, ignoreCase = true)) HomeAction.OpenUrl(target) else null
        else -> null
    }
}

private const val HTTPS_PREFIX = "https://"

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
