package com.example.onerow

import com.example.onerow.engine.*
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** The loose swipe setting must forgive sloppy curves without losing accuracy on careful ones. */
class GlideToleranceTest {
    @Test fun looseIsNotWorseOnSloppyCurves() {
        val l = Languages.byCode("en")
        if (!TestDicts.present("en")) return
        val lex = Lexicon.loadBinary(l.alphabet, TestDicts.file("en.lex")!!.inputStream(), TestDicts.file("en_bi.tsv")?.inputStream())
        val rows = l.fullRows
        val w = 1080f / rows.maxOf { it.length }
        val cx = FloatArray(l.alphabet.n) { Float.NaN }; val cy = FloatArray(l.alphabet.n) { Float.NaN }
        rows.forEachIndexed { ri, r -> val inset = (rows[0].length - r.length) / 2f
            r.forEachIndexed { i, ch -> val k = l.alphabet.index(ch); if (k >= 0) { cx[k] = (inset + i + 0.5f) * w; cy[k] = (ri + 0.5f) * 1.15f * w } } }
        val g = GlideLayout(cx, cy, w)
        val byFreq = lex.words.indices.sortedByDescending { lex.logUni[it] }
        fun run(level: Int, noise: Float): Int {
            val dec = GlideDecoder(lex); dec.applyTolerance(level); val r = Random(5)
            var n = 0; var ok = 0
            repeat(300) {
                val word = lex.words[byFreq[r.nextInt(3000)]]
                val key = l.alphabet.key(word)
                if (key.distinct().size < 3 || key.any { cx[it].isNaN() }) return@repeat
                val xs = FloatArray(key.size) { cx[key[it]] + r.nextGaussian().toFloat() * noise * w }
                val ys = FloatArray(key.size) { cy[key[it]] + r.nextGaussian().toFloat() * noise * w }
                n++; if (dec.decode(xs, ys, xs.size, g, -1).take(3).any { it.word.equals(word, true) }) ok++
            }
            return ok * 100 / n
        }
        val normal = run(1, 0.7f); val loose = run(2, 0.7f)
        println("SWIPE-TOL sloppy: normal=$normal% loose=$loose%; careful: normal=${run(1, 0.2f)}% loose=${run(2, 0.2f)}%")
        assertTrue("loose ($loose%) should not be worse than normal ($normal%) on sloppy curves", loose >= normal)
        assertTrue("loose must stay accurate on careful curves", run(2, 0.2f) >= 90)
    }
}
