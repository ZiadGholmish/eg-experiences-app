package eg.bahr.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.theme.BahrElevation
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrShadow
import eg.bahr.core.designsystem.theme.overlineCase
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.a11y_busy
import eg.bahr.core.localization.generated.resources.departure_seats_left
import eg.bahr.core.localization.generated.resources.departure_sold_out
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/*
 * Ported from the design handoff, vendored at `../docs/design/compose-reference/components/BahrComponents.kt`
 * (relative to mobile-app). Signatures are kept as delivered (plus a defaulted `loading` on the
 * button, and `SeatBadge` reading the theme locale), so these do not follow the Interface + Props
 * pattern new components use.
 *
 * Icons: Material Symbols Rounded, the prototype's set. Callers pass `BahrIcons.X.outlined()` or
 * `.filled()`; components never hard-code icons.
 */

private const val PRIMARY_BUTTON_MIN_HEIGHT = 54
private const val PRIMARY_BUTTON_ICON = 18
private const val TRAILING_ALPHA = .85f
private const val PROGRESS_STROKE = 2
private const val CHIP_ICON = 15
private const val CHIP_GAP = 5
private const val CHIP_COUNT_ALPHA = .22f
private const val CHIP_COUNT_PADDING_H = 5
private const val CHIP_COUNT_PADDING_V = 1
private const val SEAT_BADGE_PADDING_V = 3
private const val FEW_SEATS_THRESHOLD = 4
private const val URGENT_SECONDS = 120
private const val HOLD_PANEL_PADDING = 14
private const val HOLD_ICON_CIRCLE = 42
private const val HOLD_ICON = 21
private const val HOLD_BAR_WIDTH = 70
private const val HOLD_BAR_HEIGHT = 6

/** The handoff's `rgba(217,79,40,.2)` track: coral at 20 %. */
private const val HOLD_BAR_TRACK_ALPHA = .2f
private const val SCRIM_TOP_ALPHA = .45f
private const val SCRIM_TOP_END = .4f
private const val SCRIM_BOTTOM_START = .38f
private const val SCRIM_BOTTOM_ALPHA = .72f

/**
 * How the primary button looks and behaves for a given [enabled] / [loading] pair.
 *
 * Loading is not disabled: work the user started is in flight, so the button keeps its coral
 * fill and shows progress, but ignores further taps (a second tap must not place a second hold).
 */
internal data class PrimaryButtonAppearance(
    val clickable: Boolean,
    val filled: Boolean,
    val showsProgress: Boolean,
)

internal fun primaryButtonAppearance(
    enabled: Boolean,
    loading: Boolean,
) = PrimaryButtonAppearance(
    clickable = enabled && !loading,
    filled = enabled || loading,
    showsProgress = loading,
)

/**
 * The one coral button. Use once per screen, for the step forward.
 *
 * [loading] is an addition to the handoff signature (defaulted, so handoff-style calls still
 * compile): see [primaryButtonAppearance].
 */
@Composable
fun BahrPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailing: String? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val appearance = primaryButtonAppearance(enabled, loading)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (!appearance.filled) {
            BahrTheme.colors.track
        } else if (pressed && appearance.clickable) {
            BahrTheme.colors.tertiaryHover
        } else {
            scheme.tertiary
        },
        tween(BahrMotion.Short, easing = BahrMotion.Standard),
    )
    val shape = BahrTheme.shapes.full
    val busy = stringResource(Res.string.a11y_busy)
    Row(
        modifier
            .heightIn(min = PRIMARY_BUTTON_MIN_HEIGHT.dp)
            .then(if (appearance.filled) Modifier.bahrShadow(BahrElevation.Cta, shape, BahrTheme.colors) else Modifier)
            .clip(shape)
            .background(bg)
            .clickable(src, indication = null, enabled = appearance.clickable, role = Role.Button, onClick = onClick)
            // While loading, `clickable` reports the button as disabled; without a state a screen reader
            // would say the same as for an invalid form. This says it is busy (M0-M2 review R2-1).
            .then(if (appearance.showsProgress) Modifier.semantics { stateDescription = busy } else Modifier)
            .padding(horizontal = BahrSpacing.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm, Alignment.CenterHorizontally),
    ) {
        val fg = if (appearance.filled) scheme.onTertiary else BahrTheme.colors.onSurfaceDisabled
        if (appearance.showsProgress) {
            // Sits where the leading icon goes, so the label stays put and readable.
            CircularProgressIndicator(
                modifier = Modifier.size(PRIMARY_BUTTON_ICON.dp),
                color = fg,
                strokeWidth = PROGRESS_STROKE.dp,
            )
        } else if (leadingIcon != null) {
            Icon(leadingIcon, null, tint = fg, modifier = Modifier.size(PRIMARY_BUTTON_ICON.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = fg)
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = TRAILING_ALPHA))
        }
    }
}

/** Filter chip: unselected = surfaceContainer, selected = primary with teal shadow. Count is optional. */
@Composable
fun BahrFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    count: Int? = null,
) {
    val c = MaterialTheme.colorScheme
    val x = BahrTheme.colors
    val disabled = count == 0 && !selected
    val shape = BahrTheme.shapes.full
    val bg =
        when {
            selected -> c.primary
            disabled -> x.surfaceLowest
            else -> c.surfaceContainer
        }
    val fg =
        when {
            selected -> c.onPrimary
            disabled -> x.onSurfaceDisabled
            else -> x.onSurfaceSecondary
        }
    Row(
        modifier
            .heightIn(min = BahrSpacing.minTouch)
            .then(if (selected) Modifier.bahrShadow(BahrElevation.Chip, shape, x) else Modifier)
            .clip(shape)
            .background(bg)
            .clickable(enabled = !disabled, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = BahrSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                null,
                Modifier.size(CHIP_ICON.dp),
                tint =
                    when {
                        selected -> x.primaryBright
                        disabled -> x.onSurfaceDisabled
                        else -> c.primary
                    },
            )
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
        if (count != null) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) c.onPrimary else c.onSurfaceVariant,
                modifier =
                    Modifier
                        .clip(shape)
                        .background(if (selected) c.onPrimary.copy(alpha = CHIP_COUNT_ALPHA) else x.track)
                        .padding(horizontal = CHIP_COUNT_PADDING_H.dp, vertical = CHIP_COUNT_PADDING_V.dp),
            )
        }
    }
}

enum class SeatState { Available, Few, SoldOut }

fun seatState(left: Int) =
    when {
        left <= 0 -> SeatState.SoldOut
        left <= FEW_SEATS_THRESHOLD -> SeatState.Few
        else -> SeatState.Available
    }

/**
 * Seats-left badge used in the date strip, calendar and host view.
 *
 * Copy comes from `core:localization` (the handoff hard-coded EN/AR here) as a plural, so Arabic
 * reads right for 1, 2, 3–10 and 11+ (M0-M2 review R2-2); the few/available distinction is
 * carried by colour, as both read "N seats left".
 */
@Composable
fun SeatBadge(
    left: Int,
    modifier: Modifier = Modifier,
) {
    val x = BahrTheme.colors
    val c = MaterialTheme.colorScheme
    val seatsLeft = pluralStringResource(Res.plurals.departure_seats_left, left, left)
    val (bg, fg, text) =
        when (seatState(left)) {
            SeatState.SoldOut -> Triple(c.errorContainer, c.error, stringResource(Res.string.departure_sold_out))
            SeatState.Few -> Triple(c.tertiaryContainer, c.onTertiaryContainer, seatsLeft)
            SeatState.Available -> Triple(x.successContainer, x.onSuccessContainer, seatsLeft)
        }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier =
            modifier
                .clip(BahrTheme.shapes.full)
                .background(bg)
                .padding(horizontal = BahrSpacing.sm, vertical = SEAT_BADGE_PADDING_V.dp),
    )
}

/**
 * The seat-hold panel (HANDOFF screen 5): a coral circle with [icon], the overline [label], `mm:ss`
 * (left to right in Arabic too) and a bar draining with [progress] (1 → 0). The time turns
 * error-coloured in the last 2 minutes.
 *
 * Screen readers: with [announcement] set, the panel reads as that text alone and is a polite live
 * region, so it is spoken when the text changes. Callers change it once a minute (HANDOFF: the
 * countdown is announced at minute boundaries, never every second); the per-second `mm:ss` is not
 * exposed.
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
    val semantics =
        if (announcement != null) {
            Modifier.clearAndSetSemantics {
                contentDescription = announcement
                liveRegion = LiveRegionMode.Polite
            }
        } else {
            Modifier
        }
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
            Box(
                Modifier.size(HOLD_ICON_CIRCLE.dp).clip(BahrTheme.shapes.full).background(c.tertiary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = c.onTertiary, modifier = Modifier.size(HOLD_ICON.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                label.overlineCase(BahrTheme.locale.isArabic),
                style = BahrTheme.type.overline,
                color = c.onTertiaryContainer,
            )
            Text(
                BahrFormat.ltr(BahrFormat.countdown(secondsLeft)),
                style = MaterialTheme.typography.headlineMedium,
                color = if (urgent) c.error else c.onTertiaryContainer,
            )
        }
        if (progress != null) {
            Box(
                Modifier
                    .size(width = HOLD_BAR_WIDTH.dp, height = HOLD_BAR_HEIGHT.dp)
                    .clip(BahrTheme.shapes.full)
                    .background(c.tertiary.copy(alpha = HOLD_BAR_TRACK_ALPHA)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .clip(BahrTheme.shapes.full)
                        .background(if (urgent) c.error else c.tertiary),
                )
            }
        }
    }
}

/** Bottom action bar: translucent surface, top shadow, price on the start side, CTA on the end side. */
@Composable
fun StickyActionBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .bahrShadow(BahrElevation.Sticky, RectangleShape, BahrTheme.colors)
            .background(BahrTheme.colors.surfaceTranslucent)
            .navigationBarsPadding()
            .padding(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
        content = content,
    )
}

/**
 * Image placeholder. Paints surfaceDim immediately so layout, title, price and CTA never wait on
 * the network; the image (Coil 3 AsyncImage) fades in over it.
 */
@Composable
fun ImageGround(
    modifier: Modifier = Modifier,
    scrimTop: Boolean = false,
    scrimBottom: Boolean = false,
    image: @Composable () -> Unit = {},
) {
    val ink = BahrTheme.colors.scrimInk
    Box(modifier.background(BahrTheme.colors.surfaceDim)) {
        image()
        if (scrimTop) {
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(0f to ink.copy(alpha = SCRIM_TOP_ALPHA), SCRIM_TOP_END to ink.copy(alpha = 0f)),
                ),
            )
        }
        if (scrimBottom) {
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(SCRIM_BOTTOM_START to ink.copy(alpha = 0f), 1f to ink.copy(alpha = SCRIM_BOTTOM_ALPHA)),
                ),
            )
        }
    }
}

/** Overline label: uppercase + tracked in English, plain in Arabic. */
@Composable
fun Overline(
    text: String,
    modifier: Modifier = Modifier,
) {
    val ar = BahrTheme.locale.isArabic
    Text(
        text.overlineCase(ar),
        style = BahrTheme.type.overline,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Surface card. HighContrast swaps the shadow for a 1dp outline ([bahrShadow] draws it). */
@Composable
fun BahrCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val x = BahrTheme.colors
    val shape = BahrTheme.shapes.card
    Column(
        modifier
            .bahrShadow(BahrElevation.Level1, shape, x)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}
