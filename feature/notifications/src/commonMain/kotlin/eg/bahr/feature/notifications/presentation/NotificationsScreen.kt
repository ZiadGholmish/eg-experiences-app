package eg.bahr.feature.notifications.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import eg.bahr.core.designsystem.components.BahrBottomSheet
import eg.bahr.core.designsystem.components.BahrLottie
import eg.bahr.core.designsystem.components.BahrPrimaryButton
import eg.bahr.core.designsystem.components.BahrSkeleton
import eg.bahr.core.designsystem.components.BahrSkeletonCircle
import eg.bahr.core.designsystem.components.BahrSkeletonLine
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.lifecycle.RefreshesOnReturn
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrTween
import eg.bahr.core.designsystem.theme.bahrTweenOrNull
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_back
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.alerts_clear_read
import eg.bahr.core.localization.generated.resources.alerts_clear_read_body
import eg.bahr.core.localization.generated.resources.alerts_clear_read_keep
import eg.bahr.core.localization.generated.resources.alerts_clear_read_title
import eg.bahr.core.localization.generated.resources.alerts_delete
import eg.bahr.core.localization.generated.resources.alerts_delete_cancel
import eg.bahr.core.localization.generated.resources.alerts_delete_title
import eg.bahr.core.localization.generated.resources.alerts_earlier
import eg.bahr.core.localization.generated.resources.alerts_empty_body
import eg.bahr.core.localization.generated.resources.alerts_empty_title
import eg.bahr.core.localization.generated.resources.alerts_error_body
import eg.bahr.core.localization.generated.resources.alerts_error_title
import eg.bahr.core.localization.generated.resources.alerts_mark_all_read
import eg.bahr.core.localization.generated.resources.alerts_more
import eg.bahr.core.localization.generated.resources.alerts_time_days
import eg.bahr.core.localization.generated.resources.alerts_time_hours
import eg.bahr.core.localization.generated.resources.alerts_time_minutes
import eg.bahr.core.localization.generated.resources.alerts_time_now
import eg.bahr.core.localization.generated.resources.alerts_title
import eg.bahr.core.localization.generated.resources.alerts_today
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.notifications.presentation.components.NotificationRow
import eg.bahr.feature.notifications.presentation.components.NotificationRowActions
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Where a tap on an item goes, joined in :composeApp (see `notificationsScreen`). */
internal data class AlertDestinations(
    val onOpenTrip: (slug: String, date: String?, placeOpened: Boolean) -> Unit,
    val onOpenBooking: (ref: String) -> Unit,
    /** My reviews; null until it exists (M3-M4): a review's item then only reads. */
    val onOpenReview: ((id: String) -> Unit)?,
)

/** What the page's controls call, the view model's in the app and no-ops in the screenshot tests. */
internal data class NotificationsActions(
    val onBack: () -> Unit,
    val onOpen: (InboxEntry.Item) -> Unit = {},
    val onSetRead: (InboxEntry.Item, Boolean) -> Unit = { _, _ -> },
    val onAskDelete: (InboxEntry.Item) -> Unit = {},
    val onDismissDelete: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onMarkAllRead: () -> Unit = {},
    val onAskClearRead: () -> Unit = {},
    val onDismissClearRead: () -> Unit = {},
    val onClearRead: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/**
 * The notifications page (M3-M6, account handoff §3), pushed from Home's bell (M3-M6a): newest first in Today and
 * Earlier, unread rows tinted with a dot, a tap reads the item and opens its page ([destinations]), "Mark all as read",
 * a long swipe towards the start asks to delete (a sheet confirms it, M3-M22), a long press reads, unreads or deletes,
 * and the overflow's "Clear read", which asks too. Signed out it is
 * this install's guest inbox (M3-B12), so there is no sign-in prompt. Reads again on each return.
 */
@Composable
internal fun NotificationsScreen(
    onBack: () -> Unit,
    destinations: AlertDestinations,
    viewModel: NotificationsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val items = viewModel.notifications.collectAsLazyPagingItems()
    RefreshesOnReturn(viewModel::refresh)
    val actions =
        remember(viewModel, destinations, onBack) {
            NotificationsActions(
                onBack = onBack,
                onOpen = { item ->
                    viewModel.open(item)
                    destinations.open(item.target)
                },
                onSetRead = viewModel::setRead,
                onAskDelete = viewModel::askDelete,
                onDismissDelete = viewModel::dismissDelete,
                onDelete = viewModel::delete,
                onMarkAllRead = viewModel::markAllRead,
                onAskClearRead = viewModel::askClearRead,
                onDismissClearRead = viewModel::dismissClearRead,
                onClearRead = viewModel::clearRead,
                onRetry = viewModel::refresh,
            )
        }
    NotificationsContent(state = state, items = items, actions = actions)
    if (state.confirmClearRead) {
        BahrBottomSheet(props = clearReadProps(state, actions)) { ClearReadBody() }
    }
    state.confirmDelete?.let { item ->
        BahrBottomSheet(props = deleteProps(actions)) { DeleteBody(item) }
    }
}

private fun AlertDestinations.open(target: AlertTarget?) {
    when (target) {
        is AlertTarget.Trip -> onOpenTrip(target.slug, target.date, target.placeOpened)
        is AlertTarget.Booking -> onOpenBooking(target.ref)
        is AlertTarget.Review -> onOpenReview?.invoke(target.id)
        null -> Unit
    }
}

/** Stateless, for the screenshot tests. */
@Composable
internal fun NotificationsContent(
    state: NotificationsUiState,
    items: LazyPagingItems<InboxEntry>,
    actions: NotificationsActions,
) {
    val refresh = items.loadState.refresh
    val hasItems = items.itemCount > 0
    // The handoff's page is `surface` (not the off-white page of the trip lists), in light and dark.
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            // A pushed page (M3-M6a): no tab bar takes the gesture area, so the list's end stays above it.
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(actions, showListActions = hasItems && state.session != SessionState.Unknown)
            val shown =
                when {
                    // The stored sign-in not read yet: which inbox is not known, so nothing flashes.
                    state.session == SessionState.Unknown -> InboxShown.Unknown
                    hasItems -> InboxShown.List
                    refresh is LoadState.Loading -> InboxShown.Loading
                    refresh is LoadState.Error -> InboxShown.Error
                    else -> InboxShown.Empty
                }
            // A cross-fade between them (M3-M22): the last row deleted fades out while the empty state fades in, as
            // the list keeps drawing its going row for the fade. Instant under reduce motion.
            Crossfade(
                targetState = shown,
                animationSpec = bahrTween(),
                label = "inbox",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { target ->
                when (target) {
                    InboxShown.Unknown -> Unit
                    InboxShown.List -> InboxList(items, state.now, actions)
                    InboxShown.Loading -> InboxSkeleton()
                    InboxShown.Error ->
                        Centred {
                            StateMessage(
                                art = {
                                    StateDisc(
                                        BahrIcons.CloudOff,
                                        MaterialTheme.colorScheme.errorContainer,
                                        MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                },
                                title = stringResource(Res.string.alerts_error_title),
                                body = stringResource(Res.string.alerts_error_body),
                            ) {
                                BahrPrimaryButton(
                                    text = stringResource(Res.string.action_retry),
                                    onClick = actions.onRetry,
                                    modifier = Modifier.fillMaxWidth().padding(top = BahrSpacing.md),
                                )
                            }
                        }
                    InboxShown.Empty -> Centred { EmptyState() }
                }
            }
        }
    }
}

/**
 * The page's bar: Back, and with a list "Mark all as read" (the prototype's text button) and an overflow holding
 * "Clear read" (not in the prototype: see the step report); the title under it, at the prototype's heading size. One
 * row cannot hold Back, the title and both actions at 360dp ("Notifications" was cut), and the prototype, drawn as a
 * tab, had no Back.
 */
@Composable
private fun TopBar(
    actions: NotificationsActions,
    showListActions: Boolean,
) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = BahrSize.touchTarget).padding(horizontal = BahrSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
        ) {
            IconButton(onClick = actions.onBack, modifier = Modifier.size(BahrSize.touchTarget)) {
                Icon(
                    BahrIcons.ArrowBack.outlined(),
                    contentDescription = stringResource(Res.string.action_back),
                    // Set, not inherited: iOS roots have no Scaffold to provide a content colour, so dark
                    // would draw it black on the dark page.
                    tint = c.onSurface,
                    modifier = Modifier.size(BahrSize.iconNav),
                )
            }
            Box(Modifier.weight(1f))
            if (showListActions) {
                TextButton(onClick = actions.onMarkAllRead, modifier = Modifier.heightIn(min = BahrSize.touchTarget)) {
                    Icon(
                        BahrIcons.DoneAll.outlined(),
                        contentDescription = null,
                        tint = c.primary,
                        modifier = Modifier.size(BahrSize.iconMedium),
                    )
                    Text(
                        text = stringResource(Res.string.alerts_mark_all_read),
                        style = MaterialTheme.typography.labelLarge,
                        color = c.primary,
                        modifier = Modifier.padding(start = BahrSpacing.xs),
                    )
                }
                Overflow(actions)
            }
        }
        Text(
            text = stringResource(Res.string.alerts_title),
            // The prototype's 26px heading at 800.
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = c.onSurface,
            modifier = Modifier.padding(horizontal = BahrSpacing.gutter).padding(bottom = BahrSpacing.xs).semantics { heading() },
        )
    }
}

@Composable
private fun Overflow(actions: NotificationsActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(BahrSize.touchTarget)) {
            Icon(
                BahrIcons.MoreVert.outlined(),
                contentDescription = stringResource(Res.string.alerts_more),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(BahrSize.iconNav),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.alerts_clear_read)) },
                leadingIcon = { Icon(BahrIcons.Delete.outlined(), contentDescription = null) },
                onClick = {
                    open = false
                    actions.onAskClearRead()
                },
            )
        }
    }
}

@Composable
private fun InboxList(
    items: LazyPagingItems<InboxEntry>,
    now: Instant,
    actions: NotificationsActions,
) {
    LazyColumn(state = rememberLazyListState(), modifier = Modifier.fillMaxSize()) {
        items(
            count = items.itemCount,
            key = items.itemKey { it.key },
            contentType = items.itemContentType { if (it is InboxEntry.Header) CONTENT_HEADER else CONTENT_ITEM },
        ) { index ->
            // Keyed by item id and heading: a delete, Clear read, a row coming back or a new one fades and the rest slide
            // (M3-M22). Placement only: the row's own swipe offset is drawn inside it, so the two never fight.
            val motion = itemMotion()
            when (val entry = items[index]) {
                is InboxEntry.Header -> GroupLabel(entry.group, motion)
                is InboxEntry.Item ->
                    NotificationRow(
                        modifier = motion,
                        item = entry,
                        time = relativeTime(entry.createdAt, now),
                        actions =
                            NotificationRowActions(
                                onOpen = { actions.onOpen(entry) },
                                onSetRead = { read -> actions.onSetRead(entry, read) },
                                onAskDelete = { actions.onAskDelete(entry) },
                            ),
                    )
                null -> Unit
            }
        }
        when (items.loadState.append) {
            is LoadState.Loading -> item(key = KEY_APPEND, contentType = CONTENT_SKELETON) { SkeletonRow(SKELETON_WIDTHS.first()) }
            is LoadState.Error ->
                item(key = KEY_APPEND, contentType = CONTENT_RETRY) {
                    Box(Modifier.fillMaxWidth().padding(BahrSpacing.md), contentAlignment = Alignment.Center) {
                        TextButton(onClick = items::retry) { Text(stringResource(Res.string.action_retry)) }
                    }
                }
            is LoadState.NotLoading -> Unit
        }
    }
}

/**
 * An entry's motion in the list (M3-M22): it fades in and out and slides to its new place on the design system's
 * tween, instant under reduce motion (null specs). The same as core:catalog's `bahrAnimateItem`; reaching it would be
 * a new module edge for one modifier.
 */
@Composable
private fun LazyItemScope.itemMotion(): Modifier =
    Modifier.animateItem(
        fadeInSpec = bahrTweenOrNull(),
        placementSpec = bahrTweenOrNull(),
        fadeOutSpec = bahrTweenOrNull(),
    )

/** What the space under the bar shows; the cross-fade's key. */
private enum class InboxShown { Unknown, List, Loading, Error, Empty }

@Composable
private fun GroupLabel(
    group: InboxGroup,
    modifier: Modifier,
) {
    Text(
        text = stringResource(if (group == InboxGroup.Today) Res.string.alerts_today else Res.string.alerts_earlier),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = BahrSpacing.gutter, end = BahrSpacing.gutter, top = BahrSpacing.md, bottom = BahrSpacing.xs)
                .semantics { heading() },
    )
}

/**
 * "now", "12 min", "1 h", "12 d" (the prototype's), in Western digits. Counted from [now], the time of the last read,
 * so the rows do not tick on screen; each return reads the list (and the time) again.
 */
@Composable
private fun relativeTime(
    createdAt: Instant,
    now: Instant,
): String {
    val elapsed = (now - createdAt).coerceAtLeast(Duration.ZERO)
    return when {
        elapsed < 1.minutes -> stringResource(Res.string.alerts_time_now)
        elapsed < 1.hours -> stringResource(Res.string.alerts_time_minutes, elapsed.inWholeMinutes.toInt())
        elapsed < 1.days -> stringResource(Res.string.alerts_time_hours, elapsed.inWholeHours.toInt())
        else -> elapsed.inWholeDays.toInt().let { pluralStringResource(Res.plurals.alerts_time_days, it, it) }
    }
}

/** The handoff's loading state: the group label, then 7 rows, at the prototype's widths. One "Loading" node for readers. */
@Composable
private fun InboxSkeleton() {
    BahrSkeleton(modifier = Modifier.fillMaxSize().padding(top = BahrSpacing.sm), verticalArrangement = Arrangement.Top) {
        BahrSkeletonLine(
            modifier =
                Modifier
                    .padding(start = BahrSpacing.gutter, end = BahrSpacing.gutter, top = BahrSpacing.sm, bottom = BahrSpacing.md)
                    .width(BahrSize.skeletonLabelWidth),
        )
        SKELETON_WIDTHS.forEach { SkeletonRow(it) }
    }
}

@Composable
private fun SkeletonRow(widths: SkeletonWidths) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        BahrSkeletonCircle(props = BahrSkeletonCircle.Props(BahrSize.notificationDisc))
        Column(
            Modifier.weight(1f).padding(top = BahrSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
        ) {
            BahrSkeletonLine(props = BahrSkeletonLine.Props(widthFraction = widths.title))
            BahrSkeletonLine(props = BahrSkeletonLine.Props(widthFraction = widths.body, height = BahrSize.skeletonLineSmall))
        }
    }
}

/** A skeleton row's two lines, as shares of the text column (the prototype's `skel` list, docs/reviews/M3-M14). */
private data class SkeletonWidths(
    val title: Float,
    val body: Float,
)

private val SKELETON_WIDTHS =
    listOf(
        SkeletonWidths(0.62f, 0.88f),
        SkeletonWidths(0.74f, 0.76f),
        SkeletonWidths(0.55f, 0.92f),
        SkeletonWidths(0.68f, 0.70f),
        SkeletonWidths(0.80f, 0.84f),
        SkeletonWidths(0.58f, 0.66f),
        SkeletonWidths(0.70f, 0.90f),
    )

/** Empty (M3-M8): the bell rings, over and over while on screen (M3-M8b). Decorative: the title says it. */
@Composable
private fun EmptyState() {
    StateMessage(
        art = {
            BahrLottie(
                modifier = Modifier.width(BahrSize.lottieState),
                props = BahrLottie.Props(BahrLottie.Animation.Bell, playback = BahrLottie.Playback.Loop),
            )
        },
        title = stringResource(Res.string.alerts_empty_title),
        body = stringResource(Res.string.alerts_empty_body),
    )
}

/** The state sits in the middle of the space under the title, and scrolls when it does not fit (large text). */
@Composable
private fun Centred(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) { content() }
    }
}

/** The handoff's centred state: its art (a 72dp tinted disc), a title, a line, and optionally what to do. */
@Composable
private fun StateMessage(
    art: @Composable () -> Unit,
    title: String,
    body: String,
    action: @Composable () -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = BahrSpacing.xxl, end = BahrSpacing.xxl, bottom = BahrSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        Box(Modifier.padding(bottom = BahrSpacing.xs)) { art() }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action()
    }
}

/** The still states' art: a 72dp tinted disc with an icon. */
@Composable
private fun StateDisc(
    icon: BahrIcons,
    disc: Color,
    tint: Color,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(BahrSize.stateDisc)
                .clip(BahrTheme.shapes.full)
                .background(disc),
    ) {
        Icon(icon.outlined(), contentDescription = null, tint = tint, modifier = Modifier.size(BahrSize.iconState))
    }
}

/** "Clear read" cannot be undone (deletion is for good), so it asks first; Keep is the safe way out. */
@Composable
private fun clearReadProps(
    state: NotificationsUiState,
    actions: NotificationsActions,
) = BahrBottomSheet.Props(
    title = stringResource(Res.string.alerts_clear_read_title),
    onDismiss = actions.onDismissClearRead,
    actions =
        listOf(
            BahrBottomSheet.Action(
                text = stringResource(Res.string.alerts_clear_read),
                style = BahrBottomSheet.Action.Style.Destructive,
                onClick = actions.onClearRead,
                loading = state.clearingRead,
            ),
        ),
    cancel =
        BahrBottomSheet.Action(
            text = stringResource(Res.string.alerts_clear_read_keep),
            onClick = actions.onDismissClearRead,
            enabled = !state.clearingRead,
        ),
    dismissible = !state.clearingRead,
)

@Composable
private fun ClearReadBody() {
    Text(
        text = stringResource(Res.string.alerts_clear_read_body),
        style = MaterialTheme.typography.bodyMedium,
        color = BahrTheme.colors.onSurfaceSecondary,
    )
}

/**
 * "Delete this notification?" (M3-M22): asked after a full swipe, the menu's Delete or the screen reader's, as a
 * deletion cannot be undone. The destructive Delete first, Cancel (the default focus) last; any way out leaves the row.
 */
@Composable
internal fun deleteProps(actions: NotificationsActions) =
    BahrBottomSheet.Props(
        title = stringResource(Res.string.alerts_delete_title),
        onDismiss = actions.onDismissDelete,
        actions =
            listOf(
                BahrBottomSheet.Action(
                    text = stringResource(Res.string.alerts_delete),
                    style = BahrBottomSheet.Action.Style.Destructive,
                    onClick = actions.onDelete,
                ),
            ),
        cancel = BahrBottomSheet.Action(text = stringResource(Res.string.alerts_delete_cancel), onClick = actions.onDismissDelete),
    )

/** Which one: the row's title, as the list shows it. */
@Composable
internal fun DeleteBody(item: InboxEntry.Item) {
    Text(
        text = item.title,
        style = MaterialTheme.typography.bodyMedium,
        color = BahrTheme.colors.onSurfaceSecondary,
    )
}

private const val CONTENT_HEADER = "header"
private const val CONTENT_ITEM = "item"
private const val CONTENT_SKELETON = "skeleton"
private const val CONTENT_RETRY = "retry"
private const val KEY_APPEND = "append"
