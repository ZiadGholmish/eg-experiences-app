package eg.bahr.core.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue

/**
 * Platform hook that makes compose-resources resolve the selected locale's
 * strings, rather than the device's.
 *
 * compose-resources picks strings from the platform locale, not from a
 * composition local, so each platform points that locale at the app language:
 * iOS writes `AppleLanguages` (read by `NSLocale.preferredLanguages`), Android
 * sets the JVM default locale (read by `LocaleList.getDefault()`). The stored
 * app language is the only input; the device language never wins.
 *
 * On Android the default locale also forces Latin digits (`ar-u-nu-latn`), so platform number
 * formatting cannot emit Arabic-Indic digits; see `appLocale` in the Android actual.
 */
internal expect object LocalAppLocale {
    val current: String
        @Composable get

    @Composable
    infix fun provides(value: String): ProvidedValue<*>
}

@Composable
internal expect fun ApplyPlatformLocale(
    languageTag: String,
    content: @Composable () -> Unit,
)
