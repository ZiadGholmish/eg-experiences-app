package eg.bahr.core.designsystem.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier

/**
 * A lazy list's last item, as tall as the part of the navigation bar nothing above has taken yet
 * (M3-M11, Compose audit M3): so the last item scrolls clear of the gesture bar on a pushed screen,
 * and adds nothing under Android's bottom tab bar, which already pads for that inset and consumes it.
 *
 * Why not `contentPadding`: `WindowInsets.navigationBars.asPaddingValues()` reads the raw inset and
 * ignores consumption, so a tab root got a second nav-bar-high gap under its last item, and a list
 * above the keyboard (`imePadding`) got one too. `windowInsetsBottomHeight` honours both.
 *
 * The list's `verticalArrangement` gap still comes before this item, so a caller that wants a given
 * space under its last item subtracts that gap from its bottom `contentPadding`.
 */
fun LazyListScope.navigationBarsSpacer() {
    item(key = NAVIGATION_BARS_SPACER_KEY, contentType = NAVIGATION_BARS_SPACER_KEY) {
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/** Unique in any list that uses it: no screen keys an item like this. Also its content type. */
private const val NAVIGATION_BARS_SPACER_KEY = "nav-bars-spacer"
