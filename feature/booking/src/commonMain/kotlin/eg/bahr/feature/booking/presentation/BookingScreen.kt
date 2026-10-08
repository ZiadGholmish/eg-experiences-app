package eg.bahr.feature.booking.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.designsystem.components.BahrFilterChip
import eg.bahr.core.designsystem.components.BahrPrimaryButton
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.booking_confirm
import eg.bahr.core.localization.generated.resources.booking_guest_name
import eg.bahr.core.localization.generated.resources.booking_guest_phone
import eg.bahr.core.localization.generated.resources.booking_hold_label
import eg.bahr.core.localization.generated.resources.booking_party_size
import eg.bahr.core.localization.generated.resources.booking_total
import eg.bahr.core.localization.localizedMessage
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import eg.bahr.core.designsystem.components.HoldCountdown as HoldCountdownPanel

/**
 * Date + party, then checkout, on one screen.
 *
 * The canvas splits them across two artboards; they are one destination here
 * because a hold is placed between them, and a hold that outlives its screen is
 * how seats get stranded.
 */
@Composable
fun BookingScreen(
    departureId: Long,
    onBooked: (ref: String) -> Unit,
    onHoldExpired: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookingViewModel = koinViewModel { parametersOf(departureId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.holdExpired) {
        if (state.holdExpired) onHoldExpired()
    }
    // TODO(payment): this goes straight from a *hold* to the confirmation
    // screen. There is no payment step because the backend has no payment
    // endpoint yet (payments land in M3 of `../docs/PLAN.md`).
    // Until Paymob lands, a "confirmed" booking here is an unpaid hold.
    LaunchedEffect(state.held?.ref) {
        state.held?.ref?.let(onBooked)
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(BahrSpacing.gutter),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.xl),
    ) {
        state.holdRemaining?.let { remaining ->
            HoldCountdownPanel(
                secondsLeft = remaining.inWholeSeconds.toInt(),
                label = stringResource(Res.string.booking_hold_label),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
            Text(
                text = stringResource(Res.string.booking_party_size),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
                (1..PartySizeShortcuts).forEach { size ->
                    BahrFilterChip(
                        label = size.toString(),
                        selected = state.partySize == size,
                        onClick = { viewModel.setPartySize(size) },
                    )
                }
            }
        }

        OutlinedTextField(
            value = state.guestName,
            onValueChange = viewModel::setGuestName,
            label = { Text(text = stringResource(Res.string.booking_guest_name)) },
            singleLine = true,
            shape = BahrTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.guestPhone,
            onValueChange = viewModel::setGuestPhone,
            label = { Text(text = stringResource(Res.string.booking_guest_phone)) },
            singleLine = true,
            shape = BahrTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )

        state.held?.let { held ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.booking_total),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = BahrFormat.money(held.total.amount, held.total.currencyCode, BahrTheme.locale.isArabic),
                    style = BahrTheme.type.price,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        state.error?.let { error ->
            Text(
                text = error.localizedMessage(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // While a hold is in flight the button stays coral with a progress indicator and ignores
        // taps, so a double tap cannot place two holds and a slow network still shows progress.
        BahrPrimaryButton(
            text = stringResource(Res.string.booking_confirm),
            enabled = state.canPlaceHold,
            loading = state.isPlacingHold,
            onClick = viewModel::placeHold,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The canvas offers 1–6 as taps; larger parties are a phone call today. */
private const val PartySizeShortcuts = 6
