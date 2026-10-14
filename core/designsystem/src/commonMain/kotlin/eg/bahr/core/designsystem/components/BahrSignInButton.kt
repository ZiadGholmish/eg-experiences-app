package eg.bahr.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import eg.bahr.core.designsystem.generated.resources.Res
import eg.bahr.core.designsystem.generated.resources.ic_brand_apple
import eg.bahr.core.designsystem.generated.resources.ic_brand_google
import eg.bahr.core.designsystem.theme.BahrBorder
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.a11y_busy
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import eg.bahr.core.localization.generated.resources.Res as Strings

/**
 * "Continue with Apple" / "Continue with Google" (M3-M0), drawn to each provider's own rules rather
 * than the app's: Apple's black button with the white Apple mark (Human Interface Guidelines, Sign in
 * with Apple), Google's white button with a grey border and the four-colour "G" (Google's sign-in
 * branding guidelines). Their colours are the providers', not theme tokens, so they live here and not
 * in a feature (mobile rule 3), and they stay the same in every Bahr theme.
 *
 * In the design system with one caller (the sign-in sheet) for that reason only: a feature may hold no
 * colour literal. Equal width and height for both, as both guidelines ask when shown together. The
 * label is the caller's (localised), so the component holds no copy.
 */
interface BahrSignInButton {
    enum class Provider { Apple, Google }

    @Immutable
    data class Props(
        val provider: Provider,
        val text: String,
        val loading: Boolean = false,
        val enabled: Boolean = true,
        val onClick: () -> Unit = {},
    )
}

@Composable
fun BahrSignInButton(
    modifier: Modifier = Modifier,
    props: BahrSignInButton.Props,
) {
    val look = props.provider.look()
    val shape = BahrTheme.shapes.full
    val busy = stringResource(Strings.string.a11y_busy)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = BUTTON_MIN_HEIGHT.dp)
                .clip(shape)
                .background(look.background)
                .then(look.border?.let { Modifier.border(BahrBorder.hairline, it, shape) } ?: Modifier)
                .clickable(enabled = props.enabled && !props.loading, role = Role.Button, onClick = props.onClick)
                .then(if (props.loading) Modifier.semantics { stateDescription = busy } else Modifier)
                .padding(horizontal = BahrSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm, Alignment.CenterHorizontally),
    ) {
        Box(Modifier.size(BahrSize.iconMedium), contentAlignment = Alignment.Center) {
            when {
                props.loading ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(BahrSize.iconSmall),
                        color = look.content,
                        strokeWidth = BahrBorder.selected,
                    )
                props.provider == BahrSignInButton.Provider.Google ->
                    // The "G" keeps its own colours: never tinted.
                    Image(
                        painterResource(Res.drawable.ic_brand_google),
                        contentDescription = null,
                        modifier = Modifier.size(BahrSize.iconSmall),
                    )
                else ->
                    Icon(
                        painterResource(Res.drawable.ic_brand_apple),
                        contentDescription = null,
                        tint = look.content,
                        modifier = Modifier.size(BahrSize.iconMedium),
                    )
            }
        }
        Text(text = props.text, style = MaterialTheme.typography.titleMedium, color = look.content, maxLines = 1)
    }
}

private class ProviderLook(
    val background: Color,
    val content: Color,
    val border: Color? = null,
)

/** The providers' own colours (see [BahrSignInButton]). */
private fun BahrSignInButton.Provider.look(): ProviderLook =
    when (this) {
        BahrSignInButton.Provider.Apple -> ProviderLook(background = Color(APPLE_BLACK), content = Color(APPLE_WHITE))
        BahrSignInButton.Provider.Google ->
            ProviderLook(background = Color(GOOGLE_FILL), content = Color(GOOGLE_TEXT), border = Color(GOOGLE_STROKE))
    }

/** Apple's minimum is 44 pt, Google's 40 dp; 54 is the app's primary button, so the sheet's buttons line up. */
private const val BUTTON_MIN_HEIGHT = 54
private const val APPLE_BLACK = 0xFF000000
private const val APPLE_WHITE = 0xFFFFFFFF

/** Google's light theme: fill #FFFFFF, stroke #747775, text #1F1F1F. */
private const val GOOGLE_FILL = 0xFFFFFFFF
private const val GOOGLE_STROKE = 0xFF747775
private const val GOOGLE_TEXT = 0xFF1F1F1F
