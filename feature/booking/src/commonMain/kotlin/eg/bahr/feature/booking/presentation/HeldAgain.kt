package eg.bahr.feature.booking.presentation

import eg.bahr.core.common.result.AppError
import eg.bahr.core.datastore.StoredHold
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.feature.booking.model.HeldBookingDto
import eg.bahr.feature.booking.navigation.HoldRoute
import kotlin.time.Duration
import kotlin.time.Instant

/*
 * Reading the device's stored hold back, shared by the hold screen, Home's "Continue your booking"
 * card and the second-hold guard on date + party, so all three decide "still held" the same way.
 */

/**
 * The deadline, if the server says the seats are still held for this booking: HELD or
 * PAYMENT_PENDING with a deadline that parses. Anything else means they are not.
 */
internal fun HeldBookingDto.liveDeadline(receivedAt: Instant): HoldDeadline? =
    if (status in HoldViewModel.HELD_STATUSES) HoldDeadline.from(holdExpiresAt, serverNow, receivedAt) else null

/**
 * The held-seats screen for a stored hold the server has just called live ([booking], with the
 * deadline [holdExpiresAt] it sent, answered at [serverNow] on the server's clock). The countdown
 * takes this fresh pair, so it is right from the first frame even if the screen's own re-read fails;
 * the progress bar's 100 % is the hold's full length from the stored placement pair.
 *
 * [holdExpiresAt] is passed in, not read off [booking], because it is non-null only once
 * [liveDeadline] has accepted the answer, which callers do first.
 */
internal fun reopenedHold(
    stored: StoredHold,
    booking: HeldBookingDto,
    holdExpiresAt: String,
    serverNow: String = booking.serverNow,
): HoldRoute =
    HoldRoute(
        ref = stored.ref,
        holdExpiresAt = holdExpiresAt,
        serverNow = serverNow,
        totalAmount = booking.total.amount,
        totalCurrency = booking.total.currencyCode,
        guestPhone = stored.guestPhone,
        holdLengthSeconds = HoldDeadline.length(stored.holdExpiresAt, stored.serverNow)?.inWholeSeconds,
    )

/** NOT_FOUND: an unknown ref, or a phone that does not match (the server answers both the same way). */
internal fun AppError.isNotFound(): Boolean = this is AppError.Api && code == ApiErrorCodes.NOT_FOUND

/** How long to wait before re-reading after [emptyChecks] reads in a row came back empty or failed. */
internal fun recheckBackoff(emptyChecks: Int): Duration {
    val shift = (emptyChecks - 1).coerceIn(0, MAX_BACKOFF_SHIFT)
    return (HoldViewModel.FIRST_RECHECK * (1 shl shift)).coerceAtMost(HoldViewModel.MAX_RECHECK)
}

private const val MAX_BACKOFF_SHIFT = 4
