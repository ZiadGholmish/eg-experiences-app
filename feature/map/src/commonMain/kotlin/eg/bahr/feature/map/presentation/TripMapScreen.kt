package eg.bahr.feature.map.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.designsystem.components.BahrBackButton
import eg.bahr.core.designsystem.components.BahrEmptyView
import eg.bahr.core.designsystem.components.BahrErrorView
import eg.bahr.core.designsystem.components.BahrLoadingView
import eg.bahr.core.designsystem.components.BahrPillButton
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrElevation
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrShadow
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.map_empty
import eg.bahr.core.localization.generated.resources.map_list
import eg.bahr.core.localization.generated.resources.map_note_drive
import eg.bahr.core.localization.generated.resources.map_note_pick
import eg.bahr.core.localization.generated.resources.map_note_pins
import eg.bahr.core.localization.generated.resources.map_show_drive
import eg.bahr.core.localization.generated.resources.map_show_drive_from
import eg.bahr.core.localization.generated.resources.map_title
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import eg.bahr.feature.map.presentation.components.MapOverlay
import eg.bahr.feature.map.presentation.components.TripMap
import eg.bahr.feature.map.presentation.components.TripMapScene
import eg.bahr.feature.map.presentation.components.rememberTripMapScene
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * How the screen draws the map: the real [TripMap] in the app, a stand-in in the screenshot tests
 * (neither Google map runs under Robolectric).
 */
internal typealias MapContent = @Composable (
    scene: TripMapScene,
    onPinClick: (String) -> Unit,
    onMapClick: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier,
) -> Unit

/**
 * The map (M4-M2, HANDOFF screen 2): an app bar (back, "Where the trips are", "List" back to Home),
 * the map in a rounded, raised panel with its toggles at the top and the legend (or a tapped pin's
 * card) at the bottom, and a note under it on how to read it.
 *
 * Loading, error and empty take the map panel's place; the bar and note stay. A tapped pin opens its
 * card; the card opens the trip ([onTripClick]); the map itself closes it.
 */
@Composable
internal fun TripMapScreen(
    onBack: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TripMapViewModel = koinViewModel(),
    map: MapContent = { scene, onPin, onMap, padding, mod -> TripMap(scene, onPin, onMap, padding, mod) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val labels = rememberDriveLabels(state)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        MapTopBar(onBack = onBack)
        val panelShape = BahrTheme.shapes.hero
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = BahrSpacing.md)
                    .bahrShadow(BahrElevation.Level3, panelShape, BahrTheme.colors)
                    .clip(panelShape)
                    .background(BahrTheme.colors.surfaceLow),
        ) {
            val error = state.error
            when {
                state.loading && state.map == null -> BahrLoadingView()

                error != null && state.map == null ->
                    BahrErrorView(
                        message = error.localizedMessage(),
                        retryLabel = stringResource(Res.string.action_retry),
                        onRetry = if (error.isRetryable) viewModel::retry else null,
                    )

                state.isEmpty -> BahrEmptyView(message = stringResource(Res.string.map_empty))

                else -> MapPanel(state, labels, viewModel, onTripClick, map)
            }
        }
        MapNote(text = labels.note)
    }
}

/** The map with its chrome. The chrome's measured height is the map's content padding. */
@Composable
private fun MapPanel(
    state: TripMapUiState,
    labels: DriveLabels,
    viewModel: TripMapViewModel,
    onTripClick: (String) -> Unit,
    map: MapContent,
) {
    val scene = rememberTripMapScene(state) ?: return
    val density = LocalDensity.current
    var topChrome by remember { mutableStateOf(0) }
    var bottomChrome by remember { mutableStateOf(0) }
    val padding = with(density) { PaddingValues(top = topChrome.toDp(), bottom = bottomChrome.toDp()) }
    Box(Modifier.fillMaxSize()) {
        map(scene, viewModel::selectPin, viewModel::clearSelection, padding, Modifier.fillMaxSize())
        MapOverlay(
            state = state,
            driveLabel = labels.toggle,
            onAllTrips = viewModel::showAllTrips,
            onToggleDrive = viewModel::toggleDrive,
            onCloseCard = viewModel::clearSelection,
            onTripClick = onTripClick,
            onTopSize = { topChrome = it },
            onBottomSize = { bottomChrome = it },
        )
    }
}

@Composable
private fun MapTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = BahrSpacing.lg, vertical = BahrSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        BahrBackButton(props = BahrBackButton.Props(onClick = onBack))
        Text(
            text = stringResource(Res.string.map_title),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        // The prototype's "List": the map is reached from Home's list, so this goes back to it.
        BahrPillButton(
            props = BahrPillButton.Props(text = stringResource(Res.string.map_list), icon = BahrIcons.FormatListBulleted, onClick = onBack),
        )
    }
}

/** The note under the map (the prototype's mapNote panel): how to read the pins and the drive. */
@Composable
private fun MapNote(text: String) {
    val bottom = WindowInsets.navigationBars
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(bottom)
                .padding(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.lg)
                .clip(BahrTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = BahrSpacing.lg, vertical = BahrSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        Icon(
            imageVector = BahrIcons.Info.filled(),
            contentDescription = null,
            tint = BahrTheme.colors.secondaryDim,
            modifier = Modifier.size(BahrSize.iconLarge),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** The drive toggle's label and the note, both naming the city the buses leave (Cairo). */
private data class DriveLabels(
    val toggle: String,
    val note: String,
)

/**
 * "Show the drive from Cairo" when every departure point is in one city (today: Cairo), else the
 * plain "Show the drive". The note says how to read the map; with the drive on and no pin picked, it
 * says to pick one, since the line runs to the picked trip.
 */
@Composable
private fun rememberDriveLabels(state: TripMapUiState): DriveLabels {
    val points = state.map?.departurePoints.orEmpty()
    val city = points.map { it.city ?: it.placeName }.distinct().singleOrNull()
    val toggle = city?.let { stringResource(Res.string.map_show_drive_from, it) } ?: stringResource(Res.string.map_show_drive)
    val note =
        when {
            city == null -> stringResource(Res.string.map_note_pins)
            state.showsDrive && state.selectedSlug == null -> stringResource(Res.string.map_note_pick, city)
            else -> stringResource(Res.string.map_note_drive, city, toggle)
        }
    return DriveLabels(toggle = toggle, note = note)
}
