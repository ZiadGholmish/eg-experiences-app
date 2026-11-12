package eg.bahr.core.catalog.presentation.media

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Media3 ExoPlayer over one progressive MP4 (M5-B4 serves H.264/AAC, no HLS). */
private class ExoVideoPlayer(
    context: Context,
    url: String,
) : VideoPlayer {
    val exo: ExoPlayer =
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
        }

    private val mutableState = MutableStateFlow(VideoPlaybackState())
    override val state: StateFlow<VideoPlaybackState> = mutableState

    private val listener =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = mutableState.update { it.copy(playing = isPlaying) }

            override fun onPlaybackStateChanged(playbackState: Int) =
                mutableState.update {
                    it.copy(
                        buffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE,
                        ended = playbackState == Player.STATE_ENDED,
                    )
                }

            override fun onPlayerError(error: PlaybackException) = mutableState.update { it.copy(failed = true, playing = false) }

            override fun onRenderedFirstFrame() = mutableState.update { it.copy(firstFrame = true) }
        }

    init {
        exo.addListener(listener)
    }

    override fun play() {
        if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0)
        exo.play()
    }

    override fun pause() = exo.pause()

    override fun seekTo(positionMs: Long) = exo.seekTo(positionMs)

    override fun retry() {
        mutableState.update { it.copy(failed = false, buffering = true) }
        exo.prepare()
        exo.play()
    }

    override fun poll() {
        val duration = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        mutableState.update { it.copy(positionMs = exo.currentPosition, durationMs = duration ?: it.durationMs) }
    }

    fun release() {
        exo.removeListener(listener)
        exo.release()
    }
}

@Composable
internal actual fun rememberVideoPlayer(url: String): VideoPlayer {
    val context = LocalContext.current.applicationContext
    val player = remember(url, context) { ExoVideoPlayer(context, url) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

/** A TextureView, not a SurfaceView: it follows the page's offset and alpha (the drag down, the fade). */
@OptIn(UnstableApi::class)
@Composable
internal actual fun VideoSurface(
    player: VideoPlayer,
    modifier: Modifier,
) {
    val exo = (player as ExoVideoPlayer).exo
    PlayerSurface(player = exo, modifier = modifier, surfaceType = SURFACE_TYPE_TEXTURE_VIEW)
}

@Composable
internal actual fun HideSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
