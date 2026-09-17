package com.egyptexperiences

import androidx.compose.ui.window.ComposeUIViewController
import com.egyptexperiences.core.datastore.APP_PREFERENCES_FILE
import com.egyptexperiences.di.initKoin
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
 */
@Suppress("FunctionName", "unused")
fun MainViewController(
    baseUrl: String,
    isDebug: Boolean,
): UIViewController {
    initKoin(
        baseUrl = baseUrl,
        isDebug = isDebug,
        preferencesPath = { documentsPath(APP_PREFERENCES_FILE) },
    )
    return ComposeUIViewController { App() }
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
