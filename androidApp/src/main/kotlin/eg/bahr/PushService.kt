package eg.bahr

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM's service (M3-M5), a shell like the activity: a new token and a push that arrives while the app is in front go
 * to `:composeApp`. A push that arrives in the background is shown by the system itself, on the manifest's default
 * channel, and its tap reaches [MainActivity] with the data as extras.
 */
class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        onPushToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification
        onPushMessage(
            title = notification?.title,
            body = notification?.body,
            data = message.data,
            tag = notification?.tag,
        )
    }
}
