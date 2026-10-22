package eg.bahr.feature.home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import eg.bahr.core.catalog.presentation.components.ListHeading
import eg.bahr.core.catalog.presentation.components.TripCardSkeleton
import eg.bahr.core.designsystem.components.BahrSkeleton
import eg.bahr.core.designsystem.components.BahrSkeletonBlock
import eg.bahr.core.designsystem.components.BahrSkeletonLine
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.trips_all_title
import org.jetbrains.compose.resources.stringResource

/**
 * Home while its sections and first page load (M3-M14), under the app bar, search and header, which
 * are already drawn for real. It mirrors the usual loaded Home, top to bottom: the banner carousel
 * (one slide at the handoff's 16:10, in the gutter), a titled row of compact trip cards with the next
 * card peeking in, "All trips" (a fixed label, so drawn as text: the text-first rule) and the list's
 * first card. Sections are server-driven, so this is the common order, not a promise of what comes.
 *
 * Taller than the screen on purpose and clipped at its bottom, as the loaded list is, rather than
 * squeezed to fit.
 */
@Composable
internal fun HomeSkeleton(modifier: Modifier = Modifier) {
    Box(modifier.clipToBounds()) {
        BahrSkeleton(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .padding(top = BahrSpacing.lg),
        ) {
            BahrSkeletonBlock(
                modifier =
                    Modifier
                        .padding(horizontal = BahrSpacing.gutter)
                        .fillMaxWidth()
                        .aspectRatio(FALLBACK_BANNER_RATIO),
                props = BahrSkeletonBlock.Props(shape = BahrTheme.shapes.hero),
            )
            Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
                BahrSkeletonLine(
                    modifier = Modifier.padding(horizontal = BahrSpacing.gutter),
                    props = BahrSkeletonLine.Props(widthFraction = ROW_TITLE_WIDTH),
                )
                // A row as the loaded one is laid out (a lazy row's parent-width share), but still.
                LazyRow(
                    userScrollEnabled = false,
                    contentPadding = PaddingValues(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
                ) {
                    items(ROW_SKELETON_CARDS, contentType = { CONTENT_CARD_SKELETON }) {
                        TripCardSkeleton(modifier = Modifier.fillParentMaxWidth(ROW_ITEM_WIDTH_FRACTION), compact = true)
                    }
                }
            }
            ListHeading(
                label = stringResource(Res.string.trips_all_title),
                totalItems = null,
                countAtEnd = true,
                modifier = Modifier.padding(horizontal = BahrSpacing.gutter),
            )
            TripCardSkeleton(modifier = Modifier.padding(horizontal = BahrSpacing.gutter))
        }
    }
}

/** A row's title line, as a share of the width inside the gutters. */
private const val ROW_TITLE_WIDTH = .45f

/** The row's first card and the one peeking in after it. */
private const val ROW_SKELETON_CARDS = 2

private const val CONTENT_CARD_SKELETON = "card-skeleton"
