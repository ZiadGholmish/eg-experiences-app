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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
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
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrShadow
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.map_empty
import eg.bahr.core.localization.generated.resources.map_list
import eg.bahr.core.localization.generated.resources.map_title
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import eg.bahr.feature.map.presentation.components.MapOverlay
import eg.bahr.feature.map.presentation.components.PinReveal
import eg.bahr.feature.map.presentation.components.TripMap
import eg.bahr.feature.map.presentation.components.TripMapScene
import eg.bahr.feature.map.presentation.components.mapGround
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
 * the map in a rounded, raised panel with the legend (or a tapped pin's
 * card) at the bottom. M4-M12 (user decision 2026-10-12): the prototype's note under the map ("Pins are
 * the trip itself.") is gone and the panel takes its space, down to the navigation bar.
 *
 * Loading, error and empty take the map panel's place; the bar stays. A tapped pin opens its
 * card; the card opens the trip ([onTripClick]); the map itself closes it.
 *
 * M4-M8: the map is the Map tab's root, with nothing to go back to: [onBack] null draws no Back, and
 * "List" goes to [onList] (the app switches to the Home tab).
 */
@Composable
internal fun TripMapScreen(
    onBack: (() -> Unit)?,
    onTripClick: (slug: String) -> Unit,
    modifier: Modifier = Modifier,
    onList: () -> Unit = { onBack?.invoke() },
    viewModel: TripMapViewModel = koinViewModel(),
    map: MapContent = { scene, onPin, onMap, padding, mod -> TripMap(scene, onPin, onMap, padding, mod) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.statusBars)
                // The note under the panel used to keep it off the gesture area; now the panel does.
                .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        MapTopBar(onBack = onBack, onList = onList)
        val panelShape = BahrTheme.shapes.hero
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = BahrSpacing.md, end = BahrSpacing.md, bottom = BahrSpacing.md)
                    .bahrShadow(BahrElevation.Level3, panelShape, BahrTheme.colors)
                    .clip(panelShape)
                    // White on the off-white page (M4-M12), like every raised panel; the map covers it
                    // once loaded, so this is the loading / error / empty ground.
                    .background(MaterialTheme.colorScheme.surface),
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

                else -> MapPanel(state, viewModel, onTripClick, map)
            }
        }
    }
}

/**
 * The map with its chrome. The chrome's measured height is the map's content padding.
 *
 * The map itself is created once the chrome has been measured (a frame after the chrome is first
 * laid out), so it starts framed inside the chrome rather than moving there after (M4-M9). Until
 * then, and while the tiles load, the panel shows the map's placeholder ground.
 */
@Composable
private fun MapPanel(
    state: TripMapUiState,
    viewModel: TripMapViewModel,
    onTripClick: (String) -> Unit,
    map: MapContent,
) {
    val built = rememberTripMapScene(state) ?: return
    val density = LocalDensity.current
    // Each bottom chrome's measured height, by the pin whose card it is (the legend under no pin):
    // the map's bottom padding is the current selection's, once it has been measured.
    val bottoms = remember { mutableStateMapOf<String, Int>() }
    val bottomKey = state.selectedSlug ?: LEGEND_KEY
    // The selection's card has been measured (a frame after it opens): only then is a pin revealed.
    val chromeReady = bottomKey in bottoms
    val bottomChrome = bottoms[bottomKey] ?: bottoms[LEGEND_KEY] ?: 0
    val padding = with(density) { PaddingValues(bottom = bottomChrome.toDp()) }
    // The picked pin, kept clear of its own card once that card is measured (M4-M9 review #2).
    val margin = with(density) { BahrSpacing.sm.toPx() }
    val reveal =
        state.selectedSlug?.takeIf { chromeReady }?.let { slug ->
            built.markers.firstOrNull { it.id == slug }?.let { pin ->
                val belowAnchor = pin.image.bitmap.height * (1f - pin.image.anchorY)
                PinReveal(pin.id, pin.point, clearancePx = belowAnchor + margin, cardHeightPx = bottomChrome)
            }
        }
    val scene = built.copy(reveal = reveal)
    Box(Modifier.fillMaxSize().background(mapGround())) {
        if (bottomChrome > 0) {
            map(scene, viewModel::selectPin, viewModel::clearSelection, padding, Modifier.fillMaxSize())
        }
        MapOverlay(
            state = state,
            onCloseCard = viewModel::clearSelection,
            onTripClick = onTripClick,
            onBottomSize = { height, slug -> bottoms[slug ?: LEGEND_KEY] = height },
        )
    }
}

@Composable
private fun MapTopBar(
    onBack: (() -> Unit)?,
    onList: () -> Unit,
) {
    Row(
        // With no Back (the tab root), the title starts at the page's gutter.
        modifier =
            Modifier.fillMaxWidth().padding(
                start = if (onBack != null) BahrSpacing.lg else BahrSpacing.gutter,
                end = BahrSpacing.lg,
                top = BahrSpacing.md,
                bottom = BahrSpacing.md,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        onBack?.let { BahrBackButton(props = BahrBackButton.Props(onClick = it)) }
        Text(
            text = stringResource(Res.string.map_title),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        // The prototype's "List": back to Home's list (since M4-M8, the Home tab).
        BahrPillButton(
            props = BahrPillButton.Props(text = stringResource(Res.string.map_list), icon = BahrIcons.FormatListBulleted, onClick = onList),
        )
    }
}

/** The legend's entry among the bottom chrome heights (cards are keyed by their pin's slug). */
private const val LEGEND_KEY = ""
