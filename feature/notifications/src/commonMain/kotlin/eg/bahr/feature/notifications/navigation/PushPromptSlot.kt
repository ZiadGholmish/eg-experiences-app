package eg.bahr.feature.notifications.navigation

import androidx.compose.runtime.Composable
import eg.bahr.feature.notifications.presentation.PushPromptSheet

/**
 * The push pre-prompt (M3-M5, handoff §3) for another feature to place right after a successful waiting-list join
 * (the trip page). It asks only when it should (push in this build, not yet allowed, fewer than two "Not now"s), and
 * calls [onDone] when answered or not needed; the placing screen then removes it. As feature:auth's slots: the host
 * only gives it a place, and `:composeApp` joins the two, so neither feature depends on the other.
 */
@Composable
fun PushPromptSlot(onDone: () -> Unit) {
    PushPromptSheet(onDone = onDone)
}
