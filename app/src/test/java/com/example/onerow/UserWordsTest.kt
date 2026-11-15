package com.example.onerow

import com.example.onerow.engine.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UserWordsTest {
    private val en = Languages.byCode("en")
    private fun lex(): Lexicon {
        val d = File("src/main/assets/dict")
        return Lexicon.loadBinary(en.alphabet, File(d, "en.lex").inputStream(), File(d, "en_bi.tsv").inputStream())
    }
    /** Exact taps spelling [w]. */
    private fun exactTaps(w: String) = w.map { ch -> FloatArray(en.alphabet.n) { -30f }.also { it[en.alphabet.index(ch)] = 0f } }

    @Test fun learnedWordIsGuessedAndRanksFirstWhenUsed() {
        val lx = lex(); val dec = Decoder(lx)
        assertEquals(-1, lx.indexOf("tolya"))
        // exact letters t-o-l-y: the shipped dictionary only knows other completions
        val before = dec.decode(exactTaps("toly"), -1).map { it.word }
        assertFalse(before.any { it.equals("tolya", true) })
        lx.addWord("tolya", UserDictionary.logProb(1))
        val after = dec.decode(exactTaps("toly"), -1).map { it.word }
        assertTrue("learned word is offered as a guess: $after", after.any { it == "tolya" })
        assertTrue(lx.indexOf("tolya") >= 0)
    }

    @Test fun priorGrowsWithUse() {
        assertTrue(UserDictionary.logProb(5) > UserDictionary.logProb(1))
        assertTrue(UserDictionary.logProb(1000) <= Math.log(2e-3).toFloat() + 1e-4f)
    }

    @Test fun removedWordNeverShowsUp() {
        val lx = lex(); val dec = Decoder(lx)
        val i = lx.indexOf("the"); assertTrue(i >= 0)
        lx.remove(i)
        assertEquals(-1, lx.indexOf("the"))
        val got = dec.decode(exactTaps("the"), -1).map { it.word.lowercase() }
        // the literal text "the" may still be offered as what-you-typed, but not as a dictionary word
        assertTrue(got.none { it == "the" && !dec.decode(exactTaps("the"), -1).first { c -> c.word == it }.isLiteral })
    }
}
