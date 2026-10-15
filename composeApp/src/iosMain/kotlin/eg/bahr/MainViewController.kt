package eg.bahr

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.ComposeUIViewController
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.datastore.APP_PREFERENCES_FILE
import eg.bahr.deeplink.AppDeepLinkInbox
import eg.bahr.di.AppAppearanceState
import eg.bahr.di.AppLanguageState
import eg.bahr.di.AppLinkConfig
import eg.bahr.di.initKoin
import eg.bahr.di.seedStoredAppearance
import eg.bahr.feature.auth.di.AuthConfig
import eg.bahr.feature.auth.di.NativeSignIn
import eg.bahr.feature.map.di.TripMapNativeViews
import eg.bahr.navigation.AppTab
import eg.bahr.navigation.AppTabsState
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIColor
import platform.UIKit.UITraitEnvironmentLayoutDirectionLeftToRight
import platform.UIKit.UITraitEnvironmentLayoutDirectionRightToLeft
import platform.UIKit.UIViewController
import platform.UIKit.traitOverrides

/**
 * The SwiftUI `TabView` in `iosApp` (M4-M8; kept by the user's decision of 2026-10-14 over M3-M0b's
 * Compose bar, with the new tabs and the Alerts badge), as the shared code drives it. Swift implements it
 * (`iosApp/iosApp/AppTabs.swift`); the shared code calls it on the main thread.
 *
 * Swift draws the bar natively (Liquid Glass on iOS 26) but makes no decision of its own beyond
 * remembering the selected tab: the labels, badges, language and tab colours come from here, and so does when the bar
 * hides (a pushed screen) and which tab to show (Home's Map pill, a deep link).
 * Taps go back through [onTabTapped].
 *
 * Here, in the app module, rather than in a feature's `di/` (the M4-M2 pattern for feature:map's
 * `TripMapNativeViews`): the tabs belong to the app's wiring, not to a feature, so no module edge.
 */
interface AppTabBar {
    /**
     * The app's language ([languageTag] `ar`/`en`, [rightToLeft] for the bar's direction); in [AppTab]
     * order the tab labels in it, what VoiceOver should say for each ([spokenLabels]: the unread count
     * inside, handoff §1) and each tab's badge ([badges]: "3", "9+", or "" for none, M3-M0b); the
     * selected tabs' colour ([selectedTint]) and the muted colour of the others ([unselectedTint]). Sent
     * by every tab's root, again on each language switch and badge change.
     */
    fun showContent(
        languageTag: String,
        rightToLeft: Boolean,
        titles: List<String>,
        spokenLabels: List<String>,
        badges: List<String>,
        selectedTint: UIColor,
        unselectedTint: UIColor,
    )

    /**
     * Profile → Appearance (M3-M0b): `light`, `dark` or `system`. Called once at launch with the stored
     * value, before any tab draws, and again on each pick.
     */
    fun applyAppearance(appearance: String)

    /** Show [tab]. Not a tap: Swift must not report it back. */
    fun selectTab(tab: AppTab)

    /** Hide the bar while [tab] shows a pushed screen; show it on the tab's root. */
    fun setBarHidden(
        tab: AppTab,
        hidden: Boolean,
    )
}

/**
 * Swift entry point, called once at launch: `MainViewControllerKt.startApp(...)`, before any tab's
 * [TabViewController]. Starts the one Koin graph every tab shares (Koin cannot be started twice).
 *
 * [appLinkHost] is the Universal Links host (`APP_LINK_HOST` in the xcconfig, the same value as the
 * Associated Domains entitlement). Universal Links are https only, so http is never accepted here.
 *
 * [mapViews] is the Swift bridge to the Google Maps SDK for iOS (M4-M2), or null when the build has
 * no Maps key: the map screen then draws its pins on a plain ground. Naming the feature's interface
 * here is what puts it in the framework header for Swift, without exporting the feature module.
 *
 * [selectedTab] is the tab Swift restored (it keeps the last one across launches); [tabBar] is Swift's
 * bar, which the shared tab state drives from here on.
 *
 * [nativeSignIn] is the Swift bridge to Sign in with Apple and Google Sign-In (M3-M0), named here for
 * the framework header as [mapViews] is. Swift leaves it out when [allowFakeSignIn] (a Debug build of
 * the Local configuration with no Google client id): the local backend's fake verifier only takes
 * made-up tokens, which the app then signs in with instead. Honoured only with [isDebug].
 */
@Suppress("unused")
fun startApp(
    baseUrl: String,
    isDebug: Boolean,
    appLinkHost: String,
    mapViews: TripMapNativeViews?,
    nativeSignIn: NativeSignIn?,
    allowFakeSignIn: Boolean,
    tabBar: AppTabBar,
    selectedTab: AppTab,
) {
    if (IosTabs.started) return
    initKoin(
        baseUrl = baseUrl,
        isDebug = isDebug,
        appLinks = AppLinkConfig(host = appLinkHost, allowsHttp = false),
        // iOS's Google client id lives in Info.plist (Google Sign-In reads it), so none is passed here.
        // Fake sign-in is a debug-build feature whatever the xcconfig says (M3-M0 review #8): a Release build
        // never makes made-up tokens, even if a private xcconfig asked for them.
        auth = AuthConfig(googleServerClientId = "", allowFakeSignIn = allowFakeSignIn && isDebug),
        preferencesPath = { documentsPath(APP_PREFERENCES_FILE) },
    ) {
        mapViews?.let { views -> modules(module { single<TripMapNativeViews> { views } }) }
        nativeSignIn?.let { bridge -> modules(module { single<NativeSignIn> { bridge } }) }
    }
    // The stored appearance before any Compose root or the bar draws (M3-M0b review R4-1).
    KoinPlatform.getKoin().seedStoredAppearance()
    IosTabs.start(tabBar, selectedTab)
}

/**
 * Swift entry point, one per tab: `MainViewControllerKt.TabViewController(tab:)`. One Compose root
 * and NavHost per tab, over the graph [startApp] started.
 *
 * Its UIKit layout direction follows the app language (M3-M12; [IosTabs.followLanguage]).
 */
@Suppress("FunctionName", "unused")
fun TabViewController(tab: AppTab): UIViewController =
    ComposeUIViewController {
        App(tab = tab, tabs = IosTabs.state, appVersion = appVersion(), onBarContent = IosTabs::showContent)
    }.also(IosTabs::followLanguage)

/** Swift entry point for a tap on the bar, the selected tab included (a re-tap). */
@Suppress("unused")
fun onTabTapped(tab: AppTab) {
    IosTabs.state.onTabTapped(tab)
}

/**
 * Swift entry point for a Universal Link: `MainViewControllerKt.onDeepLink(url:)`, from SwiftUI's
 * `onOpenURL` / `onContinueUserActivity`. Safe to call before [startApp]: the link waits in the inbox
 * until the Home tab's NavHost opens it. A link delivered through both callbacks still ends on one trip
 * page: the NavHost opens a trip single-top.
 */
@Suppress("unused")
fun onDeepLink(url: String) {
    AppDeepLinkInbox.offer(url)
}

/**
 * The tab state every iOS root shares, mirrored to Swift's bar. One per process, like the Koin graph
 * it sits beside.
 */
internal object IosTabs {
    var state = AppTabsState()
        private set
    var started = false
        private set
    private var bar: AppTabBar? = null
    private var shownContent: IosBarContent? = null
    private val scope = MainScope()

    fun start(
        tabBar: AppTabBar,
        selectedTab: AppTab,
    ) {
        started = true
        bar = tabBar
        state = AppTabsState(initial = selectedTab)
        scope.launch {
            var shown: AppTabsState.Snapshot? = null
            state.state.collect { now ->
                if (shown?.selected != now.selected) tabBar.selectTab(now.selected)
                AppTab.entries
                    .filter { shown == null || shown?.atRoot?.get(it) != now.atRoot[it] }
                    .forEach { tab -> tabBar.setBarHidden(tab, hidden = now.atRoot[tab] != true) }
                shown = now
            }
        }
        // A language switch starts every tab over at its root (see AppTabsState.resetToRoots).
        val languages: AppLanguageState = KoinPlatform.getKoin().get()
        scope.launch { languages.language.drop(1).collect { state.resetToRoots() } }
        // Profile → Appearance (M3-M0b): the stored value at once (it was seeded), then each pick.
        val appearance: AppAppearanceState = KoinPlatform.getKoin().get()
        tabBar.applyAppearance(appearance.appearance.value.tag)
        scope.launch { appearance.appearance.drop(1).collect { tabBar.applyAppearance(it.tag) } }
        // A link from outside opens in the Home tab's stack: show Home so its NavHost can open it.
        scope.launch { AppDeepLinkInbox.pending.filterNotNull().collect { state.select(AppTab.Home) } }
    }

    /**
     * Gives [controller] the app language's layout direction as its UIKit trait (M3-M12), now and on each
     * switch. Compose Multiplatform puts its back swipe on the leading edge of the UIKit direction, not of
     * the app's: SwiftUI hosts every tab left to right (ContentView keeps it out of SwiftUI's mirroring),
     * so without this the Arabic app took Back from the left edge, while its pages leave to the left. The
     * trait changes no Compose layout: BahrTheme sets the direction from the language.
     *
     * Compose Multiplatform 1.10 reads the direction when the tab appears, so a switch reaches the tab
     * it is made in the next time that tab is shown; 1.12 also follows a trait change at once.
     */
    fun followLanguage(controller: UIViewController) {
        val languages: AppLanguageState = KoinPlatform.getKoin().get()
        // At once, before the tab first appears (launching would land a run-loop pass later); then every
        // value, the first included: the stored language may land between the two, and a second set of
        // the same direction is harmless.
        controller.applyDirection(languages.language.value)
        scope.launch { languages.language.collect { controller.applyDirection(it) } }
    }

    private fun UIViewController.applyDirection(language: AppLanguage) {
        traitOverrides.setLayoutDirection(
            if (language.isRtl) UITraitEnvironmentLayoutDirectionRightToLeft else UITraitEnvironmentLayoutDirectionLeftToRight,
        )
    }

    fun showContent(content: IosBarContent) {
        if (shownContent == content) return
        shownContent = content
        bar?.showContent(
            languageTag = content.language.tag,
            rightToLeft = content.language.isRtl,
            titles = content.titles,
            spokenLabels = content.spokenLabels,
            badges = content.badges.map { it.orEmpty() },
            selectedTint = content.selectedTint.toUIColor(),
            unselectedTint = content.unselectedTint.toUIColor(),
        )
    }

    private fun Color.toUIColor(): UIColor =
        UIColor(red = red.toDouble(), green = green.toDouble(), blue = blue.toDouble(), alpha = alpha.toDouble())
}

/** The build's version name, as Profile's About shows it (Android's comes from BuildConfig). */
private fun appVersion(): String = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun documentsPath(fileName: String): String {
    val documentDirectory: NSURL? =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
    return requireNotNull(documentDirectory).path + "/" + fileName
}
