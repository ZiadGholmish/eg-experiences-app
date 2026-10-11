import SwiftUI

@main
struct iOSApp: App {
    init() {
        // Once per process, before any tab's Compose root exists: the shared Koin graph and tab state.
        SharedApp.start(tabs: TabBarModel.shared)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
