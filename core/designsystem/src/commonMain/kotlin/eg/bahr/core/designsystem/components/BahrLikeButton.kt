package eg.bahr.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrElevation
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrShadow
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.like_action
import eg.bahr.core.localization.generated.resources.like_count
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The heart (MP-M1, HANDOFF "Trip cards" / trip page item 1: a translucent round favourite button over the photo): a
 * filled heart when [Props.liked], an outlined one when not, and the trip's public like count beside it once there is
 * one, so the circle grows into a pill. Drawn over a photo, so it never takes room in a layout.
 *
 * A toggle for screen readers ("Like, checked, 12 likes"), with the 48dp touch target whatever its drawn size. The count
 * is digits, left to right in Arabic too.
 *
 * Teal (`primary`), not the prototype's coral: coral (`tertiary`) is the action colour and nothing else
 * (docs/design-language.md); see the MP-M1 report.
 *
 * Two callers: core:catalog's trip card ([Size.Card]) and the trip page's hero ([Size.Hero], at the back button's size
 * and float shadow).
 */
interface BahrLikeButton {
    enum class Size { Card, Hero }

    @Immutable
    data class Props(
        val liked: Boolean,
        /** The trip's like count; 0 shows the heart alone. */
        val count: Int,
        val size: Size = Size.Card,
        /** Off on a card that cannot be used (a stale list's): drawn, not tappable. */
        val enabled: Boolean = true,
        val onToggle: () -> Unit = {},
    )
}

@Composable
fun BahrLikeButton(
    modifier: Modifier = Modifier,
    props: BahrLikeButton.Props,
) {
    val height = if (props.size == BahrLikeButton.Size.Hero) BahrSpacing.minTouch else BahrSize.likeButton
    val shape = BahrTheme.shapes.full
    val shadow =
        if (props.size == BahrLikeButton.Size.Hero) Modifier.bahrShadow(BahrElevation.Float, shape, BahrTheme.colors) else Modifier
    val label = stringResource(Res.string.like_action)
    val spoken = "$label, ${pluralStringResource(Res.plurals.like_count, props.count, props.count)}"
    val showsCount = props.count > 0
    Row(
        modifier =
            modifier
                // The card's small heart gets the 48dp target around it; the hero's is laid out at 44dp, as its back
                // button is, so the two line up.
                .then(if (props.size == BahrLikeButton.Size.Card) Modifier.minimumInteractiveComponentSize() else Modifier)
                .height(height)
                .widthIn(min = height)
                .then(shadow)
                .clip(shape)
                .background(BahrTheme.colors.surfaceTranslucent)
                .toggleable(
                    value = props.liked,
                    enabled = props.enabled,
                    role = Role.Checkbox,
                    onValueChange = { props.onToggle() },
                ).semantics { contentDescription = spoken }
                .testTag(LIKE_BUTTON_TAG)
                // With a count, room either side of the heart and the digits; without, the heart in the circle's middle.
                .then(if (showsCount) Modifier.padding(horizontal = BahrSpacing.sm) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs, Alignment.CenterHorizontally),
    ) {
        Icon(
            imageVector = if (props.liked) BahrIcons.Favorite.filled() else BahrIcons.Favorite.outlined(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(BahrSize.iconMedium),
        )
        if (showsCount) {
            Text(
                text = props.count.toString(),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum", textDirection = TextDirection.Ltr),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                // Said in the button's own label, with its plural.
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/** For tests: a heart. */
const val LIKE_BUTTON_TAG = "bahr_like_button"
