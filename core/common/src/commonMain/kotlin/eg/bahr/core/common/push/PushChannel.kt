package eg.bahr.core.common.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The platform's push (FCM on Android, FCM over APNs on iOS; M3-M5), as the shared code sees it. Implemented per
 * platform in `:composeApp`, which is the only module that holds the Firebase SDKs; core:network registers the install
 * with its [token], and feature:notifications asks for the permission.
 */
interface PushChannel {
    /**
     * Whether this build can receive push at all: false when the Firebase config file is missing (M3-M19a, a build
     * with push off). Fixed for the process.
     */
    val isAvailable: Boolean

    /**
     * The FCM registration token while notifications are allowed, else null (the contract registers an install without
     * a token when push permission is not granted). Changes when FCM rotates the token or the permission changes.
     */
    val token: StateFlow<String?>

    /** Whether the OS lets this app show notifications now. */
    suspend fun notificationsAllowed(): Boolean

    /**
     * Reads the permission and the token again: after the OS dialog, and when the app comes back to the foreground
     * (the person may have turned notifications on in the system settings).
     */
    fun refresh()
}

/** A build with push off (tests, and a platform that binds none). */
object NoPushChannel : PushChannel {
    override val isAvailable: Boolean = false
    override val token: StateFlow<String?> = MutableStateFlow(null)

    override suspend fun notificationsAllowed(): Boolean = false

    override fun refresh() = Unit
}
