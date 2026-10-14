package eg.bahr.feature.auth.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import eg.bahr.core.common.auth.DeviceIdSource
import eg.bahr.feature.auth.di.AuthConfig
import eg.bahr.feature.auth.model.AuthProvider
import org.koin.compose.koinInject
import java.security.MessageDigest

/**
 * Android offers Google only (Apple on Android is not built in M3). With a server client id: Credential
 * Manager's "Sign in with Google" button flow; without one, the fake sign-in where the build allows it,
 * else nothing (the sheet says sign-in is not set up).
 */
@Composable
internal actual fun rememberProviderSignIn(): ProviderSignIn {
    val context = LocalContext.current
    val config: AuthConfig = koinInject()
    val deviceIds: DeviceIdSource = koinInject()
    return remember(context, config, deviceIds) {
        when {
            config.googleServerClientId.isNotBlank() -> CredentialManagerSignIn(context, config.googleServerClientId)
            config.allowFakeSignIn -> FakeProviderSignIn(deviceIds, listOf(AuthProvider.Google))
            else -> NoProviderSignIn
        }
    }
}

internal actual fun sha256Hex(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray()).joinToString("") { byte ->
        (byte.toInt() and BYTE_MASK).toString(HEX).padStart(2, '0')
    }

private const val BYTE_MASK = 0xFF
private const val HEX = 16

/**
 * Sign in with Google through Credential Manager, the button flow (`GetSignInWithGoogleOption`, Google's
 * guidance for an explicit "Continue with Google" tap). [context] must be the Activity: the account
 * picker shows over it.
 */
private class CredentialManagerSignIn(
    private val context: Context,
    private val serverClientId: String,
) : ProviderSignIn {
    override val providers = listOf(AuthProvider.Google)

    override suspend fun signIn(
        provider: AuthProvider,
        hashedNonce: String,
    ): ProviderResult {
        if (provider != AuthProvider.Google) return ProviderResult.Failed("unsupported provider")
        val option = GetSignInWithGoogleOption.Builder(serverClientId).setNonce(hashedNonce).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(context).getCredential(context, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                ProviderResult.Success(idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                ProviderResult.Failed("unexpected credential ${credential.type}")
            }
        } catch (_: GetCredentialCancellationException) {
            ProviderResult.Cancelled
        } catch (e: GetCredentialException) {
            ProviderResult.Failed(e.type)
        } catch (e: GoogleIdTokenParsingException) {
            ProviderResult.Failed(e.message)
        }
    }
}
