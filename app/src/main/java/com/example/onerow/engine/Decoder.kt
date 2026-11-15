package com.example.onerow.engine

class Candidate(val word: String, val score: Float, val isLiteral: Boolean = false)

/**
 * Beam search over the trie: score = sum of per-tap spatial ln-likelihoods + lmWeight * ln P(word | prev).
 * Also proposes completions of the typed prefix and the literal nearest-key string.
 */
class Decoder(
    private val lex: Lexicon,
    var lmWeight: Float = 1.0f,
    var beamWidth: Int = 200,
    var completionPenalty: Float = 1.2f,   // per untyped char
    var oovLogProb: Float = -17f,
    var userBonus: Float = 3.5f,            // ln-boost for words the user typed on purpose (~30x)
    var heldBonus: Float = 4f,              // per hold-and-slide letter, for the literal reading
    var spatialFloor: Float = -9f,          // ignore letters further than this (≈4.2 sigma)
) {
    private class State(val node: Int, val score: Float)

    fun decode(taps: List<FloatArray>, prev: Int, max: Int = 5): List<Candidate> {
        if (taps.isEmpty()) return emptyList()
        // literal: nearest key for each tap (names, slang, passwords, exact letters)
        val sb = StringBuilder(); var lsc = 0f
        for (t in taps) { var bi = 0; for (l in 1 until t.size) if (t[l] > t[bi]) bi = l; sb.append(lex.alphabet.letters[bi]); lsc += t[bi] }
        // a letter picked by hold-and-slide (one-hot vector) is a deliberate act: it makes the literal text more believable
        val held = taps.count { t -> t.count { it < -20f } >= t.size - 1 }
        val literal = Candidate(sb.toString(), lsc + lmWeight * oovLogProb + heldBonus * held, true)

        var beam = listOf(State(Lexicon.ROOT, 0f))
        for (t in taps) {
            val next = ArrayList<State>(beam.size * 4)
            for (s in beam) for (l in 0 until t.size) {
                val c = lex.child(s.node, l)
                if (c < 0) continue
                val p = t[l]
                if (p < spatialFloor) continue
                next += State(c, s.score + p)
            }
            if (next.isEmpty()) return listOf(literal)   // nothing in the dictionary fits: only the literal text
            beam = if (next.size > beamWidth) next.sortedByDescending { it.score }.take(beamWidth) else next
        }
        val best = HashMap<String, Candidate>()
        fun offer(w: String, sc: Float) {
            val k = w.lowercase()
            val cur = best[k]
            if (cur == null || sc > cur.score) best[k] = Candidate(w, sc, false)
        }
        val n = taps.size
        for (s in beam) {
            for (w in lex.wordsAt(s.node)) if (!lex.isRemoved(w)) offer(lex.words[w], s.score + lmWeight * lex.lm(w, prev) + bonus(w))
            for (w in lex.top(s.node)) {
                if (lex.isRemoved(w)) continue
                val extra = keyLen(lex.words[w]) - n
                if (extra > 0) offer(lex.words[w], s.score + lmWeight * lex.lm(w, prev) + bonus(w) - completionPenalty * extra * (if (lex.isUser(w)) 0.5f else 1f))
            }
        }
        // literal competes like any candidate; if it equals a dictionary word, keep the dictionary form
        val lk = literal.word.lowercase()
        val out = ArrayList<Candidate>()
        val all = best.values.toMutableList()
        if (best[lk] == null) all += literal
        all.sortByDescending { it.score }
        out += all.take(max)
        if (out.none { it.word.lowercase() == lk }) out += literal    // always reachable
        return out
    }

    private fun bonus(w: Int) = if (lex.isUser(w)) userBonus else 0f

    private fun keyLen(w: String) = lex.alphabet.key(w).size
}
