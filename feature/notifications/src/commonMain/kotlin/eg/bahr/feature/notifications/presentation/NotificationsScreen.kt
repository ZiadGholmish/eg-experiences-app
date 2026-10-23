package eg.bahr.feature.notifications.presentation

import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.designsystem.components.BahrLottie
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_back
import eg.bahr.core.localization.generated.resources.alerts_empty_body
import eg.bahr.core.localization.generated.resources.alerts_empty_title
import eg.bahr.core.localization.generated.resources.alerts_signed_out_body
import eg.bahr.core.localization.generated.resources.alerts_signed_out_title
import eg.bahr.core.localization.generated.resources.alerts_title
import eg.bahr.core.network.auth.SessionState
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The notifications page (M3-M0b, handoff §3's states), pushed from Home's bell since M3-M6a (the Alerts
 * tab before); [onBack] is its top bar's Back. Until M3-B9 / M3-M6 bring the list: signed in,
 * the empty state ("No notifications yet"); signed out, why sign-in is needed and the sign-in buttons,
 * which are feature:auth's ([signInButtons], joined in :composeApp). Nothing while the stored sign-in
 * is still being read, so neither state flashes.
 */
@Composable
internal fun NotificationsScreen(
    onBack: () -> Unit,
    signInButtons: @Composable (Modifier) -> Unit,
    viewModel: NotificationsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NotificationsContent(state = state, onBack = onBack, signInButtons = signInButtons)
}

/** Stateless, for the screenshot tests. */
@Composable
internal fun NotificationsContent(
    state: NotificationsUiState,
    onBack: () -> Unit,
    signInButtons: @Composable (Modifier) -> Unit,
    scroll: ScrollState = rememberScrollState(),
) {
    val c = MaterialTheme.colorScheme
    // The handoff's page is `surface` (not the off-white page of the trip lists), in light and dark.
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(c.surface)
                .windowInsetsPadding(WindowInsets.statusBars)
                // A pushed page since M3-M6a, so no tab bar takes the gesture area for it: the sign-in
                // buttons, scrolled to the end with large text, stay above it.
                .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        TopBar(onBack)
        // The state sits in the middle of the space under the title, and scrolls when it does not fit
        // (large text, a small phone).
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier.verticalScroll(scroll).heightIn(min = maxHeight).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
            ) {
                StateFor(state.session, signInButtons)
            }
        }
    }
}

/**
 * A pushed page's bar (M3-M6a), as the handoff's other pushed page (Edit personal info): Back, then the
 * title. Back is drawn because iOS has no system Back button, only the edge swipe.
 */
@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = BahrSize.topBar).padding(horizontal = BahrSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(BahrSize.touchTarget)) {
            Icon(
                BahrIcons.ArrowBack.outlined(),
                contentDescription = stringResource(Res.string.action_back),
                // Set, not inherited: iOS roots have no Scaffold to provide a content colour, so dark
                // would draw it black on the dark page.
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(BahrSize.iconNav),
            )
        }
        Text(
            text = stringResource(Res.string.alerts_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun StateFor(
    session: SessionState,
    signInButtons: @Composable (Modifier) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    when (session) {
        SessionState.Unknown -> Unit
        SessionState.SignedOut ->
            StateMessage(
                art = { StateDisc(BahrIcons.LockPerson, disc = c.surfaceContainer, tint = c.onSurfaceVariant) },
                title = stringResource(Res.string.alerts_signed_out_title),
                body = stringResource(Res.string.alerts_signed_out_body),
            ) { signInButtons(Modifier.padding(top = BahrSpacing.md)) }
        // The empty state rings its bell once (M3-M8): the same disc and bell as the still state's, in
        // motion. Decorative: the title says it.
        is SessionState.SignedIn ->
            StateMessage(
                art = {
                    BahrLottie(
                        modifier = Modifier.width(BahrSize.lottieState),
                        props = BahrLottie.Props(BahrLottie.Animation.Bell),
                    )
                },
                title = stringResource(Res.string.alerts_empty_title),
                body = stringResource(Res.string.alerts_empty_body),
            )
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
