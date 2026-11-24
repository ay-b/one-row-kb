package com.example.onerow

import org.junit.Assert.*
import org.junit.Test

class QuotesTest {
    /** Types [q] after [line] (caret at the end of the line) and returns the resulting line. */
    private fun type(line: String, q: Char, after: String = ""): String {
        val p = Quotes.plan(line, after, q) ?: return line + q
        return line.dropLast(p.deleteBefore) + p.pre + p.quote + p.post
    }
    private fun typeAll(s: String): String { var out = ""; for (c in s) out = if (c in Quotes.ALL) type(out, c) else out + c; return out }

    @Test fun doubleQuoteOpensThenClosesAndSticks() {
        assertEquals("say \"hello\" ", typeAll("say \"hello\""))
        assertEquals("say \"hello\" ", typeAll("say \"hello \""))       // spaces before the closing quote are taken away
        assertEquals("\"a\" \"b\" ", typeAll("\"a\"\"b\""))               // the next pair opens again (the space after the first is automatic)
    }

    @Test fun openingQuoteIsLeftAsTyped() {
        assertEquals("word\"x\" ", typeAll("word\"x\""))                  // no space added before an opening quote
    }

    @Test fun singleQuoteOpensOnlyAfterSpace() {
        assertEquals("'go' ", typeAll("'go'"))
        assertEquals("say 'go' ", typeAll("say 'go'"))
        assertEquals("don't", typeAll("don't"))                           // apostrophe stays plain
        assertEquals("l'eau", typeAll("l'eau"))
        assertEquals("it's 'fine' ", typeAll("it's 'fine'"))
    }

    @Test fun guillemetsAndLowQuotes() {
        assertEquals("word «", type("word", '«'))
        assertEquals("«", type("", '«'))
        assertEquals("( «", type("( ", '«'))
        assertEquals("word „", type("word", '„'))
        assertEquals("» ", type("", '»'))
        assertEquals("word» ", type("word ", '»'))                        // » never has a space before it
        assertEquals("word“ ", type("word  ", '“'))
        assertEquals("x“ ", type("x", '“'))
    }

    @Test fun noSpaceAfterClosingBeforePunctuation() {
        assertEquals("a»", type("a", '»', after = ")"))
        assertEquals("a» ", type("a", '»', after = "b"))
        assertEquals("a» ", type("a", '»', after = ""))
        assertEquals("say \"x\"", type("say \"x", '"', after = "."))
    }
}
