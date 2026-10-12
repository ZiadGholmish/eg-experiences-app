package eg.bahr.feature.map.presentation

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.ANIMATION_SETTLE_MARGIN_MILLIS
import eg.bahr.feature.map.data.FakeTripMapRepository
import eg.bahr.feature.map.data.MapFixtures
import eg.bahr.feature.map.presentation.components.TripMapCanvas
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The map's pin card and selected pin in motion (M4-M9, user request 2026-10-11), frame by frame as
 * in M4-M4's motion tests: the real screen and view model on a fake repository, the map drawn by
 * [TripMapCanvas] (neither Google map runs under Robolectric; the canvas takes the same scene and the
 * same pin spring).
 *
 * - Picking a pin slides its card up from the bottom; closing it slides it down.
 * - Picking another pin slides the new card's content in from that pin's side (east: from the right,
 *   west: from the left), in Arabic too, since the map is not mirrored.
 * - The selected pin grows with a spring: past its size, then back to it, bigger than before.
 * - Nothing under the card moves while it comes and goes.
 * - A picked pin its card would cover is panned clear of it, smoothly, frame by frame.
 * - Under reduce motion all of it is at rest from the first frame.
 *
 * Scope: the shared screen, scene and card motion, and the canvas stand-in's own keep-still and pan.
 * The real maps' versions (Android's `PadAndReveal`, the iOS bridge's padding, pan and `GrowingPin`
 * spring) share only the arithmetic, tested in `MapMotionMathTest`; on the Google maps themselves
 * they are device checks.
 */
@RunWith(RobolectricTestRunner::class)
// The pins are pictures drawn into bitmaps, which need Robolectric's native graphics.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class TripMapMotionTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var vm: TripMapViewModel

    @Before
    fun pauseClock() {
        compose.mainClock.autoAdvance = false
    }

    @Test
    fun `a picked pin's card slides up from the bottom - and down when closed`() {
        show(AppLanguage.ENGLISH)
        assertNull(cardTitleBounds(DAWN_TITLE), "no card before a pin is picked")

        pick(DAWN)
        val rising = sample { cardTitleBounds(DAWN_TITLE)?.top }
        assertMoves(rising, "the card rises") { before, after -> after <= before }
        assertTrue(rising.first() > rising.last() + MIN_TRAVEL_DP, "it came up from lower down: $rising")

        onUi { vm.clearSelection() }
        val falling = sample { cardTitleBounds(DAWN_TITLE)?.top }
        assertMoves(falling, "the card falls") { before, after -> after >= before }
        settle()
        assertNull(cardTitleBounds(DAWN_TITLE), "closed, the card is gone")
    }

    @Test
    fun `another pin's card comes in from that pin's side`() = switching(AppLanguage.ENGLISH)

    /** The map is not mirrored in Arabic, so the sides are the same as in English. */
    @Test
    fun `in Arabic another pin's card comes in from that pin's side too`() = switching(AppLanguage.ARABIC)

    private fun switching(language: AppLanguage) {
        show(language)
        pick(DAWN)
        settle()

        // The beach is east of Burullus: its card comes in from the right, moving left.
        pick(BEACH)
        val fromRight = sample { cardTitleBounds(title(BEACH, language))?.left }
        assertMoves(fromRight, "east: moving left") { before, after -> after <= before }
        assertTrue(fromRight.first() > fromRight.last(), "east: came in from the right: $fromRight")
        settle()

        // The White Desert is west of the beach: its card comes in from the left, moving right.
        pick(DESERT)
        val fromLeft = sample { cardTitleBounds(title(DESERT, language))?.left }
        assertMoves(fromLeft, "west: moving right") { before, after -> after >= before }
        assertTrue(fromLeft.first() < fromLeft.last(), "west: came in from the left: $fromLeft")
    }

    @Test
    fun `the picked pin grows past its size with a spring - and settles bigger`() {
        show(AppLanguage.ENGLISH)
        val before = assertNotNull(pinBounds(DAWN_PIN)).width

        pick(DAWN)
        val widths = sample(frames = SPRING_FRAMES) { pinBounds(DAWN_PIN)?.width }
        settle()
        val after = assertNotNull(pinBounds(DAWN_PIN)).width

        assertEquals(before, widths.first(), absoluteTolerance = 1f, "it starts at its unselected size: $widths")
        assertTrue(after > before * GROWN_AT_LEAST, "it ends bigger: $before → $after")
        assertTrue(widths.max() > after, "it overshoots on the way: $widths, at rest $after")
    }

    @Test
    fun `nothing under the card moves while it comes and goes`() {
        show(AppLanguage.ENGLISH)
        val still = assertNotNull(pinBounds(BEACH_PIN))

        pick(DAWN)
        val opening = sample { pinBounds(BEACH_PIN) }
        onUi { vm.clearSelection() }
        val closing = sample { pinBounds(BEACH_PIN) }

        (opening + closing).forEach { assertEquals(still, it, "another pin stays where it was") }
    }

    /**
     * M4-M9 review #2, #12: the White Desert pin sits low, where its card lands. Once the card is up,
     * the map pans just enough that the whole pin is above the card: smoothly, everything moving
     * together, frame by frame, never in one step.
     */
    @Test
    @Config(qualifiers = WIDE_SCREEN)
    fun `a picked pin its card would cover is panned clear of it - smoothly`() {
        show(AppLanguage.ENGLISH)
        val beachBefore = assertNotNull(pinBounds(BEACH_PIN)).top

        pick(DESERT)
        val beach = sample(frames = PAN_FRAMES) { pinBounds(BEACH_PIN)?.top }
        settle()

        assertRevealed("picked once")
        val travel = beachBefore - beach.last()
        assertTrue(travel > 0f, "the map moved up: $beach")
        assertMoves(beach, "the map pans up") { before, after -> after <= before }
        val steps = beach.zipWithNext { before, after -> before - after }
        assertTrue(steps.all { it < travel / 2 }, "no single frame covers half the way: $beach")
    }

    /**
     * Quick taps send a card back before it has left (A, B, A within one animation; a pin picked and
     * closed at once): the low White Desert pin must still be panned clear of its card when its card
     * settles, not wait for a measurement that a reused card never makes again.
     */
    @Test
    @Config(qualifiers = WIDE_SCREEN)
    fun `after quick taps the picked pin is still revealed`() {
        show(AppLanguage.ENGLISH)

        pick(DESERT)
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        pick(DAWN)
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        pick(DESERT)
        settle()
        assertRevealed("after A, B, A")

        onUi { vm.clearSelection() }
        settle()
        pick(DESERT)
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        onUi { vm.clearSelection() }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        pick(DESERT)
        settle()
        assertRevealed("after pick, close, pick")
    }

    /** The whole desert pin above its open card. */
    private fun assertRevealed(case: String) {
        val pin = assertNotNull(pinBounds(DESERT_PIN))
        val card = assertNotNull(cardBounds(DESERT_TITLE))
        assertTrue(pin.bottom <= card.top, "$case: the whole pin is above its card: pin $pin, card $card")
    }

    @Test
    fun `under reduce motion the card and the pin are at rest at once`() {
        show(AppLanguage.ENGLISH, reducedMotion = true)

        pick(DAWN)
        compose.mainClock.advanceTimeByFrame()
        val first = assertNotNull(cardTitleBounds(DAWN_TITLE))
        val firstPin = assertNotNull(pinBounds(DAWN_PIN))
        settle()

        assertEquals(cardTitleBounds(DAWN_TITLE), first, "the card did not move")
        assertEquals(pinBounds(DAWN_PIN), firstPin, "the pin did not grow on the way")
    }

    // ---------- Plumbing ----------

    private fun show(
        language: AppLanguage,
        reducedMotion: Boolean = false,
    ) {
        vm =
            TripMapViewModel(
                FakeTripMapRepository(tripMap = { AppResult.Success(MapFixtures.map(arabic = language == AppLanguage.ARABIC)) }),
            )
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(
                    locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English,
                    reducedMotion = reducedMotion,
                ) {
                    TripMapScreen(
                        onBack = null,
                        onTripClick = {},
                        viewModel = vm,
                        map = { scene, onPin, onMap, padding, modifier -> TripMapCanvas(scene, onPin, onMap, padding, modifier) },
                    )
                }
            }
        }
        settle()
    }

    private fun pick(slug: String) {
        onUi { vm.selectPin(slug) }
    }

    private fun onUi(block: () -> Unit) {
        compose.runOnIdle(block)
    }

    /** [read] after each of [frames] frames, the frames it read nothing in left out. */
    private fun <T : Any> sample(
        frames: Int = CARD_FRAMES,
        read: () -> T?,
    ): List<T> =
        List(frames) {
            compose.mainClock.advanceTimeByFrame()
            read()
        }.filterNotNull()

    /** Frames for the screen to load and its chrome to be measured, then past the longest animation. */
    private fun settle() {
        repeat(SETTLE_FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.mainClock.advanceTimeBy(BahrMotion.Long.toLong() + SPRING_SETTLE_MILLIS)
        compose.waitForIdle()
    }

    private fun <T : Comparable<T>> assertMoves(
        values: List<T>,
        what: String,
        step: (before: T, after: T) -> Boolean,
    ) {
        assertTrue(values.distinct().size > 2, "$what: seen moving over several frames: $values")
        assertTrue(values.zipWithNext().all { (before, after) -> step(before, after) }, "$what, every frame: $values")
    }

    /**
     * The card's title text, in dp: in the unmerged tree, apart from the card's merged node. Null while
     * none of it shows: the bounds are clipped to what is visible, and a card starting its slide below
     * the map panel's bottom edge is all clipped away by the panel.
     */
    private fun cardTitleBounds(title: String): Rect? =
        compose
            .onAllNodesWithText(title, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .firstOrNull { !it.isEmpty }
            ?.inDp()

    /** The whole card (its merged node, the one a tap opens), in dp. */
    private fun cardBounds(title: String): Rect? =
        compose
            .onAllNodesWithText(title)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .firstOrNull { !it.isEmpty }
            ?.inDp()

    /** A pin on the canvas map, as drawn (its spring's scale included), in dp. */
    private fun pinBounds(description: String): Rect? =
        compose
            .onNodeWithContentDescription(description)
            .boundsOrNull()
            ?.inDp()

    private fun SemanticsNodeInteraction.boundsOrNull(): Rect? = runCatching { fetchSemanticsNode().boundsInRoot }.getOrNull()

    private fun Rect.inDp(): Rect =
        with(compose.density) { Rect(left.toDp().value, top.toDp().value, right.toDp().value, bottom.toDp().value) }

    private fun title(
        slug: String,
        language: AppLanguage,
    ): String =
        MapFixtures
            .map(arabic = language == AppLanguage.ARABIC)
            .pins
            .first { it.slug == slug }
            .title

    private companion object {
        const val DAWN = "burullus-dawn"
        const val BEACH = "ain-sokhna-beach-day"
        const val DESERT = "white-desert-overnight"
        const val DAWN_TITLE = "Dawn on Lake Burullus"
        const val DAWN_PIN = "Dawn on Lake Burullus · 450 EGP"
        const val BEACH_PIN = "A Red Sea beach day at Ain Sokhna · 550 EGP"
        const val DESERT_TITLE = "A night in the White Desert"
        const val DESERT_PIN = "A night in the White Desert · 2,900 EGP · Sold out"

        /** Past the card (it is measured first) and the pan's [BahrMotion.Medium], with frames to spare. */
        const val PAN_FRAMES = 40

        /**
         * A wider screen, where the pins' box fills the map's height and the lowest (the White Desert)
         * sits right above the legend, under where its taller card lands. On the 360dp phone the box
         * fills the width and leaves that pin clear of its card.
         */
        const val WIDE_SCREEN = "en-w600dp-h800dp-xhdpi"

        /** Past the card's [BahrMotion.Medium] at 60 fps, with frames to spare. */
        const val CARD_FRAMES = 24

        /** Past the pin's spring (about a third of a second), with frames to spare. */
        const val SPRING_FRAMES = 40
        const val SPRING_SETTLE_MILLIS = 600L + ANIMATION_SETTLE_MARGIN_MILLIS
        const val SETTLE_FRAMES = 3

        /** The card starts at least this far (dp) below where it ends. */
        const val MIN_TRAVEL_DP = 40f

        /** The grown pin is [BahrMotion.PinSelectedScale] of its size; a little under, for rounding. */
        const val GROWN_AT_LEAST = 1.08f
    }
}
