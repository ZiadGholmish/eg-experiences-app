package eg.bahr

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import eg.bahr.core.common.theme.AppAppearance
import eg.bahr.core.datastore.APP_PREFERENCES_FILE
import eg.bahr.core.datastore.AppSettingsStore
import eg.bahr.di.AppLinkConfig
import eg.bahr.di.initKoin
import eg.bahr.di.seedStoredAppearance
import eg.bahr.feature.auth.di.AuthConfig
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext

class EgApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(
            baseUrl = BuildConfig.BASE_URL,
            isDebug = BuildConfig.DEBUG,
            // The same per-flavor values as the manifest's App Links intent filter.
            appLinks = AppLinkConfig(host = BuildConfig.APP_LINK_HOST, allowsHttp = BuildConfig.APP_LINK_ALLOWS_HTTP),
            // Sign in with Google's server client id, and fake sign-in on the local flavor only (M3-M0).
            auth = AuthConfig(googleServerClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID, allowFakeSignIn = BuildConfig.ALLOW_FAKE_SIGN_IN),
            // filesDir is app-private and survives updates; DataStore appends
            // its own file name.
            preferencesPath = { filesDir.resolve(APP_PREFERENCES_FILE).absolutePath },
        ) {
            androidContext(this@EgApplication)
            androidLogger()
        }
        followAppearance()
    }

    /**
     * Profile → Appearance (M3-M0b) before the first frame (review R4-1): the stored value is read once
     * and seeds the app's [eg.bahr.di.AppAppearanceState], which the Compose theme reads, and AppCompat's
     * night mode, which the window and system bars follow (Light: no, Dark: yes, System: the phone's).
     * So a stored Dark starts dark everywhere. A later pick recreates the activity in the new mode, with
     * its state saved.
     */
    private fun followAppearance() {
        val koin = GlobalContext.get()
        AppCompatDelegate.setDefaultNightMode(koin.seedStoredAppearance().nightMode())
        // The store's own flow: it starts with the value just applied, then brings each new pick.
        val store = koin.get<AppSettingsStore>()
        MainScope().launch { store.appearance.distinctUntilChanged().collect { AppCompatDelegate.setDefaultNightMode(it.nightMode()) } }
    }

    private fun AppAppearance.nightMode(): Int =
        when (this) {
            AppAppearance.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppAppearance.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            AppAppearance.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
}
