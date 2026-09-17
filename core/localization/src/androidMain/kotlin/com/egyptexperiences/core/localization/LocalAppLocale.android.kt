package com.egyptexperiences.core.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf

actual object LocalAppLocale {
    private val Local = staticCompositionLocalOf { "" }

    actual val current: String
        @Composable get() = Local.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> = Local.provides(value ?: "")
}

/**
 * Android applies the locale at the Activity level (`AppCompatDelegate
 * .setApplicationLocales`), which recreates the Activity; compose-resources
 * then reads the right bundle from the new Context. Nothing to do here.
 */
@Composable
actual fun ApplyPlatformLocale(
    languageTag: String,
    content: @Composable () -> Unit,
) {
    content()
}
