package com.egyptexperiences.feature.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.egyptexperiences.core.designsystem.theme.EgOnBrand
import com.egyptexperiences.core.designsystem.theme.EgTeal
import com.egyptexperiences.core.designsystem.theme.EgTheme
import com.egyptexperiences.core.localization.generated.resources.Res
import com.egyptexperiences.core.localization.generated.resources.app_name
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * Held only long enough for the stored language to load, so the first real
 * screen is already in the right script and direction. It is not a brand
 * animation with a fixed duration.
 */
@Composable
fun SplashScreen(
    onReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        delay(MinimumVisibleMillis)
        onReady()
    }

    Column(
        modifier = modifier.fillMaxSize().background(EgTeal),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.app_name),
            style = EgTheme.typography.screenTitle,
            color = EgOnBrand,
        )
    }
}

/** Short enough not to be felt, long enough to avoid a one-frame flash. */
private const val MinimumVisibleMillis = 450L
