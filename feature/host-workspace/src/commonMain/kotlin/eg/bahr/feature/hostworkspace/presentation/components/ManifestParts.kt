package eg.bahr.feature.hostworkspace.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import eg.bahr.core.designsystem.components.BahrBadge
import eg.bahr.core.designsystem.components.Overline
import eg.bahr.core.designsystem.components.colors
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.host_call_guest
import eg.bahr.core.localization.generated.resources.host_mark_paid
import eg.bahr.core.localization.generated.resources.host_paid
import eg.bahr.core.localization.generated.resources.host_party
import eg.bahr.core.localization.generated.resources.host_ref_booked
import eg.bahr.core.localization.generated.resources.host_source_manual
import eg.bahr.core.localization.generated.resources.host_unpaid
import eg.bahr.feature.hostworkspace.model.HostTravellerDto
import eg.bahr.feature.hostworkspace.presentation.parseInstant
import org.jetbrains.compose.resources.stringResource

/**
 * One of the handoff's two stat tiles (§7): an icon, an overline, a big figure and a small one after it, on a tinted
 * `extraLarge` panel. Booked seats on `primaryContainer`, the money collected on `secondaryContainer`.
 */
@Composable
internal fun StatTile(
    icon: BahrIcons,
    label: String,
    figure: String,
    after: String?,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(BahrTheme.shapes.extraLarge)
                .background(container)
                .padding(BahrSpacing.md)
                // One sentence for a screen reader: "Seats booked, 14 / 18".
                .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        Icon(icon.filled(), contentDescription = null, tint = content, modifier = Modifier.size(BahrSize.iconLarge))
        Overline(label)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
            Text(text = figure, style = MaterialTheme.typography.headlineMedium, color = content, maxLines = 1)
            after?.let { Text(text = it, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1) }
        }
    }
}

/**
 * The handoff's seat bar (§7): one cell per seat, booked in `primary`, held (not paid yet: they lapse unless paid) in
 * `tertiaryContainer` (the app's hold ground), free in `track`. Past [MAX_CELLS] seats the cells would be slivers, so the
 * bar becomes three plain segments of the same colours. Drawn in one pass, start to end (it mirrors in RTL). Hidden
 * from screen readers: [description] (the legend's words) is read instead.
 */
@Composable
internal fun SeatBar(
    booked: Int,
    held: Int,
    capacity: Int,
    description: String,
    modifier: Modifier = Modifier,
) {
    val bookedColor = MaterialTheme.colorScheme.primary
    val heldColor = MaterialTheme.colorScheme.tertiaryContainer
    val freeColor = BahrTheme.colors.track
    val total = capacity.coerceAtLeast(1)
    val bookedCells = booked.coerceIn(0, total)
    val heldCells = held.coerceIn(0, total - bookedCells)
    val corner = BahrTheme.shapes.extraSmall.topStart
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(BahrSize.seatBar)
                .clearAndSetSemantics { contentDescription = description }
                .drawWithCache {
                    val rtl = layoutDirection == LayoutDirection.Rtl
                    val radius = CornerRadius(corner.toPx(Size(BahrSize.seatBar.toPx(), BahrSize.seatBar.toPx()), this))
                    onDrawBehind {
                        fun colorAt(index: Int) =
                            when {
                                index < bookedCells -> bookedColor
                                index < bookedCells + heldCells -> heldColor
                                else -> freeColor
                            }
                        if (total <= MAX_CELLS) {
                            val gap = BahrSize.seatBarGap.toPx()
                            val cell = (size.width - gap * (total - 1)) / total
                            for (i in 0 until total) {
                                val x = i * (cell + gap)
                                val start = if (rtl) size.width - x - cell else x
                                drawRoundRect(colorAt(i), Offset(start, 0f), Size(cell, size.height), radius)
                            }
                        } else {
                            // Three segments, each as wide as its share of the seats.
                            var x = 0f
                            listOf(bookedCells to bookedColor, heldCells to heldColor, (total - bookedCells - heldCells) to freeColor)
                                .filter { it.first > 0 }
                                .forEach { (seats, color) ->
                                    val width = size.width * seats / total
                                    val start = if (rtl) size.width - x - width else x
                                    drawRoundRect(color, Offset(start, 0f), Size(width, size.height), radius)
                                    x += width
                                }
                        }
                    }
                },
    )
}

/** Above this many seats the bar draws segments, not cells. */
internal const val MAX_CELLS = 40

/**
 * One booking on the bus (§7): the coloured initial, the name, the phone (left to right; a tap calls it) and when it was
 * booked, the reference, the seats, and PAID / UNPAID. A booking the host entered says so, and an unpaid one of those
 * offers "Mark paid" while the date is open and the server named the booking ([HostTravellerDto.id], M5-B5).
 */
@Composable
internal fun TravellerRow(
    traveller: HostTravellerDto,
    tone: BahrBadge.Tone,
    canMarkPaid: Boolean,
    onCall: (phone: String) -> Unit,
    onMarkPaid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.large)
                .background(BahrTheme.colors.surfaceLow)
                .padding(horizontal = BahrSpacing.md, vertical = BahrSpacing.md),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
            val (container, content) = tone.colors()
            InitialDisc(name = traveller.name, size = BahrSize.methodDisc, container = container, content = content)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
                Text(
                    text = traveller.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val booked = parseInstant(traveller.bookedAt)?.let { cairoMoment(it) }
                Text(
                    text =
                        booked?.let { stringResource(Res.string.host_ref_booked, BahrFormat.ltr(traveller.ref), it) }
                            ?: BahrFormat.ltr(traveller.ref),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(Res.string.host_party, traveller.partySize),
                style = MaterialTheme.typography.labelLarge,
                color = BahrTheme.colors.onSurfaceSecondary,
            )
            BahrBadge(
                props =
                    if (traveller.paid) {
                        BahrBadge.Props(stringResource(Res.string.host_paid), BahrBadge.Tone.Success)
                    } else {
                        BahrBadge.Props(stringResource(Res.string.host_unpaid), BahrBadge.Tone.Tertiary)
                    },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
            traveller.phone?.let { phone ->
                val call = stringResource(Res.string.host_call_guest, traveller.name)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
                    modifier =
                        Modifier
                            .heightIn(min = BahrSize.touchTarget)
                            .clip(BahrTheme.shapes.full)
                            .clickable(role = Role.Button, onClick = { onCall(phone) })
                            .padding(horizontal = BahrSpacing.sm)
                            .clearAndSetSemantics {
                                contentDescription = call
                                onClick(label = call) {
                                    onCall(phone)
                                    true
                                }
                            }.testTag(callTag(traveller.ref)),
                ) {
                    Icon(
                        BahrIcons.Smartphone.outlined(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(BahrSize.iconSmall),
                    )
                    // A phone reads left to right inside Arabic too.
                    Text(
                        text = BahrFormat.ltr(phone),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (traveller.manual) {
                Text(
                    text = stringResource(Res.string.host_source_manual),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canMarkPaid) {
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onMarkPaid,
                    modifier = Modifier.heightIn(min = BahrSize.touchTarget).testTag(markPaidTag(traveller.ref)),
                ) {
                    Text(
                        stringResource(Res.string.host_mark_paid),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** The avatar tints of the handoff's list, in turn. */
internal val AVATAR_TONES =
    listOf(BahrBadge.Tone.Primary, BahrBadge.Tone.Secondary, BahrBadge.Tone.Quaternary, BahrBadge.Tone.Tertiary, BahrBadge.Tone.Success)

internal fun callTag(ref: String) = "host_call_$ref"

internal fun markPaidTag(ref: String) = "host_mark_paid_$ref"
