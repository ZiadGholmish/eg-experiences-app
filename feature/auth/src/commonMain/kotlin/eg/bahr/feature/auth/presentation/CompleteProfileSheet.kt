package eg.bahr.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.designsystem.components.BahrBottomSheet
import eg.bahr.core.designsystem.components.BahrBottomSheetContent
import eg.bahr.core.designsystem.components.BahrFormField
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.account_name_invalid
import eg.bahr.core.localization.generated.resources.account_phone_placeholder
import eg.bahr.core.localization.generated.resources.auth_profile_body
import eg.bahr.core.localization.generated.resources.auth_profile_later
import eg.bahr.core.localization.generated.resources.auth_profile_later_note
import eg.bahr.core.localization.generated.resources.auth_profile_name
import eg.bahr.core.localization.generated.resources.auth_profile_name_from
import eg.bahr.core.localization.generated.resources.auth_profile_phone
import eg.bahr.core.localization.generated.resources.auth_profile_save
import eg.bahr.core.localization.generated.resources.auth_profile_title
import eg.bahr.core.localization.generated.resources.edit_saving
import eg.bahr.core.localization.generated.resources.provider_apple
import eg.bahr.core.localization.generated.resources.provider_google
import eg.bahr.core.localization.localizedMessage
import eg.bahr.core.localization.mobileDigitsCounter
import eg.bahr.feature.auth.model.AuthProvider
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * "Complete your profile" as a modal bottom sheet (M3-M0b, handoff §2; it replaces M3-M0's full-screen
 * step): shown over whatever screen is up after a sign-in without a name or phone. Draws nothing while
 * nothing is asked. A [BahrBottomSheet] (review R4-3): Save is the coral step, Later the way out, and
 * swiping it away is Later too, except while saving (the sheet stays to show the answer).
 */
@Composable
internal fun CompleteProfileSheet(viewModel: CompleteProfileViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.prompt == null) return
    val actions =
        CompleteProfileActions(
            onName = viewModel::setName,
            onPhone = viewModel::setPhone,
            onSave = viewModel::save,
            onLater = viewModel::later,
        )
    BahrBottomSheet(props = sheetProps(state, actions)) { CompleteProfileForm(state, actions) }
}

internal class CompleteProfileActions(
    val onName: (String) -> Unit = {},
    val onPhone: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onLater: () -> Unit = {},
)

/** The sheet's inside, for the screenshot tests (a sheet is a second window they cannot draw). */
@Composable
internal fun CompleteProfileContent(
    state: CompleteProfileUiState,
    actions: CompleteProfileActions,
) {
    BahrBottomSheetContent(sheetProps(state, actions)) { CompleteProfileForm(state, actions) }
}

@Composable
private fun sheetProps(
    state: CompleteProfileUiState,
    actions: CompleteProfileActions,
) = BahrBottomSheet.Props(
    title = stringResource(Res.string.auth_profile_title),
    onDismiss = actions.onLater,
    actions =
        listOf(
            BahrBottomSheet.Action(
                text = stringResource(if (state.saving) Res.string.edit_saving else Res.string.auth_profile_save),
                onClick = actions.onSave,
                loading = state.saving,
                testTag = PROFILE_SAVE_TAG,
            ),
        ),
    cancel =
        BahrBottomSheet.Action(
            text = stringResource(Res.string.auth_profile_later),
            onClick = actions.onLater,
            enabled = !state.saving,
        ),
    dismissible = !state.saving,
    footnote = stringResource(Res.string.auth_profile_later_note),
)

/** The `how_to_reg` disc, why, the name (prefilled) and the mobile, and an error if the save failed. */
@Composable
private fun ColumnScope.CompleteProfileForm(
    state: CompleteProfileUiState,
    actions: CompleteProfileActions,
) {
    val c = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(BahrSize.avatar).clip(BahrTheme.shapes.full).background(c.primaryContainer),
    ) {
        Icon(
            BahrIcons.HowToReg.filled(),
            contentDescription = null,
            tint = c.onPrimaryContainer,
            modifier = Modifier.size(BahrSize.iconNav),
        )
    }
    Text(
        text = stringResource(Res.string.auth_profile_body),
        style = MaterialTheme.typography.bodyLarge,
        color = BahrTheme.colors.onSurfaceSecondary,
    )
    BahrFormField(
        props =
            BahrFormField.Props(
                label = stringResource(Res.string.auth_profile_name),
                value = state.name,
                onValueChange = actions.onName,
                enabled = !state.saving,
                support = state.prompt?.provider?.let { stringResource(Res.string.auth_profile_name_from, it.providerName()) },
                error = if (state.nameInvalid) stringResource(Res.string.account_name_invalid) else null,
                keyboard = BahrFormField.Keyboard.Name,
                testTag = PROFILE_NAME_TAG,
            ),
    )
    BahrFormField(
        props =
            BahrFormField.Props(
                label = stringResource(Res.string.auth_profile_phone),
                value = state.phone,
                onValueChange = actions.onPhone,
                enabled = !state.saving,
                leadingIcon = BahrIcons.Smartphone,
                placeholder = stringResource(Res.string.account_phone_placeholder),
                counter = if (state.checked) mobileDigitsCounter(state.digits) else null,
                error = state.phoneProblem?.localizedMessage(),
                leftToRight = true,
                keyboard = BahrFormField.Keyboard.Phone,
                imeAction = ImeAction.Done,
                onImeAction = actions.onSave,
                testTag = PROFILE_PHONE_TAG,
            ),
    )
    state.error?.let { error ->
        Text(
            text = error.localizedMessage(),
            style = MaterialTheme.typography.bodyMedium,
            color = c.onErrorContainer,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }
}

/** The provider's own name for a `providers` entry ("From your Google account"). */
@Composable
internal fun String.providerName(): String =
    when (this) {
        AuthProvider.Apple.wire -> stringResource(Res.string.provider_apple)
        else -> stringResource(Res.string.provider_google)
    }

internal const val PROFILE_NAME_TAG = "profile_name"
internal const val PROFILE_PHONE_TAG = "profile_phone"
internal const val PROFILE_SAVE_TAG = "profile_save"
