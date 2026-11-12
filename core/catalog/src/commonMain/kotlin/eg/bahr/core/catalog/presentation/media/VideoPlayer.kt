package eg.bahr.core.catalog.presentation.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

/*
 * A video in the full-screen viewer (MV-M3, M5-M2's scope): Media3 ExoPlayer on Android, AVPlayer on iOS, behind one
 * player interface and one surface. Google publishes no Kotlin Multiplatform player (Media3 is Android-only AARs, checked
 * on Google Maven 2026-11-12, media3 1.11.1), hence the expect/actual. The controls are drawn once, in common Compose,
 * over [VideoPlayer]; the platform part is only the decoder and the surface, which takes no touches, so a sideways swipe
 * over a playing video still pages and a drag down still closes.
 */

/** What the controls draw. Positions in milliseconds; [durationMs] null until the file says. */
@Immutable
internal data class VideoPlaybackState(
    val playing: Boolean = false,
    val buffering: Boolean = true,
    val positionMs: Long = 0,
    val durationMs: Long? = null,
    val ended: Boolean = false,
    val failed: Boolean = false,
    /** Whether a frame has been drawn: the poster stays over the surface until then. */
    val firstFrame: Boolean = false,
)

/**
 * One video's player, made when the reader taps play and released when its page leaves the composition (swiped away,
 * closed). [poll] reads the position; the viewer calls it on a ticker while the page is shown.
 */
internal interface VideoPlayer {
    val state: StateFlow<VideoPlaybackState>

    /** Plays, with sound; from the start again once it has ended. */
    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    /** After a failure: loads the file again. */
    fun retry()

    fun poll()
}

/** A player for the MP4 at [url], released when it leaves the composition. */
@Composable
internal expect fun rememberVideoPlayer(url: String): VideoPlayer

/** [player]'s picture, filling [modifier]'s box (the caller gives it the video's aspect ratio). Takes no touches. */
@Composable
internal expect fun VideoSurface(
    player: VideoPlayer,
    modifier: Modifier,
)

/**
 * Hides the status and navigation bars while in the composition, and shows them again after (the viewer is full
 * screen). Android: the window's insets controller, a swipe from the edge shows them for a moment. iOS: the SwiftUI app's
 * status bar and home indicator, through `IosMediaChrome` (iosMain), which `:composeApp` wires to Swift.
 */
@Composable
internal expect fun HideSystemBars()
