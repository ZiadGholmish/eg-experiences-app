package com.egyptexperiences.feature.booking.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egyptexperiences.core.common.money.MoneyFormatter
import com.egyptexperiences.core.designsystem.component.EgBadge
import com.egyptexperiences.core.designsystem.component.EgFilterChip
import com.egyptexperiences.core.designsystem.component.EgPrimaryButton
import com.egyptexperiences.core.designsystem.theme.EgCoralDeep
import com.egyptexperiences.core.designsystem.theme.EgCoralTint
import com.egyptexperiences.core.designsystem.theme.EgInkSubtle
import com.egyptexperiences.core.designsystem.theme.EgTeal
import com.egyptexperiences.core.designsystem.theme.EgTheme
import com.egyptexperiences.core.localization.generated.resources.Res
import com.egyptexperiences.core.localization.generated.resources.booking_confirm
import com.egyptexperiences.core.localization.generated.resources.booking_guest_name
import com.egyptexperiences.core.localization.generated.resources.booking_guest_phone
import com.egyptexperiences.core.localization.generated.resources.booking_hold_expires_in
import com.egyptexperiences.core.localization.generated.resources.booking_party_size
import com.egyptexperiences.core.localization.generated.resources.booking_total
import com.egyptexperiences.core.localization.localizedMessage
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

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
    // endpoint yet (`be/` has trip, departure and booking controllers only).
    // Until Paymob lands, a "confirmed" booking here is an unpaid hold.
    LaunchedEffect(state.held?.ref) {
        state.held?.ref?.let(onBooked)
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(EgTheme.spacings.screenGutter),
        verticalArrangement = Arrangement.spacedBy(EgTheme.spacings.x5l),
    ) {
        state.holdRemaining?.let { remaining ->
            EgBadge(
                text =
                    stringResource(
                        Res.string.booking_hold_expires_in,
                        HoldCountdown.format(remaining),
                    ),
                container = EgCoralTint,
                content = EgCoralDeep,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(EgTheme.spacings.xl)) {
            Text(
                text = stringResource(Res.string.booking_party_size),
                style = EgTheme.typography.cardTitle,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(EgTheme.spacings.md)) {
                (1..PartySizeShortcuts).forEach { size ->
                    EgFilterChip(
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
            shape = EgTheme.shapes.md,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.guestPhone,
            onValueChange = viewModel::setGuestPhone,
            label = { Text(text = stringResource(Res.string.booking_guest_phone)) },
            singleLine = true,
            shape = EgTheme.shapes.md,
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
                    style = EgTheme.typography.body,
                    color = EgInkSubtle,
                )
                Text(
                    text = MoneyFormatter.format(held.total.toMoney()),
                    style = EgTheme.typography.priceLarge,
                    color = EgTeal,
                )
            }
        }

        state.error?.let { error ->
            Text(
                text = error.localizedMessage(),
                style = EgTheme.typography.body,
                color = EgCoralDeep,
            )
        }

        EgPrimaryButton(
            text = stringResource(Res.string.booking_confirm),
            enabled = state.canPlaceHold,
            loading = state.isPlacingHold,
            onClick = viewModel::placeHold,
        )
    }
}

/** The canvas offers 1–6 as taps; larger parties are a phone call today. */
private const val PartySizeShortcuts = 6
