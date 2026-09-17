package com.egyptexperiences.core.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue

/**
 * Platform hook that makes compose-resources resolve the selected locale's
 * strings, rather than the device's.
 *
 * iOS applies it globally through `NSUserDefaults`; Android applies it at the
 * Activity level via `AppCompatDelegate`, so its side is a pass-through.
 */
expect object LocalAppLocale {
    val current: String
        @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}

@Composable
expect fun ApplyPlatformLocale(
    languageTag: String,
    content: @Composable () -> Unit,
)
