package eg.bahr.feature.hostworkspace.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.BahrThemeName
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.hostworkspace.data.sampleDeparture
import eg.bahr.feature.hostworkspace.data.sampleDepartures
import eg.bahr.feature.hostworkspace.data.sampleMe
import eg.bahr.feature.hostworkspace.data.sampleTravellers
import eg.bahr.feature.hostworkspace.model.HostBroadcastDto
import eg.bahr.feature.hostworkspace.model.HostDepartureStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

/**
 * M5-M1, host mode × {ar, en} (× dark for the main screens): the sign-in (and refused), the forced password change, the
 * departure picker, the host's profile, the manifest (stat tiles, seat bar with held seats, who's on the bus, the
 * sticky broadcast; a "check the list" notice; a called-off date), the broadcast sheet (write, then confirm refused as
 * too soon), the manual booking form (filled, then the key reused), mark paid, suspended and not available. Drawn from
 * the stateless content, the sheets without their window.
 *
 * Record: `./gradlew :feature:host-workspace:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class HostWorkspaceScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val signedIn = SessionState.SignedIn(accountName = null, profileComplete = true, providers = emptySet())

    // ---------- sign-in ----------

    private val refused = HostSignInUiState(email = "ashraf@example.com", problem = HostSignInProblem.Refused)
    private val changing =
        HostSignInUiState(
            step = HostSignInStep.ChangePassword,
            email = "ashraf@example.com",
            newPassword = "short",
            confirmPassword = "short",
            checked = true,
        )

    @Test fun signInAr() = shoot("host_sign_in", AppLanguage.ARABIC) { HostSignInContent(HostSignInUiState(), HostSignInActions()) }

    @Test fun signInEn() = shoot("host_sign_in", AppLanguage.ENGLISH) { HostSignInContent(HostSignInUiState(), HostSignInActions()) }

    @Test fun signInDarkAr() =
        shoot(
            "host_sign_in_dark",
            AppLanguage.ARABIC,
            dark = true,
        ) { HostSignInContent(refused, HostSignInActions()) }

    @Test fun signInDarkEn() =
        shoot(
            "host_sign_in_dark",
            AppLanguage.ENGLISH,
            dark = true,
        ) { HostSignInContent(refused, HostSignInActions()) }

    @Test fun signInRefusedAr() = shoot("host_sign_in_refused", AppLanguage.ARABIC) { HostSignInContent(refused, HostSignInActions()) }

    @Test fun signInRefusedEn() = shoot("host_sign_in_refused", AppLanguage.ENGLISH) { HostSignInContent(refused, HostSignInActions()) }

    @Test fun changeAr() = shoot("host_change_password", AppLanguage.ARABIC) { HostSignInContent(changing, HostSignInActions()) }

    @Test fun changeEn() = shoot("host_change_password", AppLanguage.ENGLISH) { HostSignInContent(changing, HostSignInActions()) }

    @Test fun changeDarkAr() =
        shoot("host_change_password_dark", AppLanguage.ARABIC, dark = true) {
            HostSignInContent(changing, HostSignInActions())
        }

    @Test fun changeDarkEn() =
        shoot("host_change_password_dark", AppLanguage.ENGLISH, dark = true) {
            HostSignInContent(changing, HostSignInActions())
        }

    // ---------- the shell ----------

    private fun home(
        access: HostAccess = HostAccess.Ready(sampleMe()),
        departures: HostDepartures = HostDepartures.Loaded(sampleDepartures()),
    ) = HostHomeUiState(session = signedIn, access = access, departures = departures)

    @Test fun departuresAr() = shoot("host_departures", AppLanguage.ARABIC) { Home(home(), HostTab.Departures) }

    @Test fun departuresEn() = shoot("host_departures", AppLanguage.ENGLISH) { Home(home(), HostTab.Departures) }

    @Test fun departuresDarkAr() = shoot("host_departures_dark", AppLanguage.ARABIC, dark = true) { Home(home(), HostTab.Departures) }

    @Test fun departuresDarkEn() = shoot("host_departures_dark", AppLanguage.ENGLISH, dark = true) { Home(home(), HostTab.Departures) }

    @Test fun departuresLoadingAr() =
        shoot("host_departures_loading", AppLanguage.ARABIC) {
            Home(home(departures = HostDepartures.Loading), HostTab.Departures)
        }

    @Test fun departuresEmptyEn() =
        shoot("host_departures_empty", AppLanguage.ENGLISH) {
            Home(home(departures = HostDepartures.Loaded(emptyList())), HostTab.Departures)
        }

    @Test fun profileAr() = shoot("host_profile", AppLanguage.ARABIC) { Home(home(), HostTab.Profile) }

    @Test fun profileEn() =
        shoot("host_profile", AppLanguage.ENGLISH) {
            Home(home(access = HostAccess.Ready(sampleMe(name = "Ashraf El Bahr"))), HostTab.Profile)
        }

    @Test fun suspendedAr() = shoot("host_suspended", AppLanguage.ARABIC) { Home(home(access = HostAccess.Suspended), HostTab.Departures) }

    @Test fun suspendedEn() = shoot("host_suspended", AppLanguage.ENGLISH) { Home(home(access = HostAccess.Suspended), HostTab.Departures) }

    @Test fun suspendedDarkAr() =
        shoot("host_suspended_dark", AppLanguage.ARABIC, dark = true) {
            Home(home(access = HostAccess.Suspended), HostTab.Departures)
        }

    @Test fun suspendedDarkEn() =
        shoot("host_suspended_dark", AppLanguage.ENGLISH, dark = true) {
            Home(home(access = HostAccess.Suspended), HostTab.Departures)
        }

    // ---------- the manifest ----------

    private val lastBroadcast =
        HostBroadcastDto(lastSentAt = "2026-11-19T19:12:00+02:00", recipients = 9, nextAllowedAt = "2026-11-19T19:42:00+02:00")
    private val loaded = HostDepartureUiState(load = ManifestLoad.Loaded(sampleDeparture(broadcast = lastBroadcast)))
    private val checkList = loaded.copy(notice = ManifestNotice.CheckList)
    private val cancelled =
        HostDepartureUiState(load = ManifestLoad.Loaded(sampleDeparture(status = HostDepartureStatus.CANCELLED, seatsHeld = 0)))

    @Test fun manifestAr() = shoot("host_manifest", AppLanguage.ARABIC) { HostDepartureContent(loaded, HostDepartureActions()) }

    @Test fun manifestEn() = shoot("host_manifest", AppLanguage.ENGLISH) { HostDepartureContent(loaded, HostDepartureActions()) }

    @Test fun manifestDarkAr() =
        shoot("host_manifest_dark", AppLanguage.ARABIC, dark = true) {
            HostDepartureContent(loaded, HostDepartureActions())
        }

    @Test fun manifestDarkEn() =
        shoot("host_manifest_dark", AppLanguage.ENGLISH, dark = true) {
            HostDepartureContent(loaded, HostDepartureActions())
        }

    @Test fun manifestCheckListAr() =
        shoot("host_manifest_check_list", AppLanguage.ARABIC) {
            HostDepartureContent(checkList, HostDepartureActions())
        }

    @Test fun manifestCheckListEn() =
        shoot("host_manifest_check_list", AppLanguage.ENGLISH) {
            HostDepartureContent(checkList, HostDepartureActions())
        }

    @Test fun manifestCancelledAr() =
        shoot("host_manifest_cancelled", AppLanguage.ARABIC) {
            HostDepartureContent(cancelled, HostDepartureActions())
        }

    @Test fun manifestCancelledEn() =
        shoot("host_manifest_cancelled", AppLanguage.ENGLISH) {
            HostDepartureContent(cancelled, HostDepartureActions())
        }

    @Test
    fun manifestLockedAr() =
        shoot("host_manifest_locked", AppLanguage.ARABIC) {
            HostDepartureContent(loaded.copy(broadcastLockedUntil = Instant.parse("2026-11-19T17:42:00Z")), HostDepartureActions())
        }

    @Test fun manifestLoadingEn() =
        shoot("host_manifest_loading", AppLanguage.ENGLISH) {
            HostDepartureContent(HostDepartureUiState(), HostDepartureActions())
        }

    @Test
    fun manifestFailedAr() =
        shoot("host_manifest_failed", AppLanguage.ARABIC) {
            HostDepartureContent(HostDepartureUiState(load = ManifestLoad.Failed(AppError.Network)), HostDepartureActions())
        }

    @Test fun notAvailableAr() =
        shoot("host_not_available", AppLanguage.ARABIC) {
            HostDepartureContent(HostDepartureUiState(load = ManifestLoad.NotAvailable), HostDepartureActions())
        }

    @Test fun notAvailableEn() =
        shoot("host_not_available", AppLanguage.ENGLISH) {
            HostDepartureContent(HostDepartureUiState(load = ManifestLoad.NotAvailable), HostDepartureActions())
        }

    // ---------- the sheets ----------

    private val writing = BroadcastForm(message = "صباح الخير! الأتوبيس هيتحرك 5 الصبح بالظبط من عبد المنعم رياض. متتأخروش.")
    private val tooSoon =
        writing.copy(step = BroadcastStep.Confirm, problem = BroadcastProblem.TooSoon(Instant.parse("2026-11-19T17:42:00Z")))

    @Test fun broadcastAr() =
        shoot("host_broadcast", AppLanguage.ARABIC) {
            BroadcastSheet(writing, "السبت 21 نوفمبر", 4, BroadcastActions(), inSheet = false)
        }

    @Test fun broadcastEn() =
        shoot("host_broadcast", AppLanguage.ENGLISH) {
            BroadcastSheet(
                writing.copy(message = "Morning! The bus leaves at 05:00 sharp."),
                "Sat 21 Nov",
                4,
                BroadcastActions(),
                inSheet = false,
            )
        }

    @Test fun broadcastDarkAr() =
        shoot("host_broadcast_dark", AppLanguage.ARABIC, dark = true) {
            BroadcastSheet(writing, "السبت 21 نوفمبر", 4, BroadcastActions(), inSheet = false)
        }

    @Test fun broadcastDarkEn() =
        shoot("host_broadcast_dark", AppLanguage.ENGLISH, dark = true) {
            BroadcastSheet(writing, "Sat 21 Nov", 4, BroadcastActions(), inSheet = false)
        }

    @Test fun broadcastTooSoonAr() =
        shoot("host_broadcast_too_soon", AppLanguage.ARABIC) {
            BroadcastSheet(tooSoon, "السبت 21 نوفمبر", 4, BroadcastActions(), inSheet = false)
        }

    @Test fun broadcastTooSoonEn() =
        shoot("host_broadcast_too_soon", AppLanguage.ENGLISH) {
            BroadcastSheet(
                tooSoon.copy(message = "Morning! The bus leaves at 05:00 sharp."),
                "Sat 21 Nov",
                4,
                BroadcastActions(),
                inSheet = false,
            )
        }

    private val filled = ManualForm(name = "كريم السيد", phone = "٠١٠ ٤٤١٢ ٧٧٣٩", partySize = 3, paid = true, locale = "ar")
    private val reused = filled.copy(step = ManualStep.Review, checked = true, problem = ManualProblem.KeyReused, uncertain = true)
    private val badFields = ManualForm(name = "", phone = "12", locale = "en", checked = true)

    @Test fun manualAr() =
        shoot("host_manual_booking", AppLanguage.ARABIC) {
            ManualBookingSheet(filled, "السبت 21 نوفمبر", ManualActions(), inSheet = false)
        }

    @Test fun manualEn() =
        shoot("host_manual_booking", AppLanguage.ENGLISH) {
            ManualBookingSheet(filled.copy(name = "Karim El Sayed", locale = "en"), "Sat 21 Nov", ManualActions(), inSheet = false)
        }

    @Test fun manualDarkAr() =
        shoot("host_manual_booking_dark", AppLanguage.ARABIC, dark = true) {
            ManualBookingSheet(filled, "السبت 21 نوفمبر", ManualActions(), inSheet = false)
        }

    @Test fun manualDarkEn() =
        shoot("host_manual_booking_dark", AppLanguage.ENGLISH, dark = true) {
            ManualBookingSheet(filled.copy(name = "Karim El Sayed"), "Sat 21 Nov", ManualActions(), inSheet = false)
        }

    @Test fun manualErrorsEn() =
        shoot("host_manual_booking_errors", AppLanguage.ENGLISH) {
            ManualBookingSheet(badFields, "Sat 21 Nov", ManualActions(), inSheet = false)
        }

    @Test fun manualKeyReusedAr() =
        shoot("host_manual_booking_key_reused", AppLanguage.ARABIC) {
            ManualBookingSheet(reused, "السبت 21 نوفمبر", ManualActions(), inSheet = false)
        }

    @Test fun manualKeyReusedEn() =
        shoot("host_manual_booking_key_reused", AppLanguage.ENGLISH) {
            ManualBookingSheet(reused.copy(name = "Karim El Sayed"), "Sat 21 Nov", ManualActions(), inSheet = false)
        }

    @Test
    fun manualRetrySafeEn() =
        shoot("host_manual_booking_retry_safe", AppLanguage.ENGLISH) {
            ManualBookingSheet(
                filled.copy(name = "Karim El Sayed", step = ManualStep.Review, problem = ManualProblem.RetrySafe),
                "Sat 21 Nov",
                ManualActions(),
                inSheet = false,
            )
        }

    private val markPaid = sampleTravellers()[1].let { MarkPaidForm(bookingId = it.id!!, name = it.name, ref = it.ref) }

    @Test fun markPaidAr() = shoot("host_mark_paid", AppLanguage.ARABIC) { MarkPaidSheet(markPaid, MarkPaidActions(), inSheet = false) }

    @Test
    fun markPaidEn() =
        shoot("host_mark_paid", AppLanguage.ENGLISH) {
            MarkPaidSheet(
                markPaid.copy(name = "Mostafa Ragab", error = AppError.Api(ApiErrorCodes.CONFLICT, "x", 409)),
                MarkPaidActions(),
                inSheet = false,
            )
        }

    // ---------- the other language (and dark) of each edge state ----------

    @Test fun departuresLoadingEn() =
        shoot("host_departures_loading", AppLanguage.ENGLISH) {
            Home(home(departures = HostDepartures.Loading), HostTab.Departures)
        }

    @Test fun departuresEmptyAr() =
        shoot("host_departures_empty", AppLanguage.ARABIC) {
            Home(home(departures = HostDepartures.Loaded(emptyList())), HostTab.Departures)
        }

    @Test fun manifestLoadingAr() =
        shoot("host_manifest_loading", AppLanguage.ARABIC) {
            HostDepartureContent(HostDepartureUiState(), HostDepartureActions())
        }

    @Test
    fun manifestFailedEn() =
        shoot("host_manifest_failed", AppLanguage.ENGLISH) {
            HostDepartureContent(HostDepartureUiState(load = ManifestLoad.Failed(AppError.Network)), HostDepartureActions())
        }

    @Test
    fun manifestLockedEn() =
        shoot("host_manifest_locked", AppLanguage.ENGLISH) {
            HostDepartureContent(loaded.copy(broadcastLockedUntil = Instant.parse("2026-11-19T17:42:00Z")), HostDepartureActions())
        }

    @Test
    fun notAvailableDarkAr() =
        shoot("host_not_available_dark", AppLanguage.ARABIC, dark = true) {
            HostDepartureContent(HostDepartureUiState(load = ManifestLoad.NotAvailable), HostDepartureActions())
        }

    @Test
    fun notAvailableDarkEn() =
        shoot("host_not_available_dark", AppLanguage.ENGLISH, dark = true) {
            HostDepartureContent(HostDepartureUiState(load = ManifestLoad.NotAvailable), HostDepartureActions())
        }

    @Test fun manualErrorsAr() =
        shoot("host_manual_booking_errors", AppLanguage.ARABIC) {
            ManualBookingSheet(badFields.copy(locale = "ar"), "السبت 21 نوفمبر", ManualActions(), inSheet = false)
        }

    @Test
    fun manualRetrySafeAr() =
        shoot("host_manual_booking_retry_safe", AppLanguage.ARABIC) {
            ManualBookingSheet(
                filled.copy(step = ManualStep.Review, problem = ManualProblem.RetrySafe),
                "السبت 21 نوفمبر",
                ManualActions(),
                inSheet = false,
            )
        }

    @Composable
    private fun Home(
        state: HostHomeUiState,
        tab: HostTab,
    ) = HostHomeContent(state, tab, HostHomeActions(), windowInsets = false)

    private fun shoot(
        prefix: String,
        language: AppLanguage,
        dark: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(
                    locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English,
                    theme = if (dark) BahrThemeName.Dark else BahrThemeName.LakeBurullus,
                ) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
                }
            }
        }
        compose.captureScreenshot("${prefix}_${language.tag}")
    }
}
