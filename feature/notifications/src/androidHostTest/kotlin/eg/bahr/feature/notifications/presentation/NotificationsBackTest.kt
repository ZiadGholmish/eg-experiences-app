package eg.bahr.feature.notifications.presentation

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.network.auth.SessionState
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * M3-M6a: the page's Back calls onBack. M3-M6: a tap on a row opens it, "Mark all as read" marks all, and a screen
 * reader reaches the swipe's delete and the read toggle as the row's custom actions.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationsBackTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-10-24T09:00:00Z")
    private val item =
        InboxEntry.Item("n1", AlertKind.BookingConfirmed, "Booking confirmed", "Dawn on Lake Burullus", now, read = false, target = null)

    private fun show(actions: NotificationsActions) {
        val loaded = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
        compose.setContent {
            ProvideAppLanguage(AppLanguage.ENGLISH) {
                BahrTheme(locale = BahrLocale.English) {
                    NotificationsContent(
                        state = NotificationsUiState(SessionState.SignedOut, now = now),
                        items =
                            flowOf(
                                PagingData.from(listOf<InboxEntry>(InboxEntry.Header(InboxGroup.Today), item), loaded),
                            ).collectAsLazyPagingItems(),
                        actions = actions,
                    )
                }
            }
        }
    }

    @Test
    fun `Back calls onBack - a row opens - Mark all as read marks all`() {
        val calls = mutableListOf<String>()
        show(
            NotificationsActions(
                onBack = { calls += "back" },
                onOpen = { calls += "open ${it.id}" },
                onMarkAllRead = { calls += "all" },
            ),
        )

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Unread. Booking confirmed. Dawn on Lake Burullus. now").performClick()
        compose.onNodeWithText("Mark all as read").performClick()

        assertEquals(listOf("back", "open n1", "all"), calls)
    }

    @Test
    fun `the row's custom actions mark it read and delete it`() {
        val calls = mutableListOf<String>()
        show(
            NotificationsActions(
                onBack = {},
                onSetRead = { it, read -> calls += "read ${it.id} $read" },
                onDelete = { calls += "delete ${it.id}" },
            ),
        )
        val row = compose.onNodeWithContentDescription("Unread. Booking confirmed. Dawn on Lake Burullus. now")

        row.fetchSemanticsNode().config[SemanticsActions.CustomActions].forEach { it.action() }

        assertEquals(listOf("read n1 true", "delete n1"), calls)
    }
}
