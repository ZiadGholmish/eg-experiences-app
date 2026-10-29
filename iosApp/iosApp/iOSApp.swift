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
