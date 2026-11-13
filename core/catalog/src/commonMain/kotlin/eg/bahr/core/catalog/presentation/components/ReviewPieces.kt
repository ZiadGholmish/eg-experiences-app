package eg.bahr.core.catalog.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import eg.bahr.core.catalog.model.ImageDto
import eg.bahr.core.catalog.model.MAX_STARS
import eg.bahr.core.catalog.model.MIN_STARS
import eg.bahr.core.catalog.model.RatingSummaryDto
import eg.bahr.core.catalog.model.ReviewDto
import eg.bahr.core.catalog.model.StarCountDto
import eg.bahr.core.catalog.model.stars
import eg.bahr.core.catalog.presentation.media.ViewerMedia
import eg.bahr.core.catalog.presentation.media.opensMediaViewer
import eg.bahr.core.designsystem.components.BahrCard
import eg.bahr.core.designsystem.components.ImageGround
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.host_no_reviews
import eg.bahr.core.localization.generated.resources.host_rating_average
import eg.bahr.core.localization.generated.resources.host_rating_bar
import eg.bahr.core.localization.generated.resources.host_reviews_count
import eg.bahr.core.localization.generated.resources.review_author_open
import eg.bahr.core.localization.generated.resources.review_photo
import eg.bahr.core.localization.generated.resources.review_verified
import eg.bahr.core.localization.generated.resources.reviews_see_all
import eg.bahr.core.localization.generated.resources.trip_reviews
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/*
 * The review parts the trip page (feature:trip-details), the host page (feature:host) and "See all reviews"
 * (feature:reviews) share: the contract's one `Review` card, the rating summary and the "See all reviews" rule
 * and link. Moved here from feature:trips when it was split (M3-M16), unchanged.
 */

/**
 * The server's preview size (`TripDetail.reviews.items`, `HostProfile.reviews`: "a server-configured
 * number, 3 by default", openapi.yaml). A preview shorter than this is every real review there is.
 */
const val REVIEW_PREVIEW_SIZE = 3

/**
 * Whether a page's review preview gets "See all reviews" (the trip page's and the host page's): only
 * when the preview is full and the summary counts more than it shows. Since M4-B10 the count is the
 * review rows, so `count > previewed` alone would do; the full-preview check stays because it costs
 * nothing and keeps an older server's authored total ("4.9 · 64" over no rows, M4-M12) from offering a
 * link to an empty list.
 */
fun showsSeeAllReviews(
    count: Int,
    previewed: Int,
): Boolean = previewed >= REVIEW_PREVIEW_SIZE && count > previewed

/**
 * "See all reviews" under a page's review previews (the trip page's and the host page's, M4-M11), at
 * the 44dp touch minimum. A link, not the page's goal, so primary, never coral. No count in it (user
 * decision 2026-10-12).
 */
@Composable
fun SeeAllReviewsLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = BahrSpacing.minTouch).testTag(SEE_ALL_REVIEWS_TAG)) {
        Text(
            text = stringResource(Res.string.reviews_see_all),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** For tests: the "See all reviews" link. */
const val SEE_ALL_REVIEWS_TAG = "see_all_reviews"

/**
 * One review: shared by the trip page's reviews, the host page's and the full list (the contract's one
 * `Review` shape). Its stars when the server sent them (M4-B8; an older server sends none, and then
 * none are drawn); a rating-only review has stars and no text. Since M3-M4 its photos when it has any,
 * and "Verified trip" when the server says so (M3-B7).
 *
 * MP-M2: the author's name opens their public profile ([onAuthorClick], with `authorPublicId`) when the server sent
 * that id and the caller wires a profile page; otherwise it is plain text, drawn as before. [footer] goes under the
 * rest: a traveller's profile names the trip each review is about there.
 */
@Composable
fun ReviewCard(
    review: ReviewDto,
    onAuthorClick: ((publicId: String) -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    BahrCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(BahrSpacing.lg), verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
                ReviewAuthor(
                    props =
                        ReviewAuthor.Props(
                            author = review.author,
                            tone = review.tone,
                            publicId = review.authorPublicId,
                            onOpenProfile = onAuthorClick,
                        ),
                )
                Spacer(Modifier.weight(1f))
                review.dateISO?.let {
                    Text(
                        text = BahrFormat.dayMonthYear(it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            review.rating?.let { ReviewStars(it) }
            review.body?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = BahrTheme.colors.onSurfaceSecondary)
            }
            if (review.photos.isNotEmpty()) ReviewPhotos(review.photos)
            review.partyLabel?.let {
                Text(text = it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (review.verified) VerifiedTrip()
            footer?.invoke()
        }
    }
}

/**
 * A review's author (MP-M2; the avatar and name were ReviewCard's own before): the tinted initial and the name. With a
 * [Props.publicId] and [Props.onOpenProfile] both there, the pair is one link to the author's public profile: the name
 * in primary, a 44dp-high touch area, read out as a button ("Mona S., Open profile"). Without, plain text as before.
 *
 * Its second caller is Home's review card, which keeps the name to [Props.singleLine] (a rail's cards line up).
 */
interface ReviewAuthor {
    @Immutable
    data class Props(
        val author: String?,
        val tone: String? = null,
        val publicId: String? = null,
        val onOpenProfile: ((publicId: String) -> Unit)? = null,
        val singleLine: Boolean = false,
    )
}

@Composable
fun ReviewAuthor(
    modifier: Modifier = Modifier,
    props: ReviewAuthor.Props,
) {
    val publicId = props.publicId
    val open = props.onOpenProfile
    val link = props.author != null && publicId != null && open != null
    val openLabel = stringResource(Res.string.review_author_open)
    Row(
        modifier =
            if (link) {
                modifier
                    .heightIn(min = BahrSpacing.minTouch)
                    .clip(BahrTheme.shapes.small)
                    .clickable(role = Role.Button, onClickLabel = openLabel) { open(publicId) }
                    .testTag(REVIEW_AUTHOR_LINK_TAG)
            } else {
                modifier
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        InitialAvatar(name = props.author, tint = toneTint(props.tone), size = BahrSize.avatarSmall)
        props.author?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge.byContent(),
                color = if (link) MaterialTheme.colorScheme.primary else Color.Unspecified,
                maxLines = if (props.singleLine) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
                // Single line (Home): the name takes the row's rest, as it did before MP-M2, so it ellipsizes there.
                modifier = if (props.singleLine) Modifier.weight(1f) else Modifier,
            )
        }
    }
}

/** For tests: a review author's name when it opens their profile (MP-M2). */
const val REVIEW_AUTHOR_LINK_TAG = "review_author_link"

/**
 * The traveller's photos (M3-M4), in their order: one row of [BahrSize.reviewPhoto] squares that scrolls sideways when
 * five do not fit. Each has its size before it loads (the ground, then the LQIP, then the photo), so the card never
 * grows under the reader. A plain Row: a review has at most a few photos (the server's limit, 5).
 */
@Composable
private fun ReviewPhotos(photos: List<ImageDto>) {
    // A tap opens them full screen, in their order, on the one tapped (MV-M3).
    val gallery = remember(photos) { photos.map { ViewerMedia.Photo(it) } }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        photos.forEachIndexed { index, photo ->
            val spoken = photo.alt ?: stringResource(Res.string.review_photo, index + 1)
            ImageGround(
                modifier =
                    Modifier
                        .size(BahrSize.reviewPhoto)
                        .clip(BahrTheme.shapes.small)
                        .testTag(REVIEW_PHOTO_TAG)
                        // Before clearAndSetSemantics, so the "View full screen" action stays with the photo's name.
                        .opensMediaViewer(gallery, index)
                        .clearAndSetSemantics { contentDescription = spoken },
            ) { Photo(photo) }
        }
    }
}

/** For tests: a review's photo (MV-M3: it opens the full-screen viewer). */
const val REVIEW_PHOTO_TAG = "review_photo"

/**
 * "Verified trip" (M3-B7): the reviewer booked and took the trip through the app. Not a link, so no touch target.
 * Public since MH-M1: Home's review cards are its second caller.
 */
@Composable
fun VerifiedTrip() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
        Icon(
            BahrIcons.Verified.filled(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(BahrSize.iconSmall),
        )
        Text(
            text = stringResource(Res.string.review_verified),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * A review's stars: [rating] filled, the rest outlined, read out once as "Rated 4 out of 5". A value
 * outside 1–5 is held to it, so a bad payload still draws five stars. Public since MH-M1: Home's review cards are
 * its second caller.
 */
@Composable
fun ReviewStars(rating: Int) {
    val shown = rating.coerceIn(MIN_STARS, MAX_STARS)
    val spoken = stringResource(Res.string.host_rating_average, shown.toString())
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        repeat(MAX_STARS) { index ->
            Icon(
                imageVector = if (index < shown) BahrIcons.Star.filled() else BahrIcons.Star.outlined(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(BahrSize.iconSmall),
            )
        }
    }
}

/**
 * The rating summary: the average large, with how many reviews it is over, beside five bars from 5
 * stars down to 1, each as long as its share of the reviews. With no reviews (count 0, no average),
 * one line says so.
 *
 * The host page's, and the reviews page's header (M4-M11), which has its own heading: [showTitle] false
 * leaves out the "Reviews" title.
 */
@Composable
fun RatingSummary(
    rating: RatingSummaryDto,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
        if (showTitle) SectionTitle(stringResource(Res.string.trip_reviews), Modifier.semantics { heading() })
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

/**
 * Names arrive as typed ("Mahmoud A."): a Latin name inside an Arabic page must read in its own
 * direction, or the bidi algorithm moves its trailing period to the front.
 */
private fun TextStyle.byContent() = copy(textDirection = TextDirection.Content)
