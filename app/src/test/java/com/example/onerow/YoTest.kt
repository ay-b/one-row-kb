package com.example.onerow

import com.example.onerow.engine.*
import org.junit.Assert.*
import org.junit.Test

/** The ё forms of the Russian dictionary: real ones are preferred over their е twin, mistaken ones (ёё, лёт) are not offered or not preferred. */
class YoTest {
    private fun ru(): Lexicon {
        val l = Languages.all.first { it.code == "ru" }
        return Lexicon.loadBinary(l.alphabet, TestDicts.file("ru.lex")!!.inputStream(), TestDicts.file("ru_bi.tsv")?.inputStream()).also { it.preferYo() }
    }

    @Test fun mistakenYoFormsAreNotOffered() {
        val lex = ru()
        for (w in listOf("ёё", "ёй", "перёд", "самоё", "запрещёно")) assertEquals("$w must be hidden", -1, lex.indexOf(w))
    }

    @Test fun realYoFormsStayAndWinOverTheirTwin() {
        val lex = ru()
        for ((yo, e) in listOf("всё" to "все", "её" to "ее", "моё" to "мое", "нём" to "нем")) {
            val a = lex.indexOf(yo); val b = lex.indexOf(e)
            assertTrue("$yo is missing", a >= 0)
            if (b >= 0) assertTrue("$yo should outrank $e", lex.lm(a, -1) > lex.lm(b, -1))
        }
    }
}
