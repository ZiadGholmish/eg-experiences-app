import AuthenticationServices
import ComposeApp
import UIKit
#if canImport(GoogleSignIn)
import GoogleSignIn
#endif

/// Sign in with Apple (AuthenticationServices) and Google (Google Sign-In for iOS) behind the shared
/// sign-in sheet (M3-M0). Swift only runs each provider's own UI and reports what it gave back; the
/// shared code makes the nonce (it hands over SHA-256 hex, passed on as it is), talks to the backend and
/// keeps the tokens (`NativeSignIn` in feature:auth). Each call reports exactly once, on the main thread.
final class NativeSignInBridge: NSObject, AuthNativeSignIn {
    /// The Apple request in flight, kept alive until its delegate reports (the controller holds it weakly).
    private var apple: AppleSignInRequest?

    /// The bridge for `startApp`, or nil when the build signs in with the local backend's fake tokens.
    static func bridge() -> AuthNativeSignIn? { fakeSignIn ? nil : NativeSignInBridge() }

    /// Fake sign-in (M3-M0 review #8): a Debug build only, of the Local configuration (`FAKE_SIGN_IN = YES`
    /// is set there and nowhere else), and only while no Google client id is configured: adding real ids
    /// in SignIn.private.xcconfig switches Local to Apple and Google without any other flag. The shared
    /// code checks the debug build again.
    static var fakeSignIn: Bool {
        #if DEBUG
        let local = (Bundle.main.object(forInfoDictionaryKey: "FAKE_SIGN_IN") as? String) == "YES"
        return local && !NativeSignInBridge().googleAvailable()
        #else
        return false
        #endif
    }

    func signInWithApple(hashedNonce: String, callback: AuthNativeSignInCallback) {
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = hashedNonce
        let pending = AppleSignInRequest(callback: callback) { [weak self] in self?.apple = nil }
        apple = pending
        pending.start(request)
    }

    func signInWithGoogle(hashedNonce: String, callback: AuthNativeSignInCallback) {
        #if canImport(GoogleSignIn)
        guard googleAvailable(), let presenter = Self.topViewController() else {
            callback.onFailure(message: "Google Sign-In is not configured")
            return
        }
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter, hint: nil, additionalScopes: nil, nonce: hashedNonce) { result, error in
            if let error {
                if (error as? GIDSignInError)?.code == .canceled {
                    callback.onCancelled()
                } else {
                    callback.onFailure(message: error.localizedDescription)
                }
                return
            }
            guard let token = result?.user.idToken?.tokenString else {
                callback.onFailure(message: "Google returned no ID token")
                return
            }
            callback.onSuccess(idToken: token, fullName: nil, authorizationCode: nil)
        }
        #else
        callback.onFailure(message: "Google Sign-In is not in this build")
        #endif
    }

    /// Google shows only with a client id in Info.plist (GIDClientID, from SignIn.private.xcconfig).
    func googleAvailable() -> Bool {
        #if canImport(GoogleSignIn)
        let id = (Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String ?? "").trimmingCharacters(in: .whitespaces)
        return !id.isEmpty
        #else
        return false
        #endif
    }

    /// Google Sign-In returns to the app on its URL scheme; true when the URL was its.
    static func handle(_ url: URL) -> Bool {
        #if canImport(GoogleSignIn)
        return GIDSignIn.sharedInstance.handle(url)
        #else
        return false
        #endif
    }

    /// The view controller the provider's sheet shows over: the key window's topmost one.
    static func topViewController() -> UIViewController? {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)
        var top = window?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}

/// One Sign in with Apple request and its delegate.
private final class AppleSignInRequest: NSObject, ASAuthorizationControllerDelegate,
    ASAuthorizationControllerPresentationContextProviding {
    private let callback: AuthNativeSignInCallback
    private let finished: () -> Void

    init(callback: AuthNativeSignInCallback, finished: @escaping () -> Void) {
        self.callback = callback
        self.finished = finished
    }

    func start(_ request: ASAuthorizationAppleIDRequest) {
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        controller.performRequests()
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        defer { finished() }
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let tokenData = credential.identityToken,
              let token = String(data: tokenData, encoding: .utf8)
        else {
            callback.onFailure(message: "Apple returned no identity token")
            return
        }
        let code = credential.authorizationCode.flatMap { String(data: $0, encoding: .utf8) }
        // Apple sends the name on the first sign-in only; the backend keeps it if the account has none.
        let name = credential.fullName.map { PersonNameComponentsFormatter().string(from: $0) }
        callback.onSuccess(idToken: token, fullName: name?.isEmpty == false ? name : nil, authorizationCode: code)
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        defer { finished() }
        if (error as? ASAuthorizationError)?.code == .canceled {
            callback.onCancelled()
        } else {
            callback.onFailure(message: error.localizedDescription)
        }
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        NativeSignInBridge.topViewController()?.view.window ?? ASPresentationAnchor()
    }
}
