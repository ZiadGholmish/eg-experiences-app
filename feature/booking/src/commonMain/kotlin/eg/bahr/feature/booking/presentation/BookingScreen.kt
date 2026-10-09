package eg.bahr.feature.booking.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.designsystem.components.BahrErrorView
import eg.bahr.core.designsystem.components.BahrLoadingView
import eg.bahr.core.designsystem.components.BahrPrimaryButton
import eg.bahr.core.designsystem.components.StickyActionBar
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.booking_hold_cta
import eg.bahr.core.localization.generated.resources.booking_hold_note
import eg.bahr.core.localization.generated.resources.booking_price_per_person
import eg.bahr.core.localization.generated.resources.booking_step_title
import eg.bahr.core.localization.generated.resources.error_hold_expired
import eg.bahr.core.localization.generated.resources.format_pair
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import eg.bahr.feature.booking.model.BookingTripDto
import eg.bahr.feature.booking.navigation.HoldRoute
import eg.bahr.feature.booking.presentation.components.BookingTopBar
import eg.bahr.feature.booking.presentation.components.DateRows
import eg.bahr.feature.booking.presentation.components.GuestDetails
import eg.bahr.feature.booking.presentation.components.PartyStepper
import eg.bahr.feature.booking.presentation.components.PriceSummary
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Date and party (HANDOFF screen 4): the trip's dates as rows, the party stepper, the lead contact,
 * the price per person, and the coral "Hold seats and pay".
 *
 * The handoff collects name and number on the payment screen, but the contract's hold
 * (`PlaceHoldRequest.guest`) needs them, so they are asked for here, just above the price.
 *
 * Once seats are held, [onHeld] gets the hold and the screen stays on the back stack underneath, so
 * Back returns here; the view model forgets the hold so returning does not navigate forward again.
 * `alreadyHeld` is true when no new hold was placed because this device already had a live one
 * (M2-M4: one hold at a time, also across a restart); [onHeld] then gets that one.
 */
@Composable
internal fun BookingScreen(
    slug: String,
    departureId: String,
    onBack: () -> Unit,
    onHeld: (hold: HoldRoute, alreadyHeld: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    holdEnded: Boolean? = null,
    onHoldEndedHandled: () -> Unit = {},
    viewModel: BookingViewModel = koinViewModel { parametersOf(slug, departureId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.held) {
        val held = state.held ?: return@LaunchedEffect
        onHeld(held, state.heldAlready)
        viewModel.onHeldHandled()
    }

    // Back from the held seats: [holdEnded] is true when the hold ran out, false when it was released.
    LaunchedEffect(holdEnded) {
        val expired = holdEnded ?: return@LaunchedEffect
        viewModel.onHoldEnded(expired)
        onHoldEndedHandled()
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        BookingTopBar(title = stringResource(Res.string.booking_step_title), step = 1, onBack = onBack)
        val trip = state.trip
        val loadError = state.loadError
        Box(modifier = Modifier.weight(1f)) {
            when {
                trip != null -> BookingForm(state, trip, viewModel)
                loadError != null ->
                    BahrErrorView(
                        message = loadError.localizedMessage(),
                        retryLabel = stringResource(Res.string.action_retry),
                        onRetry = if (loadError.isRetryable) viewModel::load else null,
                    )
                else -> BahrLoadingView()
            }
        }
        HoldBar(state, onHold = viewModel::placeHold)
    }
}

@Composable
private fun BookingForm(
    state: BookingUiState,
    trip: BookingTripDto,
    viewModel: BookingViewModel,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(BOOKING_FORM_TAG)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.xl),
    ) {
        Heading(trip)
        DateRows(
            departures = state.departures,
            selectedId = state.selectedDeparture?.id,
            onSelect = viewModel::selectDeparture,
        )
        PartyStepper(
            partySize = state.partySize,
            maxPartySize = state.maxPartySize,
            seatsLeftCap = state.seatsLeftCap,
            canDecrease = state.canDecreaseParty,
            canIncrease = state.canIncreaseParty,
            onDecrease = viewModel::decreaseParty,
            onIncrease = viewModel::increaseParty,
        )
        GuestDetails(
            name = state.guestName,
            phone = state.guestPhone,
            phoneValid = state.isPhoneValid,
            onNameChange = viewModel::setGuestName,
            onPhoneChange = viewModel::setGuestPhone,
            onDone = viewModel::placeHold,
        )
        PriceSummary(
            pricePerPerson = state.selectedDeparture?.price ?: trip.price,
            partySize = state.partySize,
        )
    }
}

/** The trip's title, then "450 EGP per person · 05:00 → 22:00", both from the server. */
@Composable
private fun Heading(trip: BookingTripDto) {
    Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
        Text(text = trip.title, style = MaterialTheme.typography.titleLarge)
        val price =
            stringResource(
                Res.string.booking_price_per_person,
                BahrFormat.money(trip.price.amount, trip.price.currencyCode, BahrTheme.locale.isArabic),
            )
        val duration = trip.durationLabel?.takeIf { it.isNotBlank() }
        Text(
            text = if (duration != null) stringResource(Res.string.format_pair, price, BahrFormat.ltr(duration)) else price,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The sticky bar: why the last hold failed (if it did), the one coral button, and the hold
 * explanation. While the hold is in flight the button keeps its coral fill with a progress
 * indicator and ignores taps, so a double tap cannot place two holds.
 */
@Composable
private fun HoldBar(
    state: BookingUiState,
    onHold: () -> Unit,
) {
    StickyActionBar {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
            val message =
                when {
                    state.holdError != null -> state.holdError.localizedMessage()
                    state.holdExpired -> stringResource(Res.string.error_hold_expired)
                    else -> null
                }
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            BahrPrimaryButton(
                text = stringResource(Res.string.booking_hold_cta),
                onClick = onHold,
                enabled = state.canPlaceHold,
                loading = state.isPlacingHold,
                leadingIcon = BahrIcons.ArrowForward.outlined(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(Res.string.booking_hold_note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The scrolling form, for tests that scroll to a part of it. */
internal const val BOOKING_FORM_TAG = "booking_form"
