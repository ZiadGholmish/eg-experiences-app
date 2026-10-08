package eg.bahr.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

enum class BahrLocale(
    val tag: String,
    val direction: LayoutDirection,
) {
    Arabic("ar-EG", LayoutDirection.Rtl),
    English("en", LayoutDirection.Ltr),
    ;

    val isArabic get() = this == Arabic
}

val LocalBahrLocale = staticCompositionLocalOf { BahrLocale.Arabic }

/**
 * Wrap every screen in this. Changing [theme] or [locale] re-themes / re-flows the whole app —
 * screens must only read `MaterialTheme.*` and `BahrTheme.*`, never literals.
 *
 * [locale] defaults to Arabic because Arabic is the product's default; the app root must still
 * pass the stored language, or English users get RTL and the Arabic face.
 */
@Composable
fun BahrTheme(
    locale: BahrLocale = BahrLocale.Arabic,
    theme: BahrThemeName = BahrThemeName.LakeBurullus,
    content: @Composable () -> Unit,
) {
    val colors = bahrColorTheme(theme)
    val family = bahrFontFamily(locale)
    val shapes = BahrShapes()
    CompositionLocalProvider(
        LocalBahrLocale provides locale,
        LocalLayoutDirection provides locale.direction,
        LocalBahrColors provides colors.extended,
        LocalBahrType provides bahrExtendedType(family, locale.isArabic),
        LocalBahrShapes provides shapes,
    ) {
        MaterialTheme(
            colorScheme = colors.scheme,
            typography = bahrTypography(family, locale.isArabic),
            shapes = shapes.toMaterial(),
            content = content,
        )
    }
}

/** `BahrTheme.colors.success`, `BahrTheme.shapes.card`, `BahrTheme.type.overline` … */
object BahrTheme {
    val colors: BahrExtendedColors
        @Composable @ReadOnlyComposable
        get() = LocalBahrColors.current
    val type: BahrExtendedType
        @Composable @ReadOnlyComposable
        get() = LocalBahrType.current
    val shapes: BahrShapes
        @Composable @ReadOnlyComposable
        get() = LocalBahrShapes.current
    val locale: BahrLocale
        @Composable @ReadOnlyComposable
        get() = LocalBahrLocale.current
    val spacing = BahrSpacing
    val motion = BahrMotion
}
