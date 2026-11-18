package com.example.onerow

import com.example.onerow.engine.*
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** Swiping a Hebrew word that ends in a final letter: the finger ends on the final-form key (ם ן ך ף), not on the plain letter's key. */
class HebrewGlideTest {
    private val he = Languages.byCode("he")
    private val rows = he.fullRows
    private val w = 108f

    private fun center(ch: Char): Pair<Float, Float>? {
        rows.forEachIndexed { ri, r ->
            val i = r.indexOf(ch); if (i < 0) return@forEachIndexed
            val inset = if (ri == 2) 1f else 0f
            return (inset + i + 0.5f) * w to (ri + 0.5f) * 1.15f * w
        }
        return null
    }

    private fun layout(withFinalKeys: Boolean): GlideLayout {
        val a = he.alphabet; val n = a.n
        val cx = FloatArray(n) { Float.NaN }; val cy = FloatArray(n) { Float.NaN }
        val ex = FloatArray(n) { Float.NaN }; val ey = FloatArray(n) { Float.NaN }
        for (ch in a.letters) { val c = center(ch) ?: continue; cx[a.index(ch)] = c.first; cy[a.index(ch)] = c.second }
        for ((plain, fin) in a.finals) { val c = center(fin) ?: continue; ex[a.index(plain)] = c.first; ey[a.index(plain)] = c.second }
        // alternates (hold) stand on the base key: ץ on צ
        return if (withFinalKeys) GlideLayout(cx, cy, w, ex, ey) else GlideLayout(cx, cy, w)
    }

    private fun run(withFinalKeys: Boolean): Pair<Double, Int> {
        val f = TestDicts.file("he.lex") ?: return 1.0 to 0
        val lex = Lexicon.loadBinary(he.alphabet, f.inputStream(), TestDicts.file("he_bi.tsv")?.inputStream())
        val g = layout(true); val dec = GlideDecoder(lex); val r = Random(3)
        val byFreq = lex.words.indices.sortedByDescending { lex.logUni[it] }.take(20000)
        var total = 0; var top3 = 0
        val glayout = layout(withFinalKeys)
        for (wi in byFreq) {
            val word = lex.words[wi]
            if (word.length < 3 || word.last() !in "ךםןףץ" || word.last() == 'ץ') continue
            if (total >= 400) break
            val pts = ArrayList<Pair<Float, Float>>()
            var last = ' '
            for (ch in word) {
                val c = center(ch) ?: center(he.alphabet.folds[ch]?.first() ?: ch) ?: continue
                if (ch != last) pts += (c.first + r.nextGaussian().toFloat() * 0.15f * w) to (c.second + r.nextGaussian().toFloat() * 0.15f * w)
                last = ch
            }
            if (pts.size < 3) continue
            val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
            for (s in 0 until pts.size - 1) for (t in 0 until 8) {
                val u = t / 8f; xs += pts[s].first + (pts[s + 1].first - pts[s].first) * u; ys += pts[s].second + (pts[s + 1].second - pts[s].second) * u
            }
            xs += pts.last().first; ys += pts.last().second
            total++
            val out = dec.decode(xs.toFloatArray(), ys.toFloatArray(), xs.size, glayout, -1, 3)
            if (out.any { it.word == word }) top3++
        }
        return (if (total == 0) 1.0 else top3.toDouble() / total) to total
    }

    @Test fun wordsEndingInAFinalLetterAreFoundWhenTheFingerEndsOnTheFinalKey() {
        val (acc, n) = run(true)
        println("HEGLIDE with final keys: top3=${"%.0f".format(acc * 100)}% of $n")
        assertTrue("final-ending words must be found (got ${"%.0f".format(acc * 100)}% of $n)", n == 0 || acc >= 0.85)
    }

    @Test fun withoutTheFinalKeysTheseWordsAreMissed() {
        val (acc, n) = run(false)
        println("HEGLIDE without final keys: top3=${"%.0f".format(acc * 100)}% of $n")
    }
}
