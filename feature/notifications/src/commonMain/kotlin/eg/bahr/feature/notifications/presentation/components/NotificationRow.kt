package eg.bahr.feature.notifications.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrTween
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.alerts_delete
import eg.bahr.core.localization.generated.resources.alerts_mark_read
import eg.bahr.core.localization.generated.resources.alerts_mark_unread
import eg.bahr.core.localization.generated.resources.alerts_row_read
import eg.bahr.core.localization.generated.resources.alerts_row_unread
import eg.bahr.feature.notifications.presentation.AlertKind
import eg.bahr.feature.notifications.presentation.InboxEntry
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The row's three ways out: open it (a tap), read or unread (the menu), and ask to delete it (a full swipe, the menu
 * or the screen reader's action), which opens the page's confirm sheet; nothing is deleted from the row itself.
 */
@Immutable
internal data class NotificationRowActions(
    val onOpen: () -> Unit,
    val onSetRead: (Boolean) -> Unit,
    val onAskDelete: () -> Unit,
)

/**
 * One notification (account handoff §3): 72dp at least, the gutter on both sides, a 40dp disc tinted by type, the title
 * (800 unread, 600 read) with the relative [time] at its end, one line of body, and the unread dot; an unread row is
 * tinted `unread` too, so the state never rests on colour alone.
 *
 * Deleting takes a deliberate gesture (M3-M22, a user bug: a short or sloppy swipe deleted at once). The row follows
 * the finger towards the reading start only (right to left in English, left to right in Arabic), uncovering the
 * delete mark at its end; past [DELETE_THRESHOLD_SHARE] of its width the mark turns strong with a haptic tick, and
 * letting go there asks ([NotificationRowActions.onAskDelete]) while the row springs back. Only the distance counts:
 * a flick that ends short asks nothing. A long press opens a menu with "Mark as read/unread" and "Delete" (handoff:
 * "long-press opens the same actions in a menu"); screen readers get both as custom actions on the one node that
 * reads the row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NotificationRow(
    item: InboxEntry.Item,
    time: String,
    actions: NotificationRowActions,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }

    val markLabel = stringResource(if (item.read) Res.string.alerts_mark_unread else Res.string.alerts_mark_read)
    val deleteLabel = stringResource(Res.string.alerts_delete)
    val spoken = stringResource(if (item.read) Res.string.alerts_row_read else Res.string.alerts_row_unread, item.title, item.body, time)

    DeleteSwipe(onAskDelete = actions.onAskDelete, modifier = modifier) { swipeModifier ->
        Box(swipeModifier) {
            RowContent(
                item = item,
                time = time,
                modifier =
                    Modifier
                        .combinedClickable(onClick = actions.onOpen, onLongClick = { menu = true })
                        .clearAndSetSemantics {
                            contentDescription = spoken
                            role = Role.Button
                            onClick {
                                actions.onOpen()
                                true
                            }
                            onLongClick {
                                menu = true
                                true
                            }
                            customActions =
                                listOf(
                                    CustomAccessibilityAction(markLabel) {
                                        actions.onSetRead(!item.read)
                                        true
                                    },
                                    CustomAccessibilityAction(deleteLabel) {
                                        actions.onAskDelete()
                                        true
                                    },
                                )
                        },
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(markLabel) },
                    leadingIcon = {
                        Icon(
                            (if (item.read) BahrIcons.MarkEmailUnread else BahrIcons.MarkEmailRead).outlined(),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menu = false
                        actions.onSetRead(!item.read)
                    },
                )
                DropdownMenuItem(
                    text = { Text(deleteLabel) },
                    leadingIcon = { Icon(BahrIcons.Delete.outlined(), contentDescription = null) },
                    onClick = {
                        menu = false
                        actions.onAskDelete()
                    },
                )
            }
        }
    }
}

/**
 * The one-way delete swipe, by hand rather than Material's `SwipeToDismissBox`: that one settles a fast flick as a
 * dismissal whatever the distance and only reports one once the row has left the screen, while here only a long drag
 * may ask, and the row always comes back.
 *
 * The offset is in layout terms (negative: towards the start), so `Modifier.offset` mirrors it in Arabic and the
 * drag's physical deltas are reversed there to match. It is read in the layout and draw phases only, so a drag does
 * not recompose the row; only crossing the threshold does ([armed]).
 */
@Composable
private fun DeleteSwipe(
    onAskDelete: () -> Unit,
    modifier: Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptics = LocalHapticFeedback.current
    val currentAsk by rememberUpdatedState(onAskDelete)
    val springBack by rememberUpdatedState(bahrTween<Float>())
    val offset = remember { mutableFloatStateOf(0f) }
    val width = remember { mutableIntStateOf(0) }
    var armed by remember { mutableStateOf(false) }
    // Read in the drag callbacks only, never in composition.
    val fingerDown = remember { mutableStateOf(false) }
    val drag =
        rememberDraggableState { delta ->
            val full = width.intValue.toFloat()
            val next = (offset.floatValue + delta).coerceIn(-full, 0f)
            offset.floatValue = next
            // Only the finger arms it: the spring back passes the line too, and must neither tick nor re-arm.
            val past = fingerDown.value && full > 0f && -next >= full * DELETE_THRESHOLD_SHARE
            if (past != armed) {
                armed = past
                if (past) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            }
        }
    Box(
        modifier =
            modifier
                .onSizeChanged { width.intValue = it.width }
                .draggable(
                    state = drag,
                    orientation = Orientation.Horizontal,
                    reverseDirection = isRtl,
                    onDragStarted = { fingerDown.value = true },
                    // The fling's velocity is ignored on purpose: where the finger let go decides.
                    onDragStopped = {
                        fingerDown.value = false
                        val ask = armed
                        armed = false
                        if (ask) currentAsk()
                        // Through the drag's own mutex, so a new touch takes the row from the spring at once.
                        drag.drag {
                            var last = offset.floatValue
                            animate(last, 0f, animationSpec = springBack) { value, _ ->
                                dragBy(value - last)
                                last = value
                            }
                        }
                    },
                ),
    ) {
        DeleteMark(
            armed = armed,
            modifier = Modifier.matchParentSize().graphicsLayer { alpha = if (offset.floatValue == 0f) 0f else 1f },
        )
        content(Modifier.offset { IntOffset(offset.floatValue.roundToInt(), 0) })
    }
}

@Composable
private fun RowContent(
    item: InboxEntry.Item,
    time: String,
    modifier: Modifier,
) {
    val c = MaterialTheme.colorScheme
    val tint = item.kind.tint()
    // Read or unread eases between the two (M3-M22), drawn in the draw phase only; instant under reduce motion.
    val ground = animateColorAsState(if (item.read) c.surface else BahrTheme.colors.unread.compositeOver(c.surface), bahrTween())
    val dot = animateColorAsState(if (item.read) Color.Transparent else c.primary, bahrTween())
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                // The row paints its own ground (the page's, with the unread tint over it), so the swipe's mark shows
                // only where the row has moved away.
                .drawBehind { drawRect(ground.value) }
                .heightIn(min = BahrSize.notificationRow)
                .padding(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(BahrSize.notificationDisc).clip(BahrTheme.shapes.full).background(tint.disc),
        ) {
            Icon(item.kind.icon.filled(), contentDescription = null, tint = tint.icon, modifier = Modifier.size(BahrSize.iconNotification))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (item.read) FontWeight.SemiBold else FontWeight.ExtraBold,
                        ),
                    color = c.onSurface,
                    maxLines = TITLE_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // Western digits from the string's own format; in Arabic it reads "12 د" right to left, as the prototype.
                Text(text = time, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant, maxLines = 1)
            }
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .padding(top = BahrSpacing.sm)
                .size(BahrSize.unreadDot)
                .clip(BahrTheme.shapes.full)
                .drawBehind { drawRect(dot.value) },
        )
    }
}

/**
 * What the swipe uncovers at the row's end: the delete mark on `errorContainer`, turning `errorStrong` once letting go
 * would ask, so the threshold is seen as well as felt.
 */
@Composable
private fun DeleteMark(
    armed: Boolean,
    modifier: Modifier,
) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier =
            modifier
                .background(if (armed) BahrTheme.colors.errorStrong else c.errorContainer)
                .padding(horizontal = BahrSpacing.gutter),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(BahrIcons.Delete.outlined(), contentDescription = null, tint = if (armed) c.onError else c.onErrorContainer)
    }
}

/** A disc's ground and its icon's colour. */
private data class KindTint(
    val disc: Color,
    val icon: Color,
)

/**
 * Handoff §3's table: hold expiring error, seat opened success, host message quaternary, booking confirmed and trip
 * reminder primary, review approved secondary, review not published neutral. The types added since are ours: a
 * cancelled booking error, a refund success, a moved date or changed booking primary, a broadcast secondary.
 */
@Composable
private fun AlertKind.tint(): KindTint {
    val c = MaterialTheme.colorScheme
    val x = BahrTheme.colors
    return when (this) {
        AlertKind.HoldExpiring, AlertKind.BookingCancelled -> KindTint(c.errorContainer, c.onErrorContainer)
        AlertKind.SeatOpened, AlertKind.BookingRefund -> KindTint(x.successContainer, x.onSuccessContainer)
        AlertKind.HostMessage -> KindTint(x.quaternaryContainer, x.onQuaternaryContainer)
        AlertKind.BookingConfirmed, AlertKind.TripReminder, AlertKind.DateMoved, AlertKind.BookingChanged ->
            KindTint(c.primaryContainer, c.onPrimaryContainer)
        AlertKind.ReviewApproved, AlertKind.Broadcast -> KindTint(c.secondaryContainer, c.onSecondaryContainer)
        AlertKind.ReviewRejected, AlertKind.Other -> KindTint(c.surfaceContainer, c.onSurfaceVariant)
    }
}

private val AlertKind.icon: BahrIcons
    get() =
        when (this) {
            AlertKind.BookingConfirmed -> BahrIcons.CheckCircle
            AlertKind.SeatOpened -> BahrIcons.EventSeat
            AlertKind.DateMoved -> BahrIcons.EventUpcoming
            AlertKind.HoldExpiring -> BahrIcons.Timer
            AlertKind.TripReminder -> BahrIcons.DirectionsBus
            AlertKind.ReviewApproved -> BahrIcons.Star
            AlertKind.ReviewRejected -> BahrIcons.VisibilityOff
            AlertKind.HostMessage -> BahrIcons.Chat
            AlertKind.BookingCancelled -> BahrIcons.EventBusy
            AlertKind.BookingRefund -> BahrIcons.Payments
            AlertKind.BookingChanged -> BahrIcons.Schedule
            AlertKind.Broadcast -> BahrIcons.Campaign
            AlertKind.Other -> BahrIcons.Notifications
        }

/** The handoff's title is one line in the prototype; two keep a long one readable at large text. */
private const val TITLE_LINES = 2

/**
 * How far the row must be dragged, as a share of its width, before letting go asks to delete (M3-M22: at least 40%,
 * so a scroll that drifts sideways never gets there).
 */
internal const val DELETE_THRESHOLD_SHARE = .4f
