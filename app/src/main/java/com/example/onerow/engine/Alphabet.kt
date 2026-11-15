package com.example.onerow.engine

/**
 * The typable letters of a language, in a fixed order, plus folding of everything else onto them
 * ("é" -> e, "ß" -> ss, "ё" -> е). The decoder only ever sees letter indices 0 until n.
 */
class Alphabet(val letters: String, val folds: Map<Char, String> = emptyMap()) {
    val n = letters.length
    private val idx = HashMap<Char, Int>().also { m -> letters.forEachIndexed { i, c -> m[c] = i } }

    fun index(c: Char): Int = idx[c.lowercaseChar()] ?: -1

    /** Letter indices a user would type for [word]; apostrophes, hyphens, digits are skipped. */
    private val tab = IntArray(0x530) { -1 }.also { t -> letters.forEachIndexed { i, c -> if (c.code < t.size) t[c.code] = i } }
    private fun lookup(c: Char): Int = if (c.code < tab.size) tab[c.code] else (idx[c] ?: -1)

    fun key(word: String): IntArray {
        var out = IntArray(word.length); var m = 0
        for (ch in word) {
            val c = ch.lowercaseChar()
            val i = lookup(c)
            if (i >= 0) { if (m == out.size) out = out.copyOf(m * 2 + 2); out[m++] = i; continue }
            val f = folds[c] ?: continue
            for (g in f) { val j = lookup(g); if (j >= 0) { if (m == out.size) out = out.copyOf(m * 2 + 2); out[m++] = j } }
        }
        return if (m == out.size) out else out.copyOf(m)
    }

    companion object {
        /** Accent folding shared by the Latin-script languages. */
        val LATIN_FOLDS: Map<Char, String> = buildMap {
            fun add(to: String, chars: String) = chars.forEach { put(it, to) }
            add("a", "àáâãäåąāă"); add("c", "çćč"); add("d", "ďđ"); add("e", "èéêëēęěė"); add("g", "ģğ"); add("i", "ìíîïīįı")
            add("k", "ķ"); add("l", "łľĺļ"); add("n", "ñńňņ"); add("o", "òóôõöøő"); add("r", "řŕ"); add("s", "śšșş")
            add("t", "țťţ"); add("u", "ùúûüūůűų"); add("y", "ýÿ"); add("z", "źżž")
            put('œ', "oe"); put('æ', "ae"); put('ß', "ss")
        }
        val LATIN = "abcdefghijklmnopqrstuvwxyz"
    }
}
