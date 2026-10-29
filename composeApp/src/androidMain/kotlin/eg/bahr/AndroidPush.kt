package eg.bahr

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import co.touchlab.kermit.Logger
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import eg.bahr.push.AppPushChannel
import eg.bahr.push.PUSH_LINK
import eg.bahr.push.PUSH_TYPE
import eg.bahr.push.openPush
import kotlinx.coroutines.CoroutineScope

/**
 * Push on Android (M3-M5): FCM through the Firebase SDK, which starts itself from the resources the Google Services
 * plugin generates out of `androidApp/src/<flavor>/google-services.json`. Without that file (M3-M19a: every flavor on a
 * fresh checkout) there is no `FirebaseApp`, and `FirebaseMessaging.getInstance()` would throw: push is then off and
 * the install registers without a token.
 */
internal object AndroidPush {
    private val log = Logger.withTag("Push")

    /** Set by [start]; null before it (a service woken before the Application ran is not possible, but cheap to guard). */
    private var channel: AppPushChannel? = null

    fun start(
        context: Context,
        scope: CoroutineScope,
        channelId: String,
        channelName: String,
    ): AppPushChannel {
        val available = FirebaseApp.getApps(context).isNotEmpty()
        if (!available) log.i { "No Firebase config in this build: push is off" }
        createDefaultChannel(context, channelId, channelName)
        val push =
            AppPushChannel(
                isAvailable = available,
                allowed = { NotificationManagerCompat.from(context).areNotificationsEnabled() },
                scope = scope,
            )
        channel = push
        if (available) {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) push.onFcmToken(task.result) else log.w(task.exception) { "No FCM token" }
            }
        }
        return push
    }

    /** FCM rotated the token (`FirebaseMessagingService.onNewToken`). Never logged. */
    fun onNewToken(token: String) {
        channel?.onFcmToken(token)
    }

    /**
     * The app's default channel, with the default sound, named in the manifest as FCM's default
     * (`com.google.firebase.messaging.default_notification_channel_id`), so a push the system shows in the
     * background lands here (the server sends `default_sound`, and from Android 8 the channel decides). Creating it
     * again with a new [name] renames it (a language switch, next start).
     */
    private fun createDefaultChannel(
        context: Context,
        id: String,
        name: String,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val sound =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        val channel =
            NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), sound)
            }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /**
     * A push that arrived while the app was in the foreground (`onMessageReceived`): FCM shows nothing then, so it is
     * posted here as the system would have, on the default channel, replacing one with the same [tag] (the server's
     * collapse id), and a tap opens the app with the same extras a background tap carries.
     */
    @SuppressLint("MissingPermission") // Checked: areNotificationsEnabled covers POST_NOTIFICATIONS on Android 13+.
    fun showInForeground(
        context: Context,
        channelId: String,
        title: String?,
        body: String?,
        data: Map<String, String>,
        tag: String?,
    ) {
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled() || (title == null && body == null)) return
        val open =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data[PUSH_LINK]?.let { putExtra(PUSH_LINK, it) }
                data[PUSH_TYPE]?.let { putExtra(PUSH_TYPE, it) }
            } ?: return
        val tap =
            PendingIntent.getActivity(
                context,
                (tag ?: title).hashCode(),
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, channelId)
                .setSmallIcon(context.applicationInfo.icon)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(tap)
                .build()
        notifications.notify(tag, NOTIFICATION_ID, notification)
    }

    /**
     * A tap on a notification the system showed for us (the app was in the background): FCM starts the launcher
     * activity with the push's data as string extras. True when it carried one of our links.
     */
    fun openFromExtras(intent: Intent): Boolean {
        val link = intent.getStringExtra(PUSH_LINK) ?: return false
        openPush(link, intent.getStringExtra(PUSH_TYPE))
        return true
    }

    /** One id for all: the [showInForeground] tag tells our notifications apart (the server's collapse id). */
    private const val NOTIFICATION_ID = 1
}
