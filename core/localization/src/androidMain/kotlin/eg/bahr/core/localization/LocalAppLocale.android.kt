package eg.bahr.core.localization

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * compose-resources resolves strings from `androidx.compose.ui.text.intl.Locale.current`, which on
 * Android is `LocaleList.getDefault()`: the JVM default locale, i.e. the device language unless
 * the app changes it. So the stored app language is applied by setting the JVM default (and a
 * matching [Configuration] for anything that reads Android resources) before content composes.
 *
 * Without this, a first launch on an English device rendered the Arabic theme (RTL, Plex) with
 * English strings: layout direction followed the app language, strings followed the device.
 */
internal actual object LocalAppLocale {
    actual val current: String
        @Composable get() = LocalConfiguration.current.locales[0].toLanguageTag()

    @Composable
    actual infix fun provides(value: String): ProvidedValue<*> {
        val base = LocalConfiguration.current
        val locale = appLocale(value)
        // A side effect during composition, on purpose: it must happen before the children read
        // Locale.current, and ProvideAppLanguage's key(language) re-runs it on every switch.
        Locale.setDefault(locale)
        val configuration =
            remember(base, locale) {
                Configuration(base).apply { setLocale(locale) }
            }
        return LocalConfiguration.provides(configuration)
    }
}

/**
 * The app language with Latin digits forced (`ar` → `ar-u-nu-latn`).
 *
 * The rule is Western digits in every language (BahrFormat formats them by hand). Plain `ar` as
 * the process default would make any `String.format`, `NumberFormat` or `DateFormat` — ours by
 * mistake, or a library's — emit Arabic-Indic digits (١٢). The `nu` extension changes only the
 * numbering system: resource lookup still matches `values-ar`, and the language is still `ar`.
 */
internal fun appLocale(languageTag: String): Locale =
    Locale
        .Builder()
        .setLanguageTag(languageTag)
        .setUnicodeLocaleKeyword("nu", "latn")
        .build()

/** The locale is applied in [LocalAppLocale.provides]; nothing left to do per subtree. */
@Composable
internal actual fun ApplyPlatformLocale(
    languageTag: String,
    content: @Composable () -> Unit,
) {
    content()
}
