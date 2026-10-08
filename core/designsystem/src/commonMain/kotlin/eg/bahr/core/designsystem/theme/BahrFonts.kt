package eg.bahr.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import eg.bahr.core.designsystem.generated.resources.Res
import eg.bahr.core.designsystem.generated.resources.ibm_plex_sans_arabic_bold
import eg.bahr.core.designsystem.generated.resources.ibm_plex_sans_arabic_medium
import eg.bahr.core.designsystem.generated.resources.ibm_plex_sans_arabic_regular
import eg.bahr.core.designsystem.generated.resources.ibm_plex_sans_arabic_semibold
import eg.bahr.core.designsystem.generated.resources.manrope_bold
import eg.bahr.core.designsystem.generated.resources.manrope_extrabold
import eg.bahr.core.designsystem.generated.resources.manrope_medium
import eg.bahr.core.designsystem.generated.resources.manrope_regular
import eg.bahr.core.designsystem.generated.resources.manrope_semibold
import org.jetbrains.compose.resources.Font

/*
 * The only file that knows which font files exist. Everything else asks [bahrFontFamily].
 *
 * Files live in `composeResources/font/` (lower_snake_case), both SIL OFL, byte-identical to
 * upstream (sources and hashes in docs/design-language.md, licence texts in
 * `composeResources/files/licenses/`):
 *   manrope_{regular,medium,semibold,bold,extrabold}.ttf          — ref.typeface.plain
 *   ibm_plex_sans_arabic_{regular,medium,semibold,bold}.ttf       — ref.typeface.arabic
 *
 * Static weight cuts, not variable fonts: minSdk is 24 and Android honours variable weight axes
 * only from API 26.
 */

@Composable
internal fun manropeFamily(): FontFamily =
    FontFamily(
        Font(Res.font.manrope_regular, FontWeight.Normal),
        Font(Res.font.manrope_medium, FontWeight.Medium),
        Font(Res.font.manrope_semibold, FontWeight.SemiBold),
        Font(Res.font.manrope_bold, FontWeight.Bold),
        Font(Res.font.manrope_extrabold, FontWeight.ExtraBold),
    )

/**
 * IBM Plex Sans Arabic stops at Bold (700). The Arabic type rule asks for one weight heavier at
 * display sizes (800 → 900), so ExtraBold and Black are mapped to the Bold cut explicitly: the
 * heaviest real Arabic weight, rather than whatever the platform's font matcher picks.
 *
 * So the "one weight heavier" rule cannot show in Arabic: every style it bumps (display,
 * headline, titleLarge) already asks for 700 or more, and all of them land on this Bold cut. That
 * is a limit of the typeface, not a bug; no heavier OFL cut of Plex Arabic exists.
 */
@Composable
internal fun plexArabicFamily(): FontFamily =
    FontFamily(
        Font(Res.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
        Font(Res.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
        Font(Res.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
        Font(Res.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
        Font(Res.font.ibm_plex_sans_arabic_bold, FontWeight.ExtraBold),
        Font(Res.font.ibm_plex_sans_arabic_bold, FontWeight.Black),
    )

/** Plex Arabic in Arabic, Manrope otherwise. The one switch between the two scripts. */
@Composable
internal fun bahrFontFamily(locale: BahrLocale): FontFamily = if (locale.isArabic) plexArabicFamily() else manropeFamily()
