package com.example.onerow

import com.example.onerow.engine.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HebrewTest {
    private val he = Languages.byCode("he")

    @Test fun finalFormsFoldOntoThePlainLetters() {
        val a = he.alphabet
        assertEquals(22, a.n)
        assertEquals(a.key("שלום").toList(), a.key("שלומ").toList())
        assertEquals(a.key("ארץ").toList(), a.key("ארצ").toList())
        assertEquals(a.key("מלך").toList(), a.key("מלכ").toList())
    }

    @Test fun lastLetterTakesItsFinalForm() {
        val a = he.alphabet
        assertEquals("שלום", a.close("שלומ"))
        assertEquals("ארץ", a.close("ארצ"))
        assertEquals("מלך", a.close("מלכ"))
        assertEquals("חן", a.close("חנ"))
        assertEquals("אף", a.close("אפ"))
        assertEquals("שלום", a.close("שלום"))               // already final
        assertEquals("בית", a.close("בית"))                 // no final form
        assertEquals("", a.close(""))
        assertEquals("abc", Languages.byCode("en").alphabet.close("abc"))
    }

    @Test fun dictionaryHasCommonWordsAndNoMisplacedFinals() {
        val f = TestDicts.file("he.lex") ?: return
        val lex = Lexicon.loadBinary(he.alphabet, f.inputStream(), TestDicts.file("he_bi.tsv")?.inputStream())
        val words = lex.words.toSet()
        for (w in listOf("שלום", "תודה", "בבקשה", "ארץ", "עץ", "מה", "אני", "אוהב", "אותך", "טלפון")) assertTrue("$w missing", w in words)
        val bad = words.filter { Regex("[ךםןףץ].").containsMatchIn(it) || Regex("[כמנפצ]$").containsMatchIn(it) }
        assertTrue("a final form inside a word or a plain form at the end: ${bad.take(10)}", bad.isEmpty())
    }

    @Test fun typingThePlainLetterGivesTheFinalWord() {
        val f = TestDicts.file("he.lex") ?: return
        val lex = Lexicon.loadBinary(he.alphabet, f.inputStream(), TestDicts.file("he_bi.tsv")?.inputStream())
        val taps = "שלומ".map { c -> FloatArray(he.alphabet.n) { -30f }.also { it[he.alphabet.index(c)] = 0f } }
        val out = Decoder(lex).decode(taps, -1, 5)
        assertEquals("שלום", out[0].word)
        assertTrue("the literal must be the final form too", out.filter { it.isLiteral }.all { it.word == "שלום" })
    }
}
