package eg.bahr.feature.trips.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import eg.bahr.core.designsystem.components.BahrBadge
import eg.bahr.core.designsystem.components.BahrErrorView
import eg.bahr.core.designsystem.components.BahrLoadingView
import eg.bahr.core.designsystem.components.BahrPrimaryButton
import eg.bahr.core.designsystem.components.ImageGround
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.booking_continue
import eg.bahr.core.localization.generated.resources.departure_sold_out
import eg.bahr.core.localization.generated.resources.departures_pick_a_date
import eg.bahr.core.localization.generated.resources.trip_best_time
import eg.bahr.core.localization.generated.resources.trip_host_verified
import eg.bahr.core.localization.generated.resources.trip_hosted_by
import eg.bahr.core.localization.generated.resources.trip_itinerary
import eg.bahr.core.localization.generated.resources.trip_look_out_for
import eg.bahr.core.localization.generated.resources.trip_meeting_point
import eg.bahr.core.localization.generated.resources.trip_per_person
import eg.bahr.core.localization.generated.resources.trip_what_to_bring
import eg.bahr.core.localization.generated.resources.trip_whats_included
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import eg.bahr.feature.trips.model.TripDetailDto
import eg.bahr.feature.trips.presentation.components.DeparturePicker
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
        state.isLoading -> BahrLoadingView(modifier)

        trip == null -> {
            val error = state.error
            BahrErrorView(
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
    val gutter = BahrSpacing.gutter

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = BahrSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(BahrSpacing.xl),
        ) {
            item {
                // surfaceDim is painted first, so the text below never waits on the photo.
                ImageGround(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(HeroAspectRatio),
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
                    verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
                ) {
                    trip.kicker?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(text = trip.title, style = MaterialTheme.typography.headlineMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
                        Text(
                            text =
                                BahrFormat.money(
                                    trip.pricePerPerson.amount,
                                    trip.pricePerPerson.currencyCode,
                                    BahrTheme.locale.isArabic,
                                ),
                            style = BahrTheme.type.price,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.alignByBaseline(),
                        )
                        Text(
                            text = stringResource(Res.string.trip_per_person),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                    trip.deck?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (trip.inclusions.isNotEmpty()) {
                item {
                    Section(title = stringResource(Res.string.trip_whats_included)) {
                        trip.inclusions.forEach { inclusion ->
                            Text(
                                text = inclusion.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color =
                                    if (inclusion.included) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        BahrTheme.colors.onSurfaceDisabled
                                    },
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
                            Row(horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
                                Text(
                                    text = entry.time.orEmpty(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = entry.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            trip.knowledge?.let { knowledge ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.xl)) {
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
                            BahrBadge(
                                props =
                                    BahrBadge.Props(
                                        text = stringResource(Res.string.trip_host_verified),
                                        tone = BahrBadge.Tone.Success,
                                    ),
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
                    Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
                        Text(
                            text = stringResource(Res.string.departures_pick_a_date),
                            style = MaterialTheme.typography.titleMedium,
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
            BahrPrimaryButton(
                text =
                    if (state.allSoldOut) {
                        soldOutLabel
                    } else {
                        stringResource(Res.string.booking_continue)
                    },
                enabled = selected != null && selected.bookable,
                onClick = { selected?.let { onContinue(it.id) } },
                modifier = Modifier.fillMaxWidth(),
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
        modifier = Modifier.padding(horizontal = BahrSpacing.gutter),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The canvas hero is a 390x330 crop. */
private const val HeroAspectRatio = 390f / 330f
