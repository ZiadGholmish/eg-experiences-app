package eg.bahr.feature.auth.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eg.bahr.feature.auth.presentation.CompleteProfileSheet
import eg.bahr.feature.auth.presentation.DeleteAccountSheet
import eg.bahr.feature.auth.presentation.SignInButtons

/**
 * "Continue with Apple" (iOS) / "Continue with Google" for another feature's signed-out state (M3-M0b:
 * Profile and the Alerts tab, handoff §2/§3). They sign in where they are drawn; the host screen follows
 * the session itself. As booking's `ContinueBookingSlot`: the host only gives them a place, and
 * `:composeApp` joins the two, so neither feature depends on the other.
 */
@Composable
fun SignInButtonsSlot(modifier: Modifier = Modifier) {
    SignInButtons(modifier = modifier)
}

/**
 * The first-sign-in sheet ("Complete your profile", M3-M0b handoff §2), for the app's root to place once
 * over every screen: a sign-in without a name or phone asks for them here, whichever screen it started
 * from (the sign-in sheet, Profile, the Alerts tab). Draws nothing until then.
 */
@Composable
fun CompleteProfileSheetSlot() {
    CompleteProfileSheet()
}

/**
 * "Delete your account?" as a bottom sheet (M3-M0b), for Profile to place while its Delete account row
 * has it open. [onDismiss] when kept, swiped away, or once the account is deleted (Profile then follows
 * the signed-out session).
 */
@Composable
fun DeleteAccountSheetSlot(onDismiss: () -> Unit) {
    DeleteAccountSheet(onDismiss = onDismiss)
}
