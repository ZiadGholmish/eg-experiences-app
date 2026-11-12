package eg.bahr.feature.mytrips.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import eg.bahr.core.catalog.model.ImageDto
import eg.bahr.core.designsystem.components.BahrBadge
import eg.bahr.core.designsystem.components.BahrPillButton
import eg.bahr.core.designsystem.components.BahrSkeleton
import eg.bahr.core.designsystem.components.BahrSkeletonBlock
import eg.bahr.core.designsystem.components.BahrSkeletonLine
import eg.bahr.core.designsystem.components.ImageGround
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrElevation
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrShadow
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.booking_method_card
import eg.bahr.core.localization.generated.resources.booking_method_test_badge
import eg.bahr.core.localization.generated.resources.booking_method_test_cash
import eg.bahr.core.localization.generated.resources.booking_method_vf
import eg.bahr.core.localization.generated.resources.booking_paid_with
import eg.bahr.core.localization.generated.resources.format_pair
import eg.bahr.core.localization.generated.resources.mytrips_date_closed
import eg.bahr.core.localization.generated.resources.mytrips_party_count
import eg.bahr.core.localization.generated.resources.mytrips_place_opened
import eg.bahr.core.localization.generated.resources.mytrips_rate_trip
import eg.bahr.core.localization.generated.resources.mytrips_review_pending
import eg.bahr.core.localization.generated.resources.mytrips_review_published
import eg.bahr.core.localization.generated.resources.mytrips_review_rejected
import eg.bahr.core.localization.generated.resources.mytrips_status_cancelled
import eg.bahr.core.localization.generated.resources.mytrips_status_completed
import eg.bahr.core.localization.generated.resources.mytrips_status_confirmed
import eg.bahr.core.localization.generated.resources.mytrips_status_expired
import eg.bahr.core.localization.generated.resources.mytrips_status_held
import eg.bahr.core.localization.generated.resources.mytrips_status_paying
import eg.bahr.core.localization.generated.resources.mytrips_still_full
import eg.bahr.core.localization.generated.resources.mytrips_trip_fallback
import eg.bahr.feature.mytrips.model.BookingStatus
import eg.bahr.feature.mytrips.model.MyBookingDto
import eg.bahr.feature.mytrips.model.MyWaitlistEntryDto
import eg.bahr.feature.mytrips.model.ReviewStatus
import eg.bahr.feature.mytrips.presentation.DeviceJoin
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A booking in Upcoming or Past: the trip's thumbnail, its title, "Sat 10 Oct · 05:00", the party, and
 * the booking's status as a badge. Text first: the thumbnail has its size before the photo loads.
 */
@Composable
internal fun BookingRow(
    booking: MyBookingDto,
    onClick: () -> Unit,
    onRateTrip: () -> Unit = {},
) {
    val status = BookingStatus.of(booking.status)
    val (label, tone) = status.badge()
    val method = paidMethodName(booking.method)
    TripRow(
        image = booking.trip?.cardImage,
        title = booking.trip?.title,
        when_ = whenLine(booking.date, booking.departure?.timeLocal),
        party = booking.partySize,
        badge = label?.let { BahrBadge.Props(text = stringResource(it), tone = tone) },
        onClick = onClick,
        // How it was paid (MV-M4); a test booking also wears a "Test" badge, as on the confirmation.
        paidWith = method?.let { stringResource(Res.string.booking_paid_with, stringResource(it)) },
        testBadge = booking.method == TEST_CASH,
        // A finished trip (M3-M4): "Rate your trip" while it may be reviewed, then where its review stands.
        review = booking.review?.let { ReviewStatus.of(it.status).badge() },
        onRateTrip = if (booking.canReview && booking.review == null) onRateTrip else null,
    )
}

/**
 * A signed-in waiting-list entry: "A place opened. Book now" when the date is bookable (a hint: the
 * hold decides), else still full, or no longer bookable.
 */
@Composable
internal fun WaitlistRow(
    entry: MyWaitlistEntryDto,
    onClick: () -> Unit,
) {
    val badge =
        when {
            entry.bookable -> BahrBadge.Props(stringResource(Res.string.mytrips_place_opened), BahrBadge.Tone.Success)
            entry.unavailableReason == null || entry.unavailableReason == SOLD_OUT ->
                BahrBadge.Props(stringResource(Res.string.mytrips_still_full), BahrBadge.Tone.Secondary)
            else -> BahrBadge.Props(stringResource(Res.string.mytrips_date_closed), BahrBadge.Tone.Quaternary)
        }
    TripRow(
        image = entry.trip?.cardImage,
        title = entry.trip?.title,
        when_ = whenLine(entry.date, entry.departTime),
        party = entry.partySize,
        badge = badge,
        onClick = onClick,
    )
}

/** Signed out: a join this device made. Only what the device knows: no photo, the title if a card showed it. */
@Composable
internal fun DeviceJoinRow(
    join: DeviceJoin,
    onClick: () -> Unit,
) {
    TripRow(
        image = null,
        title = join.title,
        when_ = BahrFormat.date(join.date),
        party = join.partySize,
        badge = null,
        onClick = onClick,
    )
}

@Composable
private fun TripRow(
    image: ImageDto?,
    title: String?,
    when_: String?,
    party: Int,
    badge: BahrBadge.Props?,
    onClick: () -> Unit,
    review: Pair<StringResource, BahrBadge.Tone>? = null,
    onRateTrip: (() -> Unit)? = null,
    paidWith: String? = null,
    testBadge: Boolean = false,
) {
    val x = BahrTheme.colors
    val shape = BahrTheme.shapes.extraLarge
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .bahrShadow(BahrElevation.Level1, shape, x)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = BahrSpacing.minTouch)
                .padding(BahrSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        Thumbnail(image)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
            Text(
                text = title ?: stringResource(Res.string.mytrips_trip_fallback),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val partyText = pluralStringResource(Res.plurals.mytrips_party_count, party, party)
            Text(
                text = when_?.let { stringResource(Res.string.format_pair, it, partyText) } ?: partyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            paidWith?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(PAID_WITH_TAG),
                )
            }
            if (badge != null || review != null || testBadge) {
                Row(horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
                    badge?.let { BahrBadge(props = it) }
                    if (testBadge) {
                        BahrBadge(props = BahrBadge.Props(stringResource(Res.string.booking_method_test_badge), BahrBadge.Tone.Quaternary))
                    }
                    review?.let { (text, tone) -> BahrBadge(props = BahrBadge.Props(stringResource(text), tone)) }
                }
            }
            // Its own target inside the row: the row opens the trip, this the review page. A way in, not the
            // screen's one step forward (a list may offer it many times), so primary, never coral.
            onRateTrip?.let {
                BahrPillButton(
                    modifier = Modifier.padding(top = BahrSpacing.xs).testTag(RATE_TRIP_TAG),
                    props =
                        BahrPillButton.Props(
                            text = stringResource(Res.string.mytrips_rate_trip),
                            icon = BahrIcons.RateReview,
                            onClick = it,
                        ),
                )
            }
        }
    }
}

/** For tests: a row's "Rate your trip". */
internal const val RATE_TRIP_TAG = "mytrips_rate_trip"

/** For tests: a row's "Paid with …". */
internal const val PAID_WITH_TAG = "mytrips_paid_with"

// openapi `PaymentMethod` values; `test_cash` (MV-B2) marks a test booking, local and dev only.
private const val VODAFONE_CASH = "vodafone_cash"
private const val CARD = "card"
private const val TEST_CASH = "test_cash"

/** The name of the method a booking was paid with (openapi `Booking.method`), or null when absent or unknown. */
private fun paidMethodName(method: String?): StringResource? =
    when (method) {
        VODAFONE_CASH -> Res.string.booking_method_vf
        CARD -> Res.string.booking_method_card
        TEST_CASH -> Res.string.booking_method_test_cash
        else -> null
    }

/** Where moderation has the booking's review. Not published is the quiet neutral tone, not an error. */
private fun ReviewStatus.badge(): Pair<StringResource, BahrBadge.Tone> =
    when (this) {
        ReviewStatus.Pending -> Res.string.mytrips_review_pending to BahrBadge.Tone.Secondary
        ReviewStatus.Published -> Res.string.mytrips_review_published to BahrBadge.Tone.Success
        ReviewStatus.Rejected -> Res.string.mytrips_review_rejected to BahrBadge.Tone.Quaternary
    }

/**
 * Booking or waiting-list rows still loading (M3-M14): [rows] rows drawn as [TripRow] lays one out (its
 * ground, radius and shadow, the thumbnail's room, then the title, the date line and the badge), at the
 * list's gap, read out as one "Loading".
 */
@Composable
internal fun TripRowsSkeleton(
    rows: Int,
    modifier: Modifier = Modifier,
) {
    val x = BahrTheme.colors
    val shape = BahrTheme.shapes.extraLarge
    BahrSkeleton(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
        repeat(rows) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .bahrShadow(BahrElevation.Level1, shape, x)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(BahrSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
            ) {
                BahrSkeletonBlock(
                    modifier = Modifier.size(BahrSize.thumbnail),
                    props = BahrSkeletonBlock.Props(shape = BahrTheme.shapes.medium),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
                    BahrSkeletonLine(props = BahrSkeletonLine.Props(widthFraction = SKELETON_TITLE_WIDTH))
                    BahrSkeletonLine(props = BahrSkeletonLine.Props(widthFraction = SKELETON_WHEN_WIDTH))
                    BahrSkeletonLine(props = BahrSkeletonLine.Props(widthFraction = SKELETON_BADGE_WIDTH))
                }
            }
        }
    }
}

// The skeleton row's lines, as shares of the text column: the title, the date and party, the badge.
private const val SKELETON_TITLE_WIDTH = .75f
private const val SKELETON_WHEN_WIDTH = .55f
private const val SKELETON_BADGE_WIDTH = .3f

/** The trip's card image at the summary-thumbnail size; the LQIP first, the photo over it. */
@Composable
private fun Thumbnail(image: ImageDto?) {
    ImageGround(modifier = Modifier.size(BahrSize.thumbnail).clip(BahrTheme.shapes.medium)) {
        image?.lqip?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        image?.let {
            AsyncImage(
                model = it.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** "Sat 10 Oct · 05:00" (the time left to right inside Arabic); just the date without a usable time; null if neither parses. */
@Composable
private fun whenLine(
    isoDate: String,
    time: String?,
): String? {
    val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return null
    val clock = time?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    return if (clock == null) {
        BahrFormat.date(date)
    } else {
        stringResource(Res.string.format_pair, BahrFormat.date(date), BahrFormat.ltr(BahrFormat.time(clock)))
    }
}

/** A badge per status. Held and paying are coral-free tints: coral stays for primary actions. */
private fun BookingStatus.badge(): Pair<StringResource?, BahrBadge.Tone> =
    when (this) {
        BookingStatus.Held -> Res.string.mytrips_status_held to BahrBadge.Tone.Secondary
        BookingStatus.PaymentPending -> Res.string.mytrips_status_paying to BahrBadge.Tone.Secondary
        BookingStatus.Confirmed -> Res.string.mytrips_status_confirmed to BahrBadge.Tone.Success
        BookingStatus.Completed -> Res.string.mytrips_status_completed to BahrBadge.Tone.Primary
        BookingStatus.Cancelled -> Res.string.mytrips_status_cancelled to BahrBadge.Tone.Quaternary
        BookingStatus.Expired -> Res.string.mytrips_status_expired to BahrBadge.Tone.Quaternary
        BookingStatus.Other -> null to BahrBadge.Tone.Primary
    }

private const val SOLD_OUT = "SOLD_OUT"
