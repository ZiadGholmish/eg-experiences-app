package eg.bahr.feature.notifications.navigation

import androidx.compose.runtime.Composable
import eg.bahr.feature.notifications.presentation.PushPromptSheet

/** Why the push pre-prompt asks: its copy, and whether it needs push in this build. */
enum class PushPromptReason {
    /** Right after a waiting-list join (M3-M5): "a place opened" is a server push, so only in a build with push. */
    WaitingList,

    /**
     * Right after a hold is placed (M3-M7): the "about to expire" reminder is scheduled on the phone, so it is asked
     * in every build, Firebase or not.
     */
    HoldReminder,
}

/**
 * The push pre-prompt (M3-M5, handoff §3) for another feature to place: the trip page right after a successful
 * waiting-list join, the held seats right after a hold is placed ([reason]). It asks only when it should
 * (notifications not yet allowed, fewer than two "Not now"s for this [reason], and push in this build for the
 * waiting list), and calls [onDone] when answered or not needed; the placing screen then removes it. As feature:auth's
 * slots: the host only gives it a place, and `:composeApp` joins the two, so neither feature depends on the other.
 */
@Composable
fun PushPromptSlot(
    onDone: () -> Unit,
    reason: PushPromptReason = PushPromptReason.WaitingList,
) {
    PushPromptSheet(onDone = onDone, reason = reason)
}
