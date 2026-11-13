package eg.bahr.feature.search.presentation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import eg.bahr.core.catalog.model.HostCardDto
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.feature.search.data.FakeHostListRepository
import eg.bahr.feature.search.data.FakeRecentSearchesStore
import eg.bahr.feature.search.data.FakeTripListRepository
import eg.bahr.feature.search.data.LastSearch
import eg.bahr.feature.search.data.TripFixtures.page
import eg.bahr.feature.search.data.TripFixtures.trip
import eg.bahr.feature.search.data.waitlistMemory
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * MV-M11: opening a result lets go of the field and puts the keyboard away before the next page opens, so the keyboard
 * is not left up over the trip page. The keyboard is a fake controller, which records what search asked of it (Robolectric
 * has no soft keyboard to look at); the focus is the real one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class SearchLeavesKeyboardTest {
    @get:Rule
    val compose = createComposeRule()

    private val keyboard = RecordingKeyboard()

    /** Search stays composed (the callback only records): the field itself must have let go of the focus. */
    @Test
    fun `opening a trip clears the field's focus and hides the keyboard`() {
        val opened = mutableListOf<String>()
        val vm = searchWithResults()
        show { SearchTripsScreen(onBack = {}, onTripClick = { opened += it }, viewModel = vm) }
        searchFor(vm)
        compose.onNodeWithTag(SEARCH_FIELD_TAG).assertIsFocused()
        val hidesBefore = keyboard.hides

        compose.onNodeWithText(TRIP_TITLE).performClick()
        compose.waitForIdle()

        assertEquals(listOf(TRIP_SLUG), opened)
        compose.onNodeWithTag(SEARCH_FIELD_TAG).assertIsNotFocused()
        assertEquals(hidesBefore + 1, keyboard.hides, "the keyboard was put away")
    }

    /** A matching host and "See all hosts" open other pages the same way. */
    @Test
    fun `opening a host or the hosts directory clears the focus and hides the keyboard`() {
        val vm = searchWithResults()
        show { SearchTripsScreen(onBack = {}, onTripClick = {}, viewModel = vm) }
        searchFor(vm)
        val hidesBefore = keyboard.hides

        compose.onNodeWithText(HOST_NAME).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SEARCH_FIELD_TAG).assertIsNotFocused()
        assertEquals(hidesBefore + 1, keyboard.hides, "host")

        compose.onNodeWithTag(SEARCH_FIELD_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SEARCH_FIELD_TAG).assertIsFocused()
        compose.onNodeWithTag(SEE_ALL_HOSTS_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SEARCH_FIELD_TAG).assertIsNotFocused()
        assertEquals(hidesBefore + 2, keyboard.hides, "see all hosts")
    }

    /**
     * Through a real NavHost, as the app opens a trip from search: the trip page is shown, and the keyboard was put away on
     * the way (once the trip page is on top, search's field is gone, so its focus says nothing; the keyboard is what stays).
     */
    @Test
    fun `navigating from search to a trip puts the keyboard away`() {
        val vm = searchWithResults()
        show {
            val nav = rememberNavController()
            NavHost(nav, startDestination = SearchStub) {
                composable<SearchStub> {
                    SearchTripsScreen(onBack = {}, onTripClick = { nav.navigate(TripStub(it)) }, viewModel = vm)
                }
                composable<TripStub> { Text(TRIP_PAGE) }
            }
        }
        searchFor(vm)
        compose.onNodeWithTag(SEARCH_FIELD_TAG).assertIsFocused()
        val hidesBefore = keyboard.hides

        compose.onNodeWithText(TRIP_TITLE).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(TRIP_PAGE).assertIsDisplayed()
        compose.onNodeWithTag(SEARCH_FIELD_TAG).assertDoesNotExist()
        assertEquals(hidesBefore + 1, keyboard.hides)
    }

    private fun searchWithResults() =
        SearchTripsViewModel(
            FakeTripListRepository(listFiltered = { page(listOf(trip(1, title = TRIP_TITLE).copy(slug = TRIP_SLUG))) }),
            FakeRecentSearchesStore(),
            waitlistMemory(),
            LastSearch(),
            FakeHostListRepository(
                listHosts = { FakeHostListRepository.hosts(HostCardDto(slug = "hoda-eissa", name = HOST_NAME, publishedTrips = 1)) },
            ),
        )

    /** Runs a search without touching the field, which keeps the focus it took on the way in. */
    private fun searchFor(vm: SearchTripsViewModel) {
        compose.runOnIdle { vm.searchRecent(QUERY) }
        compose.waitForIdle()
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            ProvideAppLanguage(AppLanguage.ENGLISH) {
                BahrTheme(locale = BahrLocale.English) {
                    CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard, content = content)
                }
            }
        }
        compose.waitForIdle()
    }

    private class RecordingKeyboard : SoftwareKeyboardController {
        var hides = 0

        override fun show() = Unit

        override fun hide() {
            hides++
        }
    }

    @Serializable
    data object SearchStub

    @Serializable
    data class TripStub(
        val slug: String,
    )

    private companion object {
        const val QUERY = "lake"
        const val TRIP_TITLE = "Dawn on Lake Burullus"
        const val TRIP_SLUG = "burullus-dawn"
        const val TRIP_PAGE = "The trip page"
        const val HOST_NAME = "Hoda Eissa"
    }
}
