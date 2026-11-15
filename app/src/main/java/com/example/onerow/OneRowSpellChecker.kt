package com.example.onerow

import android.service.textservice.SpellCheckerService
import com.example.onerow.engine.Lexicon
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import java.io.File

/**
 * System spell-checker that knows every language enabled in the keyboard at once.
 *
 * The stock checker checks one language (the system's), so words typed in any other language are red-underlined.
 * Select "OneRow Keyboard" under Settings > Languages & input > Spell checker and a word is only marked when it is
 * unknown in *all* of the keyboard's enabled languages (and in the user's own words). The language the system
 * asks for is ignored on purpose.
 */
class OneRowSpellChecker : SpellCheckerService() {

    override fun createSession(): Session = object : Session() {
        override fun onCreate() {}

        override fun onGetSuggestions(text: TextInfo, limit: Int): SuggestionsInfo =
            SuggestionsInfo(if (Words.known(applicationContext, text.text)) SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY
                            else SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO, emptyArray())
    }

    private object Words {
        private var codes = ""
        private var sets: List<Known> = emptyList()
        private var loading = false
        private var userStamp = 0L

        @Synchronized
        fun known(ctx: android.content.Context, raw: String): Boolean {
            val parts = raw.split('-', '‐', '‑', '_').filter { it.isNotEmpty() }
            if (parts.isEmpty()) return true
            val enabled = Prefs(ctx).enabledCodes
            val key = enabled.joinToString(",")
            if (key != codes && !loading) {
                loading = true
                Thread {
                    val loaded = enabled.map { c -> load(ctx, c) }
                    synchronized(this) { sets = loaded; codes = key; loading = false }
                }.start()
            }
            if (key != codes) return true                 // still loading: don't mark anything yet
            return parts.all { p -> wordOk(p.trim('\'', '’')) }
        }

        private fun wordOk(w: String): Boolean {
            if (w.length < 2 || w.none { it.isLetter() }) return true     // digits, symbols, single letters
            val l = w.lowercase()
            return sets.any { it.has(l) }
        }

        /** The shipped dictionary of one language (words only) and the user's own words, lowercase. */
        private class Known(val lex: Lexicon?, val user: Set<String>) {
            fun has(w: String) = w in user || (lex != null && lex.indexOf(w) >= 0)
        }

        private fun load(ctx: android.content.Context, code: String): Known {
            val lex = try { Lexicon.loadBinary(Languages.byCode(code).alphabet, ctx.assets.open("dict/$code.lex"), null) } catch (e: Exception) { null }
            val ud = UserDictionary(File(ctx.filesDir, "user_$code.txt"))
            return Known(lex, ud.counts.keys.map { it.lowercase() }.toSet())
        }
    }
}
