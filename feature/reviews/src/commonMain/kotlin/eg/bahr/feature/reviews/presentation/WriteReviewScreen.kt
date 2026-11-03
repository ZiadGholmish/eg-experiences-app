package eg.bahr.feature.reviews.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import eg.bahr.core.catalog.model.MAX_STARS
import eg.bahr.core.designsystem.components.BahrLottie
import eg.bahr.core.designsystem.components.BahrPrimaryButton
import eg.bahr.core.designsystem.components.ImageGround
import eg.bahr.core.designsystem.components.StickyActionBar
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrAlpha
import eg.bahr.core.designsystem.theme.BahrBorder
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_back
import eg.bahr.core.localization.generated.resources.review_add_photos
import eg.bahr.core.localization.generated.resources.review_already
import eg.bahr.core.localization.generated.resources.review_done
import eg.bahr.core.localization.generated.resources.review_error_not_found
import eg.bahr.core.localization.generated.resources.review_error_not_yet
import eg.bahr.core.localization.generated.resources.review_error_photo_type
import eg.bahr.core.localization.generated.resources.review_error_too_large
import eg.bahr.core.localization.generated.resources.review_moderation_note
import eg.bahr.core.localization.generated.resources.review_photo
import eg.bahr.core.localization.generated.resources.review_photos_failed
import eg.bahr.core.localization.generated.resources.review_photos_hint
import eg.bahr.core.localization.generated.resources.review_photos_label
import eg.bahr.core.localization.generated.resources.review_remove_photo
import eg.bahr.core.localization.generated.resources.review_star
import eg.bahr.core.localization.generated.resources.review_stars_label
import eg.bahr.core.localization.generated.resources.review_stars_missing
import eg.bahr.core.localization.generated.resources.review_submit
import eg.bahr.core.localization.generated.resources.review_submitting
import eg.bahr.core.localization.generated.resources.review_text_count
import eg.bahr.core.localization.generated.resources.review_text_label
import eg.bahr.core.localization.generated.resources.review_text_placeholder
import eg.bahr.core.localization.generated.resources.review_text_too_long
import eg.bahr.core.localization.generated.resources.review_thanks
import eg.bahr.core.localization.generated.resources.review_title
import eg.bahr.core.localization.localizedMessage
import eg.bahr.feature.reviews.model.ReviewLimits
import eg.bahr.feature.reviews.presentation.components.rememberPhotoPicker
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Review a trip (M3-M4), pushed from My trips' "Rate your trip" (bar hidden). The handoffs draw no such page; the
 * layout is this step's and follows Edit personal info (top bar, a scrolling form, the coral step forward in the sticky
 * bar): the stars, the text, the photos, the moderation note. Sent, the form gives way to the thanks and "Done".
 *
 * [onBack] leaves it (unsent, or after sending); [onDone] is "Done" after sending.
 */
@Composable
internal fun WriteReviewScreen(
    ref: String,
    title: String?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: WriteReviewViewModel = koinViewModel(key = "review:$ref") { parametersOf(ref, title) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberPhotoPicker(onPicked = viewModel::addPhotos)
    WriteReviewContent(
        state = state,
        actions =
            WriteReviewActions(
                onBack = onBack,
                onDone = onDone,
                onRating = viewModel::setRating,
                onText = viewModel::setText,
                onAddPhotos = { picker.pick(state.photoSlotsLeft) },
                onRemovePhoto = viewModel::removePhoto,
                onSubmit = viewModel::submit,
            ),
    )
}

internal class WriteReviewActions(
    val onBack: () -> Unit = {},
    val onDone: () -> Unit = {},
    val onRating: (Int) -> Unit = {},
    val onText: (String) -> Unit = {},
    val onAddPhotos: () -> Unit = {},
    val onRemovePhoto: (Int) -> Unit = {},
    val onSubmit: () -> Unit = {},
)

/** Stateless, for the screenshot tests. */
@Composable
internal fun WriteReviewContent(
    state: WriteReviewUiState,
    actions: WriteReviewActions,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.statusBars)
                .imePadding(),
    ) {
        TopBar(state.title, actions.onBack)
        val outcome = state.outcome
        if (outcome != null) {
            Sent(outcome, Modifier.weight(1f))
            StickyActionBar {
                BahrPrimaryButton(
                    text = stringResource(Res.string.review_done),
                    onClick = actions.onDone,
                    modifier = Modifier.weight(1f).testTag(REVIEW_DONE_TAG),
                )
            }
        } else {
            Form(state, actions, Modifier.weight(1f))
            StickyActionBar {
                BahrPrimaryButton(
                    text = stringResource(if (state.submitting) Res.string.review_submitting else Res.string.review_submit),
                    onClick = actions.onSubmit,
                    loading = state.submitting,
                    modifier = Modifier.weight(1f).testTag(REVIEW_SUBMIT_TAG),
                )
            }
        }
    }
}

/** The 64dp top bar: back (auto-mirrored), "Rate your trip" and the trip's title under it. */
@Composable
private fun TopBar(
    title: String?,
    onBack: () -> Unit,
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
                // Set, not inherited: iOS roots have no Scaffold to provide a content colour.
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(BahrSize.iconNav),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.review_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Form(
    state: WriteReviewUiState,
    actions: WriteReviewActions,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = BahrSpacing.gutter, end = BahrSpacing.gutter, top = BahrSpacing.xs, bottom = BahrSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.xl),
    ) {
        Stars(state, actions.onRating)
        ReviewText(state, actions.onText)
        Photos(state, actions)
        // The moderation note on the advisory tint (the account prototype's revHint).
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
                text = stringResource(Res.string.review_moderation_note),
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSecondaryContainer,
            )
        }
        state.problem?.let { problem ->
            Text(
                text = problem.message(),
                style = MaterialTheme.typography.bodyMedium,
                color = c.onErrorContainer,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }.testTag(REVIEW_PROBLEM_TAG),
            )
        }
    }
}

/** Five stars, each a 48dp target, chosen as one group ("3 stars, selected"). Required: its error after a first "Send". */
@Composable
private fun Stars(
    state: WriteReviewUiState,
    onRating: (Int) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
        Text(stringResource(Res.string.review_stars_label), style = MaterialTheme.typography.titleMedium, color = c.onSurface)
        Row(Modifier.selectableGroup()) {
            for (stars in 1..MAX_STARS) {
                val spoken = pluralStringResource(Res.plurals.review_star, stars, stars)
                Box(
                    modifier =
                        Modifier
                            .size(BahrSize.touchTarget)
                            .selectable(
                                selected = state.rating == stars,
                                enabled = state.editable,
                                role = Role.RadioButton,
                                onClick = { onRating(stars) },
                            ).semantics { contentDescription = spoken }
                            .testTag(starTag(stars)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (stars <= state.rating) BahrIcons.Star.filled() else BahrIcons.Star.outlined(),
                        contentDescription = null,
                        tint = if (stars <= state.rating) c.secondary else c.onSurfaceVariant,
                        modifier = Modifier.size(BahrSize.iconNav),
                    )
                }
            }
        }
        if (state.ratingMissing) FieldError(stringResource(Res.string.review_stars_missing))
    }
}

/**
 * The text, optional: a several-line box drawn as the design system's form field draws its box (one caller, so it
 * stays here), its count at the end under it, red past the server's limit.
 */
@Composable
private fun ReviewText(
    state: WriteReviewUiState,
    onText: (String) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val label = stringResource(Res.string.review_text_label)
    val tooLong = state.textTooLong
    val error = if (tooLong) stringResource(Res.string.review_text_too_long, ReviewLimits.MAX_TEXT) else null
    val shape = BahrTheme.shapes.medium
    Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = c.onSurface)
        BasicTextField(
            value = state.text,
            onValueChange = onText,
            enabled = state.editable,
            minLines = TEXT_MIN_LINES,
            maxLines = TEXT_MAX_LINES,
            cursorBrush = SolidColor(c.primary),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (state.editable) Modifier else Modifier.alpha(BahrAlpha.disabledField))
                    .semantics {
                        contentDescription = label
                        error?.let { error(it) }
                    }.testTag(REVIEW_TEXT_TAG),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .clip(shape)
                        .background(c.surface)
                        .border(if (tooLong) BahrBorder.selected else BahrBorder.hairline, if (tooLong) c.error else c.outline, shape)
                        .padding(BahrSpacing.lg),
                ) {
                    if (state.text.isEmpty()) {
                        Text(
                            stringResource(Res.string.review_text_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = BahrTheme.colors.onSurfaceDisabled,
                        )
                    }
                    inner()
                }
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { error?.let { FieldError(it) } }
            Text(
                text = stringResource(Res.string.review_text_count, state.text.length, ReviewLimits.MAX_TEXT),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = if (tooLong) c.onErrorContainer else c.onSurfaceVariant,
                // Read as part of the field, not on its own.
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/**
 * The picked photos, each with its remove button, and the "Add photos" tile while there is room: square tiles, three
 * to a row. A plain flow, not a lazy one: five photos at most.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Photos(
    state: WriteReviewUiState,
    actions: WriteReviewActions,
) {
    val c = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
        Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
            Text(stringResource(Res.string.review_photos_label), style = MaterialTheme.typography.titleMedium, color = c.onSurface)
            Text(
                stringResource(Res.string.review_photos_hint, ReviewLimits.MAX_PHOTOS),
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
        ) {
            state.photos.forEachIndexed { index, photo ->
                PhotoTile(photo, index + 1, enabled = state.editable, onRemove = { actions.onRemovePhoto(photo.id) })
            }
            if (state.photoSlotsLeft > 0) AddPhotosTile(enabled = state.editable, onClick = actions.onAddPhotos)
        }
        if (state.photoFailed) FieldError(stringResource(Res.string.review_photos_failed))
    }
}

/** A picked photo (its compressed JPEG, from memory) with a remove button at its top end corner. */
@Composable
private fun PhotoTile(
    photo: ReviewPhoto,
    position: Int,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val spoken = stringResource(Res.string.review_photo, position)
    Box(Modifier.size(BahrSize.photoTile).testTag(photoTag(position))) {
        ImageGround(modifier = Modifier.fillMaxSize().clip(BahrTheme.shapes.medium).clearAndSetSemantics { contentDescription = spoken }) {
            AsyncImage(model = photo.jpeg, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        IconButton(
            onClick = onRemove,
            enabled = enabled,
            modifier = Modifier.align(Alignment.TopEnd).size(BahrSize.touchTarget).testTag(removePhotoTag(position)),
        ) {
            Box(
                modifier = Modifier.size(BahrSize.marker).clip(BahrTheme.shapes.full).background(c.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    BahrIcons.Close.outlined(),
                    contentDescription = stringResource(Res.string.review_remove_photo, position),
                    tint = c.onSurface,
                    modifier = Modifier.size(BahrSize.iconSmall),
                )
            }
        }
    }
}

/** "Add photos": a tile the size of a photo on the quiet container fill, a way in rather than the page's goal. */
@Composable
private fun AddPhotosTile(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val shape = BahrTheme.shapes.medium
    Column(
        modifier =
            Modifier
                .size(BahrSize.photoTile)
                .clip(shape)
                .background(c.primaryContainer)
                .border(BahrBorder.hairline, c.outlineVariant, shape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(BahrSpacing.sm)
                .testTag(ADD_PHOTOS_TAG),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(BahrIcons.Add.outlined(), contentDescription = null, tint = c.primary, modifier = Modifier.size(BahrSize.iconLarge))
        Text(
            text = stringResource(Res.string.review_add_photos),
            style = MaterialTheme.typography.labelMedium,
            color = c.primary,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** Sent: the check, the thanks (or "already reviewed") and the moderation note. */
@Composable
private fun Sent(
    outcome: ReviewOutcome,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(BahrSpacing.gutter),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BahrLottie(
            modifier = Modifier.width(BahrSize.lottieMoment),
            props = BahrLottie.Props(BahrLottie.Animation.SuccessCheck),
        )
        Text(
            text = stringResource(if (outcome == ReviewOutcome.Submitted) Res.string.review_thanks else Res.string.review_already),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(REVIEW_SENT_TAG),
        )
        Text(
            text = stringResource(Res.string.review_moderation_note),
            style = MaterialTheme.typography.bodyMedium,
            color = BahrTheme.colors.onSurfaceSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** An error line under a part of the form, announced at once (as the design system's form field does). */
@Composable
private fun FieldError(text: String) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        Icon(BahrIcons.Error.outlined(), contentDescription = null, tint = c.onErrorContainer, modifier = Modifier.size(BahrSize.iconSmall))
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = c.onErrorContainer)
    }
}

@Composable
private fun SubmitProblem.message(): String =
    when (this) {
        SubmitProblem.NotYet -> stringResource(Res.string.review_error_not_yet)
        SubmitProblem.NotFound -> stringResource(Res.string.review_error_not_found)
        SubmitProblem.PhotoType -> stringResource(Res.string.review_error_photo_type)
        SubmitProblem.TooLarge -> stringResource(Res.string.review_error_too_large)
        is SubmitProblem.Other -> error.localizedMessage()
    }

/** The text box's height before it grows: room for a few sentences. */
private const val TEXT_MIN_LINES = 4

/** Where it stops growing and scrolls inside, so the photos and the note stay within reach of a long text. */
private const val TEXT_MAX_LINES = 10

internal fun starTag(stars: Int) = "review_star_$stars"

internal fun photoTag(position: Int) = "review_photo_$position"

internal fun removePhotoTag(position: Int) = "review_remove_photo_$position"

internal const val REVIEW_TEXT_TAG = "review_text"
internal const val ADD_PHOTOS_TAG = "review_add_photos"
internal const val REVIEW_SUBMIT_TAG = "review_submit"
internal const val REVIEW_DONE_TAG = "review_done"
internal const val REVIEW_SENT_TAG = "review_sent"
internal const val REVIEW_PROBLEM_TAG = "review_problem"
