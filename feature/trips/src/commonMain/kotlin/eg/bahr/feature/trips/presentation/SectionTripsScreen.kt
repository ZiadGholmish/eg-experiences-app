package eg.bahr.feature.trips.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.bahrSharedBounds
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.trip_per_person
import eg.bahr.core.localization.generated.resources.trips_empty
import eg.bahr.core.localization.generated.resources.trips_title
import eg.bahr.feature.trips.presentation.components.ListHeading
import eg.bahr.feature.trips.presentation.components.ListTopBar
import eg.bahr.feature.trips.presentation.components.listBody
import eg.bahr.feature.trips.presentation.components.rememberListMotion
import eg.bahr.feature.trips.presentation.components.rememberQueryStatus
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A Home row's "See all" (M4-M1b): the row's title and trip count, then every trip of the row, paged,
 * in the row's order. A section gone since Home was read is the error state (404), with its retry.
 * [sharedKey] is the Home row title that morphs into the heading on the way in (M4-M6).
 */
@Composable
internal fun SectionTripsScreen(
    sectionId: String,
    title: String?,
    onBack: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    modifier: Modifier = Modifier,
    sharedKey: String? = null,
    viewModel: SectionTripsViewModel = koinViewModel(key = "section:$sectionId") { parametersOf(sectionId, title) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val trips = viewModel.trips.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val perPersonLabel = stringResource(Res.string.trip_per_person)
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + BahrSpacing.xl
    val status = trips.rememberQueryStatus()
    // One list, never switched: no chip order to slide from.
    val motion = rememberListMotion(status, switchKey = sectionId, order = 0)
    val appendRetry = rememberThrottled(trips::retry)

    Column(modifier = modifier.fillMaxSize()) {
        ListTopBar(onBack = onBack)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(BahrSpacing.lg),
        ) {
            item(key = KEY_HEADER) {
                ListHeading(
                    label = state.title?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.trips_title),
                    totalItems = state.totalItems,
                    modifier = Modifier.padding(horizontal = BahrSpacing.gutter).bahrSharedBounds(sharedKey),
                )
            }
            listBody(
                trips = trips,
                status = status,
                motion = motion,
                perPersonLabel = perPersonLabel,
                waitlistTags = state.waitlistTags,
                emptyMessage = { stringResource(Res.string.trips_empty) },
                onRetry = viewModel::retry,
                onAppendRetry = appendRetry,
                onTripClick = onTripClick,
            )
        }
    }
}

private const val KEY_HEADER = "header"
