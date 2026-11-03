package eg.bahr.feature.reviews.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.catalog.model.MAX_STARS
import eg.bahr.core.catalog.model.MIN_STARS
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.feature.reviews.data.WriteReviewRepository
import eg.bahr.feature.reviews.model.ReviewLimits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A photo picked for the review, already compressed to a JPEG on the device (the platform picker does it as the photo
 * is picked: the picker's access to the original may not last until "Send"). [id] is this form's own, for keys and
 * removal. A plain class: two photos are the same only if they are the same pick, never by their bytes.
 */
internal class ReviewPhoto(
    val id: Int,
    val jpeg: ByteArray,
)

/** Why a send did not go through, for the line above the button. Branches on the error's code, never its message. */
internal sealed interface SubmitProblem {
    /** FORBIDDEN: the booking cannot be reviewed (not confirmed, date called off, or the trip has not finished). */
    data object NotYet : SubmitProblem

    /** NOT_FOUND: no such booking, or not this account's. */
    data object NotFound : SubmitProblem

    /** UNSUPPORTED_MEDIA_TYPE: a photo the server cannot take. */
    data object PhotoType : SubmitProblem

    /** 413 (VALIDATION_FAILED): the request is beyond the server's upload limit. */
    data object TooLarge : SubmitProblem

    /** Anything else: the app's message for [error] (network, rate limit, signed out, …). */
    data class Other(
        val error: AppError,
    ) : SubmitProblem
}

/** How the page ended: sent for moderation, or the booking had a review already (CONFLICT). */
internal enum class ReviewOutcome { Submitted, AlreadyReviewed }

internal data class WriteReviewUiState(
    val title: String? = null,
    /** 0 until a star is picked. */
    val rating: Int = 0,
    val text: String = "",
    val photos: List<ReviewPhoto> = emptyList(),
    /** Set by the first "Send": from then on a missing rating shows its error, and goes as soon as it is fixed. */
    val validated: Boolean = false,
    /** A picked photo could not be read or compressed. */
    val photoFailed: Boolean = false,
    val submitting: Boolean = false,
    val problem: SubmitProblem? = null,
    val outcome: ReviewOutcome? = null,
) {
    val ratingMissing: Boolean get() = validated && rating == 0

    /** Live, not only after "Send": the counter turns red the moment the text passes the limit. */
    val textTooLong: Boolean get() = text.length > ReviewLimits.MAX_TEXT

    val photoSlotsLeft: Int get() = ReviewLimits.MAX_PHOTOS - photos.size

    val editable: Boolean get() = !submitting && outcome == null
}

/**
 * Review a trip (M3-M4): booking [ref]'s stars (required), text and photos, sent once to `submitReview`. The review is
 * then pending until the back-office approves it; My trips shows its status when it is shown again.
 *
 * [title] is the trip's title as My trips showed it, for the heading.
 */
internal class WriteReviewViewModel(
    private val ref: String,
    title: String?,
    private val repository: WriteReviewRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WriteReviewUiState(title = title))
    val uiState: StateFlow<WriteReviewUiState> = _uiState.asStateFlow()

    private var nextPhotoId = 0

    fun setRating(stars: Int) {
        if (!_uiState.value.editable || stars !in MIN_STARS..MAX_STARS) return
        _uiState.update { it.copy(rating = stars, problem = null) }
    }

    fun setText(text: String) {
        if (!_uiState.value.editable) return
        _uiState.update { it.copy(text = text, problem = null) }
    }

    /**
     * Photos the picker compressed; [failed] of the picked ones could not be read. Only as many as there are slots
     * left are kept: the system picker's own limit is a hint on some Android versions.
     */
    fun addPhotos(
        jpegs: List<ByteArray>,
        failed: Int = 0,
    ) {
        if (!_uiState.value.editable) return
        _uiState.update { state ->
            val added = jpegs.take(state.photoSlotsLeft.coerceAtLeast(0)).map { ReviewPhoto(nextPhotoId++, it) }
            state.copy(photos = state.photos + added, photoFailed = failed > 0, problem = null)
        }
    }

    fun removePhoto(id: Int) {
        if (!_uiState.value.editable) return
        _uiState.update { state -> state.copy(photos = state.photos.filterNot { it.id == id }, photoFailed = false, problem = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (!state.editable) return
        _uiState.update { it.copy(validated = true, problem = null) }
        if (state.rating == 0 || state.textTooLong) return
        _uiState.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val text = state.text.trim().ifEmpty { null }
            val result = repository.submit(ref, state.rating, text, state.photos.map { it.jpeg })
            _uiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(submitting = false, outcome = ReviewOutcome.Submitted)
                    is AppResult.Failure ->
                        when (val problem = result.error.toProblem()) {
                            // The booking has a review already: most likely this one, sent before an answer was lost.
                            null -> it.copy(submitting = false, outcome = ReviewOutcome.AlreadyReviewed)
                            else -> it.copy(submitting = false, problem = problem)
                        }
                }
            }
        }
    }

    /** Null for CONFLICT, which is not a problem to show but the end of the page. */
    private fun AppError.toProblem(): SubmitProblem? {
        val api = this as? AppError.Api ?: return SubmitProblem.Other(this)
        return when {
            api.code == ApiErrorCodes.CONFLICT -> null
            api.code == ApiErrorCodes.FORBIDDEN -> SubmitProblem.NotYet
            api.code == ApiErrorCodes.NOT_FOUND -> SubmitProblem.NotFound
            api.code == ApiErrorCodes.UNSUPPORTED_MEDIA_TYPE -> SubmitProblem.PhotoType
            api.code == ApiErrorCodes.VALIDATION_FAILED && api.httpStatus == PAYLOAD_TOO_LARGE -> SubmitProblem.TooLarge
            else -> SubmitProblem.Other(this)
        }
    }

    private companion object {
        /** The contract's "413 status, with code VALIDATION_FAILED, for a request beyond the upload limit". */
        const val PAYLOAD_TOO_LARGE = 413
    }
}
