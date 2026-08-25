package com.firmlyplanted.app.domain

sealed class ScopeCheck {
    data object Ok : ScopeCheck()
    data class Blocked(val messageResName: String) : ScopeCheck()
}

/**
 * Enforces each translation's licensing limits on a project's *intended scope* (the full
 * book/chapter/verse range the user wants to memorize). This is a belt-and-suspenders check:
 * the day-to-day cache is already bounded to a small rolling window (see ProjectRepository),
 * so no single fetch ever approaches these limits, but a huge declared scope for a restricted
 * text is still worth flagging up front.
 */
object LicensePolicy {

    fun checkScope(translation: Translation, verseCount: Int): ScopeCheck {
        val cap = translation.maxCachedVerses
        return if (cap != null && verseCount > cap) {
            ScopeCheck.Blocked("esv_scope_error")
        } else {
            ScopeCheck.Ok
        }
    }

    /**
     * Vietnamese 1925 has no hard scope cap (see DefaultTranslations doc comment — the
     * fetch.bible file is public domain), but a separate UBS edition of the same translation
     * carries its own copyright notice elsewhere, so the New Project confirmation screen shows
     * an informational note (not a blocking one) for this translation.
     */
    fun showsInformationalNote(translation: Translation): Boolean =
        translation.id == DefaultTranslations.VIE1925_ID

    /** Whether this translation should avoid ever being rendered next to an English gloss on-screen. */
    fun forbidsDiglot(translation: Translation): Boolean = translation.id == DefaultTranslations.SBLGNT_ID
}
