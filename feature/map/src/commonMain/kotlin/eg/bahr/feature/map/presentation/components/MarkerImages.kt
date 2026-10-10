package eg.bahr.feature.map.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import eg.bahr.core.designsystem.theme.BahrBorder
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import kotlin.math.ceil

/** A pin's colours: the pill, stem and dot are [fill], the label [content]; [outline] rings it, if any. */
@Immutable
internal data class PinColors(
    val fill: Color,
    val content: Color,
    val outline: Color? = null,
)

/**
 * Draws the map's markers as pictures, in common code, so Android and iOS show the very same pin in
 * the app's own font and colours (map.html `.pin`: a price pill, a short stem, a dot on the spot).
 * A Google map marker is a picture on both platforms; drawing it here keeps the Swift bridge free of
 * any design decision.
 */
@Composable
internal fun rememberMarkerPainter(): MarkerPainter {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val style = MaterialTheme.typography.labelLarge
    val ring = MaterialTheme.colorScheme.surface
    return remember(measurer, density, direction, style, ring) { MarkerPainter(measurer, density, direction, style, ring) }
}

internal class MarkerPainter(
    private val measurer: TextMeasurer,
    private val density: Density,
    private val direction: LayoutDirection,
    private val style: TextStyle,
    /** The dot's ring and a selected pin's outline: the page colour, so they read on any map tile. */
    private val ring: Color,
) {
    private val padH = with(density) { BahrSpacing.md.toPx() }
    private val padV = with(density) { BahrSpacing.sm.toPx() }
    private val stemWidth = with(density) { BahrSize.connector.toPx() }
    private val stemHeight = with(density) { BahrSize.pinStem.toPx() }
    private val dotRadius = with(density) { BahrSize.pinDot.toPx() } / 2
    private val ringWidth = with(density) { BahrBorder.selected.toPx() }
    private val hairline = with(density) { BahrBorder.hairline.toPx() }

    /**
     * Pictures already drawn, by what they show. A tap changes two pins (the old and new selection), so
     * the rest keep their very picture, and the platform maps (iOS caches its `UIImage`s by picture)
     * convert only those two. Bounded by the map's content: each pin selected or not, plus the
     * departure markers and distance tags shown. Only touched from composition.
     */
    private val drawn = mutableMapOf<DrawnKey, MarkerImage>()

    private data class DrawnKey(
        val label: String,
        val colors: PinColors,
        val selected: Boolean,
        val tag: Boolean,
    )

    /**
     * A pin: [label] in a pill over a stem and a dot, anchored on the dot's centre. [selected] rings the
     * pill in the page colour, so the open card's pin stands out on the map.
     */
    fun pin(
        label: String,
        colors: PinColors,
        selected: Boolean = false,
    ): MarkerImage = drawn.getOrPut(DrawnKey(label, colors, selected, tag = false)) { drawPin(label, colors, selected) }

    private fun drawPin(
        label: String,
        colors: PinColors,
        selected: Boolean,
    ): MarkerImage {
        val text = measurer.measure(label, style.copy(color = colors.content), layoutDirection = direction, density = density)
        // The ring is drawn inside the picture, so the pill sits inset by it on every side.
        val inset = ringWidth
        val pillW = text.size.width + 2 * padH
        val pillH = text.size.height + 2 * padV
        val dotOuter = dotRadius + ringWidth
        val width = ceil(pillW + 2 * inset).toInt()
        val height = ceil(inset + pillH + stemHeight + 2 * dotOuter).toInt()
        val bitmap = ImageBitmap(width, height)
        val centerX = width / 2f
        val dotCenterY = inset + pillH + stemHeight + dotOuter
        draw(bitmap) {
            val pillTopLeft = Offset(inset, inset)
            val corner = CornerRadius(pillH / 2)
            drawRect(colors.fill, Offset(centerX - stemWidth / 2, inset + pillH), Size(stemWidth, stemHeight))
            drawCircle(ring, dotOuter, Offset(centerX, dotCenterY))
            drawCircle(colors.fill, dotRadius, Offset(centerX, dotCenterY))
            if (selected) {
                drawRoundRect(ring, Offset.Zero, Size(pillW + 2 * inset, pillH + 2 * inset), CornerRadius(pillH / 2 + inset))
            }
            drawRoundRect(colors.fill, pillTopLeft, Size(pillW, pillH), corner)
            colors.outline?.let { drawRoundRect(it, pillTopLeft, Size(pillW, pillH), corner, style = Stroke(hairline)) }
            drawText(text, topLeft = Offset(inset + padH, inset + padV))
        }
        return MarkerImage(bitmap, anchorX = .5f, anchorY = dotCenterY / height)
    }

    /** A label alone (the drive's "150 km"), centred on its point. */
    fun tag(
        label: String,
        colors: PinColors,
    ): MarkerImage = drawn.getOrPut(DrawnKey(label, colors, selected = false, tag = true)) { drawTag(label, colors) }

    private fun drawTag(
        label: String,
        colors: PinColors,
    ): MarkerImage {
        val text = measurer.measure(label, style.copy(color = colors.content), layoutDirection = direction, density = density)
        val width = ceil(text.size.width + 2 * padH).toInt()
        val height = ceil(text.size.height + 2 * padV).toInt()
        val bitmap = ImageBitmap(width, height)
        draw(bitmap) {
            val corner = CornerRadius(height / 2f)
            drawRoundRect(colors.fill, cornerRadius = corner)
            colors.outline?.let { drawRoundRect(it, cornerRadius = corner, style = Stroke(hairline)) }
            drawText(text, topLeft = Offset(padH, padV))
        }
        return MarkerImage(bitmap, anchorX = .5f, anchorY = .5f)
    }

    private fun draw(
        bitmap: ImageBitmap,
        block: DrawScope.() -> Unit,
    ) {
        val size = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
        CanvasDrawScope().draw(density, direction, Canvas(bitmap), size, block)
    }
}

/** The colours of the map's markers, all from the theme. */
@Immutable
internal data class MapPalette(
    val tones: Map<String, PinColors>,
    val fallback: PinColors,
    val soldOut: PinColors,
    val departure: PinColors,
    val distance: PinColors,
    val route: Color,
) {
    /** A pin in the contract's `Tone`; one this build does not know is drawn as `primary`. */
    fun tone(tone: String?): PinColors = tones[tone] ?: fallback
}

/**
 * Solid pins, dark fill and light label, as the handoff draws them on the map (teal for the water,
 * a dark coral for the village, ink for the bus). Each tone takes its tint family's darkest role for
 * the fill and its container for the label, the badge pairing turned inside out, so every tone stays
 * readable on a pale map. The coral tone uses the coral *container* family only: coral itself is for
 * actions (mobile rule 3). Sold out is grey with an outline; the bus departure is ink.
 */
@Composable
internal fun rememberMapPalette(): MapPalette {
    val c = MaterialTheme.colorScheme
    val x = BahrTheme.colors
    return remember(c, x) {
        val primary = PinColors(c.onPrimaryContainer, c.primaryContainer)
        MapPalette(
            tones =
                mapOf(
                    "primary" to primary,
                    "secondary" to PinColors(c.onSecondaryContainer, c.secondaryContainer),
                    "tertiary" to PinColors(c.onTertiaryContainer, c.tertiaryContainer),
                    "quaternary" to PinColors(x.onQuaternaryContainer, x.quaternaryContainer),
                    "success" to PinColors(x.onSuccessContainer, x.successContainer),
                ),
            fallback = primary,
            soldOut = PinColors(c.surfaceContainer, c.onSurfaceVariant, outline = c.outline),
            departure = PinColors(c.onSurface, c.surface),
            distance = PinColors(c.surface, c.onSurface, outline = c.onSurface),
            route = c.onSurface,
        )
    }
}
