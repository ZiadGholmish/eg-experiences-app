package eg.bahr.feature.hostworkspace.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_back
import eg.bahr.core.localization.generated.resources.host_day_time
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/** The handoff's 64dp top bar: a back arrow (it mirrors in RTL) and the title, with an optional line under it. */
@Composable
internal fun HostTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = BahrSize.topBar).padding(horizontal = BahrSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(BahrSize.touchTarget)) {
            Icon(
                BahrIcons.ArrowBack.outlined(),
                contentDescription = stringResource(Res.string.action_back),
                // Set, not inherited: iOS roots have no Scaffold to provide a content colour (M3-M6a review).
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(BahrSize.iconNav),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A coloured disc with a name's first letter (the handoff's manifest avatars): an Arabic name's first letter (its
 * letters join, so one reads best), else the first letters of two words. Hidden from screen readers: the name beside it
 * says who it is.
 */
@Composable
internal fun InitialDisc(
    name: String,
    size: Dp,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(size)
                .clip(BahrTheme.shapes.full)
                .background(container)
                .clearAndSetSemantics {},
    ) {
        val letters = initials(name)
        if (letters.isEmpty()) {
            Icon(BahrIcons.Person.filled(), contentDescription = null, tint = content, modifier = Modifier.size(BahrSize.iconMedium))
        } else {
            Text(text = letters, style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}

internal fun initials(name: String): String {
    val words = name.trim().split(WHITESPACE).filter { word -> word.any { it.isLetter() } }
    val first = words.firstOrNull()?.first { it.isLetter() } ?: return ""
    return if (first in ARABIC_BLOCK) first.toString() else words.take(LATIN_INITIALS).joinToString("") { it.first().uppercase() }
}

/** The Cairo wall clock the contract's times mean (`Africa/Cairo`), whatever the phone's own zone. */
internal val CAIRO: TimeZone = TimeZone.of("Africa/Cairo")

/** "21 Nov, 18:42" in Cairo time: a booking's or a broadcast's moment. */
@Composable
internal fun cairoMoment(instant: Instant): String {
    val local = instant.toLocalDateTime(CAIRO)
    return stringResource(Res.string.host_day_time, BahrFormat.dayMonth(local.date), BahrFormat.ltr(BahrFormat.time(local.time)))
}

/** "18:42" in Cairo time. */
internal fun cairoTime(instant: Instant): String = BahrFormat.time(instant.toLocalDateTime(CAIRO).time)

/** The Cairo date of [instant]. */
internal fun cairoDate(instant: Instant): LocalDate = instant.toLocalDateTime(CAIRO).date

private val WHITESPACE = Regex("\\s+")
private val ARABIC_BLOCK = '؀'..'ۿ'
private const val LATIN_INITIALS = 2
