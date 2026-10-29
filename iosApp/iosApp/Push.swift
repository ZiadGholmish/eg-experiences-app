import ComposeApp
import FirebaseCore
import FirebaseMessaging
import UIKit
import UserNotifications

/// Push (M3-M5): FCM over APNs through the Firebase iOS SDK. Firebase starts only when the build carries a
/// `GoogleService-Info.plist` (copied in by the "Copy Firebase Config" phase, M3-M19a): `FirebaseApp.configure()`
/// fails hard without one, so a checkout with no file runs with push off. The permission itself is asked by the shared
/// code (the pre-prompt's "Yes"), which also calls `registerForRemoteNotifications`.
enum PushSetup {
    private(set) static var available = false

    /// Once, before the shared app starts (it is told whether push is available).
    static func configure() {
        guard !available, Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else { return }
        FirebaseApp.configure()
        available = true
    }
}

/// The app delegate SwiftUI keeps for push: Firebase's token, notifications shown while the app is in front, and taps.
final class PushAppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Set at launch, so a tap that launched the app is delivered too.
        UNUserNotificationCenter.current().delegate = self
        if PushSetup.available {
            Messaging.messaging().delegate = self
            // The APNs token FCM's token is made from. It needs no permission; showing alerts does.
            application.registerForRemoteNotifications()
        }
        return true
    }

    /// At start and whenever it rotates. Never logged.
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        MainViewControllerKt.onPushToken(token: fcmToken)
    }

    /// In front, iOS shows nothing unless asked: show it as it would in the background, with its sound.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }

    /// A tap: the push's `link` opens in the Home tab, like a Universal Link.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let data = response.notification.request.content.userInfo
        let link = data["link"] as? String
        let type = data["type"] as? String
        DispatchQueue.main.async {
            if link != nil { TabBarModel.shared.showHomeForLink() }
            MainViewControllerKt.onPushOpened(link: link, type: type)
            completionHandler()
        }
    }
}
