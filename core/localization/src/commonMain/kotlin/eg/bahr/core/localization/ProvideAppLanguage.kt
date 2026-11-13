package eg.bahr.core.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import eg.bahr.core.common.locale.AppLanguage

/**
 * Wraps the app in the selected language and its writing direction.
 *
 * Arabic is the default locale, so RTL is the default layout direction, not a
 * special case bolted on later. Every feature must be laid out with
 * `start`/`end` rather than `left`/`right` for this to hold — `Modifier.padding`
 * with `start` mirrors, `Alignment.Start` mirrors, a hardcoded `.left` does not.
 *
 * The [key] forces a fresh composition when the language changes so that
 * compose-resources re-reads the string bundle.
 */
@Composable
fun ProvideAppLanguage(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    val direction = if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalAppLocale provides language.tag,
        LocalLayoutDirection provides direction,
    ) {
        key(language) {
            ApplyPlatformLocale(language.tag) { content() }
        }
    }
}

/**
 * The language the nearest [ProvideAppLanguage] draws its content in: the one that content is keyed on, so it is
 * constant for the life of everything below the key (MV-M8: the tab state tags each NavHost's reports with it).
 */
val currentAppLanguage: AppLanguage
    @Composable get() = AppLanguage.fromTag(LocalAppLocale.current)
