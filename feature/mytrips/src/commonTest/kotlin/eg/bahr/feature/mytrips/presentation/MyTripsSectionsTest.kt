package eg.bahr.feature.mytrips.presentation

import eg.bahr.core.common.result.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** MV-M2: the one list's sections, in order, empty ones left out, a failed one with its own error. */
class MyTripsSectionsTest {
    @Test
    fun `the sections read Upcoming - Waiting list - Past with their counts`() {
        val blocks = sectionBlocks(SectionStatus.Shown(2), SectionStatus.Shown(1), SectionStatus.Shown(14))

        assertEquals(
            listOf(
                SectionBlock.Rows(MyTripsSection.Upcoming, 2),
                SectionBlock.Rows(MyTripsSection.Waitlist, 1),
                SectionBlock.Rows(MyTripsSection.Past, 14),
            ),
            blocks,
        )
    }

    @Test
    fun `an empty section is left out - no empty header`() {
        val blocks = sectionBlocks(SectionStatus.Empty, SectionStatus.Empty, SectionStatus.Shown(null))

        assertEquals(listOf(SectionBlock.Rows(MyTripsSection.Past, null)), blocks)
    }

    @Test
    fun `all three empty is no section at all - the screen's empty state`() {
        assertTrue(sectionBlocks(SectionStatus.Empty, SectionStatus.Empty, SectionStatus.Empty).isEmpty())
    }

    @Test
    fun `a failed section keeps its place with its error - the others stay`() {
        val blocks = sectionBlocks(SectionStatus.Shown(2), SectionStatus.Failed(AppError.Network), SectionStatus.Shown(3))

        assertEquals(
            listOf(
                SectionBlock.Rows(MyTripsSection.Upcoming, 2),
                SectionBlock.Error(MyTripsSection.Waitlist, AppError.Network),
                SectionBlock.Rows(MyTripsSection.Past, 3),
            ),
            blocks,
        )
    }

    @Test
    fun `only the first section still loading shows a skeleton - never the empty state while one loads`() {
        assertEquals(
            listOf(SectionBlock.Skeleton(MyTripsSection.Upcoming), SectionBlock.Rows(MyTripsSection.Past, 1)),
            sectionBlocks(SectionStatus.Loading, SectionStatus.Loading, SectionStatus.Shown(1)),
        )
        assertEquals(
            listOf(SectionBlock.Skeleton(MyTripsSection.Past)),
            sectionBlocks(SectionStatus.Empty, SectionStatus.Empty, SectionStatus.Loading),
        )
    }
}
