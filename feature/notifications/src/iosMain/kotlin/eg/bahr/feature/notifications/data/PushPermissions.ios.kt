package eg.bahr.feature.notifications.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenNotificationSettingsURLString
import platform.UIKit.registerForRemoteNotifications
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

@Composable
internal actual fun rememberPushPermissions(): PushPermissions = remember { IosPushPermissions() }

/** `UNUserNotificationCenter`: iOS itself remembers whether it asked, so [status] needs no memory of ours. */
private class IosPushPermissions : PushPermissions {
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun status(systemDialogShown: Boolean): PushPermissionStatus =
        suspendCancellableCoroutine { continuation ->
            center.getNotificationSettingsWithCompletionHandler { settings ->
                val status =
                    when (settings?.authorizationStatus) {
                        UNAuthorizationStatusAuthorized, UNAuthorizationStatusProvisional, UNAuthorizationStatusEphemeral ->
                            PushPermissionStatus.Granted
                        UNAuthorizationStatusNotDetermined -> PushPermissionStatus.CanAsk
                        else -> PushPermissionStatus.Denied
                    }
                continuation.resume(status)
            }
        }

    override fun requestSystemDialog(onResult: (Boolean) -> Unit) {
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted, _ ->
            // The answer arrives on a background queue; UIKit and the caller's state want the main thread.
            dispatch_async(dispatch_get_main_queue()) {
                // The APNs token FCM's token is made from; Swift's delegate hands the FCM token over once it exists.
                if (granted) UIApplication.sharedApplication.registerForRemoteNotifications()
                onResult(granted)
            }
        }
    }

    override fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenNotificationSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}
