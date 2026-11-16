package com.example.onerow.engine

import java.util.regex.Pattern

/**
 * Numbers in free text, for adding up a selection. [decimal] and [grouping] come from the regional settings:
 * US "1,234.5", Germany "1.234,5", Russia/France "1 234,5" (a space or no-break space groups).
 * A number with a minus in front is an operator already: "25 a kg, -5 discount" is 25-5.
 */
object Numbers {
    /** The numbers as one expression (plus between them; a negative one brings its own minus) and how many there were. */
    fun expression(text: String, decimal: Char, grouping: Char): Pair<String, Int> {
        val groups = HashSet<Char>().apply {
            add(grouping)
            if (grouping == ' ' || grouping == ' ' || grouping == ' ') { add(' '); add(' '); add(' ') }
        }
        val decimals = LinkedHashSet<Char>().apply {
            add(decimal)
            if ('.' !in groups) add('.')
            if (',' !in groups) add(',')
        }
        fun alt(cs: Set<Char>) = "(?:" + cs.joinToString("|") { Pattern.quote(it.toString()) } + ")"
        val num = "(?:\\d{1,3}(?:${alt(groups)}\\d{3})+|\\d+)(?:${alt(decimals)}\\d+)?"
        val re = Regex("(?:(?<!\\p{L})([-−])|(?<![\\p{L}\\d]))($num)")
        val sb = StringBuilder(); var n = 0
        for (m in re.findAll(text)) {
            val neg = m.groupValues[1].isNotEmpty()
            val clean = StringBuilder()
            for (ch in m.groupValues[2]) when {
                ch in groups && ch !in decimals -> {}
                ch in decimals -> clean.append(decimal)
                else -> clean.append(ch)
            }
            if (n == 0) { if (neg) sb.append('-'); sb.append(clean) }
            else sb.append(if (neg) "-" else "+").append(clean)
            n++
        }
        return sb.toString() to n
    }
}
