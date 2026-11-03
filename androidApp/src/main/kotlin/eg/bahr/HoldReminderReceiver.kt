package eg.bahr

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The hold reminder's alarm (M3-M7), a shell like [PushService]: the alarm `:composeApp` set wakes this, and it hands
 * the intent straight back to `:composeApp`, which posts the notification.
 */
class HoldReminderReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        context.onHoldReminderAlarm(intent)
    }
}
