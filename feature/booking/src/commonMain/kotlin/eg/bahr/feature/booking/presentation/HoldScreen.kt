package eg.bahr.feature.booking.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eg.bahr.core.designsystem.components.Overline
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.booking_held_next
import eg.bahr.core.localization.generated.resources.booking_held_title
import eg.bahr.core.localization.generated.resources.booking_total
import eg.bahr.core.localization.generated.resources.booking_your_ref
import eg.bahr.feature.booking.navigation.HoldRoute
import eg.bahr.feature.booking.presentation.components.BookingTopBar
import org.jetbrains.compose.resources.stringResource

/**
 * Placeholder for the held seats (step 2 of 3), so the hold placed on date + party lands somewhere
 * real. It shows only what the hold handed over: the reference and the server's total. M2-M2 turns
 * this into the deadline-driven countdown (from [HoldRoute.holdExpiresAt] and [HoldRoute.serverNow])
 * and M3 adds payment; nothing here reads the booking again or counts down.
 */
@Composable
internal fun HoldScreen(
    hold: HoldRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        BookingTopBar(title = stringResource(Res.string.booking_held_title), step = 2, onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
        ) {
            Overline(stringResource(Res.string.booking_your_ref))
            // A reference reads left to right in Arabic too, like a phone number.
            Text(text = BahrFormat.ltr(hold.ref), style = BahrTheme.type.display, color = MaterialTheme.colorScheme.primary)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.booking_total),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = BahrFormat.money(hold.totalAmount, hold.totalCurrency, BahrTheme.locale.isArabic),
                    style = BahrTheme.type.price,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(Res.string.booking_held_next),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
