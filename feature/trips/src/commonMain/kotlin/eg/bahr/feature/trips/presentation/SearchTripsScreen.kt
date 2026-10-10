package eg.bahr.feature.trips.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.then
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import eg.bahr.core.designsystem.components.BahrBackButton
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.search_clear
import eg.bahr.core.localization.generated.resources.search_hint
import eg.bahr.core.localization.generated.resources.search_no_results
import eg.bahr.core.localization.generated.resources.search_recent_clear
import eg.bahr.core.localization.generated.resources.search_recent_title
import eg.bahr.core.localization.generated.resources.search_start
import eg.bahr.core.localization.generated.resources.trip_per_person
import eg.bahr.core.localization.generated.resources.trips_filter_empty
import eg.bahr.feature.trips.data.TripApiService
import eg.bahr.feature.trips.presentation.components.FacetFilterChips
import eg.bahr.feature.trips.presentation.components.ListHeading
import eg.bahr.feature.trips.presentation.components.listBody
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Search (M4-M3), opened from Home's search entry. The top bar is Back and the field, focused with
 * the keyboard up on the way in. Under it: the recent searches until two letters are typed, then
 * the count, the filter chips (as on the category page) and the results, paged, best match first.
 *
 * The field's text lives here, in a [TextFieldState] (typing stays in step with the keyboard, Arabic
 * composition included); every change goes to the view model, which debounces it into a search.
 */
@Composable
internal fun SearchTripsScreen(
    onBack: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchTripsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val trips = viewModel.trips.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val perPersonLabel = stringResource(Res.string.trip_per_person)
    val keyboard = LocalSoftwareKeyboardController.current
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + BahrSpacing.xl

    // Seeded from the view model, so a language switch (which re-keys the screen) keeps the text.
    val field = rememberTextFieldState(initialText = state.text)
    LaunchedEffect(field) { snapshotFlow { field.text.toString() }.collect(viewModel::onTextChange) }

    val focus = remember { FocusRequester() }
    // Only on the way in: coming back from a trip page must not pop the keyboard over the results.
    var focusedOnEntry by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!focusedOnEntry) {
            focus.requestFocus()
            keyboard?.show()
            focusedOnEntry = true
        }
    }

    // imePadding: the list ends above the keyboard, so the last result can be scrolled into view.
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        SearchBar(
            field = field,
            focus = focus,
            onBack = onBack,
            onSearch = {
                keyboard?.hide()
                viewModel.search()
            },
            onClear = {
                field.clearText()
                focus.requestFocus()
            },
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = BahrSpacing.sm, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(BahrSpacing.lg),
        ) {
            val query = state.query
            if (query == null) {
                recentSearches(
                    recents = state.recents,
                    onRecent = { recent ->
                        keyboard?.hide()
                        viewModel.searchRecent(recent)
                        field.setTextAndPlaceCursorAtEnd(recent)
                    },
                    onClearAll = viewModel::clearRecents,
                )
                return@LazyColumn
            }
            item(key = KEY_COUNT) {
                ListHeading(label = null, totalItems = state.totalItems, modifier = Modifier.padding(horizontal = BahrSpacing.gutter))
            }
            val chips = state.filterChips
            if (chips.isNotEmpty()) {
                item(key = KEY_CHIPS) {
                    FacetFilterChips(chips, isSelected = state::isSelected, onSelect = viewModel::selectFilter)
                }
            }
            listBody(
                trips = trips,
                perPersonLabel = perPersonLabel,
                waitlistTags = state.waitlistTags,
                emptyMessage = {
                    if (state.filter != null) {
                        stringResource(Res.string.trips_filter_empty)
                    } else {
                        stringResource(Res.string.search_no_results, query)
                    }
                },
                onTripClick = { slug ->
                    viewModel.onResultOpened()
                    onTripClick(slug)
                },
            )
        }
    }
}

/**
 * Back, then the field: a search icon, the placeholder, × once there is text. The field holds at
 * most [TripApiService.MAX_QUERY_LENGTH] characters, the contract's limit, and no control characters; text is drawn in its own
 * direction, so Arabic typed into the English app reads right to left. Carries the status-bar inset
 * (edge-to-edge; each screen handles its own top inset).
 */
@Composable
private fun SearchBar(
    field: TextFieldState,
    focus: FocusRequester,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = BahrSpacing.lg, end = BahrSpacing.gutter, top = BahrSpacing.md, bottom = BahrSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        BahrBackButton(props = BahrBackButton.Props(onClick = onBack))
        OutlinedTextField(
            state = field,
            modifier = Modifier.weight(1f).focusRequester(focus).testTag(SEARCH_FIELD_TAG),
            placeholder = { Text(stringResource(Res.string.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = {
                Icon(
                    BahrIcons.Search.outlined(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(BahrSize.iconLarge),
                )
            },
            trailingIcon =
                if (field.text.isNotEmpty()) {
                    {
                        IconButton(onClick = onClear) {
                            Icon(
                                BahrIcons.Close.outlined(),
                                contentDescription = stringResource(Res.string.search_clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(BahrSize.iconLarge),
                            )
                        }
                    }
                } else {
                    null
                },
            inputTransformation = StripControlCharacters.then(InputTransformation.maxLength(TripApiService.MAX_QUERY_LENGTH)),
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            onKeyboardAction = { onSearch() },
            shape = BahrTheme.shapes.full,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = BahrTheme.colors.surfaceLowest,
                    unfocusedContainerColor = BahrTheme.colors.surfaceLowest,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
        )
    }
}

/**
 * Before a search: the recent ones, newest first, each tappable to run again, under a heading with
 * "Clear all"; with none yet, a line on what can be searched.
 */
private fun LazyListScope.recentSearches(
    recents: List<String>,
    onRecent: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    if (recents.isEmpty()) {
        item(key = KEY_START) {
            Text(
                text = stringResource(Res.string.search_start),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = BahrSpacing.gutter),
            )
        }
        return
    }
    item(key = KEY_RECENT_TITLE) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = BahrSpacing.gutter, end = BahrSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.search_recent_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = onClearAll) {
                Text(
                    text = stringResource(Res.string.search_recent_clear),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    // One item, so the rows sit at touch height under each other rather than a card gap apart.
    item(key = KEY_RECENTS) {
        Column {
            recents.forEach { recent ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = BahrSpacing.minTouch)
                            .clickable(role = Role.Button) { onRecent(recent) }
                            .padding(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
                ) {
                    Icon(
                        BahrIcons.History.outlined(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(BahrSize.iconMedium),
                    )
                    Text(
                        text = recent,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Typed or pasted control characters never reach the field (the server refuses them); stripped
 * before the length cap, so a paste is measured without them.
 */
private val StripControlCharacters =
    InputTransformation {
        val text = asCharSequence().toString()
        val cleaned = text.withoutControlCharacters()
        if (cleaned != text) replace(0, length, cleaned)
    }

/** For tests: the search field. */
internal const val SEARCH_FIELD_TAG = "search_field"

private const val KEY_COUNT = "count"
private const val KEY_CHIPS = "chips"
private const val KEY_START = "start"
private const val KEY_RECENT_TITLE = "recent-title"
private const val KEY_RECENTS = "recents"
