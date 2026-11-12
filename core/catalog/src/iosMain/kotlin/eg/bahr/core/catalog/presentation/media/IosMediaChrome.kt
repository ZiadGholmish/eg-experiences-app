package eg.bahr.core.catalog.presentation.media

/**
 * The full-screen viewer's way to the iOS status bar (MV-M3). On iOS the bar belongs to the SwiftUI app around the
 * Compose roots, which Kotlin cannot reach from here, so `:composeApp`'s iOS entry point sets [onFullScreen] to the
 * Swift bridge (`AppTabBar.setFullScreen`), and the viewer calls [fullScreen] while it is open. Unset (tests), nothing
 * happens.
 */
object IosMediaChrome {
    var onFullScreen: ((Boolean) -> Unit)? = null

    internal fun fullScreen(on: Boolean) {
        onFullScreen?.invoke(on)
    }
}
