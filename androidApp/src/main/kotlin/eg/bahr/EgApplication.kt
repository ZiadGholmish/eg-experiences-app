package eg.bahr

import android.app.Application

/** The process entry point: hands this flavor's BuildConfig to the shared module's [startApp]. */
class EgApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startApp(
            baseUrl = BuildConfig.BASE_URL,
            isDebug = BuildConfig.DEBUG,
            appLinkHost = BuildConfig.APP_LINK_HOST,
            appLinkAllowsHttp = BuildConfig.APP_LINK_ALLOWS_HTTP,
            googleServerClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID,
            allowFakeSignIn = BuildConfig.ALLOW_FAKE_SIGN_IN,
        )
    }
}
