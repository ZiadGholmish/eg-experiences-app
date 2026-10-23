package eg.bahr.feature.reviews.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eg.bahr.core.designsystem.components.BahrSkeletonCard
import eg.bahr.core.designsystem.components.BahrSkeletonCircle
import eg.bahr.core.designsystem.components.BahrSkeletonLine
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing

/**
 * A [ReviewCard] still loading (M3-M14): the card, the avatar's circle, the name and date line, and
 * two lines of text, at the loaded card's padding and gaps.
 */
@Composable
internal fun ReviewCardSkeleton() {
    BahrSkeletonCard {
        Column(modifier = Modifier.padding(BahrSpacing.lg), verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
                BahrSkeletonCircle(props = BahrSkeletonCircle.Props(size = BahrSize.avatarSmall))
                BahrSkeletonLine(props = BahrSkeletonLine.Props(widthFraction = REVIEW_SKELETON_NAME_WIDTH))
            }
            BahrSkeletonLine()
            BahrSkeletonLine(props = BahrSkeletonLine.Props(widthFraction = REVIEW_SKELETON_LAST_LINE_WIDTH))
        }
    }
}

/** The skeleton's name line (a share of the row after the avatar) and its paragraph's shorter last line. */
private const val REVIEW_SKELETON_NAME_WIDTH = .4f
private const val REVIEW_SKELETON_LAST_LINE_WIDTH = .6f
