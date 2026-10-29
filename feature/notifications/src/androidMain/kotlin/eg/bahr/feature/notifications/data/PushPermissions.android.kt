package eg.bahr.feature.notifications.data

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat

@Composable
internal actual fun rememberPushPermissions(): PushPermissions {
    val context = LocalContext.current
    val permissions = remember(context) { AndroidPushPermissions(context) }
    // The result comes back to whoever asked last; the holder outlives recompositions of the launcher.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permissions.answer(granted) }
    // After composition, never during it: the holder only needs the launcher by the time "Yes" is tapped.
    SideEffect { permissions.launcher = launcher }
    return permissions
}

/**
 * `POST_NOTIFICATIONS` from Android 13 (API 33). Before 13 there is nothing to ask: notifications are on unless the
 * person turned them off in the settings, which only the settings page undoes.
 */
private class AndroidPushPermissions(
    private val context: Context,
) : PushPermissions {
    var launcher: ManagedActivityResultLauncher<String, Boolean>? = null
    private var pending: ((Boolean) -> Unit)? = null

    override suspend fun status(systemDialogShown: Boolean): PushPermissionStatus =
        when {
            NotificationManagerCompat.from(context).areNotificationsEnabled() -> PushPermissionStatus.Granted
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> PushPermissionStatus.Denied
            !systemDialogShown -> PushPermissionStatus.CanAsk
            // Android shows the dialog twice; after the first refusal it offers a rationale, after the second none.
            context.findActivity()?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, NOTIFICATIONS) } == true ->
                PushPermissionStatus.CanAsk
            else -> PushPermissionStatus.Denied
        }

    override fun requestSystemDialog(onResult: (Boolean) -> Unit) {
        val launcher = launcher
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || launcher == null) {
            onResult(NotificationManagerCompat.from(context).areNotificationsEnabled())
            return
        }
        pending = onResult
        launcher.launch(NOTIFICATIONS)
    }

    fun answer(granted: Boolean) {
        pending?.invoke(granted)
        pending = null
    }

    override fun openSettings() {
        val intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.O }
                ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun Context.findActivity(): Activity? =
        when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.findActivity()
            else -> null
        }

    private companion object {
        // The constant is API 33; the string is what older SDKs would see, and they never ask.
        const val NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS
    }
}
