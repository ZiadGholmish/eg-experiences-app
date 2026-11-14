package eg.bahr.feature.notifications.presentation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.notifications.data.AlertsCenter
import eg.bahr.feature.notifications.data.FakeNotificationsRepository
import eg.bahr.feature.notifications.data.InboxOwner
import eg.bahr.feature.notifications.data.NotificationFixtures
import eg.bahr.feature.notifications.data.NotificationFixtures.item
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Deleting one notification (M3-M22, a user bug: a short or sloppy swipe deleted at once). The real page on its real
 * view model over the in-memory inbox: only a swipe towards the reading start past 40% of the row asks (with one haptic
 * tick), whatever the speed; asking opens the confirm sheet; Cancel leaves the row and sends nothing, Delete removes it
 * and sends one DELETE. The long-press menu and the screen reader's action open the same sheet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class NotificationDeleteTest {
    @get:Rule
    val compose = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val clock =
        object : Clock {
            override fun now() = Instant.parse(NotificationFixtures.NOW)
        }
    private val haptics = mutableListOf<HapticFeedbackType>()

    /** Enough rows for the list to scroll, so a vertical drag is a real scroll. */
    private val inbox =
        listOf(item("n1", title = "First alert")) +
            (2..INBOX_SIZE).map { item("n$it", title = "Alert $it", createdAt = "2026-10-24T0${it % MAX_HOUR}:00:00Z") }

    private lateinit var repository: FakeNotificationsRepository

    @After
    fun tearDown() = scope.cancel()

    private fun show(
        language: AppLanguage = AppLanguage.ENGLISH,
        reducedMotion: Boolean = false,
    ) {
        val signedIn = SessionState.SignedIn("Sara", profileComplete = true, providers = setOf("google"))
        repository = FakeNotificationsRepository(loaded = signedIn, inboxes = mapOf(InboxOwner.Account to inbox))
        val center = AlertsCenter(repository, registrations = emptyFlow(), scope = scope)
        val vm = NotificationsViewModel(repository, center, AppErrorController(), clock)
        val feedback =
            object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                    haptics += hapticFeedbackType
                }
            }
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(
                    locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English,
                    reducedMotion = reducedMotion,
                ) {
                    CompositionLocalProvider(LocalHapticFeedback provides feedback) {
                        NotificationsScreen(
                            onBack = {},
                            destinations = AlertDestinations(onOpenTrip = { _, _, _ -> }, onOpenBooking = {}, onOpenReview = null),
                            viewModel = vm,
                        )
                    }
                }
            }
        }
        compose.waitUntil(LOAD_TIMEOUT_MS) { rows().fetchSemanticsNodes().isNotEmpty() }
    }

    /** Frame by frame (the clock held) until the list has dropped the deleted row: its motion starts there. */
    private fun untilRowGone() {
        repeat(MAX_FRAMES) {
            if (rows().fetchSemanticsNodes().isEmpty()) return
            compose.mainClock.advanceTimeByFrame()
        }
        error("the row never left the list")
    }

    /** The row under the first one ("Alert 2": the fake keeps the list order). */
    private fun next() = compose.onNodeWithContentDescription("Alert 2.", substring = true).fetchSemanticsNode()

    private fun rows() = compose.onAllNodesWithContentDescription("First alert", substring = true)

    private fun row(): SemanticsNodeInteraction = compose.onNodeWithContentDescription("First alert", substring = true)

    /** A drag across the first row from [from] to [to], as shares of its width (0 the left edge), over [millis]. */
    private fun drag(
        from: Float,
        to: Float,
        millis: Long = SLOW_MS,
        drift: Float = 0f,
    ) {
        row().performTouchInput {
            swipe(Offset(width * from, centerY), Offset(width * to, centerY + height * drift), millis)
        }
        compose.waitForIdle()
    }

    private fun sheetShown(title: String) = compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()

    private fun deletes() = repository.calls.filter { it.startsWith("delete") }

    @Test
    fun `a short swipe - the wrong way - a fast short flick - a sideways scroll - none of them asks`() {
        show()

        drag(from = .9f, to = .7f)
        drag(from = .05f, to = .95f)
        drag(from = .9f, to = .6f, millis = FLICK_MS)
        drag(from = .9f, to = .65f, drift = -SCROLL_DRIFT)

        assertTrue(!sheetShown(TITLE_EN), "no sheet")
        assertTrue(haptics.isEmpty(), "the threshold was never crossed")
        assertTrue(deletes().isEmpty())
        row().fetchSemanticsNode()
    }

    @Test
    fun `a full swipe towards the start asks with one tick - Cancel leaves the row and sends nothing`() {
        show()

        drag(from = .95f, to = .05f)
        assertTrue(sheetShown(TITLE_EN), "the sheet opens")
        assertEquals(listOf(HapticFeedbackType.GestureThresholdActivate), haptics)
        rows().assertCountEquals(1)

        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()

        assertTrue(!sheetShown(TITLE_EN))
        row().fetchSemanticsNode()
        assertTrue(deletes().isEmpty(), "Cancel sends nothing")
    }

    @Test
    fun `past the line and back under it before letting go - nothing asks - one tick only`() {
        show()

        row().performTouchInput {
            down(Offset(width * .95f, centerY))
            moveTo(Offset(width * .3f, centerY))
            moveTo(Offset(width * .85f, centerY))
            up()
        }
        compose.waitForIdle()

        assertTrue(!sheetShown(TITLE_EN))
        assertEquals(listOf(HapticFeedbackType.GestureThresholdActivate), haptics)
        assertTrue(deletes().isEmpty())
    }

    @Test
    fun `Delete in the sheet removes the row and sends one DELETE`() {
        show()

        drag(from = .95f, to = .05f)
        compose.onNodeWithText("Delete").performClick()
        compose.waitForIdle()

        assertEquals(listOf("delete(Account:n1)"), deletes())
        rows().assertCountEquals(0)
    }

    @Test
    fun `in Arabic the start is on the right - left to right asks - right to left does nothing`() {
        show(AppLanguage.ARABIC)

        drag(from = .95f, to = .05f)
        assertTrue(!sheetShown(TITLE_AR), "towards the end: nothing")

        drag(from = .05f, to = .95f)
        assertTrue(sheetShown(TITLE_AR))
        compose.onNodeWithText("امسح").performClick()
        compose.waitForIdle()
        assertEquals(listOf("delete(Account:n1)"), deletes())
    }

    @Test
    fun `the long-press menu's Delete opens the same sheet`() {
        show()

        row().performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Delete").performClick()
        compose.waitForIdle()
        assertTrue(sheetShown(TITLE_EN))
        assertTrue(deletes().isEmpty(), "the menu only asks")

        compose.onNodeWithText("Delete").performClick()
        compose.waitForIdle()
        assertEquals(listOf("delete(Account:n1)"), deletes())
    }

    @Test
    fun `the screen reader's Delete action opens the same sheet`() {
        show()

        val delete = row().fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "Delete" }
        compose.runOnUiThread { delete.action() }
        compose.waitForIdle()

        assertTrue(sheetShown(TITLE_EN))
        assertTrue(deletes().isEmpty())
    }

    /**
     * The old Undo needed more than one tap (user, 2026-11-14): the row came back with its saved swipe state still
     * "dismissed", and Material's box deleted it again at once. A row brought back now (a failed DELETE) is at rest:
     * it does not ask again, and a single tap opens it.
     */
    @Test
    fun `a row brought back by a failed delete is at rest - one DELETE - nothing asks again`() {
        show()
        repository.fail = mapOf("delete" to AppResult.Failure(AppError.Network))

        drag(from = .95f, to = .05f)
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(LOAD_TIMEOUT_MS) { rows().fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()

        assertEquals(listOf("delete(Account:n1)"), deletes())
        assertTrue(!sheetShown(TITLE_EN), "nothing asks again")
        assertEquals(0f, row().fetchSemanticsNode().positionInRoot.x, "back at rest")
    }

    @Test
    fun `Clear read takes one tap in its sheet`() {
        show()

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Clear read").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Clear read")[0].performClick()
        compose.waitForIdle()

        assertEquals(listOf("clearRead(Account)"), repository.calls.filter { it.startsWith("clear") })
    }

    /**
     * A deleted row leaves with motion (M3-M22): the one under it slides up into its place over the design system's
     * tween instead of jumping (the going row fades out meanwhile, which semantics do not show), and is there at the end.
     */
    @Test
    fun `a deleted row animates out - the next one slides up - all settled after the animation`() {
        show()
        val first =
            rows()
                .fetchSemanticsNodes()
                .single()
                .positionInRoot.y
        val start = next().positionInRoot.y
        drag(from = .95f, to = .05f)

        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Delete").performClick()
        untilRowGone()
        repeat(FRAMES_INTO_FADE) { compose.mainClock.advanceTimeByFrame() }
        val midway = next().positionInRoot.y
        assertTrue(midway < start && midway > first, "sliding: $first < $midway < $start")

        compose.mainClock.advanceTimeBy(BahrMotion.Medium.toLong() * 2)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        rows().assertCountEquals(0)
        assertEquals(first, next().positionInRoot.y, "in the deleted row's place")
        assertEquals(listOf("delete(Account:n1)"), deletes())
    }

    /** Under reduce motion the next row is in place on the first frames. */
    @Test
    fun `under reduce motion a deleted row goes at once`() {
        show(reducedMotion = true)
        drag(from = .95f, to = .05f)

        val first =
            rows()
                .fetchSemanticsNodes()
                .single()
                .positionInRoot.y
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Delete").performClick()
        untilRowGone()
        repeat(FRAMES_INTO_FADE) { compose.mainClock.advanceTimeByFrame() }

        rows().assertCountEquals(0)
        assertEquals(first, next().positionInRoot.y, "no slide")
        compose.mainClock.autoAdvance = true
    }

    private companion object {
        /** A few frames after the tap: the list has its new contents, a tween of [BahrMotion.Medium] has not ended. */
        const val FRAMES_INTO_FADE = 4
        const val MAX_FRAMES = 30

        const val TITLE_EN = "Delete this notification?"
        const val TITLE_AR = "تمسح الإشعار ده؟"
        const val INBOX_SIZE = 9
        const val MAX_HOUR = 9
        const val SLOW_MS = 600L

        /** Fast enough that Material's swipe-to-dismiss would have taken it as a throw. */
        const val FLICK_MS = 40L

        /** A drag that goes down more than sideways, as a sloppy scroll does (a share of the row's height). */
        const val SCROLL_DRIFT = 3f
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
