package eg.bahr.feature.trips.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import eg.bahr.core.catalog.presentation.components.SectionTitle
import eg.bahr.core.catalog.presentation.components.Tint
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrSharedBounds
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.format_pair
import eg.bahr.core.localization.generated.resources.host_kind_company
import eg.bahr.core.localization.generated.resources.host_kind_person
import eg.bahr.core.localization.generated.resources.host_member_since
import eg.bahr.core.localization.generated.resources.host_no_reviews
import eg.bahr.core.localization.generated.resources.host_rating_average
import eg.bahr.core.localization.generated.resources.host_rating_bar
import eg.bahr.core.localization.generated.resources.host_reviews_count
import eg.bahr.core.localization.generated.resources.trip_host_trips_run
import eg.bahr.core.localization.generated.resources.trip_host_verified
import eg.bahr.core.localization.generated.resources.trip_reviews
import eg.bahr.feature.trips.model.HostKind
import eg.bahr.feature.trips.model.HostProfileDto
import eg.bahr.feature.trips.model.RatingSummaryDto
import eg.bahr.feature.trips.model.StarCountDto
import eg.bahr.feature.trips.model.stars
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/*
 * The host page's own parts (M4-M10). The handoff has no host page: these follow the trip page's host
 * card (avatar, name, verified mark) and reviews, so the card reads as growing into the page.
 */

/**
 * The host page's header: photo, name with the verified mark and role (the part the trip page's host
 * row morphs into, keyed [hostSharedKey]), then "Host company · Member since Sep 2024 · 11 trips run"
 * and the bio. [name] and [avatar] are there before [profile] (from the card the user tapped), so the
 * text shows first; the rest shimmers until the profile answers.
 */
@Composable
internal fun HostHeader(
    slug: String,
    name: String?,
    avatar: String?,
    profile: HostProfileDto?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth().bahrSharedBounds(hostSharedKey(slug)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BahrSpacing.lg),
        ) {
            InitialAvatar(name = name, tint = Tint.Teal, size = BahrSize.avatarLarge, image = avatar)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
                    name?.let {
                        Text(
                            text = it,
                            // A Latin name inside an Arabic page reads in its own direction (see TripStory).
                            style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content),
                            modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                        )
                    }
                    if (profile?.verified == true) {
                        Icon(
                            imageVector = BahrIcons.Verified.filled(),
                            contentDescription = stringResource(Res.string.trip_host_verified),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(BahrSize.iconMedium),
                        )
                    }
                }
                profile?.role?.let {
                    Text(text = it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (profile == null) {
            TextSkeleton(lines = HEADER_SKELETON_LINES)
        } else {
            Text(
                text = profile.facts(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            profile.bio?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = BahrTheme.colors.onSurfaceSecondary)
            }
        }
    }
}

/**
 * "Host company · Member since Sep 2024 · 11 trips run". [HostProfileDto.tripsRun] is the host's track
 * record (every trip they have run), not the number of trips listed under the header, so it keeps the
 * trip card's "trips run" wording and the list has its own count. Left out at 0.
 */
@Composable
private fun HostProfileDto.facts(): String {
    val kindLabel = stringResource(if (HostKind.isCompany(kind)) Res.string.host_kind_company else Res.string.host_kind_person)
    val since = memberSince?.let { stringResource(Res.string.host_member_since, BahrFormat.monthYear(it)) }
    // A host who has not run a trip yet (just approved) has no track record to state.
    val run = tripsRun.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.trip_host_trips_run, it, it) }
    return listOfNotNull(kindLabel, since, run).reduce { line, part -> stringResource(Res.string.format_pair, line, part) }
}

/**
 * The rating summary: the average large, with how many reviews it is over, beside five bars from 5
 * stars down to 1, each as long as its share of the reviews. With no reviews (count 0, no average),
 * one line says so.
 */
@Composable
internal fun HostRating(
    rating: RatingSummaryDto,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
        SectionTitle(stringResource(Res.string.trip_reviews), Modifier.semantics { heading() })
        val average = rating.average
        if (rating.count <= 0 || average == null) {
            Text(
                text = stringResource(Res.string.host_no_reviews),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xl),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
                val shown = BahrFormat.rating(average)
                val spoken = stringResource(Res.string.host_rating_average, shown)
                Row(
                    modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
                ) {
                    Icon(
                        BahrIcons.Star.filled(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(BahrSize.iconLarge),
                    )
                    Text(text = shown, style = MaterialTheme.typography.headlineMedium)
                }
                Text(
                    text = pluralStringResource(Res.plurals.host_reviews_count, rating.count, rating.count),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
                rating.stars.forEach { RatingBar(it, total = rating.count) }
            }
        }
    }
}

/** "5 ★ ▬▬▬▬▬▬──── 12": one star bucket, read out as "5 stars: 12". */
@Composable
private fun RatingBar(
    bucket: StarCountDto,
    total: Int,
) {
    val spoken = pluralStringResource(Res.plurals.host_rating_bar, bucket.stars, bucket.stars, bucket.count)
    // A share of the reviews, for the bar's length only (never money). Capped: the server's buckets
    // and count come from one read, but a bar must never draw past its track.
    val share = if (total > 0) (bucket.count.toFloat() / total).coerceIn(0f, 1f) else 0f
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        Row(
            modifier = Modifier.width(BahrSize.ratingStarColumn),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
        ) {
            Text(text = bucket.stars.toString(), style = MaterialTheme.typography.labelMedium)
            Icon(
                BahrIcons.Star.filled(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(BahrSize.iconSmall),
            )
        }
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(BahrSize.ratingBarHeight)
                    .clip(BahrTheme.shapes.full)
                    .background(BahrTheme.colors.track),
        ) {
            // Fills from the start edge, so the bars grow right to left in Arabic.
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(share)
                    .clip(BahrTheme.shapes.full)
                    .background(MaterialTheme.colorScheme.secondary),
            )
        }
        Text(
            text = bucket.count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(BahrSize.ratingStarColumn),
        )
    }
}

private const val HEADER_SKELETON_LINES = 2
