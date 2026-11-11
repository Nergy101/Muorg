package nl.muorg.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import nl.muorg.android.R

/**
 * Fixed dark palette for the onboarding screens (welcome, connect server,
 * local folders). Like the Orc scenes, these ignore the user's theme and
 * accent: first launch looks the same for everyone. The rest of the app stays
 * on [MuorgTheme].
 *
 * Mapped to existing tokens: [Moss] is [MuorgGreenLight] (#7aaa42 vs the
 * canvas #7DB544). Everything else is new: the brand teal-grey neutrals have
 * no counterpart in the warm `Stone*` scale.
 */
object OnboardingBrand {
    val Ink = Color(0xFF0B1418) // screen background
    val Hull = Color(0xFF15232A) // cards, inputs, back button
    val Groove = Color(0xFF23383F) // 1.5dp borders
    val GrooveRing = Color(0xFF12212A) // faint background rings
    val GrooveRingMid = Color(0xFF111F27)
    val GrooveRingOuter = Color(0xFF101D24)
    val Lip = Color(0xFF070D10) // card "lip" shadow
    val Text = Color(0xFFEAF4F1)
    val Mist = Color(0xFF9FB3B8) // secondary text, icons
    val Moss = MuorgGreenLight // primary button, local accent
    val MossLip = Color(0xFF3E6A1C) // primary button lip
    val Spotlight = Color(0xFF4FD6EE) // server accent, focus ring
    val Fang = Color(0xFFF4F1E6) // speech bubble
    val ServerStage = Color(0xFF10303A) // panel behind the server Orc
    val LocalStage = Color(0xFF1B2E19) // panel behind the local Orc
    val DisabledBg = Color(0xFF1F3139)
    val DashBorder = Color(0xFF2F4A54)
    val AddFolderLabel = Color(0xFFC8D6D9)
}

/**
 * Bricolage Grotesque (OFL), static instances cut from the Google Fonts
 * variable file: 700 at optical size 16 (row titles, bubble) and 800 at
 * optical size 28 (headlines, card titles, buttons) — the sizes each weight is
 * used at on these screens.
 */
val BricolageFamily = FontFamily(
    Font(R.font.bricolage_grotesque_bold, FontWeight.Bold),
    Font(R.font.bricolage_grotesque_extrabold, FontWeight.ExtraBold),
)

/** Text styles for the onboarding screens. Colour is applied at the call site. */
object OnboardingType {
    private fun display(size: Float, lineHeightEm: Float) = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = size.sp,
        lineHeight = (size * lineHeightEm).sp,
        letterSpacing = (-1).sp,
    )

    val Headline34 = display(34f, 1.05f)
    val Headline32 = display(32f, 1.05f)
    val Headline30 = display(30f, 1.08f)

    val CardTitle = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 21.sp,
        lineHeight = (21 * 1.1f).sp,
    )

    val Button = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
    )

    val RowTitle = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
    )

    val ActionLabel = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
    )

    val Bubble = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
    )

    private fun body(size: Float) = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = size.sp,
        lineHeight = (size * 1.4f).sp,
    )

    val Body17 = body(17f)
    val Body16 = body(16f)
    val Body15 = body(15f)
    val Body14 = body(14f)
    val Body13 = body(13f)

    /** Field labels; callers pass the text already upper-cased. */
    val FieldLabel = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.5.sp,
    )
}
