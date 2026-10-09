package eg.bahr

import android.app.Application
import eg.bahr.core.datastore.APP_PREFERENCES_FILE
import eg.bahr.di.AppLinkConfig
import eg.bahr.di.initKoin
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
            // filesDir is app-private and survives updates; DataStore appends
            // its own file name.
            preferencesPath = { filesDir.resolve(APP_PREFERENCES_FILE).absolutePath },
        ) {
            androidContext(this@EgApplication)
            androidLogger()
        }
    }
}
