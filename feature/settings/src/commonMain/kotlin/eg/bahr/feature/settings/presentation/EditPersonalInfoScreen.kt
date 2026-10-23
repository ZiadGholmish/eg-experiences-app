package eg.bahr.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.designsystem.components.BahrErrorView
import eg.bahr.core.designsystem.components.BahrFormField
import eg.bahr.core.designsystem.components.BahrPrimaryButton
import eg.bahr.core.designsystem.components.BahrSkeleton
import eg.bahr.core.designsystem.components.BahrSkeletonBlock
import eg.bahr.core.designsystem.components.StickyActionBar
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.account_name_invalid
import eg.bahr.core.localization.generated.resources.account_phone_help
import eg.bahr.core.localization.generated.resources.action_back
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.auth_profile_name
import eg.bahr.core.localization.generated.resources.auth_profile_phone
import eg.bahr.core.localization.generated.resources.auth_profile_save
import eg.bahr.core.localization.generated.resources.edit_email
import eg.bahr.core.localization.generated.resources.edit_email_hidden
import eg.bahr.core.localization.generated.resources.edit_email_hidden_note
import eg.bahr.core.localization.generated.resources.edit_email_note
import eg.bahr.core.localization.generated.resources.edit_intro
import eg.bahr.core.localization.generated.resources.edit_phone_note
import eg.bahr.core.localization.generated.resources.edit_saving
import eg.bahr.core.localization.generated.resources.profile_personal_info
import eg.bahr.core.localization.generated.resources.provider_apple
import eg.bahr.core.localization.generated.resources.provider_google
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import eg.bahr.core.localization.mobileDigitsCounter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Edit personal info (M3-M0b, handoff §2), pushed from Profile (bar hidden). [onBack] leaves it unsaved;
 * [onSaved] once the server took the change (Profile then shows "Saved").
 */
@Composable
internal fun EditPersonalInfoScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditPersonalInfoViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saved by rememberUpdatedState(onSaved)
    LaunchedEffect(state.saved) { if (state.saved) saved() }
    EditPersonalInfoContent(
        state = state,
        actions =
            EditPersonalInfoActions(
                onBack = onBack,
                onName = viewModel::setName,
                onPhone = viewModel::setPhone,
                onSave = viewModel::save,
                onRetry = viewModel::load,
            ),
    )
}

internal class EditPersonalInfoActions(
    val onBack: () -> Unit = {},
    val onName: (String) -> Unit = {},
    val onPhone: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** Stateless, for the screenshot tests. */
@Composable
internal fun EditPersonalInfoContent(
    state: EditPersonalInfoUiState,
    actions: EditPersonalInfoActions,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(c.surface)
                .windowInsetsPadding(WindowInsets.statusBars)
                .imePadding(),
    ) {
        TopBar(actions.onBack)
        when {
            state.loading -> FormSkeleton(Modifier.weight(1f))
            state.loadError != null ->
                Box(Modifier.weight(1f)) {
                    BahrErrorView(
                        message = state.loadError.localizedMessage(),
                        retryLabel = stringResource(Res.string.action_retry),
                        onRetry = if (state.loadError.isRetryable) actions.onRetry else null,
                    )
                }
            else -> {
                Form(state, actions, Modifier.weight(1f))
                StickyActionBar {
                    BahrPrimaryButton(
                        text = stringResource(if (state.saving) Res.string.edit_saving else Res.string.auth_profile_save),
                        onClick = actions.onSave,
                        loading = state.saving,
                        modifier = Modifier.weight(1f).testTag(EDIT_SAVE_TAG),
                    )
                }
            }
        }
    }
}

/** The handoff's 64dp top bar: a plain back arrow (auto-mirrored) and the title. */
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
                // would draw it black on the dark page (M3-M6a review).
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(BahrSize.iconNav),
            )
        }
        Text(
            text = stringResource(Res.string.profile_personal_info),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun Form(
    state: EditPersonalInfoUiState,
    actions: EditPersonalInfoActions,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = BahrSpacing.gutter, end = BahrSpacing.gutter, top = BahrSpacing.xs, bottom = BahrSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.gutter),
    ) {
        Text(
            text = stringResource(Res.string.edit_intro),
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
                    error = if (state.nameInvalid) stringResource(Res.string.account_name_invalid) else null,
                    keyboard = BahrFormField.Keyboard.Name,
                    testTag = EDIT_NAME_TAG,
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
                    counter = mobileDigitsCounter(state.digits),
                    support = stringResource(Res.string.account_phone_help),
                    supportIsHelp = true,
                    error = state.phoneProblem?.localizedMessage(),
                    leftToRight = true,
                    keyboard = BahrFormField.Keyboard.Phone,
                    imeAction = ImeAction.Done,
                    onImeAction = actions.onSave,
                    testTag = EDIT_PHONE_TAG,
                ),
        )
        state.email?.let { email -> EmailField(email, state.provider) }
        // The phone is never verified (no OTP): say so, on the advisory tint.
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(BahrTheme.shapes.large)
                    .background(c.secondaryContainer)
                    .padding(BahrSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
        ) {
            Icon(
                BahrIcons.Info.outlined(),
                contentDescription = null,
                tint = c.onSecondaryContainer,
                modifier = Modifier.size(BahrSize.iconMedium),
            )
            Text(
                text = stringResource(Res.string.edit_phone_note),
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSecondaryContainer,
            )
        }
        state.saveError?.let { error ->
            Text(
                text = error.localizedMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = c.onErrorContainer,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
    }
}

/**
 * The form while the profile loads (M3-M14), laid out as [Form]: its intro and the fields' labels are
 * fixed copy, so they are text (the text-first rule); the fields' boxes shimmer until the values come.
 */
@Composable
private fun FormSkeleton(modifier: Modifier = Modifier) {
    BahrSkeleton(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = BahrSpacing.gutter, end = BahrSpacing.gutter, top = BahrSpacing.xs, bottom = BahrSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.gutter),
    ) {
        Text(
            text = stringResource(Res.string.edit_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = BahrTheme.colors.onSurfaceSecondary,
        )
        listOf(Res.string.auth_profile_name, Res.string.auth_profile_phone, Res.string.edit_email).forEach { label ->
            Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
                Text(text = stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                BahrSkeletonBlock(
                    modifier = Modifier.fillMaxWidth().height(BahrSize.formField),
                    props = BahrSkeletonBlock.Props(shape = BahrTheme.shapes.medium),
                )
            }
        }
    }
}

/** Read-only, from the sign-in provider; Apple's private relay shows as "Hidden by Apple". */
@Composable
private fun EmailField(
    email: String,
    provider: String?,
) {
    val hidden = isAppleRelay(email)
    val providerName = stringResource(if (provider == APPLE_PROVIDER) Res.string.provider_apple else Res.string.provider_google)
    BahrFormField(
        props =
            BahrFormField.Props(
                label = stringResource(Res.string.edit_email),
                value = if (hidden) stringResource(Res.string.edit_email_hidden) else email,
                readOnly = true,
                leadingIcon = BahrIcons.Lock,
                support =
                    if (hidden) {
                        stringResource(Res.string.edit_email_hidden_note)
                    } else {
                        stringResource(Res.string.edit_email_note, providerName)
                    },
                // An address reads left to right in Arabic too; the "Hidden by Apple" words do not.
                leftToRight = !hidden,
                testTag = EDIT_EMAIL_TAG,
            ),
    )
}

internal const val EDIT_NAME_TAG = "edit_name"
internal const val EDIT_PHONE_TAG = "edit_phone"
internal const val EDIT_EMAIL_TAG = "edit_email"
internal const val EDIT_SAVE_TAG = "edit_save"
