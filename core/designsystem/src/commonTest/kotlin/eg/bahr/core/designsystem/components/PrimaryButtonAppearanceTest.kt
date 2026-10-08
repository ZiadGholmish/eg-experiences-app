package eg.bahr.core.designsystem.components

import kotlin.test.Test
import kotlin.test.assertEquals

class PrimaryButtonAppearanceTest {
    @Test
    fun `an enabled idle button is coral and clickable`() {
        assertEquals(
            PrimaryButtonAppearance(clickable = true, filled = true, showsProgress = false),
            primaryButtonAppearance(enabled = true, loading = false),
        )
    }

    @Test
    fun `a disabled button is grey and inert`() {
        assertEquals(
            PrimaryButtonAppearance(clickable = false, filled = false, showsProgress = false),
            primaryButtonAppearance(enabled = false, loading = false),
        )
    }

    @Test
    fun `loading stays coral and shows progress but ignores taps`() {
        // The booking screen passes enabled = false while a hold is in flight (canPlaceHold
        // excludes it); loading must still not look like an invalid form.
        val expected = PrimaryButtonAppearance(clickable = false, filled = true, showsProgress = true)
        assertEquals(expected, primaryButtonAppearance(enabled = false, loading = true))
        assertEquals(expected, primaryButtonAppearance(enabled = true, loading = true))
    }
}
