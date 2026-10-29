package eg.bahr.push

import eg.bahr.core.common.push.PushChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The app's [PushChannel] (M3-M5), the same on both platforms: the platform hands in the FCM token as the Firebase SDK
 * gives it ([onFcmToken]: Android's `FirebaseMessaging`, iOS's Swift `MessagingDelegate`) and how to ask whether
 * notifications are allowed ([allowed]); this shares the token only while they are, so the install registers without
 * one otherwise (openapi `registerDevice`: no token without push permission).
 *
 * [isAvailable] is false in a build without the Firebase config file (M3-M19a): then there is never a token.
 */
internal class AppPushChannel(
    override val isAvailable: Boolean,
    private val allowed: suspend () -> Boolean,
    private val scope: CoroutineScope,
) : PushChannel {
    private val fcmToken = MutableStateFlow<String?>(null)
    private val allowedNow = MutableStateFlow(false)

    override val token: StateFlow<String?> =
        combine(fcmToken, allowedNow) { token, allowed -> token?.takeIf { allowed && isAvailable } }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /** The SDK's token, at start and whenever it rotates; null when it could not be had. */
    fun onFcmToken(token: String?) {
        fcmToken.value = token
    }

    override suspend fun notificationsAllowed(): Boolean = allowed()

    override fun refresh() {
        if (!isAvailable) return
        scope.launch { allowedNow.value = allowed() }
    }
}
