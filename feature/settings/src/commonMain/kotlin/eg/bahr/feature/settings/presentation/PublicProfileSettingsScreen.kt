package eg.bahr.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.catalog.presentation.components.ListTopBar
import eg.bahr.core.catalog.presentation.components.TextSkeleton
import eg.bahr.core.designsystem.components.BahrErrorView
import eg.bahr.core.designsystem.components.BahrSkeleton
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrBorder
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.public_profile_full_name
import eg.bahr.core.localization.generated.resources.public_profile_full_name_hint
import eg.bahr.core.localization.generated.resources.public_profile_intro
import eg.bahr.core.localization.generated.resources.public_profile_likes
import eg.bahr.core.localization.generated.resources.public_profile_master
import eg.bahr.core.localization.generated.resources.public_profile_master_hint
import eg.bahr.core.localization.generated.resources.public_profile_needs_public
import eg.bahr.core.localization.generated.resources.public_profile_past_trips
import eg.bahr.core.localization.generated.resources.public_profile_past_trips_hint
import eg.bahr.core.localization.generated.resources.public_profile_private_note
import eg.bahr.core.localization.generated.resources.public_profile_reviews
import eg.bahr.core.localization.generated.resources.public_profile_reviews_hint
import eg.bahr.core.localization.generated.resources.public_profile_save_failed
import eg.bahr.core.localization.generated.resources.public_profile_shows
import eg.bahr.core.localization.generated.resources.public_profile_title
import eg.bahr.core.localization.generated.resources.public_profile_view
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import eg.bahr.feature.settings.model.ProfileVisibilityDto
import eg.bahr.feature.settings.presentation.components.ProfileRow
import eg.bahr.feature.settings.presentation.components.ProfileRowGroup
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Account → Public profile (MP-M2; undesigned, on Profile's look: `surface` page, `surfaceLow` groups).
 *
 * - The intro, short and plain (PDPL: opt-in, "Only you decide…"), then the master switch with what it means.
 * - "What it shows": past trips, liked trips, reviews, full name and photo, each with a line on what on/off does.
 *   Past trips, likes and full name wait for the master switch (a line says so); reviews never wait, since it also
 *   decides how the reviews are signed on the trips (see [PublicProfileSettingsUiState.enabled]).
 * - "View my public profile" ([onViewProfile] with the profile's id): only once the server says the profile is
 *   public, since a private one answers 404. While private it is disabled under a note that says why.
 *
 * Every switch moves at once and is saved alone; a failed save puts it back and a snackbar says so. Signed out
 * meanwhile (a 401 nothing could cure), the page leaves: Profile under it shows the signed-out card.
 */
@Composable
internal fun PublicProfileSettingsScreen(
    onBack: () -> Unit,
    onViewProfile: (publicId: String) -> Unit,
    viewModel: PublicProfileSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val leave by rememberUpdatedState(onBack)
    LaunchedEffect(state.signedOut) { if (state.signedOut) leave() }

    val snackbar = remember { SnackbarHostState() }
    val failed = stringResource(Res.string.public_profile_save_failed)
    // Cleared only once the snackbar has gone, as Profile's "Saved" (M3-M0b review #1).
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            try {
                snackbar.showSnackbar(failed)
            } finally {
                viewModel.saveFailedShown()
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        PublicProfileSettingsContent(
            state = state,
            actions =
                PublicProfileActions(
                    onBack = onBack,
                    onToggle = viewModel::toggle,
                    onViewProfile = onViewProfile,
                    onRetry = viewModel::retry,
                ),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(BahrSpacing.gutter)) { data ->
            Snackbar(
                shape = BahrTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) { Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

internal class PublicProfileActions(
    val onBack: () -> Unit = {},
    val onToggle: (ProfileSwitch, Boolean) -> Unit = { _, _ -> },
    val onViewProfile: (publicId: String) -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** Stateless, for the screenshot tests. */
@Composable
internal fun PublicProfileSettingsContent(
    state: PublicProfileSettingsUiState,
    actions: PublicProfileActions,
) {
    val error = state.error
    val shown = state.shown
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        ListTopBar(onBack = actions.onBack)
        when {
            shown != null -> Settings(state, shown, actions)
            error != null ->
                BahrErrorView(
                    message = error.localizedMessage(),
                    retryLabel = stringResource(Res.string.action_retry),
                    onRetry = if (error.isRetryable) actions.onRetry else null,
                )
            else ->
                BahrSkeleton(Modifier.padding(horizontal = BahrSpacing.gutter).testTag(PUBLIC_PROFILE_LOADING_TAG)) {
                    Heading()
                    TextSkeleton(lines = SKELETON_LINES)
                }
        }
    }
}

@Composable
private fun Settings(
    state: PublicProfileSettingsUiState,
    shown: ProfileVisibilityDto,
    actions: PublicProfileActions,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BahrSpacing.gutter)
                .padding(bottom = BahrSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        Heading()
        Text(
            text = stringResource(Res.string.public_profile_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = BahrTheme.colors.onSurfaceSecondary,
        )
        SwitchGroup(listOf(ProfileSwitch.Public), state, shown, actions.onToggle)

        Text(
            text = stringResource(Res.string.public_profile_shows),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = BahrSpacing.sm).semantics { heading() },
        )
        if (!shown.isPublic) Note(Res.string.public_profile_needs_public, Modifier.testTag(PUBLIC_PROFILE_NEEDS_PUBLIC_TAG))
        SwitchGroup(SUB_SWITCHES, state, shown, actions.onToggle)

        ProfileRowGroup(
            listOf(
                ProfileRow(
                    icon = BahrIcons.Visibility,
                    label = stringResource(Res.string.public_profile_view),
                    tag = PUBLIC_PROFILE_VIEW_TAG,
                    onClick = { actions.onViewProfile(shown.publicId) },
                    enabled = state.canViewProfile,
                ),
            ),
            modifier = Modifier.padding(top = BahrSpacing.sm),
        )
        if (!state.canViewProfile) Note(Res.string.public_profile_private_note, Modifier.testTag(PUBLIC_PROFILE_PRIVATE_NOTE_TAG))
    }
}

@Composable
private fun Heading() {
    Text(
        text = stringResource(Res.string.public_profile_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
}

/** Switch rows on `surfaceLow`, as Profile's row groups, with a hairline between them. */
@Composable
private fun SwitchGroup(
    switches: List<ProfileSwitch>,
    state: PublicProfileSettingsUiState,
    shown: ProfileVisibilityDto,
    onToggle: (ProfileSwitch, Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().clip(BahrTheme.shapes.extraLarge).background(BahrTheme.colors.surfaceLow)) {
        switches.forEachIndexed { index, switch ->
            if (index > 0) HorizontalDivider(thickness = BahrBorder.hairline, color = MaterialTheme.colorScheme.outlineVariant)
            SwitchRow(switch, checked = shown.isOn(switch), enabled = state.enabled(switch), onToggle = onToggle)
        }
    }
}

/** The whole row toggles (at least 48dp high), read out once as a switch with its label; the hint is under the label. */
@Composable
private fun SwitchRow(
    switch: ProfileSwitch,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (ProfileSwitch, Boolean) -> Unit,
) {
    val (label, hint) = switch.texts()
    val c = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = BahrSize.touchTarget)
                .toggleable(value = checked, enabled = enabled, role = Role.Switch) { onToggle(switch, it) }
                .testTag(switchTag(switch))
                .padding(horizontal = BahrSpacing.lg, vertical = BahrSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = if (enabled) c.onSurface else c.onSurfaceVariant,
            )
            hint?.let {
                Text(text = stringResource(it), style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
            }
        }
        // The row is the control; the switch only draws its state.
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors =
                SwitchDefaults.colors(
                    checkedTrackColor = c.primary,
                    checkedThumbColor = c.onPrimary,
                    // The page's ground rather than M3's tinted track, so "off" reads the same on every row.
                    uncheckedTrackColor = c.surface,
                ),
        )
    }
}

@Composable
private fun Note(
    text: StringResource,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = BahrSpacing.xs),
    )
}

private fun ProfileSwitch.texts(): Pair<StringResource, StringResource?> =
    when (this) {
        ProfileSwitch.Public -> Res.string.public_profile_master to Res.string.public_profile_master_hint
        ProfileSwitch.PastTrips -> Res.string.public_profile_past_trips to Res.string.public_profile_past_trips_hint
        ProfileSwitch.Likes -> Res.string.public_profile_likes to null
        ProfileSwitch.Reviews -> Res.string.public_profile_reviews to Res.string.public_profile_reviews_hint
        ProfileSwitch.FullNameAndPhoto -> Res.string.public_profile_full_name to Res.string.public_profile_full_name_hint
    }

private val SUB_SWITCHES = listOf(ProfileSwitch.PastTrips, ProfileSwitch.Likes, ProfileSwitch.Reviews, ProfileSwitch.FullNameAndPhoto)

/** For tests: a switch's row, the states and the button. */
internal fun switchTag(switch: ProfileSwitch): String = "public_profile_switch_${switch.name}"

internal const val PUBLIC_PROFILE_VIEW_TAG = "public_profile_view"
internal const val PUBLIC_PROFILE_PRIVATE_NOTE_TAG = "public_profile_private_note"
internal const val PUBLIC_PROFILE_NEEDS_PUBLIC_TAG = "public_profile_needs_public"
internal const val PUBLIC_PROFILE_LOADING_TAG = "public_profile_loading"

private const val SKELETON_LINES = 4
