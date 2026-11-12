package eg.bahr.core.catalog.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import eg.bahr.core.catalog.model.HostCardDto
import eg.bahr.core.catalog.model.HostCardRatingDto
import eg.bahr.core.catalog.model.HostKindKey
import eg.bahr.core.designsystem.components.BahrCard
import eg.bahr.core.designsystem.components.BahrSkeletonCard
import eg.bahr.core.designsystem.components.BahrSkeletonCircle
import eg.bahr.core.designsystem.components.BahrSkeletonLine
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.format_pair
import eg.bahr.core.localization.generated.resources.home_rating_count
import eg.bahr.core.localization.generated.resources.host_kind_company
import eg.bahr.core.localization.generated.resources.host_kind_person
import eg.bahr.core.localization.generated.resources.host_rating_average
import eg.bahr.core.localization.generated.resources.host_reviews_count
import eg.bahr.core.localization.generated.resources.trip_host_verified
import eg.bahr.core.localization.generated.resources.trips_count
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/*
 * One host of the hosts directory (MV-M1), in feature:host's list and above feature:search's results. The handoff
 * has no hosts list: the card follows Home's wide host card (MH-M1) and the trip page's host card, with what the
 * directory adds: the kind (host / host company), the published trips and the regions they go to.
 */

/**
 * Avatar at the start (the photo over the initial), then the name with the verified mark, a line with the kind,
 * the rating ("★ 4.8 (12)", only with reviews) and the trips listed, and the regions in one line. Text first:
 * nothing waits on the photo, whose circle keeps its size while it loads.
 */
@Composable
fun HostDirectoryCard(
    host: HostCardDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    BahrCard(modifier = modifier.fillMaxWidth(), onClick = onClick.takeIf { enabled }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(BahrSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
        ) {
            InitialAvatar(name = host.name, tint = Tint.Teal, size = BahrSize.avatar, image = host.avatar?.url)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
                HostName(host)
                HostFacts(host)
                host.governorates
                    .takeIf { it.isNotEmpty() }
                    ?.let { regions ->
                        Text(
                            text = regionsLine(regions.map { it.label }),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
            }
        }
    }
}

/**
 * "Kafr El Sheikh · Beheira": `format_pair` folded over the names, so the separator is the translation's. A few
 * names at most (the governorates a host runs trips to), so the reads stay cheap.
 */
@Composable
private fun regionsLine(labels: List<String>): String {
    var line = labels.first()
    for (index in 1 until labels.size) line = stringResource(Res.string.format_pair, line, labels[index])
    return line
}

/** The name, one line, and the verified mark after it (spoken as "Verified host"). */
@Composable
private fun HostName(host: HostCardDto) {
    Row(
        modifier = Modifier.heightIn(min = BahrSize.iconSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        Text(
            text = host.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (host.verified) {
            Icon(
                imageVector = BahrIcons.Verified.filled(),
                contentDescription = stringResource(Res.string.trip_host_verified),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(BahrSize.iconSmall),
            )
        }
    }
}

/** "Host · ★ 4.8 (12) · 5 trips". The mean and the count are the server's, formatted only. */
@Composable
private fun HostFacts(host: HostCardDto) {
    val kind = stringResource(if (HostKindKey.isCompany(host.kind)) Res.string.host_kind_company else Res.string.host_kind_person)
    val trips = pluralStringResource(Res.plurals.trips_count, host.publishedTrips, host.publishedTrips)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
        Secondary(kind)
        host.rating?.let { HostRating(it) }
        if (host.publishedTrips > 0) Secondary(trips)
    }
}

/** "★ 4.8 (12)", read out once as "Rated 4.8 out of 5 · 12 reviews". */
@Composable
private fun HostRating(rating: HostCardRatingDto) {
    val shown = BahrFormat.rating(rating.value)
    val spoken =
        stringResource(
            Res.string.format_pair,
            stringResource(Res.string.host_rating_average, shown),
            pluralStringResource(Res.plurals.host_reviews_count, rating.count, rating.count),
        )
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        Icon(
            BahrIcons.Star.filled(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(BahrSize.iconSmall),
        )
        Text(text = shown, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        Secondary(stringResource(Res.string.home_rating_count, rating.count))
    }
}

@Composable
private fun Secondary(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/** A [HostDirectoryCard] still loading: the avatar's circle and three lines, at the card's own size. */
@Composable
fun HostDirectoryCardSkeleton(modifier: Modifier = Modifier) {
    BahrSkeletonCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(BahrSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
        ) {
            BahrSkeletonCircle(props = BahrSkeletonCircle.Props(BahrSize.avatar))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
                BahrSkeletonLine(props = BahrSkeletonLine.Props(widthFraction = SKELETON_NAME_WIDTH))
                BahrSkeletonLine(props = BahrSkeletonLine.Props(widthFraction = SKELETON_FACTS_WIDTH))
                BahrSkeletonLine(props = BahrSkeletonLine.Props(widthFraction = SKELETON_REGIONS_WIDTH))
            }
        }
    }
}

// The skeleton's line lengths, as shares of the text column: the name, the facts line, the regions.
private const val SKELETON_NAME_WIDTH = .55f
private const val SKELETON_FACTS_WIDTH = .75f
private const val SKELETON_REGIONS_WIDTH = .4f
