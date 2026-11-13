package eg.bahr.feature.triplisting.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import eg.bahr.core.catalog.data.LikeState
import eg.bahr.core.catalog.data.TripHearts
import eg.bahr.core.catalog.data.TripLikes
import eg.bahr.core.catalog.model.BadgeDto
import eg.bahr.core.catalog.model.NextDepartureDto
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.BahrThemeName
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.triplisting.data.FakeTripListingRepository
import eg.bahr.feature.triplisting.data.TripFixtures.sectionPage
import eg.bahr.feature.triplisting.data.TripFixtures.trip
import eg.bahr.feature.triplisting.data.waitlistMemory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * MP-M1: Account → Liked trips × {ar, en} × {light, dark} × {filled, empty, signed out}, through the real view model on
 * a fake repository. The filled list's cards carry the heart filled with their counts, a multi-day trip's nights badge
 * stacked under its badge at the top start.
 *
 * Record: `./gradlew :feature:trip-listing:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class LikedTripsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var deviceLocale: Locale

    @Before
    fun setUp() {
        deviceLocale = Locale.getDefault()
        compose.mainClock.autoAdvance = false
    }

    @After
    fun restoreLocale() = Locale.setDefault(deviceLocale)

    @Test
    fun filledArabic() = snap("liked_trips_filled", AppLanguage.ARABIC, State.Filled)

    @Test
    fun filledEnglish() = snap("liked_trips_filled", AppLanguage.ENGLISH, State.Filled)

    @Test
    fun filledDarkArabic() = snap("liked_trips_filled_dark", AppLanguage.ARABIC, State.Filled, BahrThemeName.Dark)

    @Test
    fun filledDarkEnglish() = snap("liked_trips_filled_dark", AppLanguage.ENGLISH, State.Filled, BahrThemeName.Dark)

    @Test
    fun emptyArabic() = snap("liked_trips_empty", AppLanguage.ARABIC, State.Empty)

    @Test
    fun emptyEnglish() = snap("liked_trips_empty", AppLanguage.ENGLISH, State.Empty)

    @Test
    fun emptyDarkArabic() = snap("liked_trips_empty_dark", AppLanguage.ARABIC, State.Empty, BahrThemeName.Dark)

    @Test
    fun emptyDarkEnglish() = snap("liked_trips_empty_dark", AppLanguage.ENGLISH, State.Empty, BahrThemeName.Dark)

    @Test
    fun signedOutArabic() = snap("liked_trips_signed_out", AppLanguage.ARABIC, State.SignedOut)

    @Test
    fun signedOutEnglish() = snap("liked_trips_signed_out", AppLanguage.ENGLISH, State.SignedOut)

    @Test
    fun signedOutDarkArabic() = snap("liked_trips_signed_out_dark", AppLanguage.ARABIC, State.SignedOut, BahrThemeName.Dark)

    @Test
    fun signedOutDarkEnglish() = snap("liked_trips_signed_out_dark", AppLanguage.ENGLISH, State.SignedOut, BahrThemeName.Dark)

    private enum class State { Filled, Empty, SignedOut }

    private fun snap(
        prefix: String,
        language: AppLanguage,
        state: State,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
    ) {
        val arabic = language == AppLanguage.ARABIC
        val signedIn = state != State.SignedOut
        val repository =
            FakeTripListingRepository(likedTrips = { sectionPage(if (state == State.Filled) cards(arabic) else emptyList()) })
        val session =
            if (signedIn) {
                SessionState.SignedIn(
                    "Mona",
                    profileComplete = true,
                    providers = setOf("google"),
                )
            } else {
                SessionState.SignedOut
            }
        val vm = LikedTripsViewModel(repository, waitlistMemory(), MutableStateFlow(session), FixedHearts(signedIn))
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (arabic) BahrLocale.Arabic else BahrLocale.English, theme = theme) {
                    // The app's tab Scaffold sets onBackground as the content colour; a bare test root does not.
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            LikedTripsScreen(onBack = {}, onTripClick = {}, onSignIn = {}, viewModel = vm)
                        }
                    }
                }
            }
        }
        repeat(SETTLE_FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.mainClock.advanceTimeBy(BahrMotion.Long.toLong())
        compose.waitForIdle()
        repeat(SETTLE_FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    private fun cards(arabic: Boolean) =
        listOf(
            trip(
                1,
                title = if (arabic) "الفجر على بحيرة البرلس" else "Dawn on Lake Burullus",
                badge = BadgeDto(label = if (arabic) "الأكثر حجزًا" else "Most booked", tone = "primary"),
                nextDeparture = NextDepartureDto(FRIDAY, seatsRemaining = 6, capacity = 18, soldOut = false),
            ).copy(likeCount = 24, likedByMe = true),
            trip(
                2,
                title = if (arabic) "ليلتين في سيوة" else "Two nights in Siwa",
                priceEgp = 3200,
                durationLabel = if (arabic) "3 أيام · ليلتين" else "3 days · 2 nights",
                nights = 2,
                badge = BadgeDto(label = if (arabic) "جديد" else "New", tone = "secondary"),
                nextDeparture = NextDepartureDto(SATURDAY, seatsRemaining = 2, capacity = 12, soldOut = false),
            ).copy(likeCount = 1, likedByMe = true),
        )

    /** Hearts as the store would have them; taps change nothing. */
    private class FixedHearts(
        signedIn: Boolean,
    ) : TripHearts {
        override val likes: StateFlow<TripLikes> = MutableStateFlow(TripLikes(signedIn = signedIn))

        override fun toggle(
            slug: String,
            shown: LikeState,
        ) = false

        override fun seen(
            slug: String,
            likeCount: Int,
            likedByMe: Boolean?,
        ) = Unit
    }

    private companion object {
        const val SETTLE_FRAMES = 2
        val FRIDAY = LocalDate(2026, 10, 16)
        val SATURDAY = LocalDate(2026, 10, 17)
    }
}
