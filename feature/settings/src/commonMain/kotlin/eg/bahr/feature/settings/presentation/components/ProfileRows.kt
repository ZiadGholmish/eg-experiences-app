package eg.bahr.feature.settings.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrBorder
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme

/** One row of a Profile group (handoff §2 `row()`): what it shows and what a tap does. */
internal data class ProfileRow(
    val icon: BahrIcons,
    val label: String,
    val tag: String,
    val onClick: () -> Unit,
    /** A second line under the label (Personal info's "name · phone"). */
    val sub: String? = null,
    /** A value at the end (Language's "العربية", About's version), read left to right when [valueLtr]. */
    val value: String? = null,
    val valueLtr: Boolean = false,
    /** Sign out and Delete account act at once: no chevron. */
    val opensPage: Boolean = true,
    /** Delete account: icon and label in `onErrorContainer`. */
    val danger: Boolean = false,
    val enabled: Boolean = true,
)

/**
 * A group of rows on `surfaceLow` with `extraLarge` corners and a 1dp `outlineVariant` rule between rows
 * (handoff §2), on Profile's `surface` page. The whole row is the touch target (at least 48dp).
 */
@Composable
internal fun ProfileRowGroup(
    rows: List<ProfileRow>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.extraLarge)
                .background(BahrTheme.colors.surfaceLow),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(thickness = BahrBorder.hairline, color = MaterialTheme.colorScheme.outlineVariant)
            ProfileRowItem(row)
        }
    }
}

@Composable
private fun ProfileRowItem(row: ProfileRow) {
    val c = MaterialTheme.colorScheme
    val accent = if (row.danger) c.onErrorContainer else c.primary
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = BahrSize.touchTarget)
                .clickable(enabled = row.enabled, role = Role.Button, onClick = row.onClick)
                .testTag(row.tag)
                .padding(horizontal = BahrSpacing.lg, vertical = BahrSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        Icon(row.icon.outlined(), contentDescription = null, tint = accent, modifier = Modifier.size(BahrSize.iconLarge))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = if (row.danger) c.onErrorContainer else c.onSurface,
            )
            row.sub?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        row.value?.let {
            Text(
                text = if (row.valueLtr) BahrFormat.ltr(it) else it,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = c.onSurfaceVariant,
            )
        }
        if (row.opensPage) {
            // Auto-mirrored: points to the reading end in both languages.
            Icon(
                BahrIcons.ChevronForward.outlined(),
                contentDescription = null,
                tint = c.onSurfaceVariant,
                modifier = Modifier.size(BahrSize.iconLarge),
            )
        }
    }
}
