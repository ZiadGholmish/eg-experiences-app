package com.egyptexperiences.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.egyptexperiences.core.designsystem.theme.EgInkSubtle
import com.egyptexperiences.core.designsystem.theme.EgTeal
import com.egyptexperiences.core.designsystem.theme.EgTheme

@Composable
fun EgLoadingView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = EgTeal)
    }
}

/**
 * Failure state with one way out.
 *
 * [onRetry] is nullable because not every failure is retryable — a
 * serialization error means the contract moved and retrying just fails again.
 */
@Composable
fun EgErrorView(
    message: String,
    retryLabel: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(EgTheme.spacings.x5l),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = EgTheme.typography.body,
            color = EgInkSubtle,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry, modifier = Modifier.wrapContentWidth()) {
                Text(text = retryLabel, style = EgTheme.typography.button, color = EgTeal)
            }
        }
    }
}

@Composable
fun EgEmptyView(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(EgTheme.spacings.x5l),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = EgTheme.typography.body,
            color = EgInkSubtle,
            textAlign = TextAlign.Center,
        )
    }
}
