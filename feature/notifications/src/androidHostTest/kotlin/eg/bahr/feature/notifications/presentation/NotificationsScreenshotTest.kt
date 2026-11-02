package eg.bahr.feature.notifications.presentation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.components.BAHR_LOTTIE_READY_TAG
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.BahrThemeName
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.captureScreenshot
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

/**
 * The notifications page (M3-M6, account handoff §3; pushed from Home's bell with a Back since M3-M6a) × {ar, en}:
 * loading (the 7-row skeleton), empty (the bell, shot still under reduced motion), an unread + read mix in Today and
 * Earlier (light and dark: the handoff designs the list in both), the error state, and the guest inbox signed out
 * (no sign-in prompt since M3-B12), plus the stored sign-in not read yet.
 *
 * Record: `./gradlew :feature:notifications:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class NotificationsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val signedIn = SessionState.SignedIn("Sara", profileComplete = true, providers = setOf("google"))
    private val loaded = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
    private val loading = LoadStates(LoadState.Loading, LoadState.NotLoading(false), LoadState.NotLoading(false))
    private val failed = LoadStates(LoadState.Error(Exception("offline")), LoadState.NotLoading(false), LoadState.NotLoading(false))

    @Test
    fun loadingArabic() = shoot("alerts_loading", AppLanguage.ARABIC, loadStates = loading)

    @Test
    fun loadingEnglish() = shoot("alerts_loading", AppLanguage.ENGLISH, loadStates = loading)

    @Test
    fun emptyArabic() = shoot("alerts_empty", AppLanguage.ARABIC)

    @Test
    fun emptyEnglish() = shoot("alerts_empty", AppLanguage.ENGLISH)

    @Test
    fun emptyDarkArabic() = shoot("alerts_empty_dark", AppLanguage.ARABIC, theme = BahrThemeName.Dark)

    @Test
    fun emptyDarkEnglish() = shoot("alerts_empty_dark", AppLanguage.ENGLISH, theme = BahrThemeName.Dark)

    @Test
    fun listArabic() = shoot("alerts_list", AppLanguage.ARABIC, entries = mix(AppLanguage.ARABIC))

    @Test
    fun listEnglish() = shoot("alerts_list", AppLanguage.ENGLISH, entries = mix(AppLanguage.ENGLISH))

    @Test
    fun listDarkArabic() = shoot("alerts_list_dark", AppLanguage.ARABIC, entries = mix(AppLanguage.ARABIC), theme = BahrThemeName.Dark)

    @Test
    fun listDarkEnglish() = shoot("alerts_list_dark", AppLanguage.ENGLISH, entries = mix(AppLanguage.ENGLISH), theme = BahrThemeName.Dark)

    @Test
    fun errorArabic() = shoot("alerts_error", AppLanguage.ARABIC, loadStates = failed)

    @Test
    fun errorEnglish() = shoot("alerts_error", AppLanguage.ENGLISH, loadStates = failed)

    /** Signed out (M3-B12): this install's guest inbox, a broadcast and a place opened; no sign-in prompt. */
    @Test
    fun signedOutArabic() = shoot("alerts_guest", AppLanguage.ARABIC, SessionState.SignedOut, guest(AppLanguage.ARABIC))

    @Test
    fun signedOutEnglish() = shoot("alerts_guest", AppLanguage.ENGLISH, SessionState.SignedOut, guest(AppLanguage.ENGLISH))

    /** The stored sign-in not read yet: the bar only, so no state flashes. */
    @Test
    fun unknownArabic() = shoot("alerts_unknown", AppLanguage.ARABIC, SessionState.Unknown)

    @Test
    fun unknownEnglish() = shoot("alerts_unknown", AppLanguage.ENGLISH, SessionState.Unknown)

    private fun shoot(
        prefix: String,
        language: AppLanguage,
        session: SessionState = signedIn,
        entries: List<InboxEntry> = emptyList(),
        loadStates: LoadStates = loaded,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
    ) {
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(
                    locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English,
                    theme = theme,
                    reducedMotion = true,
                ) {
                    NotificationsContent(
                        state = NotificationsUiState(session = session, now = NOW),
                        items = flowOf(PagingData.from(entries, loadStates)).collectAsLazyPagingItems(),
                        actions = NotificationsActions(onBack = {}),
                    )
                }
            }
        }
        // The empty state's bell is read off the main thread: idle alone would shoot its empty box.
        if (session != SessionState.Unknown && entries.isEmpty() && loadStates == loaded) {
            compose.waitUntil(
                LOTTIE_LOAD_TIMEOUT_MS,
            ) { compose.onAllNodesWithTag(BAHR_LOTTIE_READY_TAG).fetchSemanticsNodes().isNotEmpty() }
        }
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    /** The prototype's list: Today (three unread, one read), Earlier (read). Server-rendered copy, per language. */
    private fun mix(language: AppLanguage): List<InboxEntry> {
        val ar = language == AppLanguage.ARABIC
        return listOf(
            InboxEntry.Header(InboxGroup.Today),
            row(
                "hold",
                AlertKind.HoldExpiring,
                pick(
                    ar,
                    "حجزك المؤقت هيخلص بعد 4 دقايق",
                    "Your seat hold ends in 4 min",
                ),
                pick(
                    ar,
                    "كياك في قنوات البوص · 2 أفراد · كمّل الدفع قبل ما الأماكن تتساب",
                    "Kayak through the reed channels · 2 people · pay before the seats are released",
                ),
                minutesAgo = 0,
                read = false,
            ),
            row(
                "seat",
                AlertKind.SeatOpened,
                pick(
                    ar,
                    "فضي مكان في رحلة مستنيها",
                    "A seat opened on a trip you're waiting for",
                ),
                pick(
                    ar,
                    "فخار قرية تونس وغروب بحيرة قارون · أول واحد يحجز ياخده",
                    "Tunis village pottery & Qarun sunset · first to book gets it",
                ),
                minutesAgo = 12,
                read = false,
            ),
            row(
                "host",
                AlertKind.HostMessage,
                pick(
                    ar,
                    "رسالة من أشرف البحر",
                    "Message from Ashraf El Bahr",
                ),
                pick(
                    ar,
                    "الأتوبيس هيتحرك 05:00 بالظبط، ياريت تكونوا موجودين 04:45",
                    "The bus leaves at 05:00 sharp, please be there by 04:45",
                ),
                minutesAgo = 60,
                read = false,
            ),
            row(
                "ok",
                AlertKind.BookingConfirmed,
                pick(
                    ar,
                    "اتأكد حجزك · BHR-7K4Q",
                    "Booking confirmed · BHR-7K4Q",
                ),
                pick(
                    ar,
                    "الفجر على بحيرة البرلس · السبت 17 أكتوبر · 2 أفراد",
                    "Dawn on Lake Burullus · Sat 17 Oct · 2 people",
                ),
                minutesAgo = 180,
                read = true,
            ),
            InboxEntry.Header(InboxGroup.Earlier),
            row(
                "remind",
                AlertKind.TripReminder,
                pick(
                    ar,
                    "رحلتك بكرة",
                    "Your trip is tomorrow",
                ),
                pick(
                    ar,
                    "صيد البوري بالليل · التجمع 15:30 في عبد المنعم رياض",
                    "Night mullet fishing · meet 15:30 at Abdel Moneim Riad",
                ),
                minutesAgo = 12 * DAY,
                read = true,
            ),
            row(
                "revok",
                AlertKind.ReviewApproved,
                pick(
                    ar,
                    "تقييمك اتنشر",
                    "Your review is live",
                ),
                pick(
                    ar,
                    "صباحية سوق السمك في البوغاز · شكرًا إنك كتبت رأيك",
                    "Boughaz fish market morning · thanks for writing it",
                ),
                minutesAgo = 5 * DAY,
                read = true,
            ),
            row(
                "revno",
                AlertKind.ReviewRejected,
                pick(
                    ar,
                    "تقييمك ما اتنشرش",
                    "Your review wasn't published",
                ),
                pick(
                    ar,
                    "صيد البوري بالليل · فيه رقم تليفون في النص، عدّله وابعته تاني",
                    "Night mullet fishing · it included a phone number, edit and resend",
                ),
                minutesAgo = 6 * DAY,
                read = true,
            ),
        )
    }

    private fun guest(language: AppLanguage): List<InboxEntry> {
        val ar = language == AppLanguage.ARABIC
        return listOf(
            InboxEntry.Header(InboxGroup.Today),
            row(
                "seat",
                AlertKind.SeatOpened,
                pick(
                    ar,
                    "فضي مكان في رحلة مستنيها",
                    "A seat opened on a trip you're waiting for",
                ),
                pick(
                    ar,
                    "الفجر على بحيرة البرلس · أول واحد يحجز ياخده",
                    "Dawn on Lake Burullus · first to book gets it",
                ),
                minutesAgo = 25,
                read = false,
            ),
            InboxEntry.Header(InboxGroup.Earlier),
            row(
                "news",
                AlertKind.Broadcast,
                pick(
                    ar,
                    "رحلات الشتا نزلت",
                    "Winter trips are out",
                ),
                pick(
                    ar,
                    "رحلات جديدة للفيوم وسيوة، احجز بدري",
                    "New trips to Fayoum and Siwa, book early",
                ),
                minutesAgo = 2 * DAY,
                read = true,
            ),
        )
    }

    private fun pick(
        ar: Boolean,
        arabic: String,
        english: String,
    ) = if (ar) arabic else english

    private fun row(
        id: String,
        kind: AlertKind,
        title: String,
        body: String,
        minutesAgo: Int,
        read: Boolean,
    ) = InboxEntry.Item(
        id = id,
        kind = kind,
        title = title,
        body = body,
        createdAt = Instant.fromEpochSeconds(NOW.epochSeconds - minutesAgo * SECONDS_PER_MINUTE),
        read = read,
        target = null,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-24T09:00:00Z")
        const val DAY = 24 * 60
        const val SECONDS_PER_MINUTE = 60L
        const val LOTTIE_LOAD_TIMEOUT_MS = 10_000L
    }
}
