package com.example.onerow

/**
 * Rules for quotation marks.  " toggles between opening and closing within the line; ' does too, but it only opens after
 * a space (or at the start of a line, or after an opening bracket / quote); after a letter it closes an open one and
 * otherwise stays an apostrophe.  A closing quote sticks to the symbol before it and adds a space after it.
 * « and „ add a space before them when there is none, » and “ add one after.
 */
object Quotes {
    const val ALL = "\"'«»„“"

    /** What to do to the text: delete [deleteBefore] chars before the caret, then type [pre] + quote + [post]. [post] is a space or "". */
    data class Plan(val deleteBefore: Int, val pre: String, val quote: Char, val post: String)

    private const val OPENERS = "([{«„“‘"
    private const val NO_SPACE_BEFORE_NEXT = ".,;:!?)]}\"'»“"

    private fun opensAfter(p: Char?) = p == null || p.isWhitespace() || p in OPENERS

    /** Is a single quote opened in [line] and not yet closed?  (an apostrophe inside a word does not count) */
    fun singleOpen(line: String): Boolean {
        var open = false
        for (i in line.indices) if (line[i] == '\'') {
            if (opensAfter(line.getOrNull(i - 1))) open = true else if (open) open = false
        }
        return open
    }

    /** [line]: the text before the caret in the current line; [after]: the character after the caret, if any. */
    fun plan(line: String, after: String, q: Char): Plan? {
        val prev = line.lastOrNull()
        val spaceAfter = if (after.isEmpty() || !(after[0].isWhitespace() || after[0] in NO_SPACE_BEFORE_NEXT)) " " else ""
        return when (q) {
            '«', '„' -> Plan(0, if (opensAfter(prev)) "" else " ", q, "")
            '»', '“' -> Plan(line.length - line.trimEnd(' ').length, "", q, spaceAfter)     // a closing mark never has a space before it
            '"', '\'' -> {
                val open = if (q == '"') line.count { it == '"' } % 2 == 1 else singleOpen(line)
                val opening = if (q == '"') !open else opensAfter(prev)
                if (opening || (q == '\'' && !open)) Plan(0, "", q, "")                      // opening quote, or a plain apostrophe
                else Plan(line.length - line.trimEnd(' ').length, "", q, spaceAfter)            // closing: stick to the last symbol
            }
            else -> null
        }
    }
}
