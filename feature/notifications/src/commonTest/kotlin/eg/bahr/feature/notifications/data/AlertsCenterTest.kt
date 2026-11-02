package eg.bahr.feature.notifications.data

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.testing.TestPager
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.PageDto
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.notifications.data.NotificationFixtures.item
import eg.bahr.feature.notifications.model.NotificationDto
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** The bell's count (M3-M6) and the inbox's pages. */
class AlertsCenterTest {
    private val signedIn = SessionState.SignedIn("Sara", profileComplete = true, providers = setOf("google"))

    @Test
    fun `the count follows the inbox on show - none while a sign-in is read - and is read again after a registration`() =
        runViewModelTest {
            val repository =
                FakeNotificationsRepository(
                    loaded = SessionState.SignedOut,
                    inboxes = mapOf(InboxOwner.Guest to listOf(item("g1")), InboxOwner.Account to listOf(item("a1"), item("a2"))),
                )
            val registrations = MutableSharedFlow<Unit>()
            val center = AlertsCenter(repository, registrations, scope = appScope())
            advanceUntilIdle()
            assertNull(center.count.value, "the stored sign-in is not read yet")

            repository.loadSession()
            advanceUntilIdle()
            assertEquals(1, center.count.value)

            repository.state.value = signedIn
            advanceUntilIdle()
            assertEquals(2, center.count.value)

            // The sign-in's registration moved a guest item into the account (openapi registerDevice).
            repository.inboxes[InboxOwner.Account]?.add(item("g1"))
            registrations.emit(Unit)
            advanceUntilIdle()
            assertEquals(3, center.count.value)
        }

    @Test
    fun `a page repeating the last items of the one before keeps each item once`() =
        runTest {
            val first = (1..3).map { item("n$it") }
            val repository =
                object : NotificationsRepository by FakeNotificationsRepository() {
                    override suspend fun page(
                        owner: InboxOwner,
                        page: Int,
                    ): AppResult<PageDto<NotificationDto>> =
                        AppResult.Success(
                            if (page == 0) PageDto(first, 0, 3, 5, 2) else PageDto(listOf(item("n3"), item("n4")), 1, 3, 5, 2),
                        )
                }
            val pager = TestPager(PagingConfig(pageSize = 3), NotificationsPagingSource(repository, InboxOwner.Account, generation = 1))

            pager.refresh()
            val second = assertIs<PagingSource.LoadResult.Page<Int, LoadedNotification>>(pager.append())

            assertEquals(listOf("n4"), second.data.map { it.dto.id })
            assertEquals(listOf(1), second.data.map { it.generation }.distinct())
        }
}
