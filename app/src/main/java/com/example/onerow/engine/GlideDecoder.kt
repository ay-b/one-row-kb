package com.example.onerow.engine

import kotlin.math.max
import kotlin.math.sqrt

/** Key centres (view pixels) per alphabet index; NaN = that letter has no key. [keyW] is the key width. */
class GlideLayout(val cx: FloatArray, val cy: FloatArray, val keyW: Float)

/**
 * Glide ("swipe") typing: finds the dictionary words whose ideal path -- the polyline through the centres of
 * their keys -- is closest to the path the finger drew.
 *
 * Both paths are resampled at equal steps of arc length and compared point by point (in key widths). The score is
 *   -[pathWeight] * mean distance  - [endWeight] * (first/last key vs the path ends)  - [visitWeight] * keys the
 *   path never came near  + [lmWeight] * ln P(word | previous word).
 *
 * Speed: the word -> key-sequence index is built once (call [sync] when the engine loads); words are looked up by
 * first key; everything cheap (end keys, length, missed keys, language model) is checked before the path
 * comparison, and the comparison stops as soon as a word can no longer reach the top.
 */
class GlideDecoder(private val lex: Lexicon) {
    /** Timing of the last decode, for the log. */
    var stats = ""
    var lmWeight = 1.0f
    var pathWeight = 14f
    var endWeight = 4f            // cost per key width between the word's first / last key and the path ends
    var visitWeight = 8f          // cost per key width by which a key of the word is missed by the whole finger path
    var visitTolerance = 0.4f
    var endRadius = 1.2f          // first / last key must lie within this many key widths of the path ends
    var tolerance = 0.25f         // distances below this (in key widths) cost nothing
    var otherFloor = -26f         // words whose end / missed-key penalty alone is worse than this are never the intended word

    // ---- index: collapsed key sequence of every word (flat), and the words grouped by first key ----
    private var off = IntArray(1 shl 16)          // word -> start in [flat]
    private var len = ByteArray(1 shl 16)         // word -> number of keys (0 = not glideable)
    private var flat = IntArray(1 shl 18)
    private var flatN = 0
    private var indexed = 0
    private var maxLogUni = 0f
    private val nk0 = lex.alphabet.n
    private val byPair = Array(nk0 * nk0) { IntList() }      // words by (first key, last key)

    private class IntList { var a = IntArray(8); var n = 0; fun add(v: Int) { if (n == a.size) a = a.copyOf(n * 2); a[n++] = v } }

    /** Brings the index up to date with the dictionary (call off the UI thread after loading; cheap afterwards). */
    @Synchronized fun sync() {
        val total = lex.words.size
        if (indexed >= total) return
        val fresh = indexed == 0
        if (off.size < total) { off = off.copyOf(total + total / 2); len = len.copyOf(total + total / 2) }
        for (i in indexed until total) {
            off[i] = flatN
            val raw = lex.alphabet.key(lex.words[i])
            var m = 0
            for (k in raw) {
                if (m > 0 && flat[flatN - 1] == k) continue
                if (flatN == flat.size) flat = flat.copyOf(flat.size * 2)
                flat[flatN++] = k; m++
            }
            if (m < 2 || m > MAX_KEYS) { flatN -= m; len[i] = 0; continue }
            len[i] = m.toByte()
            byPair[flat[off[i]] * nk0 + flat[off[i] + m - 1]].add(i)
        }
        indexed = total
        if (fresh) maxLogUni = lex.logUni.max()
        if (fresh) for (b in byPair) {              // most frequent words first: good matches are found early, so pruning bites sooner
            val idx = (0 until b.n).map { b.a[it] }.sortedByDescending { lex.logUni[it] }
            for (j in idx.indices) b.a[j] = idx[j]
        }
    }

    /** Runs a few throwaway decodes so the first real swipe is not slowed by cold code (call off the UI thread). */
    fun warmUp() {
        sync()
        val nk = lex.alphabet.n
        val cx = FloatArray(nk) { 40f + 40f * (it % 10) }; val cy = FloatArray(nk) { 40f + 46f * (it / 10) }
        val g = GlideLayout(cx, cy, 40f)
        val xs = floatArrayOf(40f, 120f, 200f, 300f, 380f); val ys = floatArrayOf(40f, 86f, 86f, 40f, 40f)
        repeat(6) { decode(xs, ys, xs.size, g, -1) }
    }

    // scratch space, reused between calls (decode runs on one thread)
    private var queue = LongArray(1 shl 14)
    private val up = FloatArray(2 * N)
    private val ix = FloatArray(MAX_KEYS); private val iy = FloatArray(MAX_KEYS)

    /**
     * @param xs,ys the finger path (n points, view pixels)
     * @param prev index of the previous word or -1
     */
    fun decode(xs: FloatArray, ys: FloatArray, n: Int, g: GlideLayout, prev: Int, maxOut: Int = 8): List<Candidate> {
        if (n < 2) return emptyList()
        val t0 = System.nanoTime()
        sync()
        val u = g.keyW
        // finger path, in key widths
        val px = FloatArray(n) { xs[it] / u }; val py = FloatArray(n) { ys[it] / u }
        var userLen = 0f
        for (i in 1 until n) userLen += dist(px[i] - px[i - 1], py[i] - py[i - 1])
        if (userLen < 1f) return emptyList()
        resampleUser(px, py, n, userLen)
        val sx = px[0]; val sy = py[0]; val ex = px[n - 1]; val ey = py[n - 1]

        // per key: centre, distance to the path start / end, and how far the whole path misses it
        val nk = lex.alphabet.n
        val cx = FloatArray(nk) { g.cx[it] / u }; val cy = FloatArray(nk) { g.cy[it] / u }
        val dS = FloatArray(nk); val dE = FloatArray(nk); val miss = FloatArray(nk)
        for (k in 0 until nk) {
            if (cx[k].isNaN()) { dS[k] = -1f; dE[k] = -1f; miss[k] = 0f; continue }
            val a = dist(cx[k] - sx, cy[k] - sy); val b = dist(cx[k] - ex, cy[k] - ey)
            dS[k] = if (a <= endRadius) a else -1f
            dE[k] = if (b <= endRadius) b else -1f
            var best = Float.MAX_VALUE
            for (p in 0 until N) { val d = dist(cx[k] - up[2 * p], cy[k] - up[2 * p + 1]); if (d < best) best = d }
            miss[k] = max(0f, best - visitTolerance)
        }
        val pair = FloatArray(nk * nk)                 // distance between key centres, for the length of ideal paths
        for (a in 0 until nk) if (!cx[a].isNaN()) for (b in 0 until nk) if (!cx[b].isNaN()) pair[a * nk + b] = dist(cx[a] - cx[b], cy[a] - cy[b])
        val tPrep = System.nanoTime()

        val bestW = IntArray(maxOut) { -1 }; val bestS = FloatArray(maxOut) { Float.NEGATIVE_INFINITY }
        var worst = Float.NEGATIVE_INFINITY            // score of the last kept word
        var scored = 0
        val minLen = 0.3f * userLen; val maxLen = 2.6f * userLen
        // best case of the language-model term for any word: lets most words be dropped before it is computed
        val lmCeil = Math.log(0.5 * lex.maxContextProb(prev) + 0.5 * Math.exp(maxLogUni.toDouble())).toFloat() * lmWeight

        // phase 1: cheap tests only (end keys, all keys exist, path length, keys the path never came near);
        // survivors are queued with their score without the language model and without the path cost
        var cn = 0
        for (first in 0 until nk) {
          if (dS[first] < 0f) continue
          for (last in 0 until nk) {
            if (dE[last] < 0f) continue
            val b = byPair[first * nk + last]
            for (bi in 0 until b.n) {
                val i = b.a[bi]
                val m = len[i].toInt(); val o = off[i]
                var ideal = 0f; var missed = 0f; var ok = true
                var prevKey = first
                for (j in 0 until m) {
                    val k = flat[o + j]
                    if (cx[k].isNaN()) { ok = false; break }
                    missed += miss[k]
                    if (j > 0) ideal += pair[prevKey * nk + k]
                    prevKey = k
                }
                if (!ok || ideal < minLen || ideal > maxLen) continue
                val other = -endWeight * (dS[first] + dE[last]) - visitWeight * missed
                if (other < otherFloor) continue
                if (cn == queue.size) queue = queue.copyOf(cn * 2)
                val bits = java.lang.Float.floatToIntBits(other)
                queue[cn++] = ((bits xor ((bits shr 31) and 0x7fffffff)).toLong() shl 32) or (i.toLong() and 0xffffffffL)
            }
          }
        }
        java.util.Arrays.sort(queue, 0, cn)

        // phase 2: best first; once even the best case of the next word cannot reach the top, all later ones cannot either
        var q = cn - 1
        while (q >= 0) {
            val key = queue[q--]
            val i = (key and 0xffffffffL).toInt()
            val sb = (key shr 32).toInt()
            val other = java.lang.Float.intBitsToFloat(sb xor ((sb shr 31) and 0x7fffffff))
            if (other + lmCeil <= worst) break
            if (lex.isRemoved(i)) continue
            val base = other + lmWeight * lex.lm(i, prev)
            if (base <= worst) continue            // the path cost can only lower it further
            // ideal polyline -> N points at equal steps, compared with the finger's points; stop when it cannot win
            scored++
            val m = len[i].toInt(); val o = off[i]
            var ideal = 0f
            for (j in 0 until m) { val k = flat[o + j]; ix[j] = cx[k]; iy[j] = cy[k]; if (j > 0) ideal += pair[flat[o + j - 1] * nk + k] }
            val step = ideal / (N - 1)
            var seg = 1; var acc = 0f
            var segLen = dist(ix[1] - ix[0], iy[1] - iy[0])
            var cost = 0f
            val budget = if (worst == Float.NEGATIVE_INFINITY) Float.MAX_VALUE else (base - worst) / pathWeight * N   // total cost allowed before it drops out
            var p = 0
            while (p < N) {
                val target = p * step
                while (seg < m - 1 && acc + segLen < target) { acc += segLen; seg++; segLen = dist(ix[seg] - ix[seg - 1], iy[seg] - iy[seg - 1]) }
                val t = if (segLen > 1e-6f) ((target - acc) / segLen).coerceIn(0f, 1f) else 0f
                val qx = ix[seg - 1] + (ix[seg] - ix[seg - 1]) * t
                val qy = iy[seg - 1] + (iy[seg] - iy[seg - 1]) * t
                val d = dist(qx - up[2 * p], qy - up[2 * p + 1]) - tolerance
                if (d > 0f) { cost += d; if (cost >= budget) break }
                p++
            }
            if (p < N) continue
            val s = base - pathWeight * cost / N
            if (s <= worst) continue
            var pos = maxOut - 1
            while (pos > 0 && bestS[pos - 1] < s) { bestS[pos] = bestS[pos - 1]; bestW[pos] = bestW[pos - 1]; pos-- }
            bestS[pos] = s; bestW[pos] = i
            worst = bestS[maxOut - 1]
        }
        val seen = cn
        val t1 = System.nanoTime()
        stats = "points=$n words=${lex.words.size} cheapOk=$seen scored=$scored prep=${(tPrep - t0) / 1000}us loop=${(t1 - tPrep) / 1000}us"
        return (0 until maxOut).filter { bestW[it] >= 0 }.map { Candidate(lex.words[bestW[it]], bestS[it]) }
    }

    private fun dist(dx: Float, dy: Float): Float = sqrt(dx * dx + dy * dy)

    /** The finger path as [N] points at equal arc-length steps, into [up]. */
    private fun resampleUser(x: FloatArray, y: FloatArray, n: Int, total: Float) {
        val step = total / (N - 1)
        var seg = 1; var acc = 0f
        var segLen = dist(x[1] - x[0], y[1] - y[0])
        for (p in 0 until N) {
            val target = if (p * step < total) p * step else total
            while (seg < n - 1 && acc + segLen < target) { acc += segLen; seg++; segLen = dist(x[seg] - x[seg - 1], y[seg] - y[seg - 1]) }
            val t = if (segLen > 1e-6f) ((target - acc) / segLen).coerceIn(0f, 1f) else 0f
            up[2 * p] = x[seg - 1] + (x[seg] - x[seg - 1]) * t
            up[2 * p + 1] = y[seg - 1] + (y[seg] - y[seg - 1]) * t
        }
    }

    private companion object { const val N = 40; const val MAX_KEYS = 24 }
}
