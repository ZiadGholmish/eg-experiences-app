package eg.bahr.feature.splash.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import eg.bahr.feature.splash.presentation.SplashScreen

/** The app's start destination; [onReady] decides where the app goes next. */
fun NavGraphBuilder.splashScreen(onReady: () -> Unit) {
    composable<SplashRoute> {
        SplashScreen(onReady = onReady)
    }
}
