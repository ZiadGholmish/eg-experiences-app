package eg.bahr

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A recreated activity (savedInstanceState != null) gets its original intent again; that link
        // was already opened and the restored back stack shows it.
        if (savedInstanceState == null) offerDeepLink(intent)
        setContent { App(appVersion = BuildConfig.VERSION_NAME) }
    }

    /** A link opened while the app is running (launchMode singleTask). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        offerDeepLink(intent)
    }

    /** The notification permission may have been changed in the system settings meanwhile (M3-M5). */
    override fun onResume() {
        super.onResume()
        onAppForeground()
    }

    private fun offerDeepLink(intent: Intent?) {
        if (intent == null) return
        // Reopened from recents: the intent is the one the task was started with, not a new tap.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        // A tap on a push the system showed (M3-M5): the launcher intent with the push's data as extras.
        if (onPushIntent(intent)) return
        if (intent.action != Intent.ACTION_VIEW) return
        intent.dataString?.let(::onDeepLink)
    }
}
