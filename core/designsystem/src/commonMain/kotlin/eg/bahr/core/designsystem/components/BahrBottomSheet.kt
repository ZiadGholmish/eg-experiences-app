package eg.bahr.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import eg.bahr.core.designsystem.theme.BahrBorder
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme

/**
 * The app's one way to confirm or choose (user decision 2026-10-14: bottom sheets instead of dialogs;
 * dialogs only for the OS's own permission prompts). A Material 3 modal bottom sheet in Bahr's tokens:
 * the drag handle, a title, the body ([BahrBottomSheet] content), then the actions stacked full width,
 * the step (coral [Action.Style.Primary]) or the destructive one (`errorStrong`) first and the way out
 * ([Props.cancel]) last.
 *
 * Keyboard and switch-access focus starts on the safe action, the cancel (touch mode gives buttons none,
 * so it is asked for again when a key first switches the input mode). Swiping the sheet away, the scrim
 * and Back all call [Props.onDismiss], unless the sheet is busy ([Props.dismissible] false). The sheet is its own window; the app's layout direction is
 * carried into it by hand, as Android takes a window's direction from the platform locale otherwise.
 *
 * Callers: Delete account and "Complete your profile" (feature:auth), leaving a seat hold
 * (feature:booking), Profile's Language, Appearance and About (feature:settings).
 */
interface BahrBottomSheet {
    @Immutable
    data class Action(
        val text: String,
        val style: Style = Style.Primary,
        val onClick: () -> Unit = {},
        val loading: Boolean = false,
        val enabled: Boolean = true,
        val testTag: String? = null,
    ) {
        enum class Style {
            /** The step forward: the coral button. */
            Primary,

            /** It cannot be undone: filled `errorStrong`, never coral. */
            Destructive,
        }
    }

    @Immutable
    data class Props(
        val title: String,
        val onDismiss: () -> Unit,
        /** Stacked under the body in this order; at most one of them should be the screen's coral step. */
        val actions: List<Action> = emptyList(),
        /** The safe way out, last, outlined; it takes the default focus. */
        val cancel: Action? = null,
        /**
         * False while a request is under way (a deletion, a release, a save): Back, a tap on the scrim and
         * a swipe then leave the sheet where it is, so an error that comes back is shown in it (M3-M0b
         * review R4-2: Material hides the sheet before it asks [onDismiss], so a veto there came too late).
         */
        val dismissible: Boolean = true,
        /** A quiet line under the actions (the first-sign-in sheet's "We'll ask again at checkout"). */
        val footnote: String? = null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BahrBottomSheet(
    modifier: Modifier = Modifier,
    props: BahrBottomSheet.Props,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val direction = LocalLayoutDirection.current
    val dismissible by rememberUpdatedState(props.dismissible)
    ModalBottomSheet(
        onDismissRequest = { if (dismissible) props.onDismiss() },
        // The sheet asks this before it moves; a busy sheet refuses to hide.
        sheetState =
            rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = {
                it != SheetValue.Hidden ||
                    dismissible
            }),
        sheetGesturesEnabled = props.dismissible,
        properties =
            ModalBottomSheetProperties(
                shouldDismissOnBackPress = props.dismissible,
                shouldDismissOnClickOutside = props.dismissible,
            ),
        shape = BahrTheme.shapes.sheet,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = BahrTheme.colors.scrim,
        modifier = modifier,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            BahrBottomSheetContent(props, content)
        }
    }
}

/** The sheet's inside, without the sheet: for screenshot tests, which cannot draw a second window. */
@Composable
fun BahrBottomSheetContent(
    props: BahrBottomSheet.Props,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val safe = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(safe, inputMode, props.cancel != null) {
        if (inputMode == InputMode.Keyboard && props.cancel != null) safe.requestFocus()
    }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = BahrSpacing.xl, end = BahrSpacing.xl, bottom = BahrSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        Text(
            text = props.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        content()
        if (props.actions.isNotEmpty() || props.cancel != null) {
            Column(Modifier.padding(top = BahrSpacing.sm), verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
                props.actions.forEach { SheetAction(it) }
                props.cancel?.let { SheetCancel(it, Modifier.focusRequester(safe)) }
            }
        }
        props.footnote?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SheetAction(action: BahrBottomSheet.Action) {
    val tagged = Modifier.fillMaxWidth().then(action.testTag?.let { Modifier.testTag(it) } ?: Modifier)
    when (action.style) {
        BahrBottomSheet.Action.Style.Primary ->
            BahrPrimaryButton(
                text = action.text,
                onClick = action.onClick,
                enabled = action.enabled,
                loading = action.loading,
                modifier = tagged,
            )
        BahrBottomSheet.Action.Style.Destructive -> {
            val c = MaterialTheme.colorScheme
            val fill = BahrTheme.colors.errorStrong
            Button(
                onClick = action.onClick,
                enabled = action.enabled && !action.loading,
                shape = BahrTheme.shapes.full,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = fill,
                        contentColor = c.onError,
                        disabledContainerColor = fill,
                        disabledContentColor = c.onError,
                    ),
                modifier = tagged.heightIn(min = BahrSize.touchTarget),
            ) {
                if (action.loading) {
                    CircularProgressIndicator(Modifier.size(BahrSize.iconSmall), color = c.onError, strokeWidth = BahrBorder.selected)
                } else {
                    Text(action.text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold))
                }
            }
        }
    }
}

@Composable
private fun SheetCancel(
    action: BahrBottomSheet.Action,
    modifier: Modifier,
) {
    OutlinedButton(
        onClick = action.onClick,
        enabled = action.enabled,
        shape = BahrTheme.shapes.full,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = BahrSize.touchTarget)
                .then(action.testTag?.let { Modifier.testTag(it) } ?: Modifier),
    ) {
        Text(action.text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
