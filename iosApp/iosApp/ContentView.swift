import ComposeApp
import SwiftUI

/// Starts the shared app once, before the first tab's Compose root is created.
///
/// Koin is started inside `startApp` rather than here, so this target needs no knowledge of the
/// shared module's wiring — see MainViewController.kt.
enum SharedApp {
    static func start(tabs: TabBarModel) {
        // BASE_URL comes from the active xcconfig via Info.plist, the same way
        // composeApp's BuildConfig.BASE_URL comes from the product flavor.
        let baseUrl = Bundle.main.object(forInfoDictionaryKey: "BASE_URL") as? String ?? ""
        // The Universal Links host, from the same xcconfig value as the Associated Domains entitlement.
        let appLinkHost = Bundle.main.object(forInfoDictionaryKey: "APP_LINK_HOST") as? String ?? ""

        #if DEBUG
        let isDebug = true
        #else
        let isDebug = false
        #endif

        // The Google Maps bridge (M4-M2), or nil without a Maps key: the map then draws a plain ground.
        MainViewControllerKt.startApp(
            baseUrl: baseUrl,
            isDebug: isDebug,
            appLinkHost: appLinkHost,
            mapViews: GoogleMapsSetup.bridge(),
            // Sign in with Apple and Google (M3-M0), or nil when the build signs in with fake tokens.
            nativeSignIn: NativeSignInBridge.bridge(),
            allowFakeSignIn: NativeSignInBridge.fakeSignIn,
            tabBar: tabs,
            selectedTab: tabs.selected.kotlin
        )
    }
}

/// The app's tabs (M4-M8; since M3-M6a Home · Map · My trips · Profile, Alerts being a bell on Home): a native `TabView` (Liquid Glass on iOS 26), one Compose root per tab.
///
/// The bar's labels and direction follow the app language, which the shared code sends with the labels
/// already translated (core:localization), so no string is kept here. A switch in Profile re-sends
/// them and the bar flips at once. Each tab's content decides its own direction in Compose, so it is
/// kept out of SwiftUI's mirroring.
struct ContentView: View {
    @ObservedObject private var tabs = TabBarModel.shared

    var body: some View {
        TabView(selection: tabs.selection) {
            ForEach(BahrTab.allCases, id: \.self) { tab in
                TabRoot(tab: tab)
                    // Full screen, under the glass bar: Compose draws its own insets, and the bar's height
                    // reaches it as part of the bottom safe area.
                    .ignoresSafeArea()
                    .environment(\.layoutDirection, .leftToRight)
                    // Outlined and muted, filled in the tab's colour only when selected (see TabBarModel.icon).
                    .tabItem {
                        Label { Text(tabs.title(tab)) } icon: { tabs.icon(tab) }
                            .environment(\.symbolVariants, .none)
                            // A count is said inside the name ("My trips, 3 unread"), not as a badge alone.
                            .accessibilityLabel(Text(tabs.spokenLabel(tab)))
                    }
                    .badge(tabs.badge(tab))
                    .tag(tab)
                    // Shown on a tab's root only: hidden on the trip page, booking, hold, confirmation.
                    .toolbar(tabs.isBarHidden(tab) ? .hidden : .visible, for: .tabBar)
            }
        }
        // The selected tab's colour (the theme's onPrimaryContainer, as Android's selected icon), from the
        // shared theme. The others are muted with the theme's onSurfaceVariant, also from the shared code:
        // the labels through TabBarModel.muteLabels, the icons drawn in that colour (TabBarModel.icon).
        .tint(tabs.selectedTint)
        .modifier(MinimizesOnScroll())
        // Light unless the person picked Dark, or the phone's setting (nil) in Profile → Appearance.
        .preferredColorScheme(tabs.colorScheme)
        .onAppear { TabBarModel.applyWindowStyle(tabs.colorScheme) }
        .environment(\.layoutDirection, tabs.rightToLeft ? .rightToLeft : .leftToRight)
        .environment(\.locale, Locale(identifier: tabs.languageTag))
        // Universal Links (https://<APP_LINK_HOST>/t/{slug}). SwiftUI may deliver one through either
        // callback; the shared code parses it and opens the trip in the Home tab, so both hand it over.
        .onOpenURL { url in
            // Google Sign-In's return to the app (its URL scheme) is not a deep link.
            if NativeSignInBridge.handle(url) { return }
            tabs.showHomeForLink()
            MainViewControllerKt.onDeepLink(url: url.absoluteString)
        }
        .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
            if let url = activity.webpageURL {
                tabs.showHomeForLink()
                MainViewControllerKt.onDeepLink(url: url.absoluteString)
            }
        }
    }
}
