package com.example.onerow

import com.example.onerow.engine.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** The binary dictionaries written by tools/build_lexicon.py must be readable and consistent with Alphabet.key. */
class LexiconFormatTest {
    @Test fun everyLanguageRoundTrips() {
        val d = File("src/main/assets/dict")
        for (l in Languages.all) {
            val f = File(d, "${l.code}.lex")
            assertTrue("${l.code}.lex is missing: run tools/build_lexicon.py", f.exists())
            val t0 = System.nanoTime()
            val lex = Lexicon.loadBinary(l.alphabet, f.inputStream(), File(d, "${l.code}_bi.tsv").inputStream())
            val ms = (System.nanoTime() - t0) / 1_000_000
            val n = lex.words.size
            assertTrue("${l.code}: dictionary too small ($n)", n > 80_000)
            var checked = 0
            for (i in 0 until n step 37) {
                val w = lex.words[i]; val key = l.alphabet.key(w)
                if (key.isEmpty()) continue
                var node = Lexicon.ROOT
                for (c in key) { node = lex.child(node, c); assertTrue("${l.code}: no path for '$w'", node >= 0) }
                assertTrue("${l.code}: '$w' not at the end of its path", i in lex.wordsAt(node))
                val found = lex.indexOf(w)
                assertTrue("${l.code}: indexOf('$w') = $found", found >= 0 && lex.words[found].equals(w, ignoreCase = true))
                checked++
            }
            // the best completions of the empty prefix are common words, best first
            val top = lex.top(Lexicon.ROOT)
            assertEquals(Lexicon.TOP_K, top.size)
            for (k in 1 until top.size) assertTrue(lex.logUni[top[k - 1]] >= lex.logUni[top[k]])
            // a word added by the user is found and survives next to the static words
            val a = l.alphabet.letters
            val odd = "" + a[0] + a[5] + a[10] + a[3] + a[1]
            if (lex.indexOf(odd) < 0) {
                val i = lex.addWord(odd, -12f)
                assertEquals("${l.code}: added word '$odd'", i, lex.indexOf(odd))
                assertTrue(lex.isUser(i))
            }
            println("LEX ${l.code}: words=$n load=${ms}ms checked=$checked top=${top.map { lex.words[it] }}")
        }
    }
}
