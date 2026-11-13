package eg.bahr.core.catalog.presentation.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.IntOffset
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrSharedElement
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.media_close
import eg.bahr.core.localization.generated.resources.media_counter_spoken
import eg.bahr.core.localization.generated.resources.media_open
import eg.bahr.core.localization.generated.resources.trip_photo_counter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The full-screen photo and video viewer (MV-M3; M5-M2's playback). One for the app, drawn by [MediaViewerHost] over the
 * screens under it, opened through [LocalMediaViewer] with [opensMediaViewer]. The handoff has no viewer, so the
 * arrangement is the platforms' usual one:
 *
 *   ┌────────────────────────────────┐
 *   │ (x)                    3 / 8   │  close at the start, counter at the end
 *   │                                │
 *   │          photo / video         │  pager, server order, starts on the tapped item
 *   │                                │
 *   │  ▶  0:03 ━━━━━━○──────── 0:08  │  a video's controls (left to right in both languages)
 *   └────────────────────────────────┘
 */

/**
 * Hosts the viewer over [content] (the app's NavHost): provides [LocalMediaViewer] and, while a gallery is open, draws the
 * viewer on top, takes Back to close it, and hides what is under it from TalkBack and VoiceOver.
 *
 * [onOpenChange] hears true when the viewer appears and false once it has faded out (or left the composition), so the app
 * can hide its bars for this host's tab only (MV-M8; on iOS the tab bar and status bar are the SwiftUI app's).
 */
@Composable
fun MediaViewerHost(
    onOpenChange: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val controller = remember { MediaViewerController() }
    CompositionLocalProvider(LocalMediaViewer provides controller) {
        val session = controller.session
        Box(Modifier.fillMaxSize()) {
            // While the viewer is open the page under it is not there for a screen reader: focus stays in the viewer.
            Box(Modifier.fillMaxSize().then(if (session != null) Modifier.clearAndSetSemantics {} else Modifier)) { content() }
            if (session != null) ViewerOverlay(controller, session, onOpenChange)
        }
    }
}

@Composable
private fun ViewerOverlay(
    controller: MediaViewerController,
    session: MediaViewerState,
    onOpenChange: (Boolean) -> Unit,
) {
    // Starts hidden and is told to show, so the viewer fades in rather than appearing at once.
    val visibility = remember(session) { MutableTransitionState(false) }
    val shown = controller.shown
    SideEffect(shown) { visibility.targetState = shown }
    val closed = !shown && visibility.isIdle && !visibility.currentState
    LaunchedEffect(closed) { if (closed) controller.onClosed() }

    // Back closes the viewer, not the page under it: at the overlay priority, which the dispatcher asks before the
    // NavHost's default-priority handler (a plain NavigationBackHandler here lost to it on a device). Enabled during the
    // close fade too, so a second Back then does not pop the page.
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    val close by rememberUpdatedState(controller::close)
    DisposableEffect(dispatcher) {
        val handler =
            object : NavigationEventHandler<NavigationEventInfo>(NavigationEventInfo.None, isBackEnabled = true) {
                override fun onBackCompleted() = close()
            }
        dispatcher?.addHandler(handler, NavigationEventDispatcher.PRIORITY_OVERLAY)
        onDispose { handler.remove() }
    }

    // A fade even under reduce motion: it moves nothing. The photo itself flies from its thumbnail (bahrSharedElement)
    // unless motion is reduced.
    AnimatedVisibility(
        visibleState = visibility,
        enter = fadeIn(tween(BahrMotion.Medium, easing = BahrMotion.Standard)),
        exit = fadeOut(tween(BahrMotion.Medium, easing = BahrMotion.Standard)),
    ) {
        HideSystemBars()
        val reportOpen by rememberUpdatedState(onOpenChange)
        DisposableEffect(Unit) {
            reportOpen(true)
            onDispose { reportOpen(false) }
        }
        MediaViewer(state = session, shown = shown, onClose = controller::close, flightKey = controller.flightKey)
    }
}

/**
 * The viewer itself: black ground, the pager, close and the counter. [shown] false while it closes (a video stops, and
 * the page of [flightKey], if any, flies back to its thumbnail). Internal for the screenshot tests, which draw it without
 * a host.
 */
@Composable
internal fun MediaViewer(
    state: MediaViewerState,
    shown: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    flightKey: String? = null,
) {
    val pager = rememberPagerState(initialPage = state.startPage) { state.count }
    LaunchedEffect(pager, state) { snapshotFlow { pager.settledPage }.collect(state::onPageSettled) }

    val ground = BahrTheme.colors.mediaGround
    val density = LocalDensity.current
    val dismissVelocity = with(density) { BahrMotion.DismissFlingVelocity.toPx() }
    val scope = rememberCoroutineScope()
    // The drag down that closes: the page follows the finger and the ground thins as it goes.
    var drag by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { delta -> drag += delta }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged { height = it.height.toFloat() }
                .drawBehind {
                    val gone = if (height > 0) (abs(drag) / (height * DISMISS_FRACTION)).coerceIn(0f, 1f) else 0f
                    drawRect(ground.copy(alpha = 1f - gone * DRAG_GROUND_FADE))
                }.testTag(MEDIA_VIEWER_TAG),
    ) {
        HorizontalPager(
            state = pager,
            // Paging only when not zoomed: then a sideways drag pans the photo instead.
            userScrollEnabled = state.pagingEnabled,
            beyondViewportPageCount = 1,
            modifier =
                Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, drag.roundToInt()) }
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        enabled = state.dismissEnabled,
                        onDragStopped = { velocity ->
                            if (abs(drag) > height * DISMISS_FRACTION || abs(velocity) > dismissVelocity) {
                                onClose()
                            } else {
                                scope.launch { animate(drag, 0f, animationSpec = tween(BahrMotion.Short)) { value, _ -> drag = value } }
                            }
                        },
                    ),
        ) { page ->
            val current = page == state.page
            val item = state.items[page]
            // Only the item in flight is a shared element; every other page is drawn as it is.
            val flight = if (item.key == flightKey) Modifier.bahrSharedElement(mediaSharedKey(item), visible = shown) else Modifier
            when (item) {
                is ViewerMedia.Photo -> ZoomablePhoto(item, state, current = current, flight = flight)
                is ViewerMedia.Video -> VideoPage(item, current = current, shown = shown, flight = flight)
            }
        }
        ViewerTopBar(state, onClose)
    }
}

/** Close at the start, "3 / 8" at the end (only for more than one item), clear of the notch and the corners. */
@Composable
private fun ViewerTopBar(
    state: MediaViewerState,
    onClose: () -> Unit,
) {
    val onGround = BahrTheme.colors.onMediaGround
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(BahrSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        Box(
            modifier =
                Modifier
                    .size(BahrSpacing.minTouch)
                    .clip(BahrTheme.shapes.full)
                    .background(BahrTheme.colors.scrim)
                    .clickable(role = Role.Button, onClick = onClose)
                    .testTag(MEDIA_VIEWER_CLOSE_TAG),
            contentAlignment = Alignment.Center,
        ) {
            Icon(BahrIcons.Close.outlined(), contentDescription = stringResource(Res.string.media_close), tint = onGround)
        }
        Spacer(Modifier.weight(1f))
        if (state.count > 1) {
            val spoken = stringResource(Res.string.media_counter_spoken, state.position, state.count)
            Text(
                // "3 / 8" reads left to right in Arabic too: numbers stay LTR inside RTL.
                text = BahrFormat.ltr(stringResource(Res.string.trip_photo_counter, state.position, state.count)),
                style = MaterialTheme.typography.labelLarge,
                color = onGround,
                modifier =
                    Modifier
                        .clip(BahrTheme.shapes.full)
                        .background(BahrTheme.colors.scrim)
                        .padding(horizontal = BahrSpacing.md, vertical = BahrSpacing.xs)
                        .testTag(MEDIA_VIEWER_COUNTER_TAG)
                        // "3 of 8", said again each time the page changes.
                        .clearAndSetSemantics {
                            contentDescription = spoken
                            liveRegion = LiveRegionMode.Polite
                        },
            )
        }
    }
}

/**
 * Makes this thumbnail open [items] full screen on [index] (MV-M3): a tap, or TalkBack's and VoiceOver's activate ("View
 * full screen"); and the thumbnail is where the page flies from and back to. Put it before a `clearAndSetSemantics` in
 * the chain, so the action is kept. [onDismiss] hears the page the reader closed on (see [MediaViewerController.open]).
 * Nothing outside a [MediaViewerHost].
 */
@Composable
fun Modifier.opensMediaViewer(
    items: List<ViewerMedia>,
    index: Int,
    onDismiss: ((page: Int) -> Unit)? = null,
): Modifier {
    val viewer = LocalMediaViewer.current ?: return this
    val item = items.getOrNull(index) ?: return this
    val label = stringResource(Res.string.media_open)
    return this
        .bahrSharedElement(mediaSharedKey(item), visible = !viewer.hidesThumbnail(item.key))
        .clickable(onClickLabel = label, role = Role.Image) { viewer.open(items, index, onDismiss) }
}

/** A drag past this share of the screen's height closes the viewer. */
private const val DISMISS_FRACTION = 0.18f

/** How far the black thins as the page is dragged away (to 40%). */
private const val DRAG_GROUND_FADE = 0.6f

internal const val MEDIA_VIEWER_TAG = "media_viewer"
internal const val MEDIA_VIEWER_CLOSE_TAG = "media_viewer_close"
internal const val MEDIA_VIEWER_COUNTER_TAG = "media_viewer_counter"
