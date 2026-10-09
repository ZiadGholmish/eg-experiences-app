package eg.bahr.core.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import eg.bahr.core.common.locale.AppLanguage
import platform.Foundation.NSUserDefaults

internal actual object LocalAppLocale {
    private const val LANG_KEY = "AppleLanguages"
    private val Local = staticCompositionLocalOf { AppLanguage.default.tag }

    actual val current: String
        @Composable get() = Local.current

    /**
     * Writing `AppleLanguages` is what makes `NSBundle` — and therefore
     * compose-resources — resolve the chosen language.
     */
    @Composable
    actual infix fun provides(value: String): ProvidedValue<*> {
        NSUserDefaults.standardUserDefaults.setObject(arrayListOf(value), LANG_KEY)
        return Local.provides(value)
    }
}

@Composable
internal actual fun ApplyPlatformLocale(
    languageTag: String,
    content: @Composable () -> Unit,
) {
    // Already applied globally in `provides` above.
    content()
}
