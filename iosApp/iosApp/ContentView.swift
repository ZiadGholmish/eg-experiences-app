import SwiftUI
import UIKit
import ComposeApp

/// Hosts the shared Compose UI.
///
/// Koin is started inside `MainViewController` rather than here, so this target
/// needs no knowledge of the shared module's wiring — see MainViewController.kt.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // BASE_URL comes from the active xcconfig via Info.plist, the same way
        // composeApp's BuildConfig.BASE_URL comes from the product flavor.
        let baseUrl = Bundle.main.object(forInfoDictionaryKey: "BASE_URL") as? String ?? ""

        #if DEBUG
        let isDebug = true
        #else
        let isDebug = false
        #endif

        return MainViewControllerKt.MainViewController(baseUrl: baseUrl, isDebug: isDebug)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // Compose draws its own insets, and the layout direction is decided by
        // the shared code from the stored language — not by SwiftUI.
        ComposeView().ignoresSafeArea()
    }
}
