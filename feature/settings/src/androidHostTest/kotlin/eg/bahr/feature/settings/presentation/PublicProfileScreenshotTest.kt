package eg.bahr.feature.settings.presentation

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.components.BAHR_LOTTIE_READY_TAG
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.BahrThemeName
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.settings.data.FakePublicProfileRepository.Companion.PUBLIC_ID
import eg.bahr.feature.settings.data.FakePublicProfileRepository.Companion.VISIBILITY
import eg.bahr.feature.settings.data.FakePublicProfileRepository.Companion.profile
import eg.bahr.feature.settings.data.FakePublicProfileRepository.Companion.review
import eg.bahr.feature.settings.model.ProfileVisibilityDto
import eg.bahr.feature.settings.model.TravellerProfileDto
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * MP-M2 × {ar, en}: a traveller's profile {every section (light and dark), some sections absent, every section on but
 * empty, nothing shared, private (404, and MP-M3's null id; light and dark), loading}, and Account → Public profile {private (defaults), public with
 * some switches on (light and dark), loading}. Then the settings' wiring: a row toggles its switch, the three waiting
 * for the master switch are disabled while private, reviews never are, and "View my public profile" hands over the id
 * only while public.
 *
 * Record: `./gradlew :feature:settings:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h1100dp-xhdpi")
class PublicProfileScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    // ---------- A traveller's profile ----------

    @Test
    fun fullArabic() = traveller("traveller_full", AppLanguage.ARABIC, loaded(profile(arabic = true)))

    @Test
    fun fullEnglish() = traveller("traveller_full", AppLanguage.ENGLISH, loaded(profile()))

    @Test
    fun fullDarkArabic() = traveller("traveller_full_dark", AppLanguage.ARABIC, loaded(profile(arabic = true)), BahrThemeName.Dark)

    @Test
    fun fullDarkEnglish() = traveller("traveller_full_dark", AppLanguage.ENGLISH, loaded(profile()), BahrThemeName.Dark)

    /** Past trips and likes switched off: only the reviews; a name with no profile picked ("A traveller") reads as sent. */
    @Test
    fun partialArabic() =
        traveller(
            "traveller_partial",
            AppLanguage.ARABIC,
            loaded(profile(arabic = true, pastTrips = null, likedTrips = null, name = "مسافر", reviews = listOf(review(true, "مسافر")))),
        )

    @Test
    fun partialEnglish() =
        traveller(
            "traveller_partial",
            AppLanguage.ENGLISH,
            loaded(profile(pastTrips = null, likedTrips = null, name = "A traveller", reviews = listOf(review(author = "A traveller")))),
        )

    @Test
    fun emptyArabic() =
        traveller("traveller_empty", AppLanguage.ARABIC, loaded(profile(arabic = true, emptyList(), emptyList(), emptyList())))

    @Test
    fun emptyEnglish() = traveller("traveller_empty", AppLanguage.ENGLISH, loaded(profile(false, emptyList(), emptyList(), emptyList())))

    @Test
    fun nothingSharedArabic() = traveller("traveller_nothing_shared", AppLanguage.ARABIC, loaded(profile(arabic = true, null, null, null)))

    @Test
    fun nothingSharedEnglish() = traveller("traveller_nothing_shared", AppLanguage.ENGLISH, loaded(profile(false, null, null, null)))

    // MP-M3: the 404 page and a reviewer with no public id are one page, "This account is private". Drawn here through
    // the screen with a null id: no Koin is started in this class, so a ViewModel (and with it a request) would crash.
    @Test
    fun notPublicArabic() = privateTraveller("traveller_not_public", AppLanguage.ARABIC)

    @Test
    fun notPublicEnglish() = privateTraveller("traveller_not_public", AppLanguage.ENGLISH)

    @Test
    fun notPublicDarkArabic() = privateTraveller("traveller_not_public_dark", AppLanguage.ARABIC, BahrThemeName.Dark)

    @Test
    fun notPublicDarkEnglish() = privateTraveller("traveller_not_public_dark", AppLanguage.ENGLISH, BahrThemeName.Dark)

    /** Reduce motion: the still avatar-and-padlock picture in place of the cat. */
    @Test
    fun notPublicReducedMotionArabic() = privateTraveller("traveller_not_public_reduced_motion", AppLanguage.ARABIC, reducedMotion = true)

    @Test
    fun notPublicReducedMotionEnglish() = privateTraveller("traveller_not_public_reduced_motion", AppLanguage.ENGLISH, reducedMotion = true)

    @Test
    fun `a null id is the private page at once - with back - and the 404 state draws the same`() {
        var backs = 0
        compose.setContent {
            // Reduce motion: the cat loops forever, which a test that waits for idle would wait out.
            Themed(AppLanguage.ENGLISH, reducedMotion = true) {
                TravellerProfileScreen(publicId = null, onBack = { backs++ }, onTripClick = {})
            }
        }
        compose.onNodeWithTag(TRAVELLER_NOT_PUBLIC_TAG).assertIsDisplayed()
        compose.onNodeWithText("This account is private.").assertIsDisplayed()
        compose.onNodeWithText("This traveller keeps their profile private.").assertIsDisplayed()
        compose.onNodeWithTag(TRAVELLER_PAGE_TAG).assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
        assertEquals(TravellerProfileUiState(isLoading = false, notAvailable = true), PRIVATE_TRAVELLER)
    }

    @Test
    fun loadingArabic() = traveller("traveller_loading", AppLanguage.ARABIC, TravellerProfileUiState())

    @Test
    fun loadingEnglish() = traveller("traveller_loading", AppLanguage.ENGLISH, TravellerProfileUiState())

    @Test
    fun `a trip card opens its trip`() {
        val opened = mutableListOf<String>()
        compose.setContent {
            Themed(
                AppLanguage.ENGLISH,
            ) { TravellerProfileContent(loaded(profile()), onBack = {}, onTripClick = { opened += it }, onRetry = {}) }
        }
        compose.onNodeWithTag("$TRAVELLER_RAIL_TAG_PREFIX$KEY_PAST").performClick()
        assertEquals(listOf("dawn-burullus"), opened)
    }

    // ---------- Account → Public profile ----------

    @Test
    fun settingsPrivateArabic() = settings("public_profile_private", AppLanguage.ARABIC, settled(VISIBILITY))

    @Test
    fun settingsPrivateEnglish() = settings("public_profile_private", AppLanguage.ENGLISH, settled(VISIBILITY))

    @Test
    fun settingsPublicArabic() = settings("public_profile_public", AppLanguage.ARABIC, settled(PUBLIC))

    @Test
    fun settingsPublicEnglish() = settings("public_profile_public", AppLanguage.ENGLISH, settled(PUBLIC))

    @Test
    fun settingsPublicDarkArabic() = settings("public_profile_public_dark", AppLanguage.ARABIC, settled(PUBLIC), BahrThemeName.Dark)

    @Test
    fun settingsPublicDarkEnglish() = settings("public_profile_public_dark", AppLanguage.ENGLISH, settled(PUBLIC), BahrThemeName.Dark)

    @Test
    fun settingsLoadingArabic() = settings("public_profile_loading", AppLanguage.ARABIC, PublicProfileSettingsUiState())

    @Test
    fun settingsLoadingEnglish() = settings("public_profile_loading", AppLanguage.ENGLISH, PublicProfileSettingsUiState())

    @Test
    fun `private - the master and reviews toggle, the rest and View wait`() {
        val toggles = mutableListOf<Pair<ProfileSwitch, Boolean>>()
        val views = mutableListOf<String>()
        compose.setContent {
            Themed(AppLanguage.ENGLISH) {
                PublicProfileSettingsContent(
                    settled(VISIBILITY),
                    PublicProfileActions(onToggle = { s, on -> toggles += s to on }, onViewProfile = { views += it }),
                )
            }
        }
        compose
            .onNodeWithTag(switchTag(ProfileSwitch.Public))
            .assertIsOff()
            .assertIsEnabled()
            .performClick()
        compose
            .onNodeWithTag(switchTag(ProfileSwitch.Reviews))
            .assertIsOn()
            .assertIsEnabled()
            .performClick()
        compose.onNodeWithTag(switchTag(ProfileSwitch.PastTrips)).assertIsNotEnabled()
        compose.onNodeWithTag(switchTag(ProfileSwitch.Likes)).assertIsNotEnabled()
        compose.onNodeWithTag(switchTag(ProfileSwitch.FullNameAndPhoto)).assertIsNotEnabled()
        compose.onNodeWithTag(PUBLIC_PROFILE_VIEW_TAG).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(PUBLIC_PROFILE_PRIVATE_NOTE_TAG).assertExists()

        assertEquals(listOf(ProfileSwitch.Public to true, ProfileSwitch.Reviews to false), toggles)
        assertEquals(emptyList(), views)
    }

    @Test
    fun `public - View opens the profile by its id`() {
        val views = mutableListOf<String>()
        compose.setContent {
            Themed(AppLanguage.ENGLISH) {
                PublicProfileSettingsContent(settled(PUBLIC), PublicProfileActions(onViewProfile = { views += it }))
            }
        }
        compose.onNodeWithTag(switchTag(ProfileSwitch.Likes)).assertIsEnabled()
        compose.onNodeWithTag(PUBLIC_PROFILE_VIEW_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(PUBLIC_PROFILE_PRIVATE_NOTE_TAG).assertDoesNotExist()
        assertEquals(listOf(PUBLIC_ID), views)
    }

    // ---------- Plumbing ----------

    private fun loaded(profile: TravellerProfileDto) = TravellerProfileUiState(profile = profile, isLoading = false)

    private fun settled(visibility: ProfileVisibilityDto) =
        PublicProfileSettingsUiState(shown = visibility, confirmed = visibility, isLoading = false)

    private fun traveller(
        prefix: String,
        language: AppLanguage,
        state: TravellerProfileUiState,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
    ) {
        compose.setContent {
            Themed(language, theme) { TravellerProfileContent(state, onBack = {}, onTripClick = {}, onRetry = {}) }
        }
        // The skeleton's shimmer must not move between runs.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    private fun privateTraveller(
        prefix: String,
        language: AppLanguage,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
        reducedMotion: Boolean = false,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Themed(language, theme, reducedMotion) { TravellerProfileScreen(publicId = null, onBack = {}, onTripClick = {}) }
        }
        if (!reducedMotion) {
            // The cat's file is read off the main thread; with the clock paused, frames carry it in. It is shot on the
            // frame it first draws, the loop's start, so every run takes the same pose.
            compose.waitUntil(LOTTIE_LOAD_TIMEOUT_MS) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithTag(BAHR_LOTTIE_READY_TAG).fetchSemanticsNodes().isNotEmpty()
            }
        } else {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithTag(BAHR_LOTTIE_READY_TAG).assertCountEquals(0)
        }
        compose.waitForIdle()
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    private fun settings(
        prefix: String,
        language: AppLanguage,
        state: PublicProfileSettingsUiState,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
    ) {
        compose.setContent {
            Themed(language, theme) { PublicProfileSettingsContent(state, PublicProfileActions()) }
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    @Composable
    private fun Themed(
        language: AppLanguage,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
        reducedMotion: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        ProvideAppLanguage(language) {
            BahrTheme(
                locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English,
                theme = theme,
                reducedMotion = reducedMotion,
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides
                        if (theme == BahrThemeName.Dark) MaterialTheme.colorScheme.onBackground else LocalContentColor.current,
                ) { content() }
            }
        }
    }

    private companion object {
        val PUBLIC = VISIBILITY.copy(isPublic = true, showPastTrips = true, showLikes = false)
    }
}

private const val LOTTIE_LOAD_TIMEOUT_MS = 10_000L
