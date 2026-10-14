package eg.bahr.feature.auth.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import eg.bahr.core.common.auth.DeviceIdSource
import eg.bahr.feature.auth.di.AuthConfig
import eg.bahr.feature.auth.di.NativeSignIn
import eg.bahr.feature.auth.di.NativeSignInCallback
import eg.bahr.feature.auth.model.AuthProvider
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.compose.getKoin
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import kotlin.coroutines.resume

/**
 * iOS offers Apple and Google through the Swift bridge ([NativeSignIn]) when `startApp` was given one;
 * otherwise the fake sign-in where the build allows it (a debug build against the local backend).
 */
@Composable
internal actual fun rememberProviderSignIn(): ProviderSignIn {
    val koin = getKoin()
    return remember(koin) {
        val config: AuthConfig = koin.get()
        val native = koin.getOrNull<NativeSignIn>()
        when {
            native != null -> NativeProviderSignIn(native)
            config.allowFakeSignIn -> FakeProviderSignIn(koin.get<DeviceIdSource>(), listOf(AuthProvider.Apple, AuthProvider.Google))
            else -> NoProviderSignIn
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun sha256Hex(text: String): String {
    val bytes = text.encodeToByteArray()
    return memScoped {
        val digest = allocArray<UByteVar>(CC_SHA256_DIGEST_LENGTH)
        bytes.usePinned { pinned ->
            CC_SHA256(if (bytes.isEmpty()) null else pinned.addressOf(0), bytes.size.toUInt(), digest)
        }
        (0 until CC_SHA256_DIGEST_LENGTH).joinToString("") { digest[it].toString(HEX).padStart(2, '0') }
    }
}

private const val HEX = 16

/** Apple first, as Apple's guidelines ask when it is offered beside another provider. */
private class NativeProviderSignIn(
    private val native: NativeSignIn,
) : ProviderSignIn {
    override val providers: List<AuthProvider> =
        listOfNotNull(AuthProvider.Apple, AuthProvider.Google.takeIf { native.googleAvailable() })

    override suspend fun signIn(
        provider: AuthProvider,
        hashedNonce: String,
    ): ProviderResult =
        suspendCancellableCoroutine { continuation ->
            val callback =
                object : NativeSignInCallback {
                    override fun onSuccess(
                        idToken: String,
                        fullName: String?,
                        authorizationCode: String?,
                    ) = finish(ProviderResult.Success(idToken, fullName?.takeIf { it.isNotBlank() }, authorizationCode))

                    override fun onCancelled() = finish(ProviderResult.Cancelled)

                    override fun onFailure(message: String) = finish(ProviderResult.Failed(message))

                    private fun finish(result: ProviderResult) {
                        if (continuation.isActive) continuation.resume(result)
                    }
                }
            when (provider) {
                AuthProvider.Apple -> native.signInWithApple(hashedNonce, callback)
                AuthProvider.Google -> native.signInWithGoogle(hashedNonce, callback)
            }
        }
}
