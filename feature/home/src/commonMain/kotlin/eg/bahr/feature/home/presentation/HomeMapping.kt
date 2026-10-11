package eg.bahr.feature.home.presentation

import eg.bahr.feature.home.model.HomeActionDto
import eg.bahr.feature.home.model.HomeBannerDto
import eg.bahr.feature.home.model.HomeSectionDto
import eg.bahr.feature.home.model.NoteSectionDto
import eg.bahr.feature.home.navigation.HomeAction

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

/**
 * Whether a slide draws the coral forward circle: the server asked for it and a tap on the slide leads
 * somewhere in this build. A `url` slide may ask for it before the app can open links; drawn on a slide
 * that is not a button, the circle would promise a tap that does nothing (M4-M1a review #5b).
 */
internal fun HomeBannerDto.showsForward(): Boolean = showForward && action.toHomeAction() != null

/**
 * Where Home draws its sections (user decision 2026-10-11, M4-M7). The run of `note` sections that ends
 * `/home` (every note after the last section of another type) is Home's footer: the handoff puts that
 * panel under the last trip card, so [trailing] is drawn after the paged "All trips" list, once its last
 * page is in. Everything else, a note in the middle included, is [inline]: above the chips, in the
 * server's order. Client-only: the contract only orders the sections and has no place after the list.
 */
internal data class HomePlacement(
    val inline: List<HomeSectionDto>,
    val trailing: List<HomeSectionDto>,
) {
    companion object {
        fun of(sections: List<HomeSectionDto>): HomePlacement {
            val footerStart = sections.indexOfLast { it !is NoteSectionDto } + 1
            return HomePlacement(inline = sections.take(footerStart), trailing = sections.drop(footerStart))
        }
    }
}
