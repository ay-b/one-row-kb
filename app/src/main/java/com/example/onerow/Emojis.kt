package com.example.onerow

import android.content.Context
import android.graphics.Paint

/** Emoji data: the full palette (whatever this device can draw) and per-user usage counts for the hold-lens. */
object Emojis {
    private val DEFAULT = listOf("😂", "😊", "😍", "👍", "😢", "😭", "😉", "🙏", "🎉", "❤️", "🔥", "😎", "🤔", "😀", "👋", "😡")

    private fun prefs(c: Context) = Storage.prefs(c, "onerow_emoji")

    fun bump(c: Context, e: String) {
        val p = prefs(c)
        p.edit().putInt(e, p.getInt(e, 0) + 1).apply()
    }

    /** [n] emoji for the lens: most used first; unused defaults fill the rest. */
    fun lens(c: Context, n: Int = 16): List<String> {
        val used = prefs(c).all.entries.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }
            .sortedByDescending { it.second }.map { it.first }
        return (used + DEFAULT).distinct().take(n)
    }

    /** Emoji already used, most used first (the "recent" row of the palette). */
    fun used(c: Context, n: Int = 16): List<String> {
        val p = prefs(c)
        return p.all.entries.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }
            .sortedByDescending { it.second }.map { it.first }.take(n)
    }

    private var cache: List<String>? = null

    /** Every emoji from the common Unicode blocks that this device has a glyph for. */
    fun all(): List<String> = cache ?: build().also { cache = it }

    private fun build(): List<String> {
        val ranges = listOf(
            0x1F600..0x1F64F, 0x1F910..0x1F93A, 0x1F440..0x1F4FF, 0x1F500..0x1F5FF, 0x1F90C..0x1F90F, 0x1F300..0x1F3FA, 0x1F400..0x1F43F,
            0x1F680..0x1F6C5, 0x1F6CB..0x1F6FC, 0x231A..0x23FF, 0x1F004..0x1F004, 0x1F0CF..0x1F0CF, 0x1F170..0x1F251, 0x1F93C..0x1F9FF, 0x2600..0x27BF, 0x2B50..0x2B55, 0x1FA70..0x1FAFF,
        )
        val paint = Paint()
        val out = LinkedHashSet<String>()
        for (r in ranges) for (cp in r) {
            val s = String(Character.toChars(cp))
            val v = if (cp < 0x10000) s + "️" else s
            if (paint.hasGlyph(v)) out += v
        }
        out += "❤️"
        return out.toList()
    }

    /** Words people type that the Unicode names do not contain, mapped to words that are in the names. */
    private val synonyms = mapOf(
        "lol" to "joy", "laugh" to "joy", "laughing" to "joy", "haha" to "joy", "happy" to "smiling", "smile" to "smiling", "sad" to "crying",
        "cry" to "crying", "tear" to "crying", "love" to "heart", "like" to "thumbs", "ok" to "ok", "yes" to "thumbs", "thanks" to "folded",
        "thank" to "folded", "pray" to "folded", "please" to "folded", "angry" to "angry", "mad" to "angry", "cool" to "sunglasses",
        "party" to "party", "birthday" to "birthday", "sleep" to "sleeping", "tired" to "sleepy", "money" to "money", "dog" to "dog", "cat" to "cat",
        "hi" to "waving", "hello" to "waving", "bye" to "waving", "wow" to "astonished", "scared" to "fearful", "think" to "thinking", "idea" to "bulb",
        "kiss" to "kiss", "hug" to "hugging", "sick" to "sick", "poo" to "pile", "fire" to "fire", "star" to "star", "sun" to "sun", "rain" to "rain")

    private var names: List<Pair<String, String>>? = null

    /** Each emoji with its lower-case Unicode name (English, from the system's ICU), built once. */
    private fun index(): List<Pair<String, String>> = names ?: all().map { e ->
        e to ((try { android.icu.lang.UCharacter.getName(e.codePointAt(0)) } catch (_: Throwable) { null }) ?: "").lowercase()
    }.also { names = it }

    /**
     * The emoji whose name has a word starting with every word of [query] (so "tears" or "face tear" find 😂); words of the
     * name that start with the query come first, then the emoji used most. At most [limit].
     */
    fun search(c: Context, query: String, limit: Int = 240): List<String> {
        val words = query.lowercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val used = prefs(c)
        fun score(name: String): Int? {
            val nameWords = name.split(' ', '-')
            var best = 2
            for (w in words) {
                val alt = synonyms[w]
                val hit = nameWords.any { it.startsWith(w) } || (alt != null && nameWords.any { it.startsWith(alt) })
                if (!hit) return null
                if (nameWords.firstOrNull()?.startsWith(w) == true) best = minOf(best, 1)
                if (nameWords.firstOrNull() == w) best = 0
            }
            return best
        }
        return index().mapNotNull { (e, n) -> score(n)?.let { Triple(e, it, used.getInt(e, 0)) } }
            .sortedWith(compareBy<Triple<String, Int, Int>> { it.second }.thenByDescending { it.third })
            .map { it.first }.take(limit)
    }
}
