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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import eg.bahr.core.designsystem.generated.resources.Res
import eg.bahr.core.designsystem.generated.resources.ic_brand_apple
import eg.bahr.core.designsystem.generated.resources.ic_brand_google
import eg.bahr.core.designsystem.theme.BahrBorder
import eg.bahr.core.designsystem.theme.BahrBrandColors
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
 * branding guidelines). Since M3-M0b their colours are tokens.json's `brand` values
 * (`BahrTheme.colors.brand`, `--bahr-brand-*`), which follow dark mode (Google's dark button, Apple's
 * white one) and no Bahr theme. The labels are the platform's own face, as both guidelines ask (Google:
 * Roboto Medium 14, Android's default; Apple: the system font, iOS's default).
 *
 * Callers: the sign-in sheet, Profile signed out and the Alerts tab signed out (M3-M0b). Equal width and
 * height for both, as both guidelines ask when shown together. The label is the caller's (localised),
 * so the component holds no copy.
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
    val look = props.provider.look(BahrTheme.colors.brand)
    val shape = BahrTheme.shapes.full
    val busy = stringResource(Strings.string.a11y_busy)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = BahrSize.touchTarget)
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
        Text(text = props.text, style = look.label, color = look.content, maxLines = 1)
    }
}

private class ProviderLook(
    val background: Color,
    val content: Color,
    val label: TextStyle,
    val border: Color? = null,
)

/** The providers' own colours and label faces (see [BahrSignInButton]). */
private fun BahrSignInButton.Provider.look(brand: BahrBrandColors): ProviderLook =
    when (this) {
        BahrSignInButton.Provider.Apple ->
            ProviderLook(background = brand.appleSurface, content = brand.appleText, label = APPLE_LABEL)
        BahrSignInButton.Provider.Google ->
            ProviderLook(background = brand.googleSurface, content = brand.googleText, label = GOOGLE_LABEL, border = brand.googleOutline)
    }

/** tokens.json `brand.google.font`: Roboto Medium 14/20 (Android's default face). */
private val GOOGLE_LABEL = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)

/** The prototype's Apple label: the system face, 600 15px (`--bahr-brand-apple-font`). */
private val APPLE_LABEL = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)

/**
 * The provider's own mark, small, in front of the provider line on Profile (M3-M0b, handoff §2): the
 * four-colour "G" as it is, the Apple mark in the text colour. Silent for screen readers (the line says
 * the provider's name).
 */
@Composable
fun BahrProviderMark(
    provider: BahrSignInButton.Provider,
    modifier: Modifier = Modifier,
) {
    when (provider) {
        BahrSignInButton.Provider.Google ->
            Image(painterResource(Res.drawable.ic_brand_google), contentDescription = null, modifier = modifier.size(BahrSize.iconSmall))
        BahrSignInButton.Provider.Apple ->
            Icon(
                painterResource(Res.drawable.ic_brand_apple),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = modifier.size(BahrSize.iconSmall),
            )
    }
}
