package com.example.onerow.engine

/**
 * The typable letters of a language, in a fixed order, plus folding of everything else onto them
 * ("é" -> e, "ß" -> ss, "ё" -> е). The decoder only ever sees letter indices 0 until n.
 */
class Alphabet(val letters: String, private val folds: Map<Char, String> = emptyMap()) {
    val n = letters.length
    private val idx = HashMap<Char, Int>().also { m -> letters.forEachIndexed { i, c -> m[c] = i } }

    fun index(c: Char): Int = idx[c.lowercaseChar()] ?: -1

    /** Letter indices a user would type for [word]; apostrophes, hyphens, digits are skipped. */
    fun key(word: String): IntArray {
        val out = ArrayList<Int>(word.length)
        for (ch in word) {
            val c = ch.lowercaseChar()
            val i = idx[c]
            if (i != null) { out += i; continue }
            folds[c]?.forEach { f -> idx[f]?.let { out += it } }
        }
        return out.toIntArray()
    }

    companion object {
        /** Accent folding shared by the Latin-script languages. */
        val LATIN_FOLDS: Map<Char, String> = buildMap {
            fun add(to: String, chars: String) = chars.forEach { put(it, to) }
            add("a", "àáâãäå"); add("c", "çćč"); add("e", "èéêëē"); add("i", "ìíîïī"); add("n", "ñń")
            add("o", "òóôõöø"); add("u", "ùúûüū"); add("y", "ýÿ"); add("s", "śš"); add("z", "źżž")
            put('œ', "oe"); put('æ', "ae"); put('ß', "ss")
        }
        val LATIN = "abcdefghijklmnopqrstuvwxyz"
    }
}
