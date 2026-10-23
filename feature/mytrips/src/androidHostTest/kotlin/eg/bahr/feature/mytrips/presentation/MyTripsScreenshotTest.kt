package eg.bahr.feature.mytrips.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.designsystem.components.BAHR_LOTTIE_READY_TAG
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.BahrThemeName
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.mytrips.data.MyTripsFixtures
import eg.bahr.feature.mytrips.model.MyBookingDto
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * M3-M0: My trips × {ar, en} × {signed out with this phone's waiting lists (also dark) or none, loading, Upcoming loaded,
 * Waiting list loaded, Past empty, waiting list failed}. From the stateless content with fixed paging
 * data, as the screen draws it.
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

    @Test
    fun loadingArabic() = shoot("mytrips_loading", AppLanguage.ARABIC, MyTripsUiState(session = signedIn), emptyList(), loading)

    @Test
    fun loadingEnglish() = shoot("mytrips_loading", AppLanguage.ENGLISH, MyTripsUiState(session = signedIn), emptyList(), loading)

    /** The rows' skeleton in dark mode (M3-M14). */
    @Test
    fun loadingDarkArabic() =
        shoot("mytrips_loading_dark", AppLanguage.ARABIC, MyTripsUiState(session = signedIn), emptyList(), loading, BahrThemeName.Dark)

    @Test
    fun loadingDarkEnglish() =
        shoot("mytrips_loading_dark", AppLanguage.ENGLISH, MyTripsUiState(session = signedIn), emptyList(), loading, BahrThemeName.Dark)

    /** The waiting list's first read: the same skeleton rows under the segments (M3-M14). */
    @Test
    fun waitlistLoadingArabic() = shoot("mytrips_waitlist_loading", AppLanguage.ARABIC, waitlistLoading, emptyList())

    @Test
    fun waitlistLoadingEnglish() = shoot("mytrips_waitlist_loading", AppLanguage.ENGLISH, waitlistLoading, emptyList())

    private val waitlistLoading = MyTripsUiState(session = signedIn, segment = MyTripsSegment.Waitlist, waitlist = WaitlistLoad.Loading)

    @Test
    fun upcomingArabic() = shoot("mytrips_upcoming", AppLanguage.ARABIC, MyTripsUiState(session = signedIn), MyTripsFixtures.upcoming())

    @Test
    fun upcomingEnglish() = shoot("mytrips_upcoming", AppLanguage.ENGLISH, MyTripsUiState(session = signedIn), MyTripsFixtures.upcoming())

    private val waitlistState =
        MyTripsUiState(session = signedIn, segment = MyTripsSegment.Waitlist, waitlist = WaitlistLoad.Loaded(MyTripsFixtures.waitlist()))

    @Test
    fun waitlistArabic() = shoot("mytrips_waitlist", AppLanguage.ARABIC, waitlistState, emptyList())

    @Test
    fun waitlistEnglish() = shoot("mytrips_waitlist", AppLanguage.ENGLISH, waitlistState, emptyList())

    private val pastEmpty = MyTripsUiState(session = signedIn, segment = MyTripsSegment.Past)

    // Signed in, no trips (M3-M8b): the looping suitcase over the line, shot still under reduced motion.
    @Test
    fun pastEmptyArabic() = shootStill("mytrips_past_empty", AppLanguage.ARABIC, pastEmpty)

    @Test
    fun pastEmptyEnglish() = shootStill("mytrips_past_empty", AppLanguage.ENGLISH, pastEmpty)

    private val upcomingEmpty = MyTripsUiState(session = signedIn)

    @Test
    fun upcomingEmptyArabic() = shootStill("mytrips_upcoming_empty", AppLanguage.ARABIC, upcomingEmpty)

    @Test
    fun upcomingEmptyEnglish() = shootStill("mytrips_upcoming_empty", AppLanguage.ENGLISH, upcomingEmpty)

    @Test
    fun upcomingEmptyDarkArabic() = shootStill("mytrips_upcoming_empty_dark", AppLanguage.ARABIC, upcomingEmpty, BahrThemeName.Dark)

    @Test
    fun upcomingEmptyDarkEnglish() = shootStill("mytrips_upcoming_empty_dark", AppLanguage.ENGLISH, upcomingEmpty, BahrThemeName.Dark)

    private val waitlistFailed =
        MyTripsUiState(session = signedIn, segment = MyTripsSegment.Waitlist, waitlist = WaitlistLoad.Failed(AppError.Network))

    @Test
    fun waitlistFailedArabic() = shoot("mytrips_waitlist_failed", AppLanguage.ARABIC, waitlistFailed, emptyList())

    @Test
    fun waitlistFailedEnglish() = shoot("mytrips_waitlist_failed", AppLanguage.ENGLISH, waitlistFailed, emptyList())

    @Test
    fun `signed out, the sign-in button and a device join call back`() {
        var signIns = 0
        val opened = mutableListOf<String>()
        compose.setContent {
            Themed(AppLanguage.ENGLISH) {
                MyTripsContent(
                    state = signedOutState,
                    bookings = flowOf(PagingData.from(emptyList<MyBookingDto>(), loaded)).collectAsLazyPagingItems(),
                    actions = MyTripsActions(onSignIn = { signIns++ }, onTripClick = { opened += it }),
                )
            }
        }
        compose.onNodeWithTag(SIGN_IN_TAG).performClick()
        assertEquals(1, signIns)
    }

    @Test
    fun `a segment tap reaches the view model`() {
        val picked = mutableListOf<MyTripsSegment>()
        compose.setContent {
            Themed(AppLanguage.ENGLISH) {
                MyTripsContent(
                    state = MyTripsUiState(session = signedIn),
                    bookings = flowOf(PagingData.from(MyTripsFixtures.upcoming(), loaded)).collectAsLazyPagingItems(),
                    actions = MyTripsActions(onSegment = { picked += it }),
                )
            }
        }
        compose.onNodeWithTag(segmentTag(MyTripsSegment.Waitlist)).performClick()
        assertEquals(listOf(MyTripsSegment.Waitlist), picked)
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
                    bookings = flowOf(PagingData.from(emptyList<MyBookingDto>(), loaded)).collectAsLazyPagingItems(),
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
                    bookings = flowOf(PagingData.from(bookings, loadStates)).collectAsLazyPagingItems(),
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
