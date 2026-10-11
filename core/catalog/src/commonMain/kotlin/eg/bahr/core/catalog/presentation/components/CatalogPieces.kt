package eg.bahr.core.catalog.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import eg.bahr.core.catalog.model.ImageDto
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrTheme

/*
 * Small parts the trip page (feature:trips), Home (feature:home) and the map (feature:map) share
 * (M4-M1c): the section heading, the contract's `Tone` as a tint, a server icon name as one of the
 * design's icons, and a photo over its LQIP. Here rather than in core:designsystem because they read contract values (`Tone`,
 * Material Symbols names), which the design system does not know.
 */

/** A section heading: the handoff's `title` role (17px / 800). */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

/**
 * The handoff's colour tints (its `TINT` table): a container, the text on it, and the icon colour.
 *
 * Coral is the action colour and nothing else (mobile rule 3), so the coral tint uses the coral
 * *container* family only: its icon is `onTertiaryContainer`, not the prototype's `tertiary`.
 */
enum class Tint { Teal, Gold, Coral, Magenta, Green }

data class TintColors(
    val container: Color,
    val content: Color,
    val icon: Color,
)

@Composable
fun Tint.colors(): TintColors {
    val c = MaterialTheme.colorScheme
    val x = BahrTheme.colors
    return when (this) {
        Tint.Teal -> TintColors(c.primaryContainer, c.onPrimaryContainer, c.primary)
        Tint.Gold -> TintColors(c.secondaryContainer, c.onSecondaryContainer, x.secondaryDim)
        Tint.Coral -> TintColors(c.tertiaryContainer, c.onTertiaryContainer, c.onTertiaryContainer)
        Tint.Magenta -> TintColors(x.quaternaryContainer, x.onQuaternaryContainer, x.quaternary)
        Tint.Green -> TintColors(x.successContainer, x.onSuccessContainer, x.success)
    }
}

/** The contract's lowercase `Tone` as a tint; an unknown tone is teal. */
fun toneTint(tone: String?): Tint =
    when (tone) {
        "secondary" -> Tint.Gold
        "tertiary" -> Tint.Coral
        "quaternary" -> Tint.Magenta
        "success" -> Tint.Green
        else -> Tint.Teal
    }

/**
 * A Material Symbols name from the server (`directions_bus`) as one of the design's icons. A name
 * this build does not have is a plain dot rather than a missing glyph.
 */
fun symbolIcon(name: String?): BahrIcons = knownSymbolIcon(name) ?: BahrIcons.Circle

/**
 * A Material Symbols name from the server as one of the design's icons, or null for a name this build
 * does not have (or none). For a place where a stand-in dot would read as a bullet, such as Home's note
 * panel (M4-M7): there an unknown icon is left out.
 */
fun knownSymbolIcon(name: String?): BahrIcons? =
    when (name) {
        "directions_bus" -> BahrIcons.DirectionsBus
        "local_cafe" -> BahrIcons.LocalCafe
        "sailing" -> BahrIcons.Sailing
        "flutter_dash" -> BahrIcons.FlutterDash
        "restaurant" -> BahrIcons.Restaurant
        "palette" -> BahrIcons.Palette
        "waves" -> BahrIcons.Waves
        "home" -> BahrIcons.Home
        "storefront" -> BahrIcons.Storefront
        "beach_access" -> BahrIcons.BeachAccess
        "bedtime" -> BahrIcons.Bedtime
        "place", "location_on" -> BahrIcons.Place
        // The trip list's filter chips (M4-B1 facets).
        "apps" -> BahrIcons.Apps
        "calendar_month" -> BahrIcons.CalendarMonth
        "sell" -> BahrIcons.Sell
        "schedule" -> BahrIcons.Schedule
        else -> null
    }

/**
 * LQIP first, then the photo over it; the ground is already painted, so nothing shifts. Shared by the trip
 * page's hero (feature:trips) and Home's banners (feature:home).
 */
@Composable
fun Photo(image: ImageDto) {
    Box(Modifier.fillMaxSize()) {
        image.lqip?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        AsyncImage(
            model = image.url,
            contentDescription = image.alt,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
