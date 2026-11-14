package eg.bahr.feature.notifications.presentation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.notifications.data.AlertsCenter
import eg.bahr.feature.notifications.data.FakeNotificationsRepository
import eg.bahr.feature.notifications.data.InboxOwner
import eg.bahr.feature.notifications.data.NotificationFixtures
import eg.bahr.feature.notifications.data.NotificationFixtures.item
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A sign-in or sign-out while the page is open (M3-M23, the M3-M0 rule): no frame shows the previous inbox's rows. The
 * list does not cross-fade out of one person's inbox (M3-M22's cross-fade kept the old list drawn for its fade), nor
 * stays drawn while the session already names the other one. The real page on its real view model; the new inbox's
 * first page is held, frame by frame, so the window between the two is long enough to look at. Under Robolectric the
 * session lands first; the last test drives the other order on the stateless page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class NotificationsOwnerChangeTest {
    @get:Rule
    val compose = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val clock =
        object : Clock {
            override fun now() = Instant.parse(NotificationFixtures.NOW)
        }
    private val signedIn = SessionState.SignedIn("Sara", profileComplete = true, providers = setOf("google"))

    private lateinit var repository: FakeNotificationsRepository

    @After
    fun tearDown() = scope.cancel()

    private fun show(session: SessionState) {
        repository =
            FakeNotificationsRepository(
                loaded = session,
                inboxes =
                    mapOf(
                        InboxOwner.Account to listOf(item("n1", title = ACCOUNT_ROW)),
                        InboxOwner.Guest to listOf(item("g1", type = "broadcast", title = GUEST_ROW)),
                    ),
            )
        val center = AlertsCenter(repository, registrations = emptyFlow(), scope = scope)
        val vm = NotificationsViewModel(repository, center, AppErrorController(), clock)
        compose.setContent {
            ProvideAppLanguage(AppLanguage.ENGLISH) {
                // Motion on: the cross-fade this guards against is skipped under reduce motion anyway.
                BahrTheme(locale = BahrLocale.English, reducedMotion = false) {
                    NotificationsScreen(
                        onBack = {},
                        destinations = AlertDestinations(onOpenTrip = { _, _, _ -> }, onOpenBooking = {}, onOpenReview = null),
                        viewModel = vm,
                    )
                }
            }
        }
    }

    private fun shown(title: String) = compose.onAllNodesWithContentDescription(title, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun listComposed() = compose.onAllNodesWithTag(INBOX_LIST_TAG).fetchSemanticsNodes().isNotEmpty()

    /**
     * From [from]'s inbox showing [before] to [to]'s showing [after]: with [to]'s first page held, every frame of the
     * changeover (longer than a cross-fade) has no list at all, so neither [before] nor anything of it; once the page
     * lands, only [after] shows.
     */
    private fun changeOwner(
        from: SessionState,
        to: SessionState,
        toOwner: InboxOwner,
        before: String,
        after: String,
    ) {
        show(from)
        compose.waitUntil(LOAD_TIMEOUT_MS) { shown(before) }
        val held = CompletableDeferred<Unit>().also { repository.holds[toOwner] = it }

        compose.mainClock.autoAdvance = false
        repository.state.value = to
        repeat(CHANGEOVER_FRAMES) { frame ->
            compose.mainClock.advanceTimeByFrame()
            assertTrue(!listComposed(), "frame $frame: no list while the other inbox is coming")
            assertTrue(!shown(before), "frame $frame: nothing of the previous inbox")
        }

        held.complete(Unit)
        compose.mainClock.autoAdvance = true
        compose.waitUntil(LOAD_TIMEOUT_MS) { shown(after) }
        assertTrue(!shown(before), "only the new inbox")
    }

    @Test
    fun `signing out - no frame of the account's rows - then the install's`() =
        changeOwner(from = signedIn, to = SessionState.SignedOut, toOwner = InboxOwner.Guest, before = ACCOUNT_ROW, after = GUEST_ROW)

    @Test
    fun `signing in - no frame of the install's rows - then the account's`() =
        changeOwner(from = SessionState.SignedOut, to = signedIn, toOwner = InboxOwner.Account, before = GUEST_ROW, after = ACCOUNT_ROW)

    /**
     * The other order: the list lets go of the account's rows (the view model's emptied, loading list) while the page
     * still has the account's session. Its going rows would fade out under the cross-fade; the list stops drawing at once.
     */
    @Test
    fun `the list emptied before the session changes - its rows do not fade out`() {
        val row =
            InboxEntry.Item(
                "n1",
                AlertKind.BookingConfirmed,
                ACCOUNT_ROW,
                "Dawn on Lake Burullus",
                Instant.parse(NotificationFixtures.NOW),
                read = false,
                target = null,
                owner = InboxOwner.Account,
            )
        val loaded = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
        val loading = LoadStates(LoadState.Loading, LoadState.NotLoading(false), LoadState.NotLoading(false))
        val pages =
            MutableStateFlow(PagingData.from(listOf<InboxEntry>(InboxEntry.Header(InboxGroup.Today, InboxOwner.Account), row), loaded))
        compose.setContent {
            ProvideAppLanguage(AppLanguage.ENGLISH) {
                BahrTheme(locale = BahrLocale.English, reducedMotion = false) {
                    NotificationsContent(
                        state = NotificationsUiState(signedIn, now = Instant.parse(NotificationFixtures.NOW)),
                        items = pages.collectAsLazyPagingItems(),
                        actions = NotificationsActions(onBack = {}),
                    )
                }
            }
        }
        compose.waitUntil(LOAD_TIMEOUT_MS) { shown(ACCOUNT_ROW) }

        compose.mainClock.autoAdvance = false
        pages.value = PagingData.empty(loading)
        // The list takes the emptied page on the next frame's composition (its count and its load state together); a
        // cross-fade would keep drawing it, its rows fading out, for the length of a fade.
        // Idle between frames (the clock held): the presenter's hand-over runs on the main thread between them, as on a phone.
        repeat(FRAMES_TO_TAKE_PAGE) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        repeat(CHANGEOVER_FRAMES) { frame ->
            assertTrue(!listComposed(), "frame $frame after the page: no list fading out")
            compose.mainClock.advanceTimeByFrame()
        }
        compose.mainClock.autoAdvance = true
    }

    private companion object {
        const val ACCOUNT_ROW = "Sara's booking is confirmed"
        const val GUEST_ROW = "Winter trips are out"

        /** Well past a [eg.bahr.core.designsystem.theme.BahrMotion.Medium] cross-fade at 16 ms a frame. */
        const val CHANGEOVER_FRAMES = 40

        /** Frames for an emptied page to reach the list: the presenter hands it over on one, it composes on the next. */
        const val FRAMES_TO_TAKE_PAGE = 2
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
