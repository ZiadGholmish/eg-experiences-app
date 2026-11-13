package eg.bahr.feature.tripdetails.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import eg.bahr.core.catalog.presentation.components.Photo
import eg.bahr.core.catalog.presentation.media.ViewerMedia
import eg.bahr.core.catalog.presentation.media.opensMediaViewer
import eg.bahr.core.designsystem.components.BahrSkeletonBlock
import eg.bahr.core.designsystem.components.ImageGround
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrElevation
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrShadow
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_back
import eg.bahr.core.localization.generated.resources.media_video
import eg.bahr.core.localization.generated.resources.trip_photo_counter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The image slider at the top of the trip page (HANDOFF screen 3, item 1): full-bleed, under the
 * status bar, with a top scrim so the bar's icons and the back button read on any photo, and a photo
 * counter at the bottom end. [items] empty leaves the reserved ground, shimmering while the trip [loading].
 *
 * Opened from a list (MV-M7), the card's photo (its `heroImage`, the trip's first gallery item, LQIP first) is the one
 * item until the trip lands, drawn through the same scrim and pager as the loaded gallery: when the trip arrives the
 * pager only gains pages, so the picture, the box and the scrim stay where they are. While [loading] a tap opens nothing:
 * the viewer pages through the whole gallery, which is not known yet.
 *
 * Since MV-M3 the slider is the trip's whole gallery, photos and videos in the server's order (`media`, M5-B4; M5-M2): a
 * video shows as its poster with a play badge and its running time, and never plays here. A tap on any item opens it
 * full screen, in the same order; closing the viewer turns the slider to the item the reader closed on, so the page
 * flies back to it.
 *
 * Share and favourite are not drawn. Share needs the platform share sheet (an Intent on Android,
 * `UIActivityViewController` on iOS), i.e. an expect/actual this module does not have yet, though
 * `shareUrl` is served since M1-B5; there is no favourites feature.
 */
@Composable
internal fun TripHero(
    items: List<ViewerMedia>,
    loading: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().aspectRatio(HeroAspectRatio).testTag(TRIP_HERO_TAG)) {
        if (items.isEmpty() && loading) {
            BahrSkeletonBlock(Modifier.fillMaxSize())
        } else {
            val pager = rememberPagerState { items.size }
            val scope = rememberCoroutineScope()
            val turnTo: (Int) -> Unit = { page -> scope.launch { pager.scrollToPage(page) } }
            ImageGround(modifier = Modifier.fillMaxSize(), scrimTop = true) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    val opens = if (loading) Modifier else Modifier.opensMediaViewer(items, page, onDismiss = turnTo)
                    Box(Modifier.fillMaxSize().then(opens), contentAlignment = Alignment.Center) {
                        when (val item = items[page]) {
                            is ViewerMedia.Photo -> Photo(item.image)
                            is ViewerMedia.Video -> VideoItem(item)
                        }
                    }
                }
            }
            if (items.size > 1) {
                Text(
                    // "2 / 6" reads left to right in Arabic too: numbers stay LTR inside RTL.
                    text = BahrFormat.ltr(stringResource(Res.string.trip_photo_counter, pager.currentPage + 1, items.size)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(BahrSpacing.md)
                            .clip(BahrTheme.shapes.full)
                            .background(BahrTheme.colors.scrim)
                            .padding(horizontal = BahrSpacing.md, vertical = BahrSpacing.xs),
                )
            }
        }
        BackButton(
            onClick = onBack,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(BahrSpacing.md),
        )
    }
}

/**
 * A video in the slider (M5-M2): its poster (LQIP first) cropped as the photos are, a play badge in the middle and the
 * running time under it. Read out as "<alt>, Video, 0:08".
 */
@Composable
private fun BoxScope.VideoItem(item: ViewerMedia.Video) {
    // The contract always sends a poster with a video; without one the ground shows. Its alt is said once, below.
    item.poster?.let { Photo(it.copy(lqip = it.lqip ?: item.lqip, alt = null)) }
    val duration = item.durationSeconds?.let { BahrFormat.duration(it.toLong()) }
    val spoken = listOfNotNull(item.alt, duration?.let { stringResource(Res.string.media_video, it) }).joinToString(", ")
    Column(
        modifier = Modifier.align(Alignment.Center).semantics { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        Box(
            modifier =
                Modifier
                    .size(BahrSize.mediaPlayBadge)
                    .clip(BahrTheme.shapes.full)
                    .background(BahrTheme.colors.scrim),
            contentAlignment = Alignment.Center,
        ) {
            Icon(BahrIcons.PlayArrow.filled(), contentDescription = null, tint = BahrTheme.colors.onMediaGround)
        }
        duration?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = BahrTheme.colors.onMediaGround,
                modifier =
                    Modifier
                        .clip(BahrTheme.shapes.full)
                        .background(BahrTheme.colors.scrim)
                        .padding(horizontal = BahrSpacing.sm, vertical = BahrSpacing.xs)
                        .testTag(TRIP_HERO_VIDEO_DURATION_TAG),
            )
        }
    }
}

/** For tests (MV-M7): the slider's box, whose bounds must not move while the page loads. */
internal const val TRIP_HERO_TAG = "trip_hero"

/** For tests: a video's running time in the slider. */
internal const val TRIP_HERO_VIDEO_DURATION_TAG = "trip_hero_video_duration"

/**
 * The translucent round button over the photo. The handoff draws it at 38px; it is laid out at the
 * 44dp minimum touch target instead (HANDOFF → Accessibility). The arrow mirrors in RTL by itself.
 */
@Composable
private fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = BahrTheme.shapes.full
    Box(
        modifier =
            modifier
                .size(BahrSpacing.minTouch)
                .bahrShadow(BahrElevation.Float, shape, BahrTheme.colors)
                .clip(shape)
                .background(BahrTheme.colors.surfaceTranslucent)
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = BahrIcons.ArrowBack.outlined(),
            contentDescription = stringResource(Res.string.action_back),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The handoff's hero is a 390x330 crop. */
private const val HeroAspectRatio = 390f / 330f
