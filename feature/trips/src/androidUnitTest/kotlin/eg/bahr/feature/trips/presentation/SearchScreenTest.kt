package eg.bahr.feature.trips.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.feature.trips.data.FakeRecentSearchesStore
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripApiService
import eg.bahr.feature.trips.data.TripFixtures.page
import eg.bahr.feature.trips.data.TripFixtures.trip
import eg.bahr.feature.trips.data.TripQuery
import eg.bahr.feature.trips.data.waitlistMemory
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals

/**
 * M4-M3 on the screen: the field takes focus on the way in (and only then), a recent search runs at
 * once and fills the field, and × goes back to the recent searches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class SearchScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var deviceLocale: Locale

    @Before
    fun saveLocale() {
        deviceLocale = Locale.getDefault()
    }

    @After
    fun restoreLocale() = Locale.setDefault(deviceLocale)

    @Test
    fun `a recent search fills the field and shows its results - x goes back to the recents`() {
        val repository = FakeTripRepository(listFiltered = { q -> page(listOf(trip(1, title = "A trip for ${q.q}"))) })
        val vm = SearchTripsViewModel(repository, FakeRecentSearchesStore(listOf("birds", "kayak")), waitlistMemory())
        show(vm)
        compose.onNodeWithTag(SEARCH_FIELD_TAG).assertIsFocused()

        compose.onNodeWithText("kayak").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("A trip for kayak").assertIsDisplayed()
        compose.onNodeWithTag(SEARCH_FIELD_TAG).assertTextEquals("kayak", includeEditableText = true)
        assertEquals(listOf(TripQuery(0, q = "kayak")), repository.requestedQueries)
        assertEquals(listOf("kayak", "birds"), vm.uiState.value.recents)

        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("A trip for kayak").assertDoesNotExist()
        compose.onNodeWithText("Recent searches").assertIsDisplayed()
        compose.onNodeWithTag(SEARCH_FIELD_TAG).assertIsFocused()
    }

    @Test
    fun `typed or pasted control characters never reach the field`() {
        val vm = SearchTripsViewModel(FakeTripRepository(), FakeRecentSearchesStore(), waitlistMemory())
        show(vm)

        compose.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("bi\u0000rd\u001Bs")
        compose.waitForIdle()

        compose.onNodeWithTag(SEARCH_FIELD_TAG).assertTextEquals("birds", includeEditableText = true)
        assertEquals("birds", vm.uiState.value.text)
    }

    @Test
    fun `a control character pasted mid-text is dropped where it is and the cursor stays after the paste`() {
        val vm = SearchTripsViewModel(FakeTripRepository(), FakeRecentSearchesStore(), waitlistMemory())
        show(vm)
        val field = compose.onNodeWithTag(SEARCH_FIELD_TAG)
        field.performTextInput("bird")
        field.performTextInputSelection(TextRange(2))

        field.performTextInput("X\u0000Y")
        compose.waitForIdle()

        field.assertTextEquals("biXYrd", includeEditableText = true)
        // After "XY", where a paste without the control character would leave it, not at the end.
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(4)))
    }

    @Test
    fun `the field refuses a paste longer than the cap`() {
        val vm = SearchTripsViewModel(FakeTripRepository(), FakeRecentSearchesStore(), waitlistMemory())
        show(vm)

        compose.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("a".repeat(TripApiService.MAX_QUERY_LENGTH + 1))
        compose.waitForIdle()

        // Refused whole (InputTransformation.maxLength reverts the change), not cut to the cap. The
        // editable text only: the node's Text also carries the placeholder.
        compose.onNodeWithTag(SEARCH_FIELD_TAG).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        assertEquals("", vm.uiState.value.text)
    }

    @Test
    fun `coming back to search does not take the focus again`() {
        val vm = SearchTripsViewModel(FakeTripRepository(), FakeRecentSearchesStore(), waitlistMemory())
        var shown by mutableStateOf(true)
        compose.setContent {
            ProvideAppLanguage(AppLanguage.ENGLISH) {
                BahrTheme(locale = BahrLocale.English) {
                    // A back-stack entry keeps the screen's saved state while a trip page is on top; a
                    // saveable-state holder does the same here (a plain `if` would forget it).
                    val entries = rememberSaveableStateHolder()
                    if (shown) {
                        entries.SaveableStateProvider("search") {
                            SearchTripsScreen(onBack = {}, onTripClick = {}, viewModel = vm)
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag(SEARCH_FIELD_TAG).assertIsFocused()

        compose.runOnIdle { shown = false }
        compose.runOnIdle { shown = true }
        compose.waitForIdle()

        compose.onNodeWithTag(SEARCH_FIELD_TAG).assertIsNotFocused()
    }

    private fun show(vm: SearchTripsViewModel) {
        compose.setContent {
            ProvideAppLanguage(AppLanguage.ENGLISH) {
                BahrTheme(locale = BahrLocale.English) {
                    SearchTripsScreen(onBack = {}, onTripClick = {}, viewModel = vm)
                }
            }
        }
        compose.waitForIdle()
    }
}
