package com.example.onerow

import java.io.File
import kotlin.math.ln
import kotlin.math.min

/**
 * The user's own words for one language: words typed on purpose that the shipped dictionary doesn't
 * know (they get a prior that grows with use), and shipped words the user removed from guesses.
 */
class UserDictionary(private val file: File) {
    val counts = LinkedHashMap<String, Int>()     // surface -> times used
    val removed = LinkedHashSet<String>()         // lowercase words hidden from guesses

    init {
        try {
            if (file.exists()) file.forEachLine {
                val t = it.split('\t')
                if (t.size >= 3 && t[0] == "+") counts[t[1]] = t[2].toIntOrNull() ?: 1
                else if (t.size >= 2 && t[0] == "-") removed += t[1]
            }
        } catch (e: Exception) { /* start empty */ }
    }

    fun has(word: String) = counts.keys.any { it.equals(word, ignoreCase = true) }

    /** Counts one more use of [word]; returns its new count. */
    fun bump(word: String): Int {
        removed -= word.lowercase()
        val key = counts.keys.firstOrNull { it.equals(word, ignoreCase = true) } ?: word
        val c = (counts[key] ?: 0) + 1
        counts[key] = c
        save()
        return c
    }

    /** Forgets a learned word and hides it (and any shipped word of the same spelling) from guesses. */
    fun forget(word: String) {
        counts.keys.removeAll { it.equals(word, ignoreCase = true) }
        removed += word.lowercase()
        save()
    }

    private fun save() {
        try {
            file.writeText(buildString {
                counts.forEach { (w, c) -> append("+\t$w\t$c\n") }
                removed.forEach { append("-\t$it\n") }
            })
        } catch (e: Exception) { /* best effort */ }
    }

    companion object {
        /** Prior of a user word after [count] uses: starts like a mid-frequency word, caps at a common one. */
        fun logProb(count: Int): Float = ln(min(2e-3, 2e-4 * count)).toFloat()
    }
}
