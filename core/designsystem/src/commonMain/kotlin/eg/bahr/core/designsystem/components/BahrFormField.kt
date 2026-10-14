package eg.bahr.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrAlpha
import eg.bahr.core.designsystem.theme.BahrBorder
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme

/**
 * A labelled form field as the account handoff draws it (M3-M0b, §2 Edit personal info and the
 * first-sign-in sheet): the label above, a 56dp box on `surface` with a 1dp `outline` (2dp `primary` while
 * focused, 2dp `error` with an error), an optional leading icon and trailing counter, and one line under it.
 *
 * The line under it is either a quiet note ([Props.support], `onSurfaceVariant`, with an info icon when
 * it is help) or the error ([Props.error]): `onErrorContainer` text (not `error`, 3.9:1 at this size)
 * announced at once (`liveRegion = Assertive`, the prototype's `role=alert`).
 *
 * [Props.readOnly] is the locked email: `surfaceContainer` fill, a lock, muted text, no border.
 * [Props.enabled] false is the saving state: 60% opacity, no input. [Props.leftToRight] keeps a phone
 * number left to right inside Arabic, as the prototype's `dir=ltr` box does, counter included.
 *
 * Callers: feature:auth's first-sign-in sheet and feature:settings' Edit personal info.
 */
interface BahrFormField {
    enum class Keyboard { Name, Phone, Text }

    @Immutable
    data class Props(
        val label: String,
        val value: String,
        val onValueChange: (String) -> Unit = {},
        val enabled: Boolean = true,
        val readOnly: Boolean = false,
        val leadingIcon: BahrIcons? = null,
        val placeholder: String? = null,
        /** E.g. "11/11", at the box's end. */
        val counter: String? = null,
        /** A note under the box; with [supportIsHelp] it has the info icon in front. */
        val support: String? = null,
        val supportIsHelp: Boolean = false,
        /** Replaces [support] while set. */
        val error: String? = null,
        val leftToRight: Boolean = false,
        val keyboard: Keyboard = Keyboard.Text,
        val imeAction: ImeAction = ImeAction.Next,
        val onImeAction: () -> Unit = {},
        val testTag: String? = null,
    )
}

@Composable
fun BahrFormField(
    modifier: Modifier = Modifier,
    props: BahrFormField.Props,
) {
    val c = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = BahrTheme.shapes.medium
    val (borderWidth, borderColor) =
        when {
            props.readOnly -> BahrBorder.hairline to c.surfaceContainer
            props.error != null -> BahrBorder.selected to c.error
            focused -> BahrBorder.selected to c.primary
            else -> BahrBorder.hairline to c.outline
        }
    val textColor = if (props.readOnly) c.onSurfaceVariant else c.onSurface
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FIELD_GAP.dp)) {
        Text(
            text = props.label,
            style = MaterialTheme.typography.labelLarge,
            color = BahrTheme.colors.onSurfaceSecondary,
        )
        val direction = if (props.leftToRight) LayoutDirection.Ltr else LocalLayoutDirection.current
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            BasicTextField(
                value = props.value,
                onValueChange = props.onValueChange,
                enabled = props.enabled && !props.readOnly,
                readOnly = props.readOnly,
                singleLine = true,
                interactionSource = interaction,
                cursorBrush = SolidColor(c.primary),
                textStyle = MaterialTheme.typography.titleMedium.copy(color = textColor, fontWeight = FontWeight.SemiBold),
                keyboardOptions = props.keyboard.options(props.imeAction),
                keyboardActions = KeyboardActions(onAny = { props.onImeAction() }),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(if (props.enabled) Modifier else Modifier.alpha(BahrAlpha.disabledField))
                        .semantics {
                            contentDescription = props.label
                            props.error?.let { error(it) }
                        }.then(props.testTag?.let { Modifier.testTag(it) } ?: Modifier),
                decorationBox = { inner ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
                        modifier =
                            Modifier
                                .heightIn(min = FIELD_HEIGHT.dp)
                                .clip(shape)
                                .background(if (props.readOnly) c.surfaceContainer else c.surface)
                                .border(borderWidth, borderColor, shape)
                                .padding(horizontal = BahrSpacing.lg),
                    ) {
                        props.leadingIcon?.let {
                            Icon(
                                it.outlined(),
                                contentDescription = null,
                                tint = c.onSurfaceVariant,
                                modifier = Modifier.size(BahrSize.iconLarge),
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            if (props.value.isEmpty() && props.placeholder != null) {
                                Text(
                                    text = props.placeholder,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = BahrTheme.colors.onSurfaceDisabled,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            inner()
                        }
                        props.counter?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                color = if (props.error != null) c.onErrorContainer else c.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        }
        val error = props.error
        when {
            error != null ->
                FieldNote(
                    error,
                    BahrIcons.Error,
                    c.onErrorContainer,
                    Modifier.semantics {
                        liveRegion =
                            LiveRegionMode.Assertive
                    },
                )
            props.support != null -> FieldNote(props.support, if (props.supportIsHelp) BahrIcons.Info else null, c.onSurfaceVariant)
        }
    }
}

@Composable
private fun FieldNote(
    text: String,
    icon: BahrIcons?,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
        icon?.let { Icon(it.outlined(), contentDescription = null, tint = color, modifier = Modifier.size(BahrSize.iconSmall)) }
        Text(text = text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = color)
    }
}

private fun BahrFormField.Keyboard.options(ime: ImeAction): KeyboardOptions =
    when (this) {
        BahrFormField.Keyboard.Name -> KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ime)
        BahrFormField.Keyboard.Phone -> KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ime)
        BahrFormField.Keyboard.Text -> KeyboardOptions(imeAction = ime)
    }

/** The prototype's 56px field box. */
private const val FIELD_HEIGHT = 56

/** The prototype's 6px between the label, the box and the line under it. */
private const val FIELD_GAP = 6
