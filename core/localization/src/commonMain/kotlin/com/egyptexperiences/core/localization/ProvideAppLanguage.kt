package com.egyptexperiences.core.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.egyptexperiences.core.common.locale.AppLanguage

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
