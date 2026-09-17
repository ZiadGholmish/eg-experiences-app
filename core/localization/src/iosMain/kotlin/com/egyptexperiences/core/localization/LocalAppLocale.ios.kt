package com.egyptexperiences.core.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

actual object LocalAppLocale {
    private const val LANG_KEY = "AppleLanguages"
    private val deviceDefault = NSLocale.preferredLanguages.first() as String
    private val Local = staticCompositionLocalOf { deviceDefault }

    actual val current: String
        @Composable get() = Local.current

    /**
     * Writing `AppleLanguages` is what makes `NSBundle` — and therefore
     * compose-resources — resolve the chosen language. Passing null clears the
     * override and hands control back to the device setting.
     */
    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val resolved = value ?: deviceDefault
        if (value == null) {
            NSUserDefaults.standardUserDefaults.removeObjectForKey(LANG_KEY)
        } else {
            NSUserDefaults.standardUserDefaults.setObject(arrayListOf(resolved), LANG_KEY)
        }
        return Local.provides(resolved)
    }
}

@Composable
actual fun ApplyPlatformLocale(
    languageTag: String,
    content: @Composable () -> Unit,
) {
    // Already applied globally in `provides` above.
    content()
}
