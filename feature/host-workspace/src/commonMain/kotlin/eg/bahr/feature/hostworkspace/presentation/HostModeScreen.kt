package eg.bahr.feature.hostworkspace.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.designsystem.components.BahrBadge
import eg.bahr.core.designsystem.components.BahrEmptyView
import eg.bahr.core.designsystem.components.BahrErrorView
import eg.bahr.core.designsystem.components.BahrLoadingView
import eg.bahr.core.designsystem.components.BahrNavigationBar
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.lifecycle.RefreshesOnReturn
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.host_day_time
import eg.bahr.core.localization.generated.resources.host_departure_status_cancelled
import eg.bahr.core.localization.generated.resources.host_departure_status_completed
import eg.bahr.core.localization.generated.resources.host_departures_body
import eg.bahr.core.localization.generated.resources.host_departures_empty
import eg.bahr.core.localization.generated.resources.host_departures_title
import eg.bahr.core.localization.generated.resources.host_mode_exit
import eg.bahr.core.localization.generated.resources.host_mode_subtitle
import eg.bahr.core.localization.generated.resources.host_mode_title
import eg.bahr.core.localization.generated.resources.host_next_departure
import eg.bahr.core.localization.generated.resources.host_not_active_body
import eg.bahr.core.localization.generated.resources.host_not_active_title
import eg.bahr.core.localization.generated.resources.host_profile_email
import eg.bahr.core.localization.generated.resources.host_profile_kind
import eg.bahr.core.localization.generated.resources.host_profile_kind_company
import eg.bahr.core.localization.generated.resources.host_profile_kind_person
import eg.bahr.core.localization.generated.resources.host_profile_locked_until
import eg.bahr.core.localization.generated.resources.host_profile_login
import eg.bahr.core.localization.generated.resources.host_profile_login_active
import eg.bahr.core.localization.generated.resources.host_profile_login_disabled
import eg.bahr.core.localization.generated.resources.host_profile_login_locked
import eg.bahr.core.localization.generated.resources.host_profile_member_since
import eg.bahr.core.localization.generated.resources.host_profile_phone
import eg.bahr.core.localization.generated.resources.host_profile_read_only
import eg.bahr.core.localization.generated.resources.host_profile_trips_run
import eg.bahr.core.localization.generated.resources.host_profile_verified
import eg.bahr.core.localization.generated.resources.host_seats_of
import eg.bahr.core.localization.generated.resources.host_seats_of_held
import eg.bahr.core.localization.generated.resources.host_sign_out
import eg.bahr.core.localization.generated.resources.host_suspended_body
import eg.bahr.core.localization.generated.resources.host_suspended_title
import eg.bahr.core.localization.generated.resources.host_tab_departures
import eg.bahr.core.localization.generated.resources.tab_profile
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.hostworkspace.model.HostDepartureStatus
import eg.bahr.feature.hostworkspace.model.HostDepartureSummaryDto
import eg.bahr.feature.hostworkspace.model.HostMeDto
import eg.bahr.feature.hostworkspace.presentation.components.InitialDisc
import eg.bahr.feature.hostworkspace.presentation.components.cairoMoment
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Host mode's own two tabs (M5-M1: Departures · Profile), drawn by host mode, not the traveller's bar. */
internal enum class HostTab { Departures, Profile }

/**
 * Host mode (M5-M1), pushed from Profile: the sign-in while host mode's own session is signed out, else the host's
 * shell with its own bottom bar (Departures · Profile). [onExit] leaves host mode for the traveller app (the traveller's
 * sign-in was never touched); [onOpenDeparture] opens one date's manifest on top.
 */
@Composable
internal fun HostModeScreen(
    onExit: () -> Unit,
    onOpenDeparture: (departureId: String) -> Unit,
    home: HostHomeViewModel = koinViewModel(),
    signIn: HostSignInViewModel = koinViewModel(),
) {
    val state by home.uiState.collectAsStateWithLifecycle()
    when (state.session) {
        SessionState.Unknown -> BahrLoadingView(Modifier.background(MaterialTheme.colorScheme.surface))
        SessionState.SignedOut -> {
            val form by signIn.uiState.collectAsStateWithLifecycle()
            HostSignInContent(
                state = form,
                actions =
                    HostSignInActions(
                        onExit = onExit,
                        onEmail = signIn::onEmail,
                        onPassword = signIn::onPassword,
                        onTogglePassword = signIn::togglePasswordShown,
                        onSignIn = signIn::signIn,
                        onNewPassword = signIn::onNewPassword,
                        onConfirmPassword = signIn::onConfirmPassword,
                        onChangePassword = signIn::changePassword,
                        onOtherAccount = signIn::backToCredentials,
                    ),
            )
        }
        is SessionState.SignedIn -> {
            RefreshesOnReturn(home::refresh)
            var tab by rememberSaveable { mutableStateOf(HostTab.Departures) }
            HostHomeContent(
                state = state,
                tab = tab,
                actions =
                    HostHomeActions(
                        onTab = { tab = it },
                        onExit = onExit,
                        onOpenDeparture = onOpenDeparture,
                        onRetry = home::refresh,
                        onSignOut = home::signOut,
                    ),
            )
        }
    }
}

internal class HostHomeActions(
    val onTab: (HostTab) -> Unit = {},
    val onExit: () -> Unit = {},
    val onOpenDeparture: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onSignOut: () -> Unit = {},
)

/** Signed in: who the host is decides what shows. Stateless, for the screenshot tests. */
@Composable
internal fun HostHomeContent(
    state: HostHomeUiState,
    tab: HostTab,
    actions: HostHomeActions,
    windowInsets: Boolean = true,
) {
    val surface = MaterialTheme.colorScheme.surface
    when (val access = state.access) {
        HostAccess.Loading -> BahrLoadingView(Modifier.background(surface))
        HostAccess.Suspended ->
            HostBlocked(stringResource(Res.string.host_suspended_title), stringResource(Res.string.host_suspended_body), state, actions)
        HostAccess.NotActive ->
            HostBlocked(
                stringResource(Res.string.host_not_active_title),
                stringResource(Res.string.host_not_active_body),
                state,
                actions,
            )
        is HostAccess.Failed ->
            Column(Modifier.fillMaxSize().background(surface).windowInsetsPadding(WindowInsets.statusBars)) {
                HomeHeader(name = null, onExit = actions.onExit)
                BahrErrorView(
                    message = access.error.localizedMessage(),
                    retryLabel = stringResource(Res.string.action_retry),
                    onRetry = if (access.error.isRetryable) actions.onRetry else null,
                    modifier = Modifier.weight(1f),
                )
            }
        is HostAccess.Ready ->
            Column(Modifier.fillMaxSize().background(surface)) {
                Column(Modifier.weight(1f).windowInsetsPadding(WindowInsets.statusBars)) {
                    HomeHeader(name = access.me.name, onExit = actions.onExit)
                    when (tab) {
                        HostTab.Departures -> DeparturesPane(state.departures, actions, Modifier.weight(1f))
                        HostTab.Profile -> ProfilePane(access.me, state.signingOut, actions, Modifier.weight(1f))
                    }
                }
                BahrNavigationBar(
                    props =
                        BahrNavigationBar.Props(
                            items =
                                listOf(
                                    BahrNavigationBar.Item(
                                        label = stringResource(Res.string.host_tab_departures),
                                        icon = BahrIcons.EventUpcoming,
                                        selected = tab == HostTab.Departures,
                                        testTag = HOST_TAB_DEPARTURES_TAG,
                                    ),
                                    BahrNavigationBar.Item(
                                        label = stringResource(Res.string.tab_profile),
                                        icon = BahrIcons.Person,
                                        selected = tab == HostTab.Profile,
                                        testTag = HOST_TAB_PROFILE_TAG,
                                    ),
                                ),
                            onSelect = { index -> actions.onTab(HostTab.entries[index]) },
                            windowInsets = windowInsets,
                        ),
                )
            }
    }
}

/** The handoff's host header: the initials disc, the name, "Host mode" under it, and the way back at the end. */
@Composable
private fun HomeHeader(
    name: String?,
    onExit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = BahrSize.topBar).padding(start = BahrSpacing.gutter, end = BahrSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        name?.let {
            InitialDisc(
                name = it,
                size = BahrSize.avatarSmall,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = name ?: stringResource(Res.string.host_mode_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (name != null) {
                Text(
                    text = stringResource(Res.string.host_mode_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onExit, modifier = Modifier.size(BahrSize.touchTarget).testTag(HOST_EXIT_TAG)) {
            Icon(
                BahrIcons.Close.outlined(),
                contentDescription = stringResource(Res.string.host_mode_exit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(BahrSize.iconNav),
            )
        }
    }
}

/** The departure picker: every date from today, by day then time; the first open one is the "Next departure". */
@Composable
private fun DeparturesPane(
    departures: HostDepartures,
    actions: HostHomeActions,
    modifier: Modifier = Modifier,
) {
    when (departures) {
        HostDepartures.Loading -> BahrLoadingView(modifier)
        is HostDepartures.Failed ->
            BahrErrorView(
                message = departures.error.localizedMessage(),
                retryLabel = stringResource(Res.string.action_retry),
                onRetry = if (departures.error.isRetryable) actions.onRetry else null,
                modifier = modifier,
            )
        is HostDepartures.Loaded -> {
            val next = departures.items.firstOrNull { it.status == HostDepartureStatus.OPEN }?.id
            LazyColumn(
                modifier = modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = BahrSpacing.gutter, end = BahrSpacing.gutter, bottom = BahrSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
            ) {
                item(key = PANE_HEADER_KEY, contentType = PANE_HEADER_KEY) {
                    Column(Modifier.padding(top = BahrSpacing.sm), verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
                        Text(
                            text = stringResource(Res.string.host_departures_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = stringResource(Res.string.host_departures_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = BahrTheme.colors.onSurfaceSecondary,
                        )
                    }
                }
                if (departures.items.isEmpty()) {
                    item(key = PANE_EMPTY_KEY, contentType = PANE_EMPTY_KEY) {
                        BahrEmptyView(
                            message = stringResource(Res.string.host_departures_empty),
                            modifier = Modifier.heightIn(min = BahrSize.lottieState),
                        )
                    }
                }
                items(departures.items, key = { it.id }, contentType = { DEPARTURE_ROW }) { item ->
                    DepartureRow(item, next = item.id == next, onClick = { actions.onOpenDeparture(item.id) })
                }
            }
        }
    }
}

@Composable
private fun DepartureRow(
    item: HostDepartureSummaryDto,
    next: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.large)
                .background(BahrTheme.colors.surfaceLow)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(BahrSpacing.lg)
                .testTag(departureRowTag(item.id)),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
            Text(
                // "Sat 21 Nov · 05:00": the server's localised day, then the bus time left to right.
                text = stringResource(Res.string.host_day_time, item.dayLabel, BahrFormat.ltr(item.departTime)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            statusBadge(item.status)?.let { BahrBadge(props = it) }
            if (next && item.status == HostDepartureStatus.OPEN) {
                BahrBadge(props = BahrBadge.Props(stringResource(Res.string.host_next_departure), BahrBadge.Tone.Primary))
            }
        }
        Text(
            text = item.tripTitle,
            style = MaterialTheme.typography.bodyMedium,
            color = BahrTheme.colors.onSurfaceSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text =
                if (item.seatsHeld > 0) {
                    stringResource(Res.string.host_seats_of_held, item.seatsBooked, item.capacity, item.seatsHeld)
                } else {
                    stringResource(Res.string.host_seats_of, item.seatsBooked, item.capacity)
                },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** A date that no longer runs says so; an open one needs no badge. */
@Composable
internal fun statusBadge(status: String): BahrBadge.Props? =
    when (status) {
        HostDepartureStatus.CANCELLED ->
            BahrBadge.Props(
                stringResource(Res.string.host_departure_status_cancelled),
                BahrBadge.Tone.Tertiary,
            )
        HostDepartureStatus.COMPLETED ->
            BahrBadge.Props(
                stringResource(Res.string.host_departure_status_completed),
                BahrBadge.Tone.Secondary,
            )
        else -> null
    }

/** The host's own details as Bahr keeps them (read only), the way back to the traveller app, and sign out. */
@Composable
private fun ProfilePane(
    me: HostMeDto,
    signingOut: Boolean,
    actions: HostHomeActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = BahrSpacing.gutter, end = BahrSpacing.gutter, top = BahrSpacing.sm, bottom = BahrSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
            InitialDisc(
                name = me.name,
                size = BahrSize.avatarProfile,
                container = BahrTheme.colors.quaternaryContainer,
                content = BahrTheme.colors.onQuaternaryContainer,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
                Text(
                    text = me.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                me.role?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (me.verified == true) {
                    BahrBadge(props = BahrBadge.Props(stringResource(Res.string.host_profile_verified), BahrBadge.Tone.Success))
                }
            }
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(BahrTheme.shapes.large)
                    .background(BahrTheme.colors.surfaceLow)
                    .padding(BahrSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
        ) {
            Fact(stringResource(Res.string.host_profile_email), BahrFormat.ltr(me.email))
            me.phone?.let { Fact(stringResource(Res.string.host_profile_phone), BahrFormat.ltr(it)) }
            me.kind?.let {
                val kind = if (it == KIND_COMPANY) Res.string.host_profile_kind_company else Res.string.host_profile_kind_person
                Fact(stringResource(Res.string.host_profile_kind), stringResource(kind))
            }
            me.memberSince?.let { since ->
                runCatching { LocalDate.parse(since) }.getOrNull()?.let {
                    Fact(stringResource(Res.string.host_profile_member_since), BahrFormat.dayMonthYear(it))
                }
            }
            me.tripsRun?.let { Fact(stringResource(Res.string.host_profile_trips_run), it.toString()) }
            me.login?.let { login ->
                val status =
                    when (login.status) {
                        LOGIN_LOCKED -> Res.string.host_profile_login_locked
                        LOGIN_DISABLED -> Res.string.host_profile_login_disabled
                        else -> Res.string.host_profile_login_active
                    }
                Fact(stringResource(Res.string.host_profile_login), stringResource(status))
                login.lockedUntil?.let(::parseInstant)?.let { Fact(stringResource(Res.string.host_profile_locked_until), cairoMoment(it)) }
            }
        }
        Text(
            text = stringResource(Res.string.host_profile_read_only),
            style = MaterialTheme.typography.bodyMedium,
            color = BahrTheme.colors.onSurfaceSecondary,
        )
        HostOutlinedButton(stringResource(Res.string.host_mode_exit), actions.onExit, Modifier.testTag(HOST_PROFILE_EXIT_TAG))
        TextButton(
            onClick = actions.onSignOut,
            enabled = !signingOut,
            modifier = Modifier.fillMaxWidth().heightIn(min = BahrSize.touchTarget).testTag(HOST_SIGN_OUT_TAG),
        ) {
            Text(
                stringResource(Res.string.host_sign_out),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun Fact(
    label: String,
    value: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Suspended, or no approved host behind the account: said plainly, with sign out and the way back; nothing else. */
@Composable
private fun HostBlocked(
    title: String,
    body: String,
    state: HostHomeUiState,
    actions: HostHomeActions,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.statusBars)) {
        HomeHeader(name = null, onExit = actions.onExit)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(BahrSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(BahrSpacing.md, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(
                            BahrSize.stateDisc,
                        ).clip(BahrTheme.shapes.full)
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Icon(
                    BahrIcons.Block.outlined(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(BahrSize.iconState),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = BahrTheme.colors.onSurfaceSecondary,
                textAlign = TextAlign.Center,
            )
            HostOutlinedButton(stringResource(Res.string.host_mode_exit), actions.onExit)
            TextButton(onClick = actions.onSignOut, enabled = !state.signingOut, modifier = Modifier.testTag(HOST_SIGN_OUT_TAG)) {
                Text(
                    stringResource(Res.string.host_sign_out),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** A way that is not the screen's step forward: outlined, never coral (the bottom sheets' cancel style). */
@Composable
internal fun HostOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = BahrTheme.shapes.full,
        modifier = modifier.fillMaxWidth().heightIn(min = BahrSize.touchTarget),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

internal fun departureRowTag(id: String) = "host_departure_$id"

internal const val HOST_TAB_DEPARTURES_TAG = "host_tab_departures"
internal const val HOST_TAB_PROFILE_TAG = "host_tab_profile"
internal const val HOST_EXIT_TAG = "host_mode_exit"
internal const val HOST_PROFILE_EXIT_TAG = "host_profile_exit"
internal const val HOST_SIGN_OUT_TAG = "host_sign_out"
private const val PANE_HEADER_KEY = "header"
private const val PANE_EMPTY_KEY = "empty"
private const val DEPARTURE_ROW = "departure"
private const val KIND_COMPANY = "company"
private const val LOGIN_LOCKED = "LOCKED"
private const val LOGIN_DISABLED = "DISABLED"
