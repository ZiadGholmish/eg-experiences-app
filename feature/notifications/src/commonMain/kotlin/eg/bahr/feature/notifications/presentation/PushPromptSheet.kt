package eg.bahr.feature.notifications.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.designsystem.components.BahrBottomSheet
import eg.bahr.core.designsystem.components.BahrBottomSheetContent
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.push_prompt_allow
import eg.bahr.core.localization.generated.resources.push_prompt_body
import eg.bahr.core.localization.generated.resources.push_prompt_not_now
import eg.bahr.core.localization.generated.resources.push_prompt_point_first
import eg.bahr.core.localization.generated.resources.push_prompt_point_no_ads
import eg.bahr.core.localization.generated.resources.push_prompt_title
import eg.bahr.feature.notifications.data.PushPermissions
import eg.bahr.feature.notifications.data.rememberPushPermissions
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The push pre-prompt (M3-M5, handoff §3) for one waiting-list join: decides whether to ask, shows the sheet if so,
 * and calls [onDone] once it is answered or not needed. The screen that placed it removes it then.
 */
@Composable
internal fun PushPromptSheet(
    onDone: () -> Unit,
    viewModel: PushPromptViewModel = koinViewModel(),
    permissions: PushPermissions = rememberPushPermissions(),
) {
    // One decision per placement: the screen places the sheet again for its next join.
    LaunchedEffect(viewModel, permissions) { viewModel.offer(permissions) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(state) {
        if (state == PushPromptUiState.Done) {
            viewModel.finished()
            done()
        }
    }
    if (state == PushPromptUiState.Showing) {
        BahrBottomSheet(props = props(onAllow = { viewModel.allow(permissions) }, onNotNow = viewModel::notNow)) { PushPromptBody() }
    }
}

/** The sheet's inside, for the screenshot tests (a sheet is a second window they cannot draw). */
@Composable
internal fun PushPromptContent(
    onAllow: () -> Unit = {},
    onNotNow: () -> Unit = {},
) {
    BahrBottomSheetContent(props(onAllow, onNotNow)) { PushPromptBody() }
}

/**
 * "Yes, notify me" is the sheet's step (coral); "Not now" the way out. Swiping the sheet away or tapping the scrim is
 * "Not now" too: the person chose not to answer, and it counts towards the two after which the app stops asking.
 */
@Composable
private fun props(
    onAllow: () -> Unit,
    onNotNow: () -> Unit,
) = BahrBottomSheet.Props(
    title = stringResource(Res.string.push_prompt_title),
    onDismiss = onNotNow,
    actions =
        listOf(
            BahrBottomSheet.Action(
                text = stringResource(Res.string.push_prompt_allow),
                onClick = onAllow,
                testTag = PUSH_PROMPT_ALLOW_TAG,
            ),
        ),
    cancel =
        BahrBottomSheet.Action(
            text = stringResource(Res.string.push_prompt_not_now),
            onClick = onNotNow,
            testTag = PUSH_PROMPT_NOT_NOW_TAG,
        ),
)

/**
 * The prototype's sheet: the `notifications_active` disc, the promise, then why it matters (no seat is held, first to
 * book gets it) and what it will never be (ads).
 */
@Composable
private fun ColumnScope.PushPromptBody() {
    val c = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(BahrSize.touchTarget).clip(BahrTheme.shapes.full).background(c.primaryContainer),
    ) {
        Icon(
            BahrIcons.NotificationsActive.filled(),
            contentDescription = null,
            tint = c.onPrimaryContainer,
            modifier = Modifier.size(BahrSize.iconNav),
        )
    }
    Text(
        text = stringResource(Res.string.push_prompt_body),
        style = MaterialTheme.typography.bodyLarge,
        color = BahrTheme.colors.onSurfaceSecondary,
    )
    Point(BahrIcons.Bolt, stringResource(Res.string.push_prompt_point_first))
    Point(BahrIcons.DoNotDisturbOn, stringResource(Res.string.push_prompt_point_no_ads))
}

@Composable
private fun Point(
    icon: BahrIcons,
    text: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
        Icon(
            icon.outlined(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(BahrSize.iconMedium),
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = BahrTheme.colors.onSurfaceSecondary)
    }
}

internal const val PUSH_PROMPT_ALLOW_TAG = "push_prompt_allow"
internal const val PUSH_PROMPT_NOT_NOW_TAG = "push_prompt_not_now"
