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
actual object LocalAppLocale {
    /** The device locale, captured before the first override so `provides(null)` can restore it. */
    private var deviceDefault: Locale? = null

    actual val current: String
        @Composable get() = LocalConfiguration.current.locales[0].toLanguageTag()

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val base = LocalConfiguration.current
        val device = deviceDefault ?: Locale.getDefault().also { deviceDefault = it }
        val locale = value?.let(Locale::forLanguageTag) ?: device
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

/** The locale is applied in [LocalAppLocale.provides]; nothing left to do per subtree. */
@Composable
actual fun ApplyPlatformLocale(
    languageTag: String,
    content: @Composable () -> Unit,
) {
    content()
}
