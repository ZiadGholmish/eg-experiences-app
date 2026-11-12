package eg.bahr.feature.mytrips.presentation

import eg.bahr.core.common.result.AppError

/** The sections of My trips' one list (MV-M2, user 2026-11-12), in the order they are read top to bottom. */
internal enum class MyTripsSection { Upcoming, Waitlist, Past }

/** Where one section's read stands, as the list needs it. */
internal sealed interface SectionStatus {
    /** The first read is under way (a re-read keeps what is shown). */
    data object Loading : SectionStatus

    /** Read, and nothing in it. */
    data object Empty : SectionStatus

    data class Failed(
        val error: AppError,
    ) : SectionStatus

    /** Read, with rows; [count] for the header, null when the server gave no usable total. */
    data class Shown(
        val count: Int?,
    ) : SectionStatus
}

/** What the list draws for one section, in its place in the order. */
internal sealed interface SectionBlock {
    val section: MyTripsSection

    /** The section's first read, as skeleton rows (no header: its count is not known yet). */
    data class Skeleton(
        override val section: MyTripsSection,
    ) : SectionBlock

    /** Its header (no count) over the error and a retry for this section only. */
    data class Error(
        override val section: MyTripsSection,
        val error: AppError,
    ) : SectionBlock

    /** Its sticky header with the count, then its rows. */
    data class Rows(
        override val section: MyTripsSection,
        val count: Int?,
    ) : SectionBlock
}

/**
 * The list's sections, top to bottom: Upcoming, Waiting list, Past. A section with nothing is left out (no empty
 * header); a failed one keeps its header with its own retry while the others stay. Only the first section still
 * loading shows a skeleton, so two skeletons never stack. An empty result means all three were read and are
 * empty: the screen's empty state.
 */
internal fun sectionBlocks(
    upcoming: SectionStatus,
    waitlist: SectionStatus,
    past: SectionStatus,
): List<SectionBlock> {
    var skeletonShown = false
    return listOf(MyTripsSection.Upcoming to upcoming, MyTripsSection.Waitlist to waitlist, MyTripsSection.Past to past)
        .mapNotNull { (section, status) ->
            when (status) {
                SectionStatus.Empty -> null
                SectionStatus.Loading ->
                    if (skeletonShown) {
                        null
                    } else {
                        skeletonShown = true
                        SectionBlock.Skeleton(section)
                    }
                is SectionStatus.Failed -> SectionBlock.Error(section, status.error)
                is SectionStatus.Shown -> SectionBlock.Rows(section, status.count)
            }
        }
}

/** A section read whole (Upcoming, Waiting list): its rows, or why not. */
internal sealed interface SectionLoad<out T> {
    data object Loading : SectionLoad<Nothing>

    data class Loaded<T>(
        val items: List<T>,
    ) : SectionLoad<T>

    data class Failed(
        val error: AppError,
    ) : SectionLoad<Nothing>
}

internal fun SectionLoad<*>.status(): SectionStatus =
    when (this) {
        SectionLoad.Loading -> SectionStatus.Loading
        is SectionLoad.Failed -> SectionStatus.Failed(error)
        is SectionLoad.Loaded -> if (items.isEmpty()) SectionStatus.Empty else SectionStatus.Shown(items.size)
    }
