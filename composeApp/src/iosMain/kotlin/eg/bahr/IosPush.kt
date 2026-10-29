package eg.bahr

import eg.bahr.push.AppPushChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * Push on iOS (M3-M5): FCM over APNs through the Firebase iOS SDK, which lives in Swift (`iosApp/iosApp/Push.swift`):
 * Swift configures Firebase only when the build carries a `GoogleService-Info.plist` ([available], M3-M19a), hands the
 * FCM token over as it gets one, and hands a tapped push's `link` and `type` over. Whether notifications are allowed
 * is asked here, of `UNUserNotificationCenter`.
 */
internal fun iosPushChannel(
    available: Boolean,
    scope: CoroutineScope,
): AppPushChannel = AppPushChannel(isAvailable = available, allowed = ::notificationsAllowed, scope = scope)

private suspend fun notificationsAllowed(): Boolean =
    suspendCancellableCoroutine { continuation ->
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            continuation.resume(
                when (settings?.authorizationStatus) {
                    UNAuthorizationStatusAuthorized, UNAuthorizationStatusProvisional, UNAuthorizationStatusEphemeral -> true
                    else -> false
                },
            )
        }
    }
