package eg.bahr.feature.auth.di

/**
 * Sign-in settings only the app module knows (its build config).
 *
 * [googleServerClientId] is the Google "web" (server) OAuth client id that Android's Credential Manager
 * puts in the ID token's `aud`, the same value as the backend's `AUTH_GOOGLE_CLIENT_IDS` entry. Empty in
 * a build without one. iOS reads its own Google client id from Info.plist (Google Sign-In for iOS).
 *
 * [allowFakeSignIn]: with no real provider configured, sign in with made-up tokens the `local`
 * backend's fake verifier accepts (M3-B1). The local flavor / a debug iOS build only.
 */
data class AuthConfig(
    val googleServerClientId: String,
    val allowFakeSignIn: Boolean,
)
