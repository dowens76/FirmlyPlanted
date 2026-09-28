package com.firmlyplanted.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import com.firmlyplanted.app.resources.Res
import com.firmlyplanted.app.resources.ezra_sil
import com.firmlyplanted.app.resources.gentium_bold
import com.firmlyplanted.app.resources.gentium_regular
import org.jetbrains.compose.resources.Font

/**
 * Ezra SIL (Hebrew) and Gentium (Greek/Latin) — both SIL Open Font License 1.1, bundled under
 * composeResources/font/. License texts kept at NOTICES/fonts/ per the OFL's redistribution requirement.
 * Regenerate/re-fetch from https://software.sil.org/ezra/ and
 * https://github.com/silnrsi/font-gentium if these ever need updating.
 */
@Composable
fun ezraSilFontFamily() = FontFamily(Font(Res.font.ezra_sil))

@Composable
fun gentiumFontFamily() = FontFamily(
    Font(Res.font.gentium_regular, FontWeight.Normal),
    Font(Res.font.gentium_bold, FontWeight.Bold),
)

/** Picks the right script-specific font for a translation's language, falling back to the default UI font. */
@Composable
fun fontFamilyForLanguage(language: String?): FontFamily = when (language?.trim()?.lowercase()) {
    "hebrew" -> ezraSilFontFamily()
    "greek" -> gentiumFontFamily()
    else -> FontFamily.Default
}

/** True for languages (currently just Hebrew) whose verse text should render right-to-left. */
fun isRtlLanguage(language: String?): Boolean = language?.trim()?.lowercase() == "hebrew"

/** Multiplier applied to bodyLarge for all rendered Bible/verse text — a single knob to tune it from. */
private const val SCRIPTURE_TEXT_SCALE = 1.5f

/**
 * The text style every rendered verse (new-verse card, review card) should use — 50% larger than
 * bodyLarge by default. Pass [rtl] for languages like Hebrew so the paragraph both flows and
 * aligns right-to-left rather than just shaping individual right-to-left glyphs within an
 * otherwise left-aligned block.
 */
@Composable
fun scriptureTextStyle(rtl: Boolean = false): TextStyle {
    val base = MaterialTheme.typography.bodyLarge
    return base.copy(
        fontSize = base.fontSize * SCRIPTURE_TEXT_SCALE,
        lineHeight = base.lineHeight * SCRIPTURE_TEXT_SCALE,
        textDirection = if (rtl) TextDirection.Rtl else TextDirection.Content,
        textAlign = if (rtl) TextAlign.Right else base.textAlign,
    )
}
