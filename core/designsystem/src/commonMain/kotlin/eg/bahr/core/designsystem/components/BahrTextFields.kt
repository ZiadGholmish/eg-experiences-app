package eg.bahr.core.designsystem.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import eg.bahr.core.designsystem.theme.BahrTheme

/**
 * HANDOFF screen 5's inputs: an `outlineVariant` border on a light fill. One place for the colours,
 * moved here in M3-M0 from private copies in feature:booking and feature:trips (M2-M3 review #3); the
 * sign-in profile form (feature:auth) is the third caller. Used with Material's `OutlinedTextField`
 * and `BahrTheme.shapes.medium`.
 */
object BahrTextFields {
    /** What the field sits on, which decides its fill. */
    enum class Ground {
        /**
         * The off-white page (M4-M12): the fill is `surface` white, as cards are, so a field reads as a
         * field rather than a hole in the page.
         */
        Page,

        /** A tinted panel (the coral sold-out notice): the handoff's own `surfaceLowest` fill. */
        Tint,
    }

    @Composable
    fun colors(ground: Ground = Ground.Page): TextFieldColors {
        val fill = if (ground == Ground.Page) MaterialTheme.colorScheme.surface else BahrTheme.colors.surfaceLowest
        return OutlinedTextFieldDefaults.colors(
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
            errorContainerColor = fill,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}
