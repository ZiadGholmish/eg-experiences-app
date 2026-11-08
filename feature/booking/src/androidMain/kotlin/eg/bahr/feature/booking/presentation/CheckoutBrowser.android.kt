package eg.bahr.feature.booking.presentation

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger

/**
 * A Custom Tab (androidx.browser; Android only, no multiplatform artifact). Opened from the Activity's context, so
 * the tab sits in the app's task and the return link (`MainActivity` is `singleTask`) clears it as it brings the app
 * back.
 */
@Composable
internal actual fun rememberCheckoutBrowser(): CheckoutBrowser {
    val context = LocalContext.current
    return remember(context) { CustomTabsCheckoutBrowser(context) }
}

private class CustomTabsCheckoutBrowser(
    private val context: Context,
) : CheckoutBrowser {
    override suspend fun open(
        url: String,
        returnUrl: String?,
    ) {
        try {
            CustomTabsIntent
                .Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(context, Uri.parse(url))
        } catch (noBrowser: ActivityNotFoundException) {
            // No browser at all: nothing opens, and the screen keeps reading the booking (it stays PAYMENT_PENDING
            // until the server's deadline). Logged without the URL, which carries the provider's keys.
            Logger.withTag(LOG_TAG).w(noBrowser) { "No browser for the hosted checkout" }
        }
    }

    private companion object {
        const val LOG_TAG = "CheckoutBrowser"
    }
}
