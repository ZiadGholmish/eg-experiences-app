package eg.bahr.feature.notifications.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.alerts_delete
import eg.bahr.core.localization.generated.resources.alerts_mark_read
import eg.bahr.core.localization.generated.resources.alerts_mark_unread
import eg.bahr.core.localization.generated.resources.alerts_row_read
import eg.bahr.core.localization.generated.resources.alerts_row_unread
import eg.bahr.feature.notifications.presentation.AlertKind
import eg.bahr.feature.notifications.presentation.InboxEntry
import org.jetbrains.compose.resources.stringResource

/** The row's three ways out: open it (a tap), read or unread (the menu), delete (a swipe, or the menu). */
@Immutable
internal data class NotificationRowActions(
    val onOpen: () -> Unit,
    val onSetRead: (Boolean) -> Unit,
    val onDelete: () -> Unit,
)

/**
 * One notification (account handoff §3): 72dp at least, the gutter on both sides, a 40dp disc tinted by type, the title
 * (800 unread, 600 read) with the relative [time] at its end, one line of body, and the unread dot; an unread row is
 * tinted `unread` too, so the state never rests on colour alone.
 *
 * A swipe either way deletes it (with Undo, the page's snackbar), revealing a delete mark on the side it leaves. A
 * long press opens a menu with "Mark as read/unread" and "Delete", the swipe's accessible twin (handoff: "long-press opens
 * the same actions in a menu"); screen readers get both as custom actions on the one node that reads the row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NotificationRow(
    item: InboxEntry.Item,
    time: String,
    actions: NotificationRowActions,
    modifier: Modifier = Modifier,
) {
    val swipe = rememberSwipeToDismissBoxState()
    // A row brought back by Undo is a new composition of the same key: it must not come back swiped away.
    LaunchedEffect(swipe) { if (swipe.currentValue != SwipeToDismissBoxValue.Settled) swipe.snapTo(SwipeToDismissBoxValue.Settled) }
    var menu by remember { mutableStateOf(false) }

    val markLabel = stringResource(if (item.read) Res.string.alerts_mark_unread else Res.string.alerts_mark_read)
    val deleteLabel = stringResource(Res.string.alerts_delete)
    val spoken = stringResource(if (item.read) Res.string.alerts_row_read else Res.string.alerts_row_unread, item.title, item.body, time)

    SwipeToDismissBox(
        state = swipe,
        modifier = modifier,
        backgroundContent = { DeleteMark(swipe.dismissDirection) },
        onDismiss = { actions.onDelete() },
    ) {
        Box {
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
                                        actions.onDelete()
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
                        actions.onDelete()
                    },
                )
            }
        }
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
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                // The row paints its own ground (the page's, then the unread tint), so the swipe's mark shows only
                // where the row has moved away.
                .background(c.surface)
                .background(if (item.read) Color.Transparent else BahrTheme.colors.unread)
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
                .background(if (item.read) Color.Transparent else c.primary),
        )
    }
}

/** What a swipe uncovers: the delete mark on the side the row leaves from, on `errorContainer`. */
@Composable
private fun DeleteMark(direction: SwipeToDismissBoxValue) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxSize().background(c.errorContainer).padding(horizontal = BahrSpacing.gutter),
        contentAlignment = if (direction == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Icon(BahrIcons.Delete.outlined(), contentDescription = null, tint = c.onErrorContainer)
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
