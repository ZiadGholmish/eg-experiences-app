package eg.bahr.feature.hostworkspace.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.components.BahrBadge
import eg.bahr.core.designsystem.components.BahrEmptyView
import eg.bahr.core.designsystem.components.BahrErrorView
import eg.bahr.core.designsystem.components.BahrLoadingView
import eg.bahr.core.designsystem.components.BahrPrimaryButton
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.lifecycle.RefreshesOnReturn
import eg.bahr.core.designsystem.theme.BahrElevation
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrShadow
import eg.bahr.core.localization.currentAppLanguage
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.host_broadcast_cta
import eg.bahr.core.localization.generated.resources.host_broadcast_last
import eg.bahr.core.localization.generated.resources.host_broadcast_next_at
import eg.bahr.core.localization.generated.resources.host_broadcast_nobody
import eg.bahr.core.localization.generated.resources.host_broadcast_note
import eg.bahr.core.localization.generated.resources.host_broadcast_reached
import eg.bahr.core.localization.generated.resources.host_broadcast_sent
import eg.bahr.core.localization.generated.resources.host_closed_cancelled
import eg.bahr.core.localization.generated.resources.host_closed_completed
import eg.bahr.core.localization.generated.resources.host_collected
import eg.bahr.core.localization.generated.resources.host_dismiss
import eg.bahr.core.localization.generated.resources.host_legend
import eg.bahr.core.localization.generated.resources.host_legend_held
import eg.bahr.core.localization.generated.resources.host_list_empty
import eg.bahr.core.localization.generated.resources.host_list_note
import eg.bahr.core.localization.generated.resources.host_list_title
import eg.bahr.core.localization.generated.resources.host_manual_added
import eg.bahr.core.localization.generated.resources.host_manual_check_notice
import eg.bahr.core.localization.generated.resources.host_manual_open
import eg.bahr.core.localization.generated.resources.host_mark_paid_done
import eg.bahr.core.localization.generated.resources.host_mode_subtitle
import eg.bahr.core.localization.generated.resources.host_not_available_body
import eg.bahr.core.localization.generated.resources.host_not_available_title
import eg.bahr.core.localization.generated.resources.host_privacy
import eg.bahr.core.localization.generated.resources.host_seats_booked
import eg.bahr.core.localization.generated.resources.host_seats_capacity
import eg.bahr.core.localization.generated.resources.host_suspended_body
import eg.bahr.core.localization.generated.resources.host_suspended_title
import eg.bahr.core.localization.generated.resources.host_trip_line
import eg.bahr.core.localization.generated.resources.host_trip_line_return
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import eg.bahr.feature.hostworkspace.model.HostDepartureDto
import eg.bahr.feature.hostworkspace.model.HostDepartureStatus
import eg.bahr.feature.hostworkspace.model.HostTravellerDto
import eg.bahr.feature.hostworkspace.presentation.components.AVATAR_TONES
import eg.bahr.feature.hostworkspace.presentation.components.HostTopBar
import eg.bahr.feature.hostworkspace.presentation.components.SeatBar
import eg.bahr.feature.hostworkspace.presentation.components.StatTile
import eg.bahr.feature.hostworkspace.presentation.components.TravellerRow
import eg.bahr.feature.hostworkspace.presentation.components.cairoMoment
import eg.bahr.feature.hostworkspace.presentation.components.cairoTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Instant

/**
 * One of the host's dates (M5-M1, handoff §7 "Host's trip view"): the date and trip, the two stat tiles, the seat bar,
 * who is on the bus (newest first), and the sticky coral "Message all N bookings" with a quiet "Add a booking" under
 * it while the date is open. [onSignedOut]: host mode's sign-in ended while this page was open (back to its sign-in).
 */
@Composable
internal fun HostDepartureScreen(
    departureId: String,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: HostDepartureViewModel = koinViewModel { parametersOf(departureId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val signedOut by rememberUpdatedState(onSignedOut)
    LaunchedEffect(state.signedOut) { if (state.signedOut) signedOut() }
    RefreshesOnReturn(viewModel::reload)
    val language = currentAppLanguage
    // Resolved once here, not in every row (Compose performance: CompositionLocals out of lazy items).
    val uriHandler = LocalUriHandler.current
    val call = remember(uriHandler) { { phone: String -> runCatching { uriHandler.openUri("tel:$phone") } } }

    HostDepartureContent(
        state = state,
        actions =
            HostDepartureActions(
                onBack = onBack,
                onRetry = viewModel::reload,
                onCall = { call(it) },
                onBroadcast = viewModel::openBroadcast,
                onManual = { viewModel.openManualBooking(language.tag) },
                onMarkPaid = viewModel::openMarkPaid,
                onDismissNotice = viewModel::dismissNotice,
            ),
    )
    val departure = state.departure
    state.broadcast?.let { form ->
        BroadcastSheet(
            form = form,
            dayLabel = departure?.dayLabel.orEmpty(),
            bookings = departure?.bookings?.size ?: 0,
            actions =
                BroadcastActions(
                    onMessage = viewModel::onBroadcastMessage,
                    onReview = viewModel::reviewBroadcast,
                    onEdit = viewModel::editBroadcast,
                    onSend = viewModel::sendBroadcast,
                    onDismiss = viewModel::closeBroadcast,
                ),
        )
    }
    state.manual?.let { form ->
        ManualBookingSheet(
            form = form,
            dayLabel = departure?.dayLabel.orEmpty(),
            actions =
                ManualActions(
                    onName = viewModel::onManualName,
                    onPhone = viewModel::onManualPhone,
                    onParty = viewModel::onManualParty,
                    onPaid = viewModel::onManualPaid,
                    onLocale = viewModel::onManualLocale,
                    onReview = viewModel::reviewManual,
                    onEdit = viewModel::editManual,
                    onAdd = viewModel::addManualBooking,
                    onDismiss = viewModel::closeManual,
                ),
        )
    }
    state.markPaid?.let { form ->
        MarkPaidSheet(form = form, actions = MarkPaidActions(onConfirm = viewModel::confirmMarkPaid, onDismiss = viewModel::closeMarkPaid))
    }
}

internal class HostDepartureActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onCall: (String) -> Unit = {},
    val onBroadcast: () -> Unit = {},
    val onManual: () -> Unit = {},
    val onMarkPaid: (HostTravellerDto) -> Unit = {},
    val onDismissNotice: () -> Unit = {},
)

/** Stateless, for the screenshot tests. */
@Composable
internal fun HostDepartureContent(
    state: HostDepartureUiState,
    actions: HostDepartureActions,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // The page's own heading carries the date and trip (handoff §7); the bar only names host mode's view.
        HostTopBar(title = stringResource(Res.string.host_mode_subtitle), onBack = actions.onBack)
        when (val load = state.load) {
            ManifestLoad.Loading -> BahrLoadingView(Modifier.weight(1f))
            ManifestLoad.NotAvailable ->
                Message(
                    stringResource(Res.string.host_not_available_title),
                    stringResource(Res.string.host_not_available_body),
                    Modifier.weight(1f),
                )
            ManifestLoad.Suspended ->
                Message(
                    stringResource(Res.string.host_suspended_title),
                    stringResource(Res.string.host_suspended_body),
                    Modifier.weight(1f),
                )
            is ManifestLoad.Failed ->
                BahrErrorView(
                    message = load.error.localizedMessage(),
                    retryLabel = stringResource(Res.string.action_retry),
                    onRetry = if (load.error.isRetryable) actions.onRetry else null,
                    modifier = Modifier.weight(1f),
                )
            is ManifestLoad.Loaded -> {
                Manifest(load.departure, state, actions, Modifier.weight(1f))
                BottomActions(load.departure, state, actions)
            }
        }
    }
}

@Composable
private fun Manifest(
    departure: HostDepartureDto,
    state: HostDepartureUiState,
    actions: HostDepartureActions,
    modifier: Modifier = Modifier,
) {
    val arabic = currentAppLanguage == AppLanguage.ARABIC
    val canMarkPaid = state.open
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = BahrSpacing.gutter, end = BahrSpacing.gutter, bottom = BahrSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        item(key = KEY_HEADER, contentType = KEY_HEADER) { Header(departure) }
        state.notice?.let { notice ->
            item(key = KEY_NOTICE, contentType = KEY_NOTICE) { Notice(notice, actions.onDismissNotice) }
        }
        item(key = KEY_STATS, contentType = KEY_STATS) {
            Row(horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
                StatTile(
                    icon = BahrIcons.Groups,
                    label = stringResource(Res.string.host_seats_booked),
                    figure = departure.seatsBooked.toString(),
                    after = stringResource(Res.string.host_seats_capacity, departure.capacity),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    icon = BahrIcons.Payments,
                    label = stringResource(Res.string.host_collected),
                    // Formatted only: the server added it up (rule 7).
                    figure = BahrFormat.money(departure.collected.amount, departure.collected.currency, arabic),
                    after = null,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item(key = KEY_SEATS, contentType = KEY_SEATS) {
            // A display hint (rule 3): the seats free now are the server's to decide when a booking is entered.
            val free = (departure.capacity - departure.seatsBooked - departure.seatsHeld).coerceAtLeast(0)
            val legend =
                if (departure.seatsHeld > 0) {
                    stringResource(Res.string.host_legend_held, departure.seatsBooked, departure.seatsHeld, free)
                } else {
                    stringResource(Res.string.host_legend, departure.seatsBooked, free)
                }
            Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
                SeatBar(booked = departure.seatsBooked, held = departure.seatsHeld, capacity = departure.capacity, description = legend)
                Text(legend, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        item(key = KEY_LIST_HEADER, contentType = KEY_LIST_HEADER) {
            Column(Modifier.padding(top = BahrSpacing.sm), verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.host_list_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    Text(
                        stringResource(Res.string.host_list_note),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(Res.string.host_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (departure.bookings.isEmpty()) {
            item(key = KEY_EMPTY, contentType = KEY_EMPTY) {
                BahrEmptyView(message = stringResource(Res.string.host_list_empty), modifier = Modifier.padding(vertical = BahrSpacing.xl))
            }
        }
        itemsIndexed(departure.bookings, key = { _, it -> it.ref }, contentType = { _, _ -> KEY_TRAVELLER }) { index, traveller ->
            TravellerRow(
                traveller = traveller,
                tone = AVATAR_TONES[index % AVATAR_TONES.size],
                canMarkPaid = canMarkPaid && traveller.manual && !traveller.paid && traveller.id != null,
                onCall = actions.onCall,
                onMarkPaid = { actions.onMarkPaid(traveller) },
            )
        }
    }
}

@Composable
private fun Header(departure: HostDepartureDto) {
    Column(Modifier.padding(top = BahrSpacing.xs), verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
        statusBadge(departure.status)?.let { BahrBadge(props = it) }
        Text(
            text = departure.dayLabel,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        val times = departure.trip
        Text(
            text =
                times.returnTime?.let {
                    stringResource(Res.string.host_trip_line_return, times.title, BahrFormat.ltr(times.departTime), BahrFormat.ltr(it))
                } ?: stringResource(Res.string.host_trip_line, times.title, BahrFormat.ltr(times.departTime)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Notice(
    notice: ManifestNotice,
    onDismiss: () -> Unit,
) {
    val warning = notice == ManifestNotice.CheckList
    val text =
        when (notice) {
            ManifestNotice.CheckList -> stringResource(Res.string.host_manual_check_notice)
            is ManifestNotice.BookingAdded -> stringResource(Res.string.host_manual_added, notice.name)
            is ManifestNotice.MarkedPaid -> stringResource(Res.string.host_mark_paid_done, notice.name)
            is ManifestNotice.BroadcastSent ->
                notice.nextAllowedAt?.let {
                    stringResource(Res.string.host_broadcast_sent, notice.recipients, BahrFormat.ltr(cairoTime(it)))
                } ?: pluralStringResource(Res.plurals.host_broadcast_reached, notice.recipients, notice.recipients)
        }
    val container = if (warning) MaterialTheme.colorScheme.secondaryContainer else BahrTheme.colors.successContainer
    val content = if (warning) MaterialTheme.colorScheme.onSecondaryContainer else BahrTheme.colors.onSuccessContainer
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.large)
                .background(container)
                .padding(start = BahrSpacing.md, top = BahrSpacing.xs, bottom = BahrSpacing.xs)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(NOTICE_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        Icon(
            (if (warning) BahrIcons.Info else BahrIcons.CheckCircle).outlined(),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(BahrSize.iconMedium),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = content, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss, modifier = Modifier.size(BahrSize.touchTarget)) {
            Icon(
                BahrIcons.Close.outlined(),
                contentDescription = stringResource(Res.string.host_dismiss),
                tint = content,
                modifier = Modifier.size(BahrSize.iconMedium),
            )
        }
    }
}

/**
 * The sticky foot (handoff §7): on an open date the coral broadcast and its note, then "Add a booking" outlined (coral
 * is for the one primary action); on a called-off or completed date only why nothing more can be done.
 */
@Composable
private fun BottomActions(
    departure: HostDepartureDto,
    state: HostDepartureUiState,
    actions: HostDepartureActions,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .bahrShadow(BahrElevation.Sticky, RectangleShape, BahrTheme.colors)
                .background(BahrTheme.colors.surfaceTranslucent)
                .navigationBarsPadding()
                .padding(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.md),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        if (departure.status != HostDepartureStatus.OPEN) {
            Text(
                text =
                    stringResource(
                        if (departure.status ==
                            HostDepartureStatus.CANCELLED
                        ) {
                            Res.string.host_closed_cancelled
                        } else {
                            Res.string.host_closed_completed
                        },
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }
        val count = departure.bookings.size
        val locked = state.broadcastLockedUntil
        BahrPrimaryButton(
            text = pluralStringResource(Res.plurals.host_broadcast_cta, count, count),
            onClick = actions.onBroadcast,
            leadingIcon = BahrIcons.Campaign.outlined(),
            enabled = count > 0 && locked == null,
            modifier = Modifier.fillMaxWidth().testTag(BROADCAST_CTA_TAG),
        )
        Text(
            text = broadcastNote(departure, count, locked),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HostOutlinedButton(stringResource(Res.string.host_manual_open), actions.onManual, Modifier.testTag(MANUAL_OPEN_TAG))
    }
}

@Composable
private fun broadcastNote(
    departure: HostDepartureDto,
    count: Int,
    locked: Instant?,
): String {
    val last = departure.broadcast?.let { parseInstant(it.lastSentAt) }
    return when {
        count == 0 -> stringResource(Res.string.host_broadcast_nobody)
        locked != null -> stringResource(Res.string.host_broadcast_next_at, BahrFormat.ltr(cairoTime(locked)))
        last != null -> stringResource(Res.string.host_broadcast_last, cairoMoment(last))
        else -> stringResource(Res.string.host_broadcast_note)
    }
}

/** Not available, or suspended: a title and a line, the top bar's back the way out. */
@Composable
private fun Message(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(BahrSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            BahrIcons.EventBusy.outlined(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(BahrSize.iconState),
        )
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(body, style = MaterialTheme.typography.bodyLarge, color = BahrTheme.colors.onSurfaceSecondary, textAlign = TextAlign.Center)
    }
}

internal const val BROADCAST_CTA_TAG = "host_broadcast_cta"
internal const val MANUAL_OPEN_TAG = "host_manual_open"
internal const val NOTICE_TAG = "host_notice"
private const val KEY_HEADER = "header"
private const val KEY_NOTICE = "notice"
private const val KEY_STATS = "stats"
private const val KEY_SEATS = "seats"
private const val KEY_LIST_HEADER = "list_header"
private const val KEY_EMPTY = "empty"
private const val KEY_TRAVELLER = "traveller"
