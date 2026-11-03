package eg.bahr

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import co.touchlab.kermit.Logger
import eg.bahr.feature.booking.di.HoldReminder
import eg.bahr.feature.booking.di.HoldReminders
import eg.bahr.push.PUSH_REF
import eg.bahr.push.PUSH_TYPE

/**
 * The hold reminder on Android (M3-M7): an `AlarmManager` alarm that wakes [receiver] (`:androidApp`'s
 * `HoldReminderReceiver` shell), which posts the notification, on the app's default channel with its monochrome icon.
 *
 * - `setAndAllowWhileIdle` on `ELAPSED_REALTIME_WAKEUP`: inexact (PLAN: inexact is fine), needs no exact-alarm
 *   permission, still fires in Doze (a locked phone during a hold), and counts from boot, not the wall clock, so a
 *   changed device clock cannot move it. WorkManager was the other candidate: its delayed work goes through
 *   JobScheduler, which Doze defers to its maintenance windows, easily past a hold of a few minutes.
 * - Everything the notification needs travels in the alarm's intent, so a process the system started only for the
 *   alarm posts it without Koin or the network.
 * - One alarm per hold: the intent's data is the hold's reference, so a re-schedule replaces that hold's alarm and a
 *   cancel removes exactly it. The notification's tag is [HoldReminder.tag] with id 0, the tag and id FCM's own tray
 *   post uses for a push with that collapse id: a server `hold_expiring` push sent with it replaces this one.
 * - Whether it shows is decided when it fires: notifications allowed, seats not yet released, and no reminder for the
 *   hold already in the tray (a server push got there first).
 */
internal class AndroidHoldReminders(
    private val context: Context,
    private val receiver: Class<out BroadcastReceiver>,
) : HoldReminders {
    override fun schedule(reminder: HoldReminder) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val now = SystemClock.elapsedRealtime()
        val intent =
            alarmIntent(reminder.ref)
                .putExtra(EXTRA_TITLE, reminder.title)
                .putExtra(EXTRA_BODY, reminder.body)
                .putExtra(EXTRA_RELEASED_AT, now + reminder.releasedIn.inWholeMilliseconds)
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, now + reminder.fireIn.inWholeMilliseconds, pending)
    }

    override fun cancel(ref: String) {
        cancelPending(ref)
        NotificationManagerCompat.from(context).cancel(HoldReminder.tag(ref), NOTIFICATION_ID)
    }

    /** The alarm only: a server `hold_expiring` push for [ref] arrived first, and takes its place in the tray. */
    fun cancelPending(ref: String) {
        val pending =
            PendingIntent.getBroadcast(
                context,
                0,
                alarmIntent(ref),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(pending)
        pending.cancel()
    }

    /** Equal (`Intent.filterEquals`) for one [ref], whatever the extras: what makes it one alarm per hold. */
    private fun alarmIntent(ref: String): Intent =
        Intent(context, receiver)
            .setAction(ACTION)
            .setData(
                Uri
                    .Builder()
                    .scheme(URI_SCHEME)
                    .authority(ref)
                    .build(),
            ).putExtra(PUSH_REF, ref)

    internal companion object {
        private val log = Logger.withTag("HoldReminder")

        const val ACTION = "eg.bahr.action.HOLD_REMINDER"
        private const val URI_SCHEME = "bahr-hold"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_BODY = "body"

        /** `SystemClock.elapsedRealtime()` at which the server releases the seats. */
        private const val EXTRA_RELEASED_AT = "released_at"

        /** FCM's tray id (`DisplayNotification` posts with tag + 0): one notification per tag, local or pushed. */
        const val NOTIFICATION_ID = 0

        /** The alarm went off ([receiver]'s `onReceive`): post the reminder if it still makes sense. */
        @SuppressLint("MissingPermission") // Checked: areNotificationsEnabled covers POST_NOTIFICATIONS on Android 13+.
        fun post(
            context: Context,
            intent: Intent,
            channelId: String,
            smallIcon: Int,
        ) {
            if (intent.action != ACTION) return
            val ref = intent.getStringExtra(PUSH_REF) ?: return
            val notifications = NotificationManagerCompat.from(context)
            // Permission denied (M3-M7 "permission-denied path"): the alarm was set anyway, and nothing shows.
            if (!notifications.areNotificationsEnabled()) return
            val left = intent.getLongExtra(EXTRA_RELEASED_AT, 0L) - SystemClock.elapsedRealtime()
            // An inexact alarm can come late: once the seats are released a reminder would be wrong.
            if (left <= 0) return
            val tag = HoldReminder.tag(ref)
            if (isShowing(context, tag)) return
            val open =
                context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(PUSH_TYPE, HoldReminder.TYPE)
                    putExtra(PUSH_REF, ref)
                } ?: return
            val tap =
                PendingIntent.getActivity(
                    context,
                    tag.hashCode(),
                    open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val body = intent.getStringExtra(EXTRA_BODY)
            val notification =
                NotificationCompat
                    .Builder(context, channelId)
                    .setSmallIcon(smallIcon.takeIf { it != 0 } ?: context.applicationInfo.icon)
                    .setContentTitle(intent.getStringExtra(EXTRA_TITLE))
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    // Gone by itself when the seats are released, even if the app never runs again.
                    .setTimeoutAfter(left)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(true)
                    .setContentIntent(tap)
                    .build()
            notifications.notify(tag, NOTIFICATION_ID, notification)
            log.i { "Hold reminder shown" }
        }

        /** A notification with [tag] is in the tray: ours from earlier, or a server push for the same hold. */
        fun isShowing(
            context: Context,
            tag: String,
        ): Boolean =
            context
                .getSystemService(NotificationManager::class.java)
                ?.activeNotifications
                ?.any { it.tag == tag && it.id == NOTIFICATION_ID } == true
    }
}
