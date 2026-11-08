package eg.bahr.feature.booking.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * An `ASWebAuthenticationSession`: the system's sheet for a web flow that comes back to the app, which ends by itself
 * when the checkout goes to the return link's scheme. Shares the browser's cookies (not ephemeral), so a card saved
 * with the provider is offered again.
 */
@Composable
internal actual fun rememberCheckoutBrowser(): CheckoutBrowser = remember { WebAuthenticationCheckoutBrowser() }

private class WebAuthenticationCheckoutBrowser : CheckoutBrowser {
    // The session holds its provider weakly: this keeps it alive while a checkout is open.
    private val anchor = KeyWindowAnchor()

    override suspend fun open(
        url: String,
        returnUrl: String?,
    ) {
        val page = NSURL.URLWithString(url) ?: return
        // A custom scheme (`eg.bahr.app://checkout/…`): the session watches for it and closes on its own.
        val scheme = returnUrl?.substringBefore(SCHEME_END, missingDelimiterValue = "")?.takeIf { it.isNotEmpty() }
        suspendCancellableCoroutine { continuation ->
            val session =
                ASWebAuthenticationSession(uRL = page, callbackURLScheme = scheme) { _, _ ->
                    // Returned, cancelled or failed: the same for the screen, which reads the booking either way.
                    if (continuation.isActive) continuation.resume(Unit)
                }
            session.presentationContextProvider = anchor
            session.prefersEphemeralWebBrowserSession = false
            continuation.invokeOnCancellation { session.cancel() }
            if (!session.start() && continuation.isActive) continuation.resume(Unit)
        }
    }

    private companion object {
        const val SCHEME_END = "://"
    }
}

/** The app's key window, where the session's sheet is shown. */
private class KeyWindowAnchor :
    NSObject(),
    ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor {
        val windows =
            UIApplication.sharedApplication.connectedScenes
                .mapNotNull { it as? UIWindowScene }
                .flatMap { scene -> scene.windows.mapNotNull { it as? UIWindow } }
        return windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull() ?: UIWindow()
    }
}
