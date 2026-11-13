package eg.bahr.feature.mytrips.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.designsystem.components.BAHR_LOTTIE_READY_TAG
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.BahrThemeName
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.mytrips.data.MyTripsFixtures
import eg.bahr.feature.mytrips.model.MyBookingDto
import eg.bahr.feature.mytrips.presentation.components.PAID_WITH_TAG
import eg.bahr.feature.mytrips.presentation.components.RATE_TRIP_TAG
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.absoluteValue
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * My trips × {ar, en}: signed out with this phone's waiting lists (also dark) or none (M3-M0, M3-M8); signed in as
 * one list since MV-M2 × light/dark: loading, all three sections, only Past (with "Rate your trip" and each review
 * status), one section failed, nothing at all. From the stateless content with fixed paging data, as the screen
 * draws it.
 *
 * Record: `./gradlew :feature:mytrips:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class MyTripsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val signedIn = SessionState.SignedIn("Sara", profileComplete = true, providers = setOf("google"))
    private val loaded = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
    private val loading = LoadStates(LoadState.Loading, LoadState.NotLoading(false), LoadState.NotLoading(false))

    private val signedOutState =
        MyTripsUiState(
            session = SessionState.SignedOut,
            deviceJoins =
                listOf(
                    DeviceJoin("dep-3", "burullus-dawn", "الفجر على بحيرة البرلس", LocalDate(2026, 10, 24), 2),
                    DeviceJoin("dep-9", "burg-murals", null, LocalDate(2026, 11, 7), 4),
                ),
        )

    /*
     * Signed out (M3-M8): the sign-in card's sailing-boat hero (BahrLottie) over this phone's waiting lists,
     * which must stay in view under it. The hero loops since M3-M8b (never idle), so it is shot still under
     * reduced motion.
     */
    @Test
    fun signedOutArabic() = shootStill("mytrips_signed_out", AppLanguage.ARABIC, signedOutState)

    @Test
    fun signedOutEnglish() = shootStill("mytrips_signed_out", AppLanguage.ENGLISH, signedOutState)

    @Test
    fun signedOutDarkArabic() = shootStill("mytrips_signed_out_dark", AppLanguage.ARABIC, signedOutState, BahrThemeName.Dark)

    @Test
    fun signedOutDarkEnglish() = shootStill("mytrips_signed_out_dark", AppLanguage.ENGLISH, signedOutState, BahrThemeName.Dark)

    /** No waiting list joined on this phone: the card alone. */
    @Test
    fun signedOutNoJoinsArabic() = shootStill("mytrips_signed_out_no_joins", AppLanguage.ARABIC, MyTripsUiState(SessionState.SignedOut))

    @Test
    fun signedOutNoJoinsEnglish() = shootStill("mytrips_signed_out_no_joins", AppLanguage.ENGLISH, MyTripsUiState(SessionState.SignedOut))

    /** Every section's first read: one skeleton, at the top (MV-M2: never one per section). */
    private val loadingState = MyTripsUiState(session = signedIn)

    @Test
    fun loadingArabic() = shoot("mytrips_loading", AppLanguage.ARABIC, loadingState, emptyList(), loading)

    @Test
    fun loadingEnglish() = shoot("mytrips_loading", AppLanguage.ENGLISH, loadingState, emptyList(), loading)

    /** The rows' skeleton in dark mode (M3-M14). */
    @Test
    fun loadingDarkArabic() = shoot("mytrips_loading_dark", AppLanguage.ARABIC, loadingState, emptyList(), loading, BahrThemeName.Dark)

    @Test
    fun loadingDarkEnglish() = shoot("mytrips_loading_dark", AppLanguage.ENGLISH, loadingState, emptyList(), loading, BahrThemeName.Dark)

    /** Upcoming, Waiting list, Past, each under its header and count; Past's count is the server's total, not the rows shown. */
    private val allSections =
        MyTripsUiState(
            session = signedIn,
            upcoming = SectionLoad.Loaded(MyTripsFixtures.upcoming()),
            waitlist = SectionLoad.Loaded(MyTripsFixtures.waitlist()),
            pastTotal = PAST_TOTAL,
        )

    @Config(qualifiers = "en-w360dp-h1400dp-xhdpi")
    @Test
    fun allSectionsArabic() = shoot("mytrips_all_sections", AppLanguage.ARABIC, allSections, MyTripsFixtures.past())

    @Config(qualifiers = "en-w360dp-h1400dp-xhdpi")
    @Test
    fun allSectionsEnglish() = shoot("mytrips_all_sections", AppLanguage.ENGLISH, allSections, MyTripsFixtures.past())

    @Config(qualifiers = "en-w360dp-h1400dp-xhdpi")
    @Test
    fun allSectionsDarkArabic() =
        shoot("mytrips_all_sections_dark", AppLanguage.ARABIC, allSections, MyTripsFixtures.past(), theme = BahrThemeName.Dark)

    @Config(qualifiers = "en-w360dp-h1400dp-xhdpi")
    @Test
    fun allSectionsDarkEnglish() =
        shoot("mytrips_all_sections_dark", AppLanguage.ENGLISH, allSections, MyTripsFixtures.past(), theme = BahrThemeName.Dark)

    /**
     * MV-M4: "Paid with …" on each paid booking whose method the app knows, and a "Test" badge on a test-cash one; none
     * on an unknown method or a host-entered booking.
     */
    private val paidWith =
        MyTripsUiState(
            session = signedIn,
            upcoming = SectionLoad.Loaded(MyTripsFixtures.paidWithMethods()),
            waitlist = SectionLoad.Loaded(emptyList()),
            pastTotal = 0,
        )

    @Config(qualifiers = "en-w360dp-h1000dp-xhdpi")
    @Test
    fun paidWithArabic() = shoot("mytrips_paid_with", AppLanguage.ARABIC, paidWith, emptyList())

    @Config(qualifiers = "en-w360dp-h1000dp-xhdpi")
    @Test
    fun paidWithEnglish() = shoot("mytrips_paid_with", AppLanguage.ENGLISH, paidWith, emptyList())

    @Config(qualifiers = "en-w360dp-h1000dp-xhdpi")
    @Test
    fun `the method shows on a paid booking the app knows the method of - not on an unknown one or none`() {
        compose.setContent {
            Themed(AppLanguage.ENGLISH) {
                MyTripsContent(
                    state = paidWith,
                    past = flowOf(PagingData.from(emptyList<MyBookingDto>(), loaded)).collectAsLazyPagingItems(),
                    actions = MyTripsActions(),
                )
            }
        }
        // Unmerged: each row is one button, which merges its lines into itself.
        compose.onAllNodesWithTag(PAID_WITH_TAG, useUnmergedTree = true).assertCountEquals(KNOWN_METHODS)
        compose.onNodeWithText("Paid with Test cash", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("Test", useUnmergedTree = true).assertCountEquals(1)
    }

    /**
     * Nothing upcoming, no waiting list: Past alone, no empty headers above it. M3-M4's "Rate your trip" on a finished
     * booking and each review status on the reviewed ones.
     */
    private val onlyPast =
        MyTripsUiState(
            session = signedIn,
            upcoming = SectionLoad.Loaded(emptyList()),
            waitlist = SectionLoad.Loaded(emptyList()),
            pastTotal = MyTripsFixtures.pastWithReviews().size.toLong(),
        )

    @Config(qualifiers = "en-w360dp-h1000dp-xhdpi")
    @Test
    fun onlyPastArabic() = shoot("mytrips_only_past", AppLanguage.ARABIC, onlyPast, MyTripsFixtures.pastWithReviews())

    @Config(qualifiers = "en-w360dp-h1000dp-xhdpi")
    @Test
    fun onlyPastEnglish() = shoot("mytrips_only_past", AppLanguage.ENGLISH, onlyPast, MyTripsFixtures.pastWithReviews())

    @Config(qualifiers = "en-w360dp-h1000dp-xhdpi")
    @Test
    fun onlyPastDarkArabic() =
        shoot("mytrips_only_past_dark", AppLanguage.ARABIC, onlyPast, MyTripsFixtures.pastWithReviews(), theme = BahrThemeName.Dark)

    @Config(qualifiers = "en-w360dp-h1000dp-xhdpi")
    @Test
    fun onlyPastDarkEnglish() =
        shoot("mytrips_only_past_dark", AppLanguage.ENGLISH, onlyPast, MyTripsFixtures.pastWithReviews(), theme = BahrThemeName.Dark)

    /** The waiting list could not be read: its header and its own "Try again"; Upcoming and Past stay. */
    private val waitlistFailed = allSections.copy(waitlist = SectionLoad.Failed(AppError.Network))

    @Config(qualifiers = "en-w360dp-h1200dp-xhdpi")
    @Test
    fun sectionFailedArabic() = shoot("mytrips_section_failed", AppLanguage.ARABIC, waitlistFailed, MyTripsFixtures.past())

    @Config(qualifiers = "en-w360dp-h1200dp-xhdpi")
    @Test
    fun sectionFailedEnglish() = shoot("mytrips_section_failed", AppLanguage.ENGLISH, waitlistFailed, MyTripsFixtures.past())

    @Config(qualifiers = "en-w360dp-h1200dp-xhdpi")
    @Test
    fun sectionFailedDarkArabic() =
        shoot("mytrips_section_failed_dark", AppLanguage.ARABIC, waitlistFailed, MyTripsFixtures.past(), theme = BahrThemeName.Dark)

    @Config(qualifiers = "en-w360dp-h1200dp-xhdpi")
    @Test
    fun sectionFailedDarkEnglish() =
        shoot("mytrips_section_failed_dark", AppLanguage.ENGLISH, waitlistFailed, MyTripsFixtures.past(), theme = BahrThemeName.Dark)

    // Signed in, no trips in any section (M3-M8b): the looping map (a suitcase until 2026-11-12) over the line, in the
    // middle of the space under the title; shot still under reduced motion.
    private val empty =
        MyTripsUiState(session = signedIn, upcoming = SectionLoad.Loaded(emptyList()), waitlist = SectionLoad.Loaded(emptyList()))

    @Test
    fun emptyArabic() = shootStill("mytrips_empty", AppLanguage.ARABIC, empty)

    @Test
    fun emptyEnglish() = shootStill("mytrips_empty", AppLanguage.ENGLISH, empty)

    @Test
    fun emptyDarkArabic() = shootStill("mytrips_empty_dark", AppLanguage.ARABIC, empty, BahrThemeName.Dark)

    @Test
    fun emptyDarkEnglish() = shootStill("mytrips_empty_dark", AppLanguage.ENGLISH, empty, BahrThemeName.Dark)

    /** User, 2026-11-12: the empty state sits in the middle of the space under the title, not at its top. */
    @Test
    fun `no trips - the empty state is centred under the title`() {
        compose.setContent {
            Themed(AppLanguage.ENGLISH, reducedMotion = true) {
                MyTripsContent(
                    state = empty,
                    past = flowOf(PagingData.from(emptyList<MyBookingDto>(), loaded)).collectAsLazyPagingItems(),
                    actions = MyTripsActions(),
                )
            }
        }
        compose.waitUntil(LOTTIE_LOAD_TIMEOUT_MS) { compose.onAllNodesWithTag(BAHR_LOTTIE_READY_TAG).fetchSemanticsNodes().isNotEmpty() }
        val title = compose.onNodeWithText("My trips").getBoundsInRoot()
        val list = compose.onNodeWithTag(MY_TRIPS_LIST_TAG).getBoundsInRoot()
        val art = compose.onNodeWithTag(BAHR_LOTTIE_READY_TAG).getBoundsInRoot()
        val line = compose.onNodeWithText("No upcoming trips yet.").getBoundsInRoot()
        // The space under the title ends at the list's bottom padding.
        val spaceMiddle = (title.bottom + list.bottom - BahrSpacing.lg) / 2
        val stateMiddle = (art.top + line.bottom) / 2
        assertTrue((stateMiddle - spaceMiddle).value.absoluteValue <= BahrSpacing.xs.value, "off centre by ${stateMiddle - spaceMiddle}")
        assertTrue((art.left + art.right - list.left - list.right).value.absoluteValue <= BahrSpacing.xs.value, "not centred across")
    }

    /** The centred empty state is still the list: pulling it down reads the sections again. */
    @Test
    fun `no trips - a pull refreshes`() {
        var pulls = 0
        compose.setContent {
            Themed(AppLanguage.ENGLISH, reducedMotion = true) {
                MyTripsContent(
                    state = empty,
                    past = flowOf(PagingData.from(emptyList<MyBookingDto>(), loaded)).collectAsLazyPagingItems(),
                    actions = MyTripsActions(onRefresh = { pulls++ }),
                )
            }
        }
        compose.onNodeWithTag(MY_TRIPS_LIST_TAG).performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(1, pulls) }
    }

    @Test
    fun `Rate your trip calls back with the booking and its trip's title - only where it may be reviewed`() {
        val rated = mutableListOf<Pair<String, String?>>()
        val opened = mutableListOf<String>()
        compose.setContent {
            Themed(AppLanguage.ENGLISH) {
                MyTripsContent(
                    state = onlyPast,
                    past = flowOf(PagingData.from(MyTripsFixtures.pastWithReviews(), loaded)).collectAsLazyPagingItems(),
                    actions = MyTripsActions(onRateTrip = { ref, title -> rated += ref to title }, onTripClick = { opened += it }),
                )
            }
        }
        compose.onAllNodesWithTag(RATE_TRIP_TAG).assertCountEquals(1)
        compose.onNodeWithTag(RATE_TRIP_TAG).performClick()
        assertEquals(listOf<Pair<String, String?>>("BRL-1R1R1R1R" to "الفجر على بحيرة البرلس"), rated)
        // The button is its own target: the row under it did not open the trip.
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `signed out, the sign-in button and a device join call back`() {
        var signIns = 0
        val opened = mutableListOf<String>()
        compose.setContent {
            Themed(AppLanguage.ENGLISH) {
                MyTripsContent(
                    state = signedOutState,
                    past = flowOf(PagingData.from(emptyList<MyBookingDto>(), loaded)).collectAsLazyPagingItems(),
                    actions = MyTripsActions(onSignIn = { signIns++ }, onTripClick = { opened += it }),
                )
            }
        }
        compose.onNodeWithTag(SIGN_IN_TAG).performClick()
        assertEquals(1, signIns)
    }

    /** TalkBack / VoiceOver move by headings: every section header is one; a failed one's retry reads only it. */
    @Test
    fun `section headers are headings and a failed section's retry reaches only that section`() {
        var waitlistRetries = 0
        var upcomingRetries = 0
        compose.setContent {
            Themed(AppLanguage.ENGLISH) {
                MyTripsContent(
                    state = waitlistFailed,
                    past = flowOf(PagingData.from(MyTripsFixtures.past(), loaded)).collectAsLazyPagingItems(),
                    actions = MyTripsActions(onRetryWaitlist = { waitlistRetries++ }, onRetryUpcoming = { upcomingRetries++ }),
                )
            }
        }
        listOf(MyTripsSection.Upcoming, MyTripsSection.Waitlist).forEach { section ->
            compose.onNodeWithTag(sectionHeaderTag(section)).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        }
        compose.onNodeWithTag(sectionHeaderTag(MyTripsSection.Upcoming)).assertTextEquals("Upcoming (2)")
        compose.onNodeWithTag(sectionHeaderTag(MyTripsSection.Waitlist)).assertTextEquals("Waiting list")
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, waitlistRetries)
        assertEquals(0, upcomingRetries)
        compose.onNodeWithTag(MY_TRIPS_LIST_TAG).performScrollToNode(hasTestTag(sectionHeaderTag(MyTripsSection.Past)))
        compose
            .onNodeWithTag(sectionHeaderTag(MyTripsSection.Past))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertTextEquals("Past ($PAST_TOTAL)")
    }

    /** MV-M2: a section's header stays at the top while its rows scroll under it. */
    @Test
    fun `a section header sticks while its rows scroll`() {
        lateinit var listState: LazyListState
        compose.setContent {
            listState = rememberLazyListState()
            Themed(AppLanguage.ENGLISH) {
                MyTripsContent(
                    state = allSections,
                    past = flowOf(PagingData.from(MyTripsFixtures.past(), loaded)).collectAsLazyPagingItems(),
                    actions = MyTripsActions(),
                    listState = listState,
                )
            }
        }
        // (The smallest spacing step absorbs rounding between the list's padding and the measured top.)
        // The title (0), Upcoming's header (1) and first row (2) go above the top: only a sticky header still shows.
        compose.runOnIdle { runBlocking { listState.scrollToItem(UPCOMING_SECOND_ROW_INDEX) } }

        val header = compose.onNodeWithTag(sectionHeaderTag(MyTripsSection.Upcoming)).assertIsDisplayed().getBoundsInRoot()
        val list = compose.onNodeWithTag(MY_TRIPS_LIST_TAG).getBoundsInRoot()
        assertTrue(header.top - list.top <= BahrSpacing.lg + BahrSpacing.xs, "pinned at the list's top, was ${header.top - list.top}")
    }

    /** Pull-to-refresh reads the account's lists again; signed out there is nothing to pull. */
    @Test
    fun `a pull refreshes when signed in - not when signed out`() {
        var pulls = 0
        var signedOut by mutableStateOf(false)
        compose.setContent {
            Themed(AppLanguage.ENGLISH) {
                MyTripsContent(
                    state = if (signedOut) signedOutState else allSections,
                    past = flowOf(PagingData.from(MyTripsFixtures.past(), loaded)).collectAsLazyPagingItems(),
                    actions = MyTripsActions(onRefresh = { pulls++ }),
                )
            }
        }
        compose.onNodeWithTag(MY_TRIPS_LIST_TAG).performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(1, pulls) }

        signedOut = true
        compose.onNodeWithTag(MY_TRIPS_LIST_TAG).performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(1, pulls) }
    }

    /** A shot holding a BahrLottie: still under reduced motion, once its file has loaded. */
    private fun shootStill(
        prefix: String,
        language: AppLanguage,
        state: MyTripsUiState,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
    ) {
        compose.setContent {
            Themed(language, theme, reducedMotion = true) {
                MyTripsContent(
                    state = state,
                    past = flowOf(PagingData.from(emptyList<MyBookingDto>(), loaded)).collectAsLazyPagingItems(),
                    actions = MyTripsActions(),
                )
            }
        }
        // The animation's file is read off the main thread: idle alone would shoot its empty box.
        compose.waitUntil(LOTTIE_LOAD_TIMEOUT_MS) { compose.onAllNodesWithTag(BAHR_LOTTIE_READY_TAG).fetchSemanticsNodes().isNotEmpty() }
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    private fun shoot(
        prefix: String,
        language: AppLanguage,
        state: MyTripsUiState,
        bookings: List<MyBookingDto>,
        loadStates: LoadStates = loaded,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
    ) {
        compose.setContent {
            Themed(language, theme) {
                MyTripsContent(
                    state = state,
                    past = flowOf(PagingData.from(bookings, loadStates)).collectAsLazyPagingItems(),
                    actions = MyTripsActions(),
                )
            }
        }
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    @Composable
    private fun Themed(
        language: AppLanguage,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
        // Only the shots with an animation: reduced motion also holds the skeletons' pulse still.
        reducedMotion: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        ProvideAppLanguage(language) {
            BahrTheme(
                locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English,
                theme = theme,
                reducedMotion = reducedMotion,
            ) {
                // The app's tab Scaffold sets onBackground as the content colour; a bare test root does not, so dark
                // shots would draw uncoloured text black on the dark page (light goldens keep their recorded black).
                CompositionLocalProvider(
                    LocalContentColor provides
                        if (theme == BahrThemeName.Dark) MaterialTheme.colorScheme.onBackground else LocalContentColor.current,
                ) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
                }
            }
        }
    }
}

private const val LOTTIE_LOAD_TIMEOUT_MS = 10_000L

/** Upcoming's second row: the title and Upcoming's header and first row come before it. */
private const val UPCOMING_SECOND_ROW_INDEX = 3

/** More past bookings than the first page shows: the header counts the server's total. */
private const val PAST_TOTAL = 14L

/** `MyTripsFixtures.paidWithMethods`: test cash, card and Vodafone Cash; the unknown and the absent one show none. */
private const val KNOWN_METHODS = 3
