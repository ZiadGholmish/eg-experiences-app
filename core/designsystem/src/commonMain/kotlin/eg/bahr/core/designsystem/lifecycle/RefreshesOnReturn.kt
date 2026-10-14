package eg.bahr.core.designsystem.lifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Calls [refresh] each time the screen comes back (its tab chosen again, back from a page above it,
 * the app brought forward), but not on its first showing, which loads anyway. My trips (M3-M0 review
 * #11) and Profile (M3-M0b) re-read their lists and account with it.
 */
@Composable
fun RefreshesOnReturn(refresh: () -> Unit) {
    var shown by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (shown) refresh() else shown = true
        onPauseOrDispose {}
    }
}
