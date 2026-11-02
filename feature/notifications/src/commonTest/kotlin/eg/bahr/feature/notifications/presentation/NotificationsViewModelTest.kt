package eg.bahr.feature.notifications.presentation

import androidx.lifecycle.ViewModelStore
import androidx.paging.testing.asSnapshot
import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.notifications.data.AlertsCenter
import eg.bahr.feature.notifications.data.FakeNotificationsRepository
import eg.bahr.feature.notifications.data.InboxOwner
import eg.bahr.feature.notifications.data.NotificationFixtures
import eg.bahr.feature.notifications.data.NotificationFixtures.item
import eg.bahr.feature.notifications.data.appScope
import eg.bahr.feature.notifications.model.NotificationDto
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The notifications page (M3-M6): the inbox of whoever is signed in (or the install's, signed out), Today / Earlier in
 * Cairo, and every change shown at once with the bell's count following it, settled by the server's answer.
 */
class NotificationsViewModelTest {
    private val signedIn = SessionState.SignedIn("Sara", profileComplete = true, providers = setOf("google"))
    private val clock =
        object : Clock {
            override fun now() = Instant.parse(NotificationFixtures.NOW)
        }

    /** Newest first: two from today (one read), one from yesterday in Cairo (21:30 UTC is 00:30 the next day there). */
    private val mixed =
        listOf(
            NotificationFixtures.seatOpened(id = "n1", createdAt = "2026-10-24T08:48:00Z"),
            item(id = "n2", createdAt = "2026-10-23T21:30:00Z", readAt = "2026-10-24T08:00:00Z"),
            item(id = "n3", type = "review_rejected", createdAt = "2026-10-23T20:59:00Z"),
        )

    private class Page(
        val vm: NotificationsViewModel,
        val repository: FakeNotificationsRepository,
        val center: AlertsCenter,
        val errors: AppErrorController,
        val store: ViewModelStore,
    )

    private fun TestScope.page(
        session: SessionState = signedIn,
        inbox: List<NotificationDto> = mixed,
        owner: InboxOwner = if (session is SessionState.SignedIn) InboxOwner.Account else InboxOwner.Guest,
    ): Page {
        val repository = FakeNotificationsRepository(loaded = session, inboxes = mapOf(owner to inbox))
        val center = AlertsCenter(repository, registrations = emptyFlow(), scope = appScope())
        val errors = AppErrorController()
        val vm = NotificationsViewModel(repository, center, errors, clock)
        // A store of its own, so a test can close the page (onCleared) as navigation would.
        val store = ViewModelStore().also { it.put("page", vm) }
        return Page(vm, repository, center, errors, store)
    }

    private suspend fun NotificationsViewModel.items() = notifications.asSnapshot().filterIsInstance<InboxEntry.Item>()

    private suspend fun NotificationsViewModel.entries() = notifications.asSnapshot().map { it.key }

    @Test
    fun `signed in - the account's inbox newest first - grouped Today then Earlier in Cairo`() =
        runViewModelTest {
            val (vm, repository) = page().let { it.vm to it.repository }
            advanceUntilIdle()

            assertEquals(listOf("header-Today", "n1", "n2", "header-Earlier", "n3"), vm.entries())
            val (seat, booking, review) = vm.items()
            assertEquals(AlertKind.SeatOpened, seat.kind)
            assertEquals(AlertTarget.Trip("burullus-dawn", date = "2026-10-31", placeOpened = true), seat.target)
            assertEquals(AlertTarget.Booking("BRL-7K4M2X9P"), booking.target)
            assertEquals(listOf(false, true, false), listOf(seat.read, booking.read, review.read))
            assertEquals(AlertKind.ReviewRejected, review.kind)
            assertTrue(repository.pageReads > 0)
        }

    @Test
    fun `signed out - it is this install's guest inbox - no sign-in prompt`() =
        runViewModelTest {
            val page = page(session = SessionState.SignedOut, inbox = listOf(item("g1", type = "broadcast")))
            advanceUntilIdle()

            assertEquals(listOf("g1"), page.vm.items().map { it.id })
            assertEquals(1, page.center.count.value)
        }

    @Test
    fun `a tap on an unread item reads it - the bell follows - a read one sends nothing`() =
        runViewModelTest {
            val page = page()
            advanceUntilIdle()
            assertEquals(2, page.center.count.value)
            val (seat, booking) = page.vm.items()

            page.vm.open(seat)
            assertEquals(1, page.center.count.value, "at once, before the server answers")
            advanceUntilIdle()
            page.vm.open(booking)
            advanceUntilIdle()

            assertEquals(listOf("mark(Account:n1:true)"), page.repository.calls)
            assertTrue(
                page.vm
                    .items()
                    .first()
                    .read,
            )
            assertEquals(1, page.center.count.value, "the server's count, read again")
        }

    @Test
    fun `mark as unread from the menu - and a failure puts it back and says so`() =
        runViewModelTest {
            val page = page()
            advanceUntilIdle()
            val booking = page.vm.items()[1]

            page.vm.setRead(booking, read = false)
            advanceUntilIdle()
            assertEquals(listOf("mark(Account:n2:false)"), page.repository.calls)
            assertEquals(false, page.vm.items()[1].read)
            assertEquals(3, page.center.count.value)

            page.repository.fail = mapOf("mark" to AppResult.Failure(AppError.Network))
            page.vm.setRead(page.vm.items()[2], read = true)
            advanceUntilIdle()
            assertEquals(false, page.vm.items()[2].read, "the change is undone")
            assertEquals(
                AppError.Network,
                page.errors.current
                    .first()
                    ?.error,
            )
            assertEquals(3, page.center.count.value)
        }

    @Test
    fun `a swipe hides the item and offers Undo - Undo sends nothing - the snackbar going sends the delete`() =
        runViewModelTest {
            val page = page()
            advanceUntilIdle()
            val seat = page.vm.items().first()

            page.vm.delete(seat)
            assertEquals(listOf("n2", "n3"), page.vm.items().map { it.id })
            assertEquals(1, page.center.count.value)
            assertEquals(
                "n1",
                page.vm.uiState.value.pendingDelete
                    ?.id,
            )

            page.vm.undoDelete("n1")
            advanceUntilIdle()
            assertEquals(listOf("n1", "n2", "n3"), page.vm.items().map { it.id })
            assertEquals(2, page.center.count.value)
            assertTrue(page.repository.calls.isEmpty(), "nothing to restore: nothing was sent")

            page.vm.delete(seat)
            page.vm.commitDelete("n1")
            advanceUntilIdle()
            assertEquals(listOf("delete(Account:n1)"), page.repository.calls)
            assertNull(page.vm.uiState.value.pendingDelete)
            assertEquals(listOf("n2", "n3"), page.vm.items().map { it.id })
            assertEquals(1, page.center.count.value)
        }

    @Test
    fun `a next swipe sends the first delete - and closing the page inside the undo sends the last`() =
        runViewModelTest {
            val page = page()
            advanceUntilIdle()
            val (seat, booking) = page.vm.items()

            page.vm.delete(seat)
            page.vm.delete(booking)
            advanceUntilIdle()
            assertEquals(listOf("delete(Account:n1)"), page.repository.calls)
            assertEquals(
                "n2",
                page.vm.uiState.value.pendingDelete
                    ?.id,
            )

            page.store.clear()
            advanceUntilIdle()
            assertEquals(listOf("delete(Account:n1)", "delete(Account:n2)"), page.repository.calls, "the page went; the delete did not")
        }

    @Test
    fun `a delete that fails brings the item back and says so`() =
        runViewModelTest {
            val page = page()
            advanceUntilIdle()
            page.repository.fail = mapOf("delete" to AppResult.Failure(AppError.Network))

            page.vm.delete(page.vm.items().first())
            page.vm.commitDelete("n1")
            advanceUntilIdle()

            assertEquals(listOf("n1", "n2", "n3"), page.vm.items().map { it.id })
            assertEquals(
                AppError.Network,
                page.errors.current
                    .first()
                    ?.error,
            )
        }

    @Test
    fun `mark all as read - every row reads read and the bell clears - a failure puts it back`() =
        runViewModelTest {
            val page = page()
            advanceUntilIdle()

            page.vm.markAllRead()
            assertEquals(0, page.center.count.value)
            assertTrue(page.vm.items().all { it.read })
            advanceUntilIdle()
            assertEquals(listOf("markAllRead(Account)"), page.repository.calls)

            val failing = page(inbox = mixed)
            advanceUntilIdle()
            failing.repository.fail = mapOf("markAllRead" to AppResult.Failure(AppError.Network))
            failing.vm.markAllRead()
            advanceUntilIdle()
            assertEquals(listOf(false, true, false), failing.vm.items().map { it.read })
            assertEquals(2, failing.center.count.value)
            assertNotNull(failing.errors.current.first())
        }

    @Test
    fun `an item marked unread after mark all as read stays unread`() =
        runViewModelTest {
            val page = page()
            advanceUntilIdle()
            page.vm.markAllRead()
            advanceUntilIdle()

            page.vm.setRead(page.vm.items().first(), read = false)
            advanceUntilIdle()

            assertEquals(listOf(false, true, true), page.vm.items().map { it.read })
        }

    @Test
    fun `clear read asks first - then deletes the read items and reads the list again`() =
        runViewModelTest {
            val page = page()
            advanceUntilIdle()
            page.vm.items()

            page.vm.askClearRead()
            assertTrue(page.vm.uiState.value.confirmClearRead)
            page.vm.clearRead()
            advanceUntilIdle()

            assertEquals(listOf("clearRead(Account)"), page.repository.calls)
            assertEquals(false, page.vm.uiState.value.confirmClearRead)
            assertEquals(listOf("n1", "n3"), page.vm.items().map { it.id })
        }

    @Test
    fun `a push while the page is open reads the list again`() =
        runViewModelTest {
            val page = page()
            advanceUntilIdle()
            page.vm.items()
            val before = page.repository.pageReads

            page.repository.inboxes[InboxOwner.Account]?.add(0, item("n0", type = "host_message", createdAt = "2026-10-24T08:59:30Z"))
            page.center.onPushReceived()
            advanceUntilIdle()

            assertEquals(
                "n0",
                page.vm
                    .items()
                    .first()
                    .id,
            )
            assertTrue(page.repository.pageReads > before)
            assertEquals(3, page.center.count.value)
        }

    @Test
    fun `signing out swaps the account's inbox for the install's - nothing of the account stays`() =
        runViewModelTest {
            val repository =
                FakeNotificationsRepository(
                    loaded = signedIn,
                    inboxes = mapOf(InboxOwner.Account to mixed, InboxOwner.Guest to listOf(item("g1", type = "broadcast"))),
                )
            val center = AlertsCenter(repository, registrations = emptyFlow(), scope = appScope())
            val vm = NotificationsViewModel(repository, center, AppErrorController(), clock)
            advanceUntilIdle()
            assertEquals(listOf("n1", "n2", "n3"), vm.items().map { it.id })

            repository.state.value = SessionState.SignedOut
            advanceUntilIdle()

            assertEquals(listOf("g1"), vm.items().map { it.id })
            assertEquals(1, center.count.value)
            assertIs<SessionState.SignedOut>(vm.uiState.value.session)
        }
}
