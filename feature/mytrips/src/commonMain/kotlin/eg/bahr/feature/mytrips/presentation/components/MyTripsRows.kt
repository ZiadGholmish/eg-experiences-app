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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import eg.bahr.core.catalog.model.ImageDto
import eg.bahr.core.designsystem.components.BahrBadge
import eg.bahr.core.designsystem.components.ImageGround
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.theme.BahrElevation
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrShadow
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.format_pair
import eg.bahr.core.localization.generated.resources.mytrips_date_closed
import eg.bahr.core.localization.generated.resources.mytrips_party_count
import eg.bahr.core.localization.generated.resources.mytrips_place_opened
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
) {
    val status = BookingStatus.of(booking.status)
    val (label, tone) = status.badge()
    TripRow(
        image = booking.trip?.cardImage,
        title = booking.trip?.title,
        when_ = whenLine(booking.date, booking.departure?.timeLocal),
        party = booking.partySize,
        badge = label?.let { BahrBadge.Props(text = stringResource(it), tone = tone) },
        onClick = onClick,
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
            badge?.let { BahrBadge(props = it) }
        }
    }
}

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
