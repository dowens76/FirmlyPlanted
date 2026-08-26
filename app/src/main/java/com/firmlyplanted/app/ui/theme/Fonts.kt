package com.firmlyplanted.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.firmlyplanted.app.R

/**
 * Ezra SIL (Hebrew) and Gentium (Greek/Latin) — both SIL Open Font License 1.1, bundled under
 * res/font/. License texts kept at NOTICES/fonts/ per the OFL's redistribution requirement.
 * Regenerate/re-fetch from https://software.sil.org/ezra/ and
 * https://github.com/silnrsi/font-gentium if these ever need updating.
 */
val EzraSilFontFamily = FontFamily(Font(R.font.ezra_sil))

val GentiumFontFamily = FontFamily(
    Font(R.font.gentium_regular, FontWeight.Normal),
    Font(R.font.gentium_bold, FontWeight.Bold),
)

/** Picks the right script-specific font for a translation's language, falling back to the default UI font. */
fun fontFamilyForLanguage(language: String?): FontFamily = when (language?.trim()?.lowercase()) {
    "hebrew" -> EzraSilFontFamily
    "greek" -> GentiumFontFamily
    else -> FontFamily.Default
}

/** Multiplier applied to bodyLarge for all rendered Bible/verse text — a single knob to tune it from. */
private const val SCRIPTURE_TEXT_SCALE = 1.5f

/** The text style every rendered verse (new-verse card, review card) should use — 50% larger than bodyLarge by default. */
@Composable
fun scriptureTextStyle(): TextStyle {
    val base = MaterialTheme.typography.bodyLarge
    return base.copy(
        fontSize = base.fontSize * SCRIPTURE_TEXT_SCALE,
        lineHeight = base.lineHeight * SCRIPTURE_TEXT_SCALE,
    )
}
