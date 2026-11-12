package eg.bahr.core.designsystem.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.then
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrTheme

/**
 * A pill search field: the search icon, the placeholder, × once there is text. Promoted from search's top bar
 * (M4-M3) when the hosts directory (MV-M1) became its second caller; the bar around it (Back, insets, padding)
 * stays each screen's.
 *
 * The text lives in the caller's [Props.state] (typing stays in step with the keyboard, Arabic composition
 * included). The field holds at most [Props.maxLength] characters (the endpoint's limit) and no control
 * characters, which the server refuses; text is drawn in its own direction, so Arabic typed into the English app
 * reads right to left. The keyboard's search key is [Props.onSearch]; × is [Props.onClear].
 */
interface BahrSearchField {
    @Immutable
    data class Props(
        val state: TextFieldState,
        val placeholder: String,
        val clearLabel: String,
        val maxLength: Int,
        /**
         * What a change past [maxLength] does: [Overflow.Refuse] keeps the text as it was (search, M4-M3), [Overflow.Cut]
         * keeps the first [maxLength] characters, so a long paste still lands (the hosts directory, MV-M1 review).
         */
        val overflow: Overflow = Overflow.Refuse,
        val onSearch: () -> Unit = {},
        val onClear: () -> Unit = {},
    )

    enum class Overflow { Refuse, Cut }
}

@Composable
fun BahrSearchField(
    modifier: Modifier = Modifier,
    props: BahrSearchField.Props,
) {
    // Only whether there is text, so a keystroke recomposes the field's icons only when that flips (Compose audit L13).
    val hasText by remember(props.state) { derivedStateOf { props.state.text.isNotEmpty() } }
    val transformation =
        remember(props.maxLength, props.overflow) {
            val cap =
                when (props.overflow) {
                    BahrSearchField.Overflow.Refuse -> InputTransformation.maxLength(props.maxLength)
                    BahrSearchField.Overflow.Cut -> cutTo(props.maxLength)
                }
            StripControlCharacters.then(cap)
        }
    OutlinedTextField(
        state = props.state,
        modifier = modifier,
        placeholder = { Text(props.placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            Icon(
                BahrIcons.Search.outlined(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(BahrSize.iconLarge),
            )
        },
        trailingIcon =
            if (hasText) {
                {
                    IconButton(onClick = props.onClear) {
                        Icon(
                            BahrIcons.Close.outlined(),
                            contentDescription = props.clearLabel,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(BahrSize.iconLarge),
                        )
                    }
                }
            } else {
                null
            },
        inputTransformation = transformation,
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        onKeyboardAction = { props.onSearch() },
        shape = BahrTheme.shapes.full,
        // White on the off-white page (M4-M12), where the handoff's `surfaceLowest` would vanish.
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
    )
}

/** A control character the field drops: any but tab and line feed, which the server folds to spaces. */
private fun Char.isStrippedControl(): Boolean = isISOControl() && this != '\t' && this != '\n'

/**
 * Typed or pasted control characters never reach the field; stripped before the length cap, so a paste is
 * measured without them.
 *
 * Each one is deleted where it is, last first so the earlier indices still hold, rather than the whole text
 * replaced: the buffer moves the cursor with each deletion, so a paste in the middle of the text leaves the cursor
 * after the pasted part, not at the end (M4-M3 review S2).
 */
private val StripControlCharacters =
    InputTransformation {
        for (at in length - 1 downTo 0) {
            if (charAt(at).isStrippedControl()) delete(at, at + 1)
        }
    }

/**
 * Keeps the first [max] characters of the text. Never splits a character: when the cut would fall between the two
 * halves of a surrogate pair (an emoji), the cut moves one back.
 */
private fun cutTo(max: Int) =
    InputTransformation {
        if (length > max) {
            val end = if (max > 0 && charAt(max - 1).isHighSurrogate()) max - 1 else max
            delete(end, length)
        }
    }
