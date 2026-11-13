import ComposeApp
import SwiftUI

@main
struct iOSApp: App {
    // Push (M3-M5): Firebase's token, foreground notifications and taps.
    @UIApplicationDelegateAdaptor(PushAppDelegate.self) private var pushDelegate
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Firebase first (only with a GoogleService-Info.plist), so the shared app knows whether push is on.
        PushSetup.configure()
        // Once per process, before any tab's Compose root exists: the shared Koin graph and tab state.
        SharedApp.start(tabs: TabBarModel.shared)
        #if DEBUG
        DebugLaunch.run(tabs: TabBarModel.shared)
        #endif
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        // Notifications may have been turned on in Settings meanwhile: the shared code reads the permission again.
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { MainViewControllerKt.onAppForeground() }
        }
    }
}

#if DEBUG
/// Debug builds only (MV-M8): paths a simulator can be driven along without touch input, from the launch
/// environment (`SIMCTL_CHILD_<NAME>=… xcrun simctl launch …` passes `<NAME>` to the app).
///
/// - `BAHR_DEBUG_OPEN_LINK`: a Universal Link, handed over as SwiftUI's `onOpenURL` would (the Local build has no
///   Associated Domains, so `simctl openurl` opens Safari instead).
/// - `BAHR_DEBUG_OPEN_PUSH`: a tapped notification's `link`, as `PushAppDelegate` hands it over.
enum DebugLaunch {
    static func run(tabs: TabBarModel) {
        let env = ProcessInfo.processInfo.environment
        if let link = env["BAHR_DEBUG_OPEN_LINK"], !link.isEmpty {
            tabs.showHomeForLink()
            MainViewControllerKt.onDeepLink(url: link)
        }
        if let link = env["BAHR_DEBUG_OPEN_PUSH"], !link.isEmpty {
            tabs.showHomeForLink()
            MainViewControllerKt.onPushOpened(link: link, type: nil, ref: nil)
        }
    }
}
#endif
