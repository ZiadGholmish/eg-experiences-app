package eg.bahr.core.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import eg.bahr.core.designsystem.theme.BahrElevation
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrShadow
import eg.bahr.core.designsystem.theme.bahrTween
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

/**
 * Filter chip: unselected = surfaceContainer, selected = primary with teal shadow. Count is optional.
 *
 * M4-M6: selecting or leaving a chip animates its fill, label, icon and count pill rather than
 * flipping them, and a new count crossfades in while the chip eases to its new width, so a row of
 * chips does not jump when their counts change. Instant under reduce motion (`bahrTween`).
 */
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
    val bg by animateColorAsState(
        when {
            selected -> c.primary
            disabled -> x.surfaceLowest
            else -> c.surfaceContainer
        },
        bahrTween(BahrMotion.Short),
    )
    val fg by animateColorAsState(
        when {
            selected -> c.onPrimary
            disabled -> x.onSurfaceDisabled
            else -> x.onSurfaceSecondary
        },
        bahrTween(BahrMotion.Short),
    )
    val iconTint by animateColorAsState(
        when {
            selected -> x.primaryBright
            disabled -> x.onSurfaceDisabled
            else -> c.primary
        },
        bahrTween(BahrMotion.Short),
    )
    val countColor by animateColorAsState(if (selected) c.onPrimary else c.onSurfaceVariant, bahrTween(BahrMotion.Short))
    val countPill by animateColorAsState(
        if (selected) c.onPrimary.copy(alpha = CHIP_COUNT_ALPHA) else x.track,
        bahrTween(BahrMotion.Short),
    )
    Row(
        modifier
            .heightIn(min = BahrSpacing.minTouch)
            .then(if (selected) Modifier.bahrShadow(BahrElevation.Chip, shape, x) else Modifier)
            .clip(shape)
            .background(bg)
            .clickable(enabled = !disabled, role = Role.Checkbox, onClick = onClick)
            .animateContentSize(bahrTween(BahrMotion.Short))
            .padding(horizontal = BahrSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(CHIP_ICON.dp), tint = iconTint)
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
        if (count != null) {
            val fade = bahrTween<Float>(BahrMotion.Short)
            AnimatedContent(
                targetState = count,
                transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) using SizeTransform(clip = false) },
                modifier =
                    Modifier
                        .clip(shape)
                        .background(countPill)
                        .padding(horizontal = CHIP_COUNT_PADDING_H.dp, vertical = CHIP_COUNT_PADDING_V.dp),
            ) { shown ->
                Text(shown.toString(), style = MaterialTheme.typography.labelMedium, color = countColor)
            }
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
