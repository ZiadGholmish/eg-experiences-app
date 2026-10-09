package eg.bahr

import androidx.compose.ui.window.ComposeUIViewController
import eg.bahr.core.datastore.APP_PREFERENCES_FILE
import eg.bahr.deeplink.AppDeepLinkInbox
import eg.bahr.di.AppLinkConfig
import eg.bahr.di.initKoin
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController

/**
 * Swift entry point: `ComposeApp.MainViewControllerKt.MainViewController()`.
 *
 * Koin is started here rather than in Swift so the iOS app target needs no
 * knowledge of the shared module's wiring.
 *
 * [appLinkHost] is the Universal Links host (`APP_LINK_HOST` in the xcconfig, the same value as the
 * Associated Domains entitlement). Universal Links are https only, so http is never accepted here.
 */
@Suppress("FunctionName", "unused")
fun MainViewController(
    baseUrl: String,
    isDebug: Boolean,
    appLinkHost: String,
): UIViewController {
    initKoin(
        baseUrl = baseUrl,
        isDebug = isDebug,
        appLinks = AppLinkConfig(host = appLinkHost, allowsHttp = false),
        preferencesPath = { documentsPath(APP_PREFERENCES_FILE) },
    )
    return ComposeUIViewController { App() }
}

/**
 * Swift entry point for a Universal Link: `MainViewControllerKt.onDeepLink(url:)`, from SwiftUI's
 * `onOpenURL` / `onContinueUserActivity`. Safe to call before [MainViewController]: the link waits in
 * the inbox until the NavHost opens it. A link delivered through both callbacks still ends on one trip
 * page: the NavHost opens a trip single-top.
 */
@Suppress("unused")
fun onDeepLink(url: String) {
    AppDeepLinkInbox.offer(url)
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun documentsPath(fileName: String): String {
    val documentDirectory: NSURL? =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
    return requireNotNull(documentDirectory).path + "/" + fileName
}
