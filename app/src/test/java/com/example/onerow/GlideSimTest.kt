package com.example.onerow

import com.example.onerow.engine.*
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Simulates glide typing on each language's full keyboard: a smooth, noisy path through the centres of a word's
 * keys (overshooting / cutting corners, jittered), decoded back to the word.
 */
class GlideSimTest {
    private fun lexicon(l: Lang): Lexicon {
        return Lexicon.loadBinary(l.alphabet, TestDicts.file("${l.code}.lex")!!.inputStream(), TestDicts.file("${l.code}_bi.tsv")?.inputStream())
    }

    /** Key centres of the full layout: unit = width of the widest row / its key count; rows 1.15 units tall. */
    private fun layout(l: Lang): GlideLayout {
        val rows = l.fullRows
        val units = rows.mapIndexed { i, r -> if (i == rows.lastIndex) r.length + 3 else r.length }.max()!!
        val w = 1080f / units
        val cx = FloatArray(l.alphabet.n) { Float.NaN }; val cy = FloatArray(l.alphabet.n) { Float.NaN }
        rows.forEachIndexed { ri, r ->
            val inset = when (ri) { 0 -> 0f; rows.lastIndex -> 1.5f; else -> (rows[0].length - r.length) / 2f }
            r.forEachIndexed { i, ch -> val k = l.alphabet.index(ch); cx[k] = (inset + i + 0.5f) * w; cy[k] = (ri + 0.5f) * 1.15f * w }
        }
        for ((base, alts) in l.alternates) for (a in alts) { val k = l.alphabet.index(a); if (k >= 0 && cx[k].isNaN()) { val b = l.alphabet.index(base); cx[k] = cx[b]; cy[k] = cy[b] } }
        return GlideLayout(cx, cy, w)
    }

    /** A finger path through [key] centres: jittered targets joined by a Catmull-Rom spline, sampled unevenly. */
    private fun path(g: GlideLayout, key: IntArray, noise: Float, r: Random): Pair<FloatArray, FloatArray> {
        val k = ArrayList<Int>(); for (c in key) if (k.isEmpty() || k.last() != c) k += c
        val tx = ArrayList<Float>(); val ty = ArrayList<Float>()
        for (c in k) { tx += g.cx[c] + r.nextGaussian().toFloat() * noise * g.keyW; ty += g.cy[c] + r.nextGaussian().toFloat() * noise * g.keyW }
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
        for (s in 0 until tx.size - 1) {
            val p0 = maxOf(s - 1, 0); val p1 = s; val p2 = s + 1; val p3 = minOf(s + 2, tx.size - 1)
            val steps = 6 + r.nextInt(4)
            for (t in 0 until steps) {
                val u = t / steps.toFloat()
                fun cr(a: Float, b: Float, c: Float, d: Float) = 0.5f * ((2 * b) + (-a + c) * u + (2 * a - 5 * b + 4 * c - d) * u * u + (-a + 3 * b - 3 * c + d) * u * u * u)
                xs += cr(tx[p0], tx[p1], tx[p2], tx[p3]); ys += cr(ty[p0], ty[p1], ty[p2], ty[p3])
            }
        }
        xs += tx.last(); ys += ty.last()
        return xs.toFloatArray() to ys.toFloatArray()
    }

    @Test fun everyLanguage() {
        for (l in Languages.all) {
            if (!TestDicts.present(l.code)) continue
            val lex = lexicon(l); val g = layout(l); val dec = GlideDecoder(lex); val r = Random(11)
            val byFreq = lex.words.indices.sortedByDescending { lex.logUni[it] }
            for (noise in listOf(0.2f, 0.35f)) {
                var total = 0; var top1 = 0; var top3 = 0; var nanos = 0L
                repeat(600) {
                    val rank = Math.min(byFreq.size - 1, Math.exp(r.nextDouble() * Math.log(20000.0)).toInt())
                    val w = lex.words[byFreq[rank]]
                    val key = l.alphabet.key(w)
                    val distinct = key.toList().zipWithNext().count { it.first != it.second } + 1
                    if (distinct < 2 || key.any { g.cx[it].isNaN() }) return@repeat
                    val (xs, ys) = path(g, key, noise, r)
                    val t1 = System.nanoTime()
                    val c = dec.decode(xs, ys, xs.size, g, -1)
                    nanos += System.nanoTime() - t1
                    total++
                    if (c.firstOrNull()?.word?.lowercase() == w.lowercase()) top1++
                    if (c.take(3).any { it.word.lowercase() == w.lowercase() }) top3++
                }
                println("GLIDE ${l.code} noise=$noise n=$total top1=${top1 * 100 / total}% top3=${top3 * 100 / total}% ${nanos / total / 1000}us/word")
                assertTrue("${l.code}: glide top-3 accuracy at noise $noise was ${top3 * 100 / total}%", top3 * 100 / total >= (if (noise <= 0.2f) 93 else 88))
            }
        }
    }
}
