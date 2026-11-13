package eg.bahr.core.catalog.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import eg.bahr.core.catalog.data.LikeState
import eg.bahr.core.catalog.model.BadgeDto
import eg.bahr.core.catalog.model.NextDepartureDto
import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.BahrThemeName
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.network.MoneyDto
import eg.bahr.core.testing.captureScreenshot
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * MP-M1: the trip card's heart × {ar, en} × {light, dark}: liked with a count, not liked with a count, and not liked
 * with none (the heart alone, in its circle) on a multi-day trip, whose nights badge stacks under the trip's badge at
 * the top start, the top end being the heart's. The middle card is a Home row's compact card.
 *
 * Record: `./gradlew :core:catalog:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h1100dp-xhdpi")
class TripCardHeartScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun heartsArabic() = snap(AppLanguage.ARABIC)

    @Test
    fun heartsEnglish() = snap(AppLanguage.ENGLISH)

    @Test
    fun heartsDarkArabic() = snap(AppLanguage.ARABIC, BahrThemeName.Dark)

    @Test
    fun heartsDarkEnglish() = snap(AppLanguage.ENGLISH, BahrThemeName.Dark)

    private fun snap(
        language: AppLanguage,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
    ) {
        val arabic = language == AppLanguage.ARABIC
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (arabic) BahrLocale.Arabic else BahrLocale.English, theme = theme) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .verticalScroll(rememberScrollState())
                                .padding(BahrSpacing.gutter),
                        verticalArrangement = Arrangement.spacedBy(BahrSpacing.lg),
                    ) {
                        val perPerson = if (arabic) "للفرد" else "per person"
                        TripCard(card(arabic, 1), perPerson, onClick = {}, like = LikeState(liked = true, count = 24))
                        TripCard(card(arabic, 2), perPerson, onClick = {}, compact = true, like = LikeState(liked = false, count = 3))
                        TripCard(card(arabic, 3, nights = 1), perPerson, onClick = {}, like = LikeState(liked = false, count = 0))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.captureScreenshot("trip_card_hearts${if (theme == BahrThemeName.Dark) "_dark" else ""}_${language.tag}")
    }

    private fun card(
        arabic: Boolean,
        n: Int,
        nights: Int = 0,
    ) = TripCardDto(
        slug = "trip-$n",
        title = if (arabic) "الفجر على بحيرة البرلس" else "Dawn on Lake Burullus",
        durationLabel = if (nights > 0) (if (arabic) "يومين · ليلة" else "2 days · 1 night") else "05:00 → 22:00",
        nights = nights,
        price = MoneyDto(amount = 450, currencyCode = "EGP"),
        badge = BadgeDto(label = if (arabic) "الأكثر حجزًا" else "Most booked", tone = "primary"),
        nextDeparture = NextDepartureDto(LocalDate(2026, 10, 16), seatsRemaining = 6, capacity = 18, soldOut = false),
    )
}
