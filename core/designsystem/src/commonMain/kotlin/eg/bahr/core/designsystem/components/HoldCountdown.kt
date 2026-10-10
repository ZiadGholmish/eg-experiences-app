package eg.bahr.core.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrTween
import eg.bahr.core.designsystem.theme.overlineCase
import eg.bahr.core.designsystem.theme.reducedMotion
import kotlin.math.roundToInt

/**
 * The seat-hold panel (HANDOFF screen 5): a coral circle with [icon], the overline [label], `mm:ss`
 * (left to right in Arabic too) and a bar draining with [progress] (1 → 0). The time turns
 * error-coloured in the last 2 minutes. One component for the hold screen and Home's "Continue your
 * booking" card.
 *
 * Motion (M4-M4), all from [BahrMotion] and all instant under reduce motion ([BahrTheme.reducedMotion]):
 * - each digit that changes rolls (the old one slides up and out, the new one up and in, both
 *   fading); digits that stay the same do not move, so 14:59 → 14:58 rolls one digit;
 * - the bar's width and the time's colour ease to their new values;
 * - in the last minute the timer's circle swells gently and warms to the error colour, and back,
 *   on a loop. Only the circle pulses (a filled shape with its icon on the matching `on` colour), so
 *   the text's contrast never dips. At rest (the pulse's start, and under reduce motion) the panel
 *   looks exactly as before M4-M4.
 *
 * Screen readers: with [announcement] set, the panel reads as that text alone and is a polite live
 * region, so it is spoken when the text changes. Callers change it once a minute (HANDOFF: the
 * countdown is announced at minute boundaries, never every second); the per-second `mm:ss` is not
 * exposed. Without it the time reads as one `mm:ss` text, never digit by digit.
 */
@Composable
fun HoldCountdown(
    secondsLeft: Int,
    label: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    icon: ImageVector? = null,
    announcement: String? = null,
) {
    val c = MaterialTheme.colorScheme
    val urgent = secondsLeft <= URGENT_SECONDS
    val lastMinute = secondsLeft in 1..LAST_MINUTE_SECONDS
    val semantics =
        if (announcement != null) {
            Modifier.clearAndSetSemantics {
                contentDescription = announcement
                liveRegion = LiveRegionMode.Polite
            }
        } else {
            Modifier
        }
    val alertColor by animateColorAsState(if (urgent) c.error else c.onTertiaryContainer, bahrTween())
    val barColor by animateColorAsState(if (urgent) c.error else c.tertiary, bahrTween())
    Row(
        modifier
            .fillMaxWidth()
            .clip(BahrTheme.shapes.card)
            .background(c.tertiaryContainer)
            .then(semantics)
            .padding(horizontal = BahrSpacing.lg, vertical = HOLD_PANEL_PADDING.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        if (icon != null) {
            TimerCircle(icon = icon, pulsing = lastMinute && !BahrTheme.reducedMotion)
        }
        Column(Modifier.weight(1f)) {
            Text(
                label.overlineCase(BahrTheme.locale.isArabic),
                style = BahrTheme.type.overline,
                color = c.onTertiaryContainer,
            )
            RollingTime(
                time = BahrFormat.countdown(secondsLeft),
                style = MaterialTheme.typography.headlineMedium,
                color = alertColor,
            )
        }
        if (progress != null) {
            // Ticks come every ~500 ms; a short ease between them makes the bar drain rather than step.
            val shown by animateFloatAsState(progress.coerceIn(0f, 1f), bahrTween())
            Box(
                Modifier
                    .size(width = HOLD_BAR_WIDTH.dp, height = HOLD_BAR_HEIGHT.dp)
                    .clip(BahrTheme.shapes.full)
                    .background(c.tertiary.copy(alpha = HOLD_BAR_TRACK_ALPHA)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(shown)
                        .clip(BahrTheme.shapes.full)
                        .background(barColor),
                )
            }
        }
    }
}

/**
 * The coral circle with the timer icon. While [pulsing] it swells to [BahrMotion.CountdownPulseScale]
 * and warms from `tertiary` to `error` and back. The icon stays `onTertiary`, which equals `onError`
 * in every scheme, and `error` is the darker fill, so the icon only gains contrast. Not pulsing, it
 * is the plain coral circle: the infinite transition is not even started, so reduce motion gets a
 * still panel.
 */
@Composable
private fun TimerCircle(
    icon: ImageVector,
    pulsing: Boolean,
) {
    val c = MaterialTheme.colorScheme
    var scale = 1f
    var fill = c.tertiary
    if (pulsing) {
        val pulse by rememberInfiniteTransition().animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    tween(BahrMotion.CountdownPulse, easing = BahrMotion.EaseInOut),
                    RepeatMode.Reverse,
                ),
        )
        scale = 1f + (BahrMotion.CountdownPulseScale - 1f) * pulse
        fill = lerp(c.tertiary, c.error, pulse)
    }
    Box(
        Modifier
            .size(HOLD_ICON_CIRCLE.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.clip(BahrTheme.shapes.full)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.onTertiary, modifier = Modifier.size(HOLD_ICON.dp))
    }
}

/**
 * `mm:ss`, one slot per character. A slot whose character changes rolls it (slide up + fade); the
 * others stay still, so 14:59 → 14:58 rolls one digit.
 *
 * Every slot draws the *whole* time, exactly where the single text it replaces drew it, and is
 * clipped to its own character's column (whole pixels, edge to edge). At rest the columns tile the
 * line, so the panel is pixel for pixel the one-text countdown; a text per character would not be,
 * because each would round its width up and lose the type's tracking at its ends. The time is laid
 * out left to right whatever the language, and reads as one `mm:ss`, never digit by digit.
 */
@Composable
private fun RollingTime(
    time: String,
    style: TextStyle,
    color: Color,
) {
    val reduced = BahrTheme.reducedMotion
    val roll = bahrTween<IntOffset>()
    val fade = bahrTween<Float>()
    val spoken = BahrFormat.ltr(time)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val measurer = rememberTextMeasurer()
        val line = remember(time, style, measurer) { measurer.measure(time, style, softWrap = false, maxLines = 1) }
        // Column edges: the start of each character in the laid-out line, rounded to whole pixels.
        val edges =
            IntArray(time.length + 1) { at ->
                when (at) {
                    0 -> 0
                    time.length -> line.size.width
                    else -> line.getHorizontalPosition(at, usePrimaryDirection = true).roundToInt()
                }
            }
        Layout(
            modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(spoken) },
            content = {
                time.forEachIndexed { at, char ->
                    val column = Modifier.column(start = edges[at], width = edges[at + 1] - edges[at])
                    if (reduced) {
                        // Nothing rolls: the slot simply shows its column of the new time.
                        Box(Modifier.clipToBounds()) { TimeLine(time, style, color, column) }
                    } else {
                        AnimatedContent(
                            targetState = char,
                            transitionSpec = {
                                (slideInVertically(roll) { it } + fadeIn(fade)) togetherWith
                                    (slideOutVertically(roll) { -it } + fadeOut(fade)) using SizeTransform(clip = false)
                            },
                            // Clipped so a rolling digit never paints over its neighbours, the label
                            // above or the space below.
                            modifier = Modifier.clipToBounds(),
                        ) { shown ->
                            // The leaving character is drawn in the time it belonged to: only this
                            // slot's column shows, and tabular digits keep every column in place.
                            TimeLine(time.replaceRange(at, at + 1, shown.toString()), style, color, column)
                        }
                    }
                }
            },
        ) { slots, constraints ->
            val placeables = slots.map { it.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)) }
            layout(line.size.width, placeables.maxOfOrNull { it.height } ?: line.size.height) {
                placeables.forEachIndexed { at, slot -> slot.place(edges[at], 0) }
            }
        }
    }
}

/** The whole time on one line, as the single text the countdown used to be. */
@Composable
private fun TimeLine(
    time: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier,
) {
    Text(text = time, style = style, color = color, softWrap = false, maxLines = 1, modifier = modifier)
}

/**
 * Lays the whole line out as usual but takes only [width] pixels, from [start]: the line is shifted
 * so that its column [start] sits at this slot's left edge.
 */
private fun Modifier.column(
    start: Int,
    width: Int,
): Modifier =
    layout { measurable, constraints ->
        val line = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
        layout(width.coerceAtLeast(0), line.height) { line.place(-start, 0) }
    }

private const val URGENT_SECONDS = 120

/** The pulse runs in the last minute only, and stops at 00:00 (the hold is being checked then). */
private const val LAST_MINUTE_SECONDS = 60
private const val HOLD_PANEL_PADDING = 14
private const val HOLD_ICON_CIRCLE = 42
private const val HOLD_ICON = 21
private const val HOLD_BAR_WIDTH = 70
private const val HOLD_BAR_HEIGHT = 6

/** The handoff's `rgba(217,79,40,.2)` track: coral at 20 %. */
private const val HOLD_BAR_TRACK_ALPHA = .2f
