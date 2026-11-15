package com.example.onerow.engine

import java.io.DataInputStream
import java.io.InputStream
import kotlin.math.ln

/**
 * Word list + unigram/bigram language model + letter trie, for one language.
 *
 * The big dictionary is a compact breadth-first array trie read from a binary file (see tools/build_lexicon.py for
 * the format): a node is an Int, its children are consecutive edges, so the child reached by edge e is node e+1.
 * Nothing is parsed or allocated per word, which keeps hundreds of thousands of word forms cheap in memory and load
 * time. Words the user adds while typing live in a small overlay (extra nodes numbered after the static ones), so
 * the static arrays never change.
 *
 * Word indices are positions in the binary file (grouped by trie node), not frequency ranks.
 */
class Lexicon private constructor(
    val alphabet: Alphabet,
    private val edgeStart: IntArray,           // node -> first edge; node n has edges edgeStart[n] until edgeStart[n+1]
    private val edgeLetter: ByteArray,         // edge -> letter index (ascending within a node)
    private val wordStart: IntArray,           // node -> first word ending there
    private val wordOff: IntArray,             // word -> start in [pool]
    private val pool: ByteArray,               // UTF-8 display forms
    logUniInit: FloatArray,                    // ln P(word)
    private val topNodes: IntArray,            // nodes with more than TOP_K words below them (ascending)
    private val topData: IntArray,             // their TOP_K best words, TOP_K per node, -1 = none
) {
    private val staticNodes = edgeStart.size - 1
    private val staticWords = wordOff.size - 1

    private val bigram = HashMap<Long, Float>()
    private val followers = HashMap<Int, IntArray>()     // prev -> likely next words, best first
    private val starts = HashMap<Int, Float>()           // P(word | sentence start)
    private var startOrder = EMPTY

    var logUni: FloatArray = logUniInit
        private set
    private val removed = HashSet<Int>()
    private val userWords = HashSet<Int>()

    // ---- overlay for words added at run time ----
    private val extraWords = ArrayList<String>()
    private val dynChild = HashMap<Long, Int>()          // (node shl 8 | letter) -> node
    private var dynNodes = 0
    private val dynWords = HashMap<Int, IntArray>()      // node -> user words ending there
    private val topOverride = HashMap<Int, IntArray>()   // node -> best words, where it differs from the file

    /** All words, by index (display forms: "I'm", "Haus", "café"). */
    val words: List<String> = object : AbstractList<String>() {
        override val size: Int get() = staticWords + extraWords.size
        override fun get(index: Int): String = surfaceOf(index)
    }

    private fun surfaceOf(i: Int): String =
        if (i < staticWords) String(pool, wordOff[i], wordOff[i + 1] - wordOff[i], Charsets.UTF_8) else extraWords[i - staticWords]

    // ---- trie ----

    /** The child of [node] along [letter] (an alphabet index), or -1. */
    fun child(node: Int, letter: Int): Int {
        if (node < staticNodes) {
            var e = edgeStart[node]; val end = edgeStart[node + 1]
            while (e < end) {
                val l = edgeLetter[e].toInt()
                if (l == letter) return e + 1
                if (l > letter) break
                e++
            }
        }
        if (dynChild.isEmpty()) return -1
        return dynChild[(node.toLong() shl 8) or letter.toLong()] ?: -1
    }

    /** Words whose letters-only key ends exactly at [node]. */
    fun wordsAt(node: Int): IntArray {
        val s = if (node < staticNodes) wordStart[node] else 0
        val e = if (node < staticNodes) wordStart[node + 1] else 0
        val dyn = if (dynWords.isEmpty()) null else dynWords[node]
        if (e == s && dyn == null) return EMPTY
        val out = IntArray((e - s) + (dyn?.size ?: 0))
        for (k in s until e) out[k - s] = k
        if (dyn != null) System.arraycopy(dyn, 0, out, e - s, dyn.size)
        return out
    }

    /** The best few words (by unigram) in the subtree of [node], best first. */
    fun top(node: Int): IntArray {
        if (topOverride.isNotEmpty()) topOverride[node]?.let { return it }
        if (node >= staticNodes) return EMPTY
        val k = java.util.Arrays.binarySearch(topNodes, node)
        if (k >= 0) {
            var n = 0
            while (n < TOP_K && topData[k * TOP_K + n] >= 0) n++
            return IntArray(n) { topData[k * TOP_K + it] }
        }
        // a small subtree (at most TOP_K words): collect it
        val found = ArrayList<Int>(TOP_K)
        val stack = IntArray(64); var sp = 0; stack[sp++] = node
        while (sp > 0 && found.size <= TOP_K) {
            val n = stack[--sp]
            for (w in wordStart[n] until wordStart[n + 1]) found += w
            for (e in edgeStart[n] until edgeStart[n + 1]) if (sp < stack.size) stack[sp++] = e + 1
        }
        found.sortByDescending { logUni[it] }
        return found.take(TOP_K).toIntArray()
    }

    private fun pathOf(key: IntArray): IntArray {
        val path = IntArray(key.size + 1); var n = 0; var len = 1
        for (c in key) { n = child(n, c); if (n < 0) break; path[len++] = n }
        return path.copyOf(len)
    }

    /** Index of [word] in the dictionary, or -1 if unknown or removed by the user. */
    fun indexOf(word: String): Int {
        val key = alphabet.key(word)
        if (key.isEmpty()) return -1
        var n = 0
        for (c in key) { n = child(n, c); if (n < 0) return -1 }
        val low = word.lowercase()
        for (i in wordsAt(n)) if (i !in removed && surfaceOf(i).lowercase() == low) return i
        return -1
    }

    fun isRemoved(i: Int) = i in removed

    /** Words the user typed on purpose; guesses prefer them. */
    fun isUser(i: Int) = i in userWords

    /** Hides a word from guesses for good (until the user types it again on purpose). */
    fun remove(i: Int) { removed += i }

    private fun pushTop(node: Int, i: Int) {
        val merged = top(node).toMutableList()
        if (i !in merged) merged += i
        merged.sortByDescending { logUni[it] }
        topOverride[node] = merged.take(TOP_K).toIntArray()
    }

    /** Adds a user word to the dictionary and the trie; returns its index. */
    fun addWord(surface: String, logP: Float): Int {
        val i = staticWords + extraWords.size
        extraWords.add(surface)
        logUni = logUni.copyOf(i + 1).also { it[i] = logP }
        userWords += i
        val key = alphabet.key(surface)
        if (key.isEmpty()) return i
        var n = 0; val path = ArrayList<Int>(key.size + 1); path += 0
        for (c in key) {
            var ch = child(n, c)
            if (ch < 0) { ch = staticNodes + dynNodes++; dynChild[(n.toLong() shl 8) or c.toLong()] = ch }
            n = ch; path += n
        }
        dynWords[n] = (dynWords[n] ?: EMPTY) + i
        for (node in path) pushTop(node, i)
        return i
    }

    /** Raises a word's prior (user words get higher priority the more they are used). */
    fun setLogProb(i: Int, logP: Float) {
        logUni[i] = logP
        userWords += i
        for (node in pathOf(alphabet.key(surfaceOf(i)))) pushTop(node, i)
    }

    /** ln P(word | prev); prev < 0 means sentence start / no context. Interpolated with the unigram. */
    fun lm(word: Int, prev: Int): Float {
        val pu = Math.exp(logUni[word].toDouble())
        val pc = if (prev < 0) starts[word] else bigram[key(prev, word)]
        val p = if (pc != null) 0.5 * pc + 0.5 * pu else 0.5 * pu
        return ln(p).toFloat()
    }

    /** Largest P(word | prev) over all words (prev < 0: sentence start); an upper bound for pruning. */
    fun maxContextProb(prev: Int): Double {
        if (prev < 0) return startOrder.firstOrNull()?.let { (starts[it] ?: 0f).toDouble() } ?: 0.0
        val f = followers[prev]?.firstOrNull() ?: return 0.0
        return (bigram[key(prev, f)] ?: 0f).toDouble()
    }

    /** Most likely next words after [prev] (prev < 0: likely sentence starters). */
    fun nextWords(prev: Int, n: Int): List<String> {
        val ids = if (prev < 0) startOrder else followers[prev] ?: return emptyList()
        return ids.asSequence().filter { it !in removed }.take(n).map { surfaceOf(it) }.toList()
    }

    /** Reads "<prev>\t<next>\t<p>" lines ("^" = sentence start), sorted by prev, then probability descending. */
    private fun loadBigrams(bi: InputStream) {
        val fol = HashMap<Int, ArrayList<Int>>()
        val stList = ArrayList<Pair<Int, Float>>()
        bi.bufferedReader().forEachLine {
            val t = it.split('\t'); if (t.size < 3) return@forEachLine
            val b = indexOf(t[1]); if (b < 0) return@forEachLine
            val p = t[2].toFloat()
            if (t[0] == "^") { starts[b] = p; stList += b to p; return@forEachLine }
            val a = indexOf(t[0]); if (a < 0) return@forEachLine
            bigram[key(a, b)] = p
            fol.getOrPut(a) { ArrayList() } += b
        }
        for ((k, v) in fol) followers[k] = v.toIntArray()
        startOrder = stList.sortedByDescending { it.second }.map { it.first }.toIntArray()
    }

    companion object {
        val EMPTY = IntArray(0)
        const val TOP_K = 4
        const val ROOT = 0
        fun key(a: Int, b: Int) = (a.toLong() shl 32) or b.toLong()

        /** Loads <lang>.lex (the trie) and <lang>_bi.tsv (the bigrams; null = words only, e.g. for spell checking). */
        fun loadBinary(alphabet: Alphabet, lex: InputStream, bi: InputStream?): Lexicon {
            val inp = DataInputStream(lex.buffered(1 shl 16))
            val magic = ByteArray(4); inp.readFully(magic)
            check(String(magic, Charsets.US_ASCII) == "OLX1") { "not a lexicon file" }
            val nodes = le(inp); val edges = le(inp); val words = le(inp); val poolBytes = le(inp); val tops = le(inp)
            val childCount = ByteArray(nodes).also { inp.readFully(it) }
            val edgeLetter = ByteArray(edges).also { inp.readFully(it) }
            val wordsAt = ByteArray(nodes).also { inp.readFully(it) }
            val wordLen = ByteArray(words).also { inp.readFully(it) }
            val pool = ByteArray(poolBytes).also { inp.readFully(it) }
            val edgeStart = IntArray(nodes + 1)
            for (n in 0 until nodes) edgeStart[n + 1] = edgeStart[n] + (childCount[n].toInt() and 0xFF)
            val wordStart = IntArray(nodes + 1)
            for (n in 0 until nodes) wordStart[n + 1] = wordStart[n] + (wordsAt[n].toInt() and 0xFF)
            val wordOff = IntArray(words + 1)
            for (i in 0 until words) wordOff[i + 1] = wordOff[i] + (wordLen[i].toInt() and 0xFF)
            val logUni = FloatArray(words)
            val buf = ByteArray(1 shl 16)
            var i = 0
            while (i < words) {                                   // 16-bit ln(freq) * 1000
                val n = minOf(words - i, buf.size / 2); inp.readFully(buf, 0, n * 2)
                for (k in 0 until n) logUni[i + k] = ((buf[2 * k].toInt() and 0xFF) or (buf[2 * k + 1].toInt() shl 8)).toShort() / 1000f
                i += n
            }
            val topNodes = readInts(inp, tops)
            val topData = readInts(inp, tops * TOP_K)
            check(edgeStart[nodes] == edges && wordStart[nodes] == words && wordOff[words] == poolBytes) { "corrupt lexicon" }
            return Lexicon(alphabet, edgeStart, edgeLetter, wordStart, wordOff, pool, logUni, topNodes, topData)
                .also { if (bi != null) it.loadBigrams(bi) }
        }

        private fun le(inp: DataInputStream): Int = Integer.reverseBytes(inp.readInt())

        private fun readInts(inp: DataInputStream, count: Int): IntArray {
            val out = IntArray(count); val buf = ByteArray(1 shl 16); var i = 0
            while (i < count) {
                val n = minOf(count - i, buf.size / 4); inp.readFully(buf, 0, n * 4)
                for (k in 0 until n) out[i + k] = (buf[4 * k].toInt() and 0xFF) or ((buf[4 * k + 1].toInt() and 0xFF) shl 8) or
                    ((buf[4 * k + 2].toInt() and 0xFF) shl 16) or (buf[4 * k + 3].toInt() shl 24)
                i += n
            }
            return out
        }
    }
}
