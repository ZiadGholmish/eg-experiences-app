package eg.bahr.core.catalog.presentation.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeControlStatus
import platform.CoreGraphics.CGRectZero
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIView

/** AVPlayer over one progressive MP4. AVFoundation has no callbacks Kotlin can take without KVO, so [poll] reads it all. */
@OptIn(ExperimentalForeignApi::class)
private class AvVideoPlayer(
    private val url: String,
) : VideoPlayer {
    val player = AVPlayer(uRL = NSURL(string = url))

    private val mutableState = MutableStateFlow(VideoPlaybackState())
    override val state: StateFlow<VideoPlaybackState> = mutableState

    override fun play() {
        // Sound on, even with the ring/silent switch on silent: the reader tapped play (Apple's category for video).
        runCatching {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
            AVAudioSession.sharedInstance().setActive(true, null)
        }
        if (mutableState.value.ended) seekTo(0)
        player.play()
        mutableState.update { it.copy(ended = false) }
        poll()
    }

    override fun pause() {
        player.pause()
        poll()
    }

    override fun seekTo(positionMs: Long) {
        player.seekToTime(CMTimeMakeWithSeconds(positionMs / MS_PER_SECOND, PREFERRED_TIMESCALE))
    }

    override fun retry() {
        player.replaceCurrentItemWithPlayerItem(AVPlayerItem(uRL = NSURL(string = url)))
        mutableState.update { it.copy(failed = false, buffering = true) }
        player.play()
    }

    override fun poll() {
        val item = player.currentItem
        val position = CMTimeGetSeconds(player.currentTime())
        val duration = item?.let { CMTimeGetSeconds(it.duration) }?.takeIf { it.isFinite() && it > 0 }
        val status = player.timeControlStatus
        val playing = status == AVPlayerTimeControlStatusPlaying
        mutableState.update {
            val positionMs = if (position.isFinite()) (position * MS_PER_SECOND).toLong() else it.positionMs
            val durationMs = duration?.let { d -> (d * MS_PER_SECOND).toLong() } ?: it.durationMs
            it.copy(
                playing = playing,
                buffering = status == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate,
                positionMs = positionMs,
                durationMs = durationMs,
                failed = item?.status == AVPlayerItemStatusFailed,
                // AVPlayer stops at the end with no event: the last position and not playing means it ended.
                ended = !playing && durationMs != null && positionMs >= durationMs - END_TOLERANCE_MS,
                firstFrame = it.firstFrame || positionMs > 0,
            )
        }
    }

    fun release() {
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
    }
}

/** A plain view whose player layer follows its bounds (Compose resizes the view; layers do not resize themselves). */
@OptIn(ExperimentalForeignApi::class)
private class PlayerView(
    player: AVPlayer,
) : UIView(frame = CGRectZero.readValue()) {
    private val playerLayer =
        AVPlayerLayer.playerLayerWithPlayer(player).apply { videoGravity = AVLayerVideoGravityResizeAspect }

    init {
        backgroundColor = UIColor.clearColor
        layer.addSublayer(playerLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        // No implicit animation: the layer must land with the view, not slide after it.
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        bounds.useContents { playerLayer.frame = this.readValue() }
        CATransaction.commit()
    }
}

@Composable
internal actual fun rememberVideoPlayer(url: String): VideoPlayer {
    val player = remember(url) { AvVideoPlayer(url) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

/**
 * Non-interactive (no interaction mode): every touch is Compose's, so the pager pages and the drag down closes over a
 * playing video too. VoiceOver reads the Compose controls; the layer has nothing to say.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun VideoSurface(
    player: VideoPlayer,
    modifier: Modifier,
) {
    val av = (player as AvVideoPlayer).player
    UIKitView(
        factory = { PlayerView(av) },
        modifier = modifier,
        properties = UIKitInteropProperties(interactionMode = null, isNativeAccessibilityEnabled = false),
    )
}

/**
 * Nothing here: on iOS the status bar, home indicator and tab bar are the SwiftUI app's, which the app hides for the tab
 * whose viewer is open from `MediaViewerHost`'s `onOpenChange` (MV-M8; MV-M3 set one flag for every tab from here).
 */
@Composable
internal actual fun HideSystemBars() = Unit

private const val MS_PER_SECOND = 1000.0

/** CMTime's usual timescale for video (600 divides every common frame rate). */
private const val PREFERRED_TIMESCALE = 600

/** AVPlayer may stop a few milliseconds short of the reported duration. */
private const val END_TOLERANCE_MS = 250L
