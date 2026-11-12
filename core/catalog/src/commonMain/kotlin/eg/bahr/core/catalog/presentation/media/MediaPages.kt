package eg.bahr.core.catalog.presentation.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.media_pause
import eg.bahr.core.localization.generated.resources.media_photo_failed
import eg.bahr.core.localization.generated.resources.media_play
import eg.bahr.core.localization.generated.resources.media_position
import eg.bahr.core.localization.generated.resources.media_video
import eg.bahr.core.localization.generated.resources.media_video_failed
import eg.bahr.core.localization.generated.resources.media_zoom
import eg.bahr.core.localization.generated.resources.media_zoom_out
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jetbrains.compose.resources.stringResource

/*
 * The viewer's two kinds of page (MV-M3): a photo that zooms and pans, and a video that shows its poster until the
 * reader taps play. Videos never play on their own, on Wi-Fi or mobile data alike (the plan's "no autoplay on
 * cellular", taken all the way: no connectivity check, and nothing is downloaded before the tap).
 */

/**
 * A photo, fitted to the screen: its LQIP (or the thumbnail already in memory) at once, then the original, decoded up to
 * [MAX_DECODE_PX] so a 4× zoom stays sharp. Pinch or double-tap to zoom, drag to pan while zoomed; TalkBack and VoiceOver
 * read its alt text and can zoom with one action, though nothing needs zooming to be seen. A photo that fails says so,
 * with a retry, on its own page only.
 */
@Composable
internal fun ZoomablePhoto(
    item: ViewerMedia.Photo,
    state: MediaViewerState,
    current: Boolean,
    flight: Modifier = Modifier,
) {
    var viewport by remember { mutableStateOf(Size.Zero) }
    // A retry is a fresh request; the attempt is part of its identity.
    var attempt by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf(PhotoStatus.Loading) }
    val zoomLabel = stringResource(if (current && state.zoomed) Res.string.media_zoom_out else Res.string.media_zoom)
    val transformable =
        rememberTransformableState { centroid, zoomChange, panChange, _ ->
            if (current) state.transform(zoomChange, panChange, viewport, centroid)
        }
    val context = LocalPlatformContext.current
    val url = item.image.url
    val request =
        remember(url, attempt, context) {
            ImageRequest
                .Builder(context)
                .data(url)
                .size(MAX_DECODE_PX)
                // The thumbnail the reader tapped is the same URL, likely still in memory: shown until the original lands.
                .placeholderMemoryCacheKey(url)
                .build()
        }
    val alt = item.alt
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it.toSize() }
                // One finger pans only while zoomed; otherwise it is the pager's (sideways) or the dismiss drag's (down).
                .transformable(transformable, canPan = { current && state.zoomed })
                .pointerInput(current) {
                    if (current) detectTapGestures(onDoubleTap = { state.toggleZoom(it, viewport) })
                }
                // Zoom is a custom action, not the photo's click: TalkBack's double tap must not zoom (and so stop the
                // pager's paging) by accident. Nothing needs zooming to be seen.
                .semantics {
                    alt?.let { contentDescription = it }
                    customActions =
                        listOf(
                            CustomAccessibilityAction(zoomLabel) {
                                state.toggleZoom(Offset(viewport.width / 2, viewport.height / 2), viewport)
                                true
                            },
                        )
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(flight)
                    // Read in the draw phase: a pinch redraws the photo without recomposing the page.
                    .graphicsLayer {
                        if (current) {
                            scaleX = state.zoom
                            scaleY = state.zoom
                            translationX = state.pan.x
                            translationY = state.pan.y
                        }
                    },
        ) {
            item.image.lqip?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onState = { status = it.toPhotoStatus() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        when (status) {
            PhotoStatus.Loading -> if (item.image.lqip == null) Spinner()
            PhotoStatus.Failed -> MediaFailed(stringResource(Res.string.media_photo_failed)) { attempt++ }
            PhotoStatus.Loaded -> Unit
        }
    }
}

private enum class PhotoStatus { Loading, Loaded, Failed }

private fun AsyncImagePainter.State.toPhotoStatus(): PhotoStatus =
    when (this) {
        is AsyncImagePainter.State.Success -> PhotoStatus.Loaded
        is AsyncImagePainter.State.Error -> PhotoStatus.Failed
        else -> PhotoStatus.Loading
    }

/**
 * A video page: its poster with a play button and the running time until the reader taps play, then the player with its
 * controls. Leaving the page (a swipe, the viewer closing) releases the player, which stops it; going to the background
 * pauses it. Sound is on: the reader asked for it with the tap.
 */
@Composable
internal fun VideoPage(
    item: ViewerMedia.Video,
    current: Boolean,
    shown: Boolean,
    flight: Modifier = Modifier,
) {
    var started by rememberSaveable(item.url) { mutableStateOf(false) }
    LaunchedEffect(current, shown) { if (!current || !shown) started = false }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (started && current && shown) {
            VideoPlayback(item)
        } else {
            VideoPoster(item, flight)
            PlayButton(item) { started = true }
        }
    }
}

/** The poster (LQIP first) in the video's own proportions, centred. */
@Composable
private fun BoxScope.VideoPoster(
    item: ViewerMedia.Video,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .aspectRatio(item.aspectRatio, matchHeightConstraintsFirst = item.aspectRatio < 1f),
    ) {
        item.lqip?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        item.poster?.let {
            AsyncImage(model = it.url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
}

/** The round play button over the poster, with the running time under it; read as "Video, 0:08", then "Play video". */
@Composable
private fun BoxScope.PlayButton(
    item: ViewerMedia.Video,
    onPlay: () -> Unit,
) {
    val onGround = BahrTheme.colors.onMediaGround
    val duration = item.durationSeconds?.let { BahrFormat.duration(it.toLong()) }
    val spoken = listOfNotNull(item.alt, duration?.let { stringResource(Res.string.media_video, it) }).joinToString(", ")
    val playLabel = stringResource(Res.string.media_play)
    Column(
        modifier =
            Modifier
                .align(Alignment.Center)
                .testTag(MEDIA_PLAY_TAG)
                .clearAndSetSemantics {
                    contentDescription = spoken
                    onClick(label = playLabel) {
                        onPlay()
                        true
                    }
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        Box(
            modifier =
                Modifier
                    .size(BahrSize.mediaPlayButton)
                    .clip(BahrTheme.shapes.full)
                    .background(BahrTheme.colors.scrim)
                    .clickable(role = Role.Button, onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(BahrIcons.PlayArrow.filled(), contentDescription = null, tint = onGround, modifier = Modifier.size(BahrSize.iconState))
        }
        duration?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = onGround,
                modifier =
                    Modifier
                        .clip(BahrTheme.shapes.full)
                        .background(BahrTheme.colors.scrim)
                        .padding(horizontal = BahrSpacing.md, vertical = BahrSpacing.xs),
            )
        }
    }
}

/** The player: its picture, the poster until the first frame, a spinner while it buffers, the controls at the bottom. */
@Composable
private fun BoxScope.VideoPlayback(item: ViewerMedia.Video) {
    val player = rememberVideoPlayer(item.url)
    val state by player.state.collectAsStateWithLifecycle()
    // The tap that got here was "play"; then the position is read on a ticker while the page is shown.
    LaunchedEffect(player) {
        player.play()
        while (isActive) {
            player.poll()
            delay(POSITION_POLL_MS)
        }
    }
    // The app going to the background (or a call) pauses it; coming back leaves it paused.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }

    Box(
        modifier =
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .aspectRatio(item.aspectRatio, matchHeightConstraintsFirst = item.aspectRatio < 1f),
    ) {
        VideoSurface(player, Modifier.fillMaxSize())
        if (!state.firstFrame) VideoPosterLayer(item)
    }
    when {
        state.failed -> MediaFailed(stringResource(Res.string.media_video_failed), onRetry = player::retry)
        state.buffering && !state.playing -> Spinner()
    }
    VideoControls(
        state = state,
        onToggle = { if (state.playing) player.pause() else player.play() },
        onSeek = player::seekTo,
        modifier = Modifier.align(Alignment.BottomCenter),
    )
}

@Composable
private fun VideoPosterLayer(item: ViewerMedia.Video) {
    Box(Modifier.fillMaxSize()) {
        item.lqip?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        item.poster?.let {
            AsyncImage(model = it.url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * Play/pause, the position, the timeline and the running time. Left to right in Arabic too: a timeline is not mirrored
 * (Material's bidirectionality guidance for media), and the times are numbers.
 */
@Composable
private fun VideoControls(
    state: VideoPlaybackState,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onGround = BahrTheme.colors.onMediaGround
    val duration = state.durationMs
    // While the thumb is dragged it shows where it is, not where the video is.
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val positionLabel = stringResource(Res.string.media_position)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .background(BahrTheme.colors.scrim)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(horizontal = BahrSpacing.md, vertical = BahrSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(BahrSpacing.minTouch)
                        .clip(BahrTheme.shapes.full)
                        .clickable(role = Role.Button, onClick = onToggle)
                        .testTag(MEDIA_PLAY_PAUSE_TAG),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (state.playing) BahrIcons.Pause.filled() else BahrIcons.PlayArrow.filled(),
                    contentDescription = stringResource(if (state.playing) Res.string.media_pause else Res.string.media_play),
                    tint = onGround,
                )
            }
            val shownMs = scrubbing?.let { (it * (duration ?: 0)).toLong() } ?: state.positionMs
            TimeText(BahrFormat.duration(shownMs / MS_PER_SECOND))
            Slider(
                value = scrubbing ?: duration?.takeIf { it > 0 }?.let { (state.positionMs.toFloat() / it).coerceIn(0f, 1f) } ?: 0f,
                onValueChange = { scrubbing = it },
                onValueChangeFinished = {
                    val to = scrubbing
                    if (to != null && duration != null) onSeek((to * duration).toLong())
                    scrubbing = null
                },
                enabled = duration != null && duration > 0,
                colors =
                    SliderDefaults.colors(
                        thumbColor = onGround,
                        activeTrackColor = onGround,
                        inactiveTrackColor = onGround.copy(alpha = INACTIVE_TRACK_ALPHA),
                    ),
                modifier = Modifier.weight(1f).semantics { contentDescription = positionLabel },
            )
            TimeText(duration?.let { BahrFormat.duration(it / MS_PER_SECOND) }.orEmpty())
        }
    }
}

@Composable
private fun TimeText(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelMedium, color = BahrTheme.colors.onMediaGround)
}

@Composable
private fun BoxScope.Spinner() {
    CircularProgressIndicator(color = BahrTheme.colors.onMediaGround, modifier = Modifier.align(Alignment.Center))
}

/** An item that would not load: says so, with a retry, on its page only (the others page as before). */
@Composable
private fun BoxScope.MediaFailed(
    message: String,
    onRetry: () -> Unit,
) {
    val onGround = BahrTheme.colors.onMediaGround
    Column(
        modifier =
            Modifier
                .align(Alignment.Center)
                .clip(BahrTheme.shapes.large)
                .background(BahrTheme.colors.scrim)
                .padding(BahrSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge, color = onGround, textAlign = TextAlign.Center)
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = BahrSpacing.minTouch).testTag(MEDIA_RETRY_TAG)) {
            Text(text = stringResource(Res.string.action_retry), style = MaterialTheme.typography.labelLarge, color = onGround)
        }
    }
}

/**
 * The longest side a photo is decoded at: past a phone screen's (about 2400 px) so a zoom has pixels to show, but a
 * 50-megapixel original is not held whole in memory.
 */
private const val MAX_DECODE_PX = 3072

/** How often the timeline is moved on: often enough to look continuous at a second's resolution. */
private const val POSITION_POLL_MS = 250L

private const val MS_PER_SECOND = 1000L

/** The unplayed part of the timeline, a dimmer white on the scrim. */
private const val INACTIVE_TRACK_ALPHA = 0.38f

internal const val MEDIA_PLAY_TAG = "media_play"
internal const val MEDIA_PLAY_PAUSE_TAG = "media_play_pause"
internal const val MEDIA_RETRY_TAG = "media_retry"
