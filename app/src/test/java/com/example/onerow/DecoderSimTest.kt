package com.example.onerow

import com.example.onerow.engine.*
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Random

/** Simulates noisy taps on each language's one-row layout; reports nearest-key vs decoder accuracy. */
class DecoderSimTest {
    private fun lexicon(l: Lang): Lexicon {
        return Lexicon.loadBinary(l.alphabet, TestDicts.file("${l.code}.lex")!!.inputStream(), TestDicts.file("${l.code}_bi.tsv")?.inputStream())
    }

    private class Geo(val l: Lang) {
        val keyW = 360f / l.alphabet.n
        val cx = FloatArray(l.alphabet.n) { Float.NaN }
        val cy = FloatArray(l.alphabet.n) { 0f }
        init { l.columns.joinToString("").forEachIndexed { i, ch -> cx[l.alphabet.index(ch)] = 4f + keyW * (i + 0.5f) } }
        fun taps(word: String, noise: Float, r: Random): List<FloatArray> =
            l.alphabet.key(word).map { k ->
                val x = cx[k] + r.nextGaussian().toFloat() * noise * keyW
                SpatialModel.logProbs(x, 0f, cx, cy, keyW * 0.9f, 1e6f)
            }
    }

    @Test fun everyLanguage() {
        for (l in Languages.all) {
            if (!TestDicts.present(l.code)) continue
            val t0 = System.nanoTime()
            val lex = lexicon(l)
            val byFreq = lex.words.indices.sortedByDescending { lex.logUni[it] }     // word indices are trie positions, not ranks
            val loadMs = (System.nanoTime() - t0) / 1_000_000
            assertTrue("${l.code}: layout must cover the alphabet", l.columns.joinToString("").toSet() == l.alphabet.letters.toSet())
            assertTrue("${l.code}: full rows must cover the alphabet", l.fullRows.joinToString("").toSet() == l.alphabet.letters.toSet())
            val geo = Geo(l); val dec = Decoder(lex); val r = Random(5)
            for (noise in listOf(0.4f, 0.7f)) {
                var total = 0; var base = 0; var top1 = 0; var top3 = 0; var nanos = 0L
                repeat(2500) {
                    val rank = Math.min(byFreq.size - 1, Math.exp(r.nextDouble() * Math.log(25000.0)).toInt())
                    val w = lex.words[byFreq[rank]]
                    val key = l.alphabet.key(w)
                    if (key.size < 2) return@repeat
                    val tp = geo.taps(w, noise, r)
                    val t1 = System.nanoTime()
                    val c = dec.decode(tp, -1)
                    nanos += System.nanoTime() - t1
                    total++
                    val lit = tp.joinToString("") { v -> l.alphabet.letters[v.indices.maxByOrNull { v[it] }!!].toString() }
                    if (lit == key.joinToString("") { l.alphabet.letters[it].toString() }) base++
                    if (c.firstOrNull()?.word?.lowercase() == w.lowercase()) top1++
                    if (c.take(3).any { it.word.lowercase() == w.lowercase() }) top3++
                }
                println("LANG ${l.code} noise=$noise words=${lex.words.size} load=${loadMs}ms n=$total nearest=${base * 100 / total}% top1=${top1 * 100 / total}% top3=${top3 * 100 / total}% ${nanos / total / 1000}us/word")
            }
        }
    }
}
