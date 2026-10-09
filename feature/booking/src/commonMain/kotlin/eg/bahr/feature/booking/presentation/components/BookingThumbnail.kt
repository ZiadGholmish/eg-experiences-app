package eg.bahr.feature.booking.presentation.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import eg.bahr.core.designsystem.components.ImageGround
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.feature.booking.model.BookingImageDto

/**
 * The trip's thumbnail on the held-seats summary and Home's "Continue your booking" card. The size
 * is fixed before anything loads, so the text beside it never waits; the LQIP draws first, the photo
 * over it.
 */
@Composable
internal fun BookingThumbnail(
    image: BookingImageDto?,
    modifier: Modifier = Modifier,
) {
    ImageGround(modifier = modifier.size(BahrSize.thumbnail).clip(BahrTheme.shapes.medium)) {
        image?.lqip?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        image?.let {
            AsyncImage(model = it.url, contentDescription = it.alt, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
