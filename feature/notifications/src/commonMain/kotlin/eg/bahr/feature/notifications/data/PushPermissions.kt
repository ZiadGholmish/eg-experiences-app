package eg.bahr.feature.notifications.data

import androidx.compose.runtime.Composable

/** Where the OS stands on this app's notifications, as the push pre-prompt needs to know it (M3-M5). */
internal enum class PushPermissionStatus {
    /** Notifications are allowed: nothing to ask. */
    Granted,

    /** The OS dialog can still be shown. */
    CanAsk,

    /** Refused for good (or turned off in the system settings): only the app's settings page can change it. */
    Denied,
}

/**
 * The platform's notification permission (Android 13+ `POST_NOTIFICATIONS`, iOS `UNUserNotificationCenter`). Made in
 * composition ([rememberPushPermissions]) because Android's request needs the activity's result launcher; the view
 * model decides what to do and calls these.
 */
internal interface PushPermissions {
    /**
     * [systemDialogShown]: whether this app showed the OS dialog before (Android cannot tell "never asked" from
     * "denied for good" without it).
     */
    suspend fun status(systemDialogShown: Boolean): PushPermissionStatus

    /** Shows the OS dialog; [onResult] gets whether notifications are now allowed. */
    fun requestSystemDialog(onResult: (Boolean) -> Unit)

    /** Opens this app's notification settings (the only way back from a refusal). */
    fun openSettings()
}

@Composable
internal expect fun rememberPushPermissions(): PushPermissions
