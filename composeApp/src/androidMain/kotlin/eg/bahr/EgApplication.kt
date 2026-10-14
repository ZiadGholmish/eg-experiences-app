package eg.bahr

import android.app.Application
import eg.bahr.core.datastore.APP_PREFERENCES_FILE
import eg.bahr.di.AppLinkConfig
import eg.bahr.di.initKoin
import eg.bahr.feature.auth.di.AuthConfig
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

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
    }
}
