package eg.bahr.feature.booking.presentation

import androidx.compose.runtime.Composable

/**
 * Opens the provider's hosted checkout (M3-M1): no card data ever passes through the app (PLAN §9). Android opens a
 * Custom Tab, iOS an `ASWebAuthenticationSession`.
 *
 * [open] returns once the browser is out of the way as far as the platform can tell: on iOS when the session ends
 * (the checkout went to [returnUrl]'s scheme, or the person closed it); on Android at once, since a Custom Tab runs
 * in the browser's process and reports nothing back (the return link brings the app to the front, and the screen
 * reads the booking again as it starts). Either way the outcome is read from the server, never from the browser.
 */
internal fun interface CheckoutBrowser {
    suspend fun open(
        url: String,
        returnUrl: String?,
    )
}

/** The platform's [CheckoutBrowser], tied to the screen (Android needs its Activity, iOS a window to show over). */
@Composable
internal expect fun rememberCheckoutBrowser(): CheckoutBrowser
