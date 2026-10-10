package eg.bahr.feature.trips.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import coil3.compose.AsyncImage
import eg.bahr.core.designsystem.components.ImageGround
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrElevation
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrShadow
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_back
import eg.bahr.core.localization.generated.resources.trip_photo_counter
import eg.bahr.feature.trips.model.ImageDto
import org.jetbrains.compose.resources.stringResource

/**
 * The image slider at the top of the trip page (HANDOFF screen 3, item 1): full-bleed, under the
 * status bar, with a top scrim so the bar's icons and the back button read on any photo, and a photo
 * counter at the bottom end. [images] empty (or still loading, [loading]) leaves the reserved ground.
 *
 * Share and favourite are not drawn. Share needs the platform share sheet (an Intent on Android,
 * `UIActivityViewController` on iOS), i.e. an expect/actual this module does not have yet, though
 * `shareUrl` is served since M1-B5; there is no favourites feature.
 */
@Composable
internal fun TripHero(
    images: List<ImageDto>,
    loading: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().aspectRatio(HeroAspectRatio)) {
        if (loading) {
            Skeleton(Modifier.fillMaxSize())
        } else {
            val pager = rememberPagerState { images.size }
            ImageGround(modifier = Modifier.fillMaxSize(), scrimTop = true) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    Photo(images[page])
                }
            }
            if (images.size > 1) {
                Text(
                    // "2 / 6" reads left to right in Arabic too: numbers stay LTR inside RTL.
                    text = BahrFormat.ltr(stringResource(Res.string.trip_photo_counter, pager.currentPage + 1, images.size)),
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
 * LQIP first, then the photo over it; the ground is already painted, so nothing shifts. Also Home's
 * banners.
 */
@Composable
internal fun Photo(image: ImageDto) {
    Box(Modifier.fillMaxSize()) {
        image.lqip?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        AsyncImage(
            model = image.url,
            contentDescription = image.alt,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

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
