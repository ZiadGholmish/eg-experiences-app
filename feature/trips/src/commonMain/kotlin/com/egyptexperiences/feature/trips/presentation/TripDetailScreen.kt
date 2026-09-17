package com.egyptexperiences.feature.trips.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.egyptexperiences.core.common.money.MoneyFormatter
import com.egyptexperiences.core.designsystem.component.EgBadge
import com.egyptexperiences.core.designsystem.component.EgErrorView
import com.egyptexperiences.core.designsystem.component.EgLoadingView
import com.egyptexperiences.core.designsystem.component.EgPrimaryButton
import com.egyptexperiences.core.designsystem.theme.EgInkFaint
import com.egyptexperiences.core.designsystem.theme.EgInkSubtle
import com.egyptexperiences.core.designsystem.theme.EgSuccess
import com.egyptexperiences.core.designsystem.theme.EgSuccessTint
import com.egyptexperiences.core.designsystem.theme.EgSurfaceSunken
import com.egyptexperiences.core.designsystem.theme.EgTeal
import com.egyptexperiences.core.designsystem.theme.EgTheme
import com.egyptexperiences.core.localization.generated.resources.Res
import com.egyptexperiences.core.localization.generated.resources.action_retry
import com.egyptexperiences.core.localization.generated.resources.booking_continue
import com.egyptexperiences.core.localization.generated.resources.departure_sold_out
import com.egyptexperiences.core.localization.generated.resources.departures_pick_a_date
import com.egyptexperiences.core.localization.generated.resources.trip_best_time
import com.egyptexperiences.core.localization.generated.resources.trip_host_verified
import com.egyptexperiences.core.localization.generated.resources.trip_hosted_by
import com.egyptexperiences.core.localization.generated.resources.trip_itinerary
import com.egyptexperiences.core.localization.generated.resources.trip_look_out_for
import com.egyptexperiences.core.localization.generated.resources.trip_meeting_point
import com.egyptexperiences.core.localization.generated.resources.trip_per_person
import com.egyptexperiences.core.localization.generated.resources.trip_what_to_bring
import com.egyptexperiences.core.localization.generated.resources.trip_whats_included
import com.egyptexperiences.core.localization.isRetryable
import com.egyptexperiences.core.localization.localizedMessage
import com.egyptexperiences.feature.trips.model.TripDetailDto
import com.egyptexperiences.feature.trips.presentation.components.DeparturePicker
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TripDetailScreen(
    slug: String,
    onContinue: (departureId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TripDetailViewModel = koinViewModel { parametersOf(slug) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val trip = state.trip

    when {
        state.isLoading -> EgLoadingView(modifier)

        trip == null -> {
            val error = state.error
            EgErrorView(
                message = error?.localizedMessage().orEmpty(),
                retryLabel = stringResource(Res.string.action_retry),
                onRetry = if (error?.isRetryable == true) viewModel::load else null,
                modifier = modifier,
            )
        }

        else ->
            TripDetailContent(
                trip = trip,
                state = state,
                onSelectDeparture = viewModel::selectDeparture,
                onContinue = onContinue,
                modifier = modifier,
            )
    }
}

@Composable
private fun TripDetailContent(
    trip: TripDetailDto,
    state: TripDetailUiState,
    onSelectDeparture: (Long) -> Unit,
    onContinue: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val soldOutLabel = stringResource(Res.string.departure_sold_out)
    val gutter = EgTheme.spacings.screenGutter

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = EgTheme.spacings.x5l),
            verticalArrangement = Arrangement.spacedBy(EgTheme.spacings.x5l),
        ) {
            item {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(HeroAspectRatio)
                            .background(EgSurfaceSunken),
                ) {
                    AsyncImage(
                        model = trip.photoUrls.firstOrNull(),
                        contentDescription = trip.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = gutter),
                    verticalArrangement = Arrangement.spacedBy(EgTheme.spacings.md),
                ) {
                    trip.kicker?.let {
                        Text(text = it, style = EgTheme.typography.kicker, color = EgInkFaint)
                    }
                    Text(text = trip.title, style = EgTheme.typography.screenTitle)
                    Row(horizontalArrangement = Arrangement.spacedBy(EgTheme.spacings.sm)) {
                        Text(
                            text = MoneyFormatter.format(trip.pricePerPerson.toMoney()),
                            style = EgTheme.typography.price,
                            color = EgTeal,
                        )
                        Text(
                            text = stringResource(Res.string.trip_per_person),
                            style = EgTheme.typography.meta,
                            color = EgInkSubtle,
                        )
                    }
                    trip.deck?.let {
                        Text(text = it, style = EgTheme.typography.body, color = EgInkSubtle)
                    }
                }
            }

            if (trip.inclusions.isNotEmpty()) {
                item {
                    Section(title = stringResource(Res.string.trip_whats_included)) {
                        trip.inclusions.forEach { inclusion ->
                            Text(
                                text = inclusion.text,
                                style = EgTheme.typography.body,
                                color = if (inclusion.included) EgInkSubtle else EgInkFaint,
                                // An excluded line is struck through rather than
                                // dropped: knowing lunch is *not* included is
                                // exactly as useful as knowing it is.
                                textDecoration =
                                    if (inclusion.included) null else TextDecoration.LineThrough,
                            )
                        }
                    }
                }
            }

            if (trip.itinerary.isNotEmpty()) {
                item {
                    Section(title = stringResource(Res.string.trip_itinerary)) {
                        trip.itinerary.forEach { entry ->
                            Row(horizontalArrangement = Arrangement.spacedBy(EgTheme.spacings.xl)) {
                                Text(
                                    text = entry.time.orEmpty(),
                                    style = EgTheme.typography.meta,
                                    color = EgTeal,
                                )
                                Text(
                                    text = entry.text,
                                    style = EgTheme.typography.body,
                                    color = EgInkSubtle,
                                )
                            }
                        }
                    }
                }
            }

            trip.knowledge?.let { knowledge ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(EgTheme.spacings.x4l)) {
                        knowledge.whatToBring?.let {
                            Section(stringResource(Res.string.trip_what_to_bring)) { Body(it) }
                        }
                        knowledge.bestTimeOfYear?.let {
                            Section(stringResource(Res.string.trip_best_time)) { Body(it) }
                        }
                        knowledge.whatToLookOutFor?.let {
                            Section(stringResource(Res.string.trip_look_out_for)) { Body(it) }
                        }
                    }
                }
            }

            trip.host?.let { host ->
                item {
                    Section(title = stringResource(Res.string.trip_hosted_by, host.displayName)) {
                        if (host.verified) {
                            EgBadge(
                                text = stringResource(Res.string.trip_host_verified),
                                container = EgSuccessTint,
                                content = EgSuccess,
                            )
                        }
                        host.bio?.let { Body(it) }
                    }
                }
            }

            trip.meetingPoint?.let { point ->
                item {
                    Section(title = stringResource(Res.string.trip_meeting_point)) { Body(point) }
                }
            }

            if (state.departures.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(EgTheme.spacings.xl)) {
                        Text(
                            text = stringResource(Res.string.departures_pick_a_date),
                            style = EgTheme.typography.cardTitle,
                            modifier = Modifier.padding(horizontal = gutter),
                        )
                        DeparturePicker(
                            departures = state.departures,
                            selectedId = state.selectedDepartureId,
                            onSelect = onSelectDeparture,
                        )
                    }
                }
            }
        }

        val selected = state.selectedDeparture
        Column(modifier = Modifier.padding(gutter)) {
            EgPrimaryButton(
                text =
                    if (state.allSoldOut) {
                        soldOutLabel
                    } else {
                        stringResource(Res.string.booking_continue)
                    },
                enabled = selected != null && selected.bookable,
                onClick = { selected?.let { onContinue(it.id) } },
            )
        }
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = EgTheme.spacings.screenGutter),
        verticalArrangement = Arrangement.spacedBy(EgTheme.spacings.md),
    ) {
        Text(text = title, style = EgTheme.typography.cardTitle)
        content()
    }
}

@Composable
private fun Body(text: String) {
    Text(text = text, style = EgTheme.typography.body, color = EgInkSubtle)
}

/** The canvas hero is a 390x330 crop. */
private const val HeroAspectRatio = 390f / 330f
