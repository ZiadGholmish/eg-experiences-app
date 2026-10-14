package eg.bahr.feature.auth.di

/**
 * Sign in with Apple (AuthenticationServices) and Google (Google Sign-In for iOS), implemented in Swift
 * (`iosApp/iosApp/NativeSignIn.swift`) and handed to `startApp` (M3-M0). Public for Swift, like
 * feature:map's `TripMapNativeViews` (M4-M2): `MainViewController` names it, which puts it in the
 * framework header without exporting the feature.
 *
 * Swift only runs the provider's own UI and reports what it gave back; Kotlin makes the nonce, talks
 * to our backend and keeps the tokens. Each call reports exactly once to [callback], on the main thread.
 */
interface NativeSignIn {
    /** [hashedNonce] goes to `ASAuthorizationAppleIDRequest.nonce` as it is (already SHA-256 hex). */
    fun signInWithApple(
        hashedNonce: String,
        callback: NativeSignInCallback,
    )

    /** [hashedNonce] goes to `GIDSignIn.signIn(…, nonce:)`. */
    fun signInWithGoogle(
        hashedNonce: String,
        callback: NativeSignInCallback,
    )

    /** Whether Google Sign-In is configured (a GIDClientID in Info.plist); without it only Apple shows. */
    fun googleAvailable(): Boolean
}

/** How a native sign-in ended. Implemented in Kotlin; Swift calls one of these once. */
interface NativeSignInCallback {
    /** [fullName]: Apple's, first sign-in only. [authorizationCode]: Apple's, for `deleteMe`. */
    fun onSuccess(
        idToken: String,
        fullName: String?,
        authorizationCode: String?,
    )

    fun onCancelled()

    fun onFailure(message: String)
}
