package eg.bahr

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import eg.bahr.core.common.push.PushChannel
import eg.bahr.core.common.theme.AppAppearance
import eg.bahr.core.datastore.APP_PREFERENCES_FILE
import eg.bahr.core.datastore.AppSettingsStore
import eg.bahr.deeplink.AppDeepLinkInbox
import eg.bahr.di.AppLinkConfig
import eg.bahr.di.initKoin
import eg.bahr.di.seedStoredAppearance
import eg.bahr.di.startInstallRegistration
import eg.bahr.feature.auth.di.AuthConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.dsl.module

/**
 * The Android entry points `:androidApp` calls (M3-M13), as iOS's `startApp` / `onDeepLink` are for Swift. The
 * application module holds only the manifest, BuildConfig and the `Application`/`Activity` shells, so it depends on
 * this module alone (bahr-modularization: `:androidApp → :composeApp`); the wiring that needs `core:*` and
 * `feature:*` stays here. Plain values in, no module types: [startApp]'s parameters are BuildConfig's fields.
 *
 * Starts Koin with the per-flavor values ([appLinkHost]/[appLinkAllowsHttp]: the same values as the manifest's App
 * Links intent filter; [googleServerClientId] and [allowFakeSignIn]: Sign in with Google, fake sign-in on the local
 * flavor only, M3-M0), then follows the stored appearance.
 */
fun Application.startApp(
    baseUrl: String,
    isDebug: Boolean,
    appLinkHost: String,
    appLinkAllowsHttp: Boolean,
    googleServerClientId: String,
    allowFakeSignIn: Boolean,
    pushChannelId: String,
    pushChannelName: String,
) {
    pushChannelIdInUse = pushChannelId
    val push = AndroidPush.start(this, appScope, pushChannelId, pushChannelName)
    initKoin(
        baseUrl = baseUrl,
        isDebug = isDebug,
        appLinks = AppLinkConfig(host = appLinkHost, allowsHttp = appLinkAllowsHttp),
        auth = AuthConfig(googleServerClientId = googleServerClientId, allowFakeSignIn = allowFakeSignIn),
        // filesDir is app-private and survives updates; DataStore appends
        // its own file name.
        preferencesPath = { filesDir.resolve(APP_PREFERENCES_FILE).absolutePath },
    ) {
        androidContext(this@startApp)
        androidLogger()
        // The platform's push (M3-M5): core:network registers the install with its token.
        modules(module { single<PushChannel> { push } })
    }
    followAppearance()
    GlobalContext.get().startInstallRegistration(appScope)
}

/** The process's own scope for what runs as long as the app: the install's registration and the push channel. */
private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** [startApp]'s channel id, for pushes shown while the app is in front. */
private var pushChannelIdInUse = ""

/**
 * The activity's intent (launch or `onNewIntent`): a tap on a push the system showed in the background carries the
 * push's data as extras (M3-M5); anything else that is a link is a deep link. True when it was a push.
 */
fun onPushIntent(intent: Intent): Boolean = AndroidPush.openFromExtras(intent)

/** `FirebaseMessagingService.onNewToken` (M3-M5). The token is never logged. */
fun onPushToken(token: String) {
    AndroidPush.onNewToken(token)
}

/** `FirebaseMessagingService.onMessageReceived` while the app is in front (M3-M5): shown as the system would. */
fun Context.onPushMessage(
    title: String?,
    body: String?,
    data: Map<String, String>,
    tag: String?,
) {
    AndroidPush.showInForeground(this, pushChannelIdInUse, title, body, data, tag)
}

/** The activity came to the front: the notification permission may have changed in the system settings (M3-M5). */
fun onAppForeground() {
    GlobalContext.getOrNull()?.getOrNull<PushChannel>()?.refresh()
}

/** A link the activity received (launch or `onNewIntent`); it waits in the inbox until the Home tab's NavHost opens it. */
fun onDeepLink(url: String) {
    AppDeepLinkInbox.offer(url)
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
