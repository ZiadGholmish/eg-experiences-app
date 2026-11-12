package eg.bahr.core.catalog.presentation.media

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import coil3.ColorImage
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.compose.asPainter
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import eg.bahr.core.catalog.model.ImageDto
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.captureScreenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The full-screen viewer (MV-M3) × {ar, en} × {photo, zoomed, video poster, error}. Coil's preview handler stands in for
 * the network (inspection mode): a photo is a flat landscape block, so the fit, the zoom and the black around it show;
 * the error shot answers every request with a failure. The video is shown before play, so no player is made.
 *
 * Record: `./gradlew :core:catalog:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp-xhdpi")
class MediaViewerScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun photoArabic() = snap("media_viewer_photo", AppLanguage.ARABIC, MediaViewerState(gallery, startIndex = 2))

    @Test
    fun photoEnglish() = snap("media_viewer_photo", AppLanguage.ENGLISH, MediaViewerState(gallery, startIndex = 2))

    @Test
    fun zoomedArabic() = snap("media_viewer_zoomed", AppLanguage.ARABIC, MediaViewerState(gallery, startIndex = 0, initialZoom = 2f))

    @Test
    fun zoomedEnglish() = snap("media_viewer_zoomed", AppLanguage.ENGLISH, MediaViewerState(gallery, startIndex = 0, initialZoom = 2f))

    @Test
    fun videoPosterArabic() = snap("media_viewer_video_poster", AppLanguage.ARABIC, MediaViewerState(gallery, startIndex = 1))

    @Test
    fun videoPosterEnglish() = snap("media_viewer_video_poster", AppLanguage.ENGLISH, MediaViewerState(gallery, startIndex = 1))

    @Test
    fun errorArabic() = snap("media_viewer_error", AppLanguage.ARABIC, MediaViewerState(gallery, startIndex = 0), failing = true)

    @Test
    fun errorEnglish() = snap("media_viewer_error", AppLanguage.ENGLISH, MediaViewerState(gallery, startIndex = 0), failing = true)

    private fun snap(
        name: String,
        language: AppLanguage,
        state: MediaViewerState,
        failing: Boolean = false,
    ) {
        val images =
            AsyncImagePreviewHandler { _, request ->
                if (failing) {
                    AsyncImagePainter.State.Error(null, ErrorResult(null, request, IllegalStateException("offline")))
                } else {
                    val image = ColorImage(PHOTO_COLOUR, PHOTO_WIDTH, PHOTO_HEIGHT)
                    AsyncImagePainter.State.Success(image.asPainter(request.context), SuccessResult(image, request))
                }
            }
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    CompositionLocalProvider(LocalInspectionMode provides true, LocalAsyncImagePreviewHandler provides images) {
                        MediaViewer(state = state, shown = true, onClose = {})
                    }
                }
            }
        }
        compose.captureScreenshot("${name}_${language.tag}")
    }

    private companion object {
        /** A muted teal block, 900×560 like the seeded trip photos. */
        const val PHOTO_COLOUR = 0xFF3F7F86.toInt()
        const val PHOTO_WIDTH = 900
        const val PHOTO_HEIGHT = 560

        val gallery =
            listOf(
                ViewerMedia.Photo(ImageDto(url = "http://127.0.0.1:9/boats.png", width = 900, height = 560, alt = "Fishing boats")),
                ViewerMedia.Video(
                    url = "http://127.0.0.1:9/dawn.mp4",
                    width = 640,
                    height = 360,
                    alt = "Video: dawn on the lake",
                    durationSeconds = 8,
                    poster = ImageDto(url = "http://127.0.0.1:9/poster.png", width = 900, height = 560),
                ),
                ViewerMedia.Photo(ImageDto(url = "http://127.0.0.1:9/birds.png", width = 900, height = 560)),
            )
    }
}
