package com.example.onerow

import android.content.Context
import android.graphics.Paint

/** Emoji data: the full palette (whatever this device can draw) and per-user usage counts for the hold-lens. */
object Emojis {
    private val DEFAULT = listOf("😂", "😊", "😍", "👍", "😢", "😭", "😉", "🙏", "🎉", "❤️", "🔥", "😎", "🤔", "😀", "👋", "😡")

    private fun prefs(c: Context) = c.getSharedPreferences("onerow_emoji", Context.MODE_PRIVATE)

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
            0x1F600..0x1F64F, 0x1F910..0x1F93A, 0x1F440..0x1F4FF, 0x1F90C..0x1F90F, 0x1F300..0x1F3FA, 0x1F400..0x1F43F,
            0x1F680..0x1F6C5, 0x1F93C..0x1F9FF, 0x2600..0x27BF, 0x2B50..0x2B55, 0x1FA70..0x1FAFF,
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
}
