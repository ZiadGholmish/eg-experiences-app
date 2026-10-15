package eg.bahr.feature.auth.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import eg.bahr.core.designsystem.theme.BahrDestination
import eg.bahr.feature.auth.presentation.SignInScreen
import kotlinx.serialization.Serializable

/** The sign-in sheet (M3-M0), pushed over whatever asked for it; the bar hides under it. */
@Serializable
data object SignInRoute

/**
 * The sign-in sheet. [onDone]: signed in, usually back to where it was opened from, which now reads the
 * signed-in session itself (a first sign-in's name and phone are asked in [CompleteProfileSheetSlot]
 * since M3-M0b). [onClose]: left signed out.
 */
fun NavGraphBuilder.signInScreen(
    onDone: () -> Unit,
    onClose: () -> Unit,
) {
    composable<SignInRoute> {
        BahrDestination(this) { SignInScreen(onDone = onDone, onClose = onClose) }
    }
}

fun NavController.navigateToSignIn(builder: NavOptionsBuilder.() -> Unit = { launchSingleTop = true }) = navigate(SignInRoute, builder)
