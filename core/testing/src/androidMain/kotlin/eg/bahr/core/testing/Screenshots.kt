package eg.bahr.core.testing

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory

/**
 * Captures the whole composition as `<module>/src/androidUnitTest/screenshots/<name>.png`
 * (the module's `roborazzi.outputDir`).
 *
 * Names are explicit, not derived from the test method, so one test can capture a series
 * (`trip_list_loaded_ar`, `trip_list_loaded_en`) and a renamed test does not orphan its golden.
 *
 * Under `testDebugUnitTest` this only writes to the build output; `recordRoborazziDebug`
 * rewrites the goldens and `verifyRoborazziDebug` fails on any pixel difference.
 */
@OptIn(ExperimentalRoborazziApi::class)
fun ComposeContentTestRule.captureScreenshot(name: String) {
    waitForIdle()
    onRoot().captureRoboImage(
        filePath = "${roborazziSystemPropertyOutputDirectory()}/$name.png",
        roborazziOptions = RoborazziOptions(),
    )
}
