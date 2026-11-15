package com.example.onerow

import android.content.Intent
import android.widget.Toast
import java.io.File
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.view.inputmethod.EditorInfo
import com.example.onerow.engine.Candidate
import com.example.onerow.engine.Decoder
import com.example.onerow.engine.GlideDecoder
import com.example.onerow.engine.GlideLayout
import com.example.onerow.engine.Lexicon

class OneRowService : InputMethodService(), KeyboardView.Listener {
    private companion object { const val AUTO_SPACE_AFTER = ".,!?:;" }
    private lateinit var kv: KeyboardView
    private lateinit var dock: FrameLayout          // the input view: holds the keyboard when docked or split, a sliver when floating
    private lateinit var floating: FloatingKeyboard
    private var layoutMode = "docked"               // "docked", "floating" or "split"
    private lateinit var prefs: Prefs
    private lateinit var lang: Lang
    private class Engine(val lex: Lexicon, val dec: Decoder, val user: UserDictionary) { val glide by lazy { GlideDecoder(lex) } }
    private val loaded = HashMap<String, Engine>()   // per-language engines, loaded on demand
    private var user: UserDictionary? = null
    private var selStart = 0                       // tracked selection, so the cursor can be moved without key events
    private var selEnd = 0
    private var ignoreSelUntil = 0L               // selection reports right after we re-open a word describe stale states
    private var resumed = false                   // composing was re-opened from existing text (don't learn it unchanged)
    private var justAutoSpaced = false            // the last thing typed was a punctuation mark plus our automatic space
    private var glideWord: String? = null         // the word a glide just typed (followed by our space): guesses replace it
    private var glideAlts: List<String> = emptyList()
    private var lastRaw = ""                      // unstyled text currently composing
    private var lastDeliberate = false            // ... and whether every letter was typed exactly
    private val ui = Handler(Looper.getMainLooper())

    // Engine (loaded off the main thread; until then letters are committed literally)
    @Volatile private var lex: Lexicon? = null
    private var decoder: Decoder? = null

    // Current word being composed
    private val taps = ArrayList<FloatArray>()
    private val shown = ArrayList<String?>()   // parallel to taps: national letter typed exactly (e.g. "ü"), else null
    private val exact = ArrayList<Boolean>()   // parallel to taps: letter was chosen precisely (hold + slide)
    private var prevIdx = -1
    private var capFirst = false
    private var capAll = false
    private var cands: List<Candidate> = emptyList()
    private var decoding = true       // false in password / non-text fields

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        lang = prefs.current()
        loadEngine(lang)
    }

    /** Loads (off the main thread) the dictionary for [l] unless cached, then makes it current if still wanted. */
    private fun loadEngine(l: Lang) {
        loaded[l.code]?.let { lex = it.lex; decoder = it.dec; user = it.user; return }
        lex = null; decoder = null; user = null
        Thread {
            val t0 = System.nanoTime()
            val lx = Lexicon.loadBinary(l.alphabet, Dictionaries.open(this, "${l.code}.lex")!!, Dictionaries.open(this, "${l.code}_bi.tsv"))
            val ud = UserDictionary(File(filesDir, "user_${l.code}.txt"))
            // the user's own words outrank the shipped ones; words they removed stay gone
            for ((w, c) in ud.counts) {
                val i = lx.indexOf(w); val lp = UserDictionary.logProb(c)
                if (i < 0) lx.addWord(w, lp) else if (lx.logUni[i] < lp) lx.setLogProb(i, lp)
            }
            for (w in ud.removed) { val i = lx.indexOf(w); if (i >= 0) lx.remove(i) }
            val eng = Engine(lx, Decoder(lx), ud)
            val rt = Runtime.getRuntime()
            android.util.Log.d("OneRowEngine", "${l.code}: ${lx.words.size} words in ${(System.nanoTime() - t0) / 1_000_000} ms, heap in use ${(rt.totalMemory() - rt.freeMemory()) / 1_000_000} MB")
            if (prefs.swipeTyping) eng.glide.warmUp()       // build the swipe index and warm the code here, not on the first swipe
            ui.post {
                loaded[l.code] = eng
                if (lang.code == l.code) { lex = lx; decoder = eng.dec; user = ud; if (::kv.isInitialized && taps.isEmpty()) refresh() }
            }
        }.start()
    }

    private fun applyPrefs() {
        val cur = prefs.current()
        kv.languages = prefs.enabled
        kv.heightScale = prefs.heightScale
        kv.hapticsOn = prefs.haptics
        kv.themeMode = prefs.theme
        kv.swipeTyping = prefs.swipeTyping
        if (prefs.swipeTyping) loaded[lang.code]?.let { e -> Thread { e.glide.warmUp() }.start() }   // switched on after the engine loaded
        kv.tapSigma = floatArrayOf(0.62f, 0.9f, 1.3f)[prefs.tolerance]
        if (cur.code != lang.code) switchLanguage(cur)
    }

    private fun switchLanguage(l: Lang) {
        finishComposing(); endGlide()
        lang = l
        prefs.currentCode = l.code
        kv.setLanguage(l)
        loadEngine(l)
        refresh()
    }

    override fun onCreateInputView(): View {
        kv = KeyboardView(this, this, lang)
        kv.languages = prefs.enabled
        kv.heightScale = prefs.heightScale
        kv.hapticsOn = prefs.haptics
        kv.themeMode = prefs.theme
        kv.swipeTyping = prefs.swipeTyping
        kv.splitAvailable = isTablet()
        if (::floating.isInitialized) floating.hide()
        floating = FloatingKeyboard(this, prefs)
        dock = FrameLayout(this)
        layoutMode = prefs.layoutMode.let { if (it == "split" && !isTablet()) "docked" else it }
        arrange()
        return dock
    }

    private fun isTablet() = resources.configuration.smallestScreenWidthDp >= 600

    /** Puts the keyboard where [layoutMode] says: in the input view (docked or split) or in the floating window. */
    private fun arrange() {
        kv.split = layoutMode == "split"
        if (layoutMode == "floating") {
            (kv.parent as? ViewGroup)?.let { if (it !== dock) it.removeView(kv) }
            dock.removeAllViews()
            dock.addView(View(this), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))      // the input view shrinks to a sliver
            showFloatingSoon()
        } else {
            floating.release(kv)
            if (kv.parent !== dock) {
                (kv.parent as? ViewGroup)?.removeView(kv)
                dock.removeAllViews()
                dock.addView(kv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }
        dock.requestLayout()
    }

    private fun showFloatingSoon() {
        dock.post {
            if (layoutMode == "floating" && isInputViewShown) {
                val p = Themes.palette(prefs.theme, resources.configuration)
                floating.show(dock, kv, p.strip, p.dim)
            }
        }
    }

    override fun onWindowShown() { super.onWindowShown(); if (::floating.isInitialized && layoutMode == "floating") showFloatingSoon() }

    override fun onWindowHidden() { super.onWindowHidden(); if (::floating.isInitialized) floating.hide() }

    override fun onDestroy() { if (::floating.isInitialized) floating.hide(); super.onDestroy() }

    /** In split layout only the two halves (and the guess strip) take touches; the gap between them belongs to the app. */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        if (layoutMode == "split" && ::kv.isInitialized && kv.isShown) {
            val loc = IntArray(2); kv.getLocationInWindow(loc)
            outInsets.touchableRegion.setEmpty()
            for (r in kv.touchRects()) { r.offset(loc[0], loc[1]); outInsets.touchableRegion.union(r) }
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        }
    }

    override fun onMode(mode: String) { ui.post { setLayoutMode(mode) } }

    private fun setLayoutMode(m: String) {
        val target = if (m == "split" && !isTablet()) "docked" else m
        if (target == layoutMode) return
        layoutMode = target; prefs.layoutMode = target
        arrange()
        kv.invalidate()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::kv.isInitialized && prefs.theme == Themes.AUTO) { kv.applyTheme(); applyNavBar() }      // system light/dark switched
    }

    /** Paints the system navigation bar under the keyboard in the keyboard's color (otherwise each phone picks its own: black, white...). */
    @Suppress("DEPRECATION")
    private fun applyNavBar() {
        val w = window?.window ?: return
        val c = Themes.palette(prefs.theme, resources.configuration).panel
        w.navigationBarColor = c
        if (Build.VERSION.SDK_INT >= 26) {
            val light = android.graphics.Color.luminance(c) > 0.5f
            val f = w.decorView.systemUiVisibility
            w.decorView.systemUiVisibility = if (light) f or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else f and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        selStart = info.initialSelStart; selEnd = info.initialSelEnd
        endGlide()
        taps.clear(); exact.clear(); shown.clear()
        applyPrefs()
        applyNavBar()
        kv.resetForNewField()
        val v = info.inputType and InputType.TYPE_MASK_VARIATION
        val isText = (info.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
        val pw = v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            v == InputType.TYPE_TEXT_VARIATION_URI || v == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        decoding = isText && !pw &&
            (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0
        refresh()
    }

    override fun onUpdateSelection(oldSs: Int, oldSe: Int, newSs: Int, newSe: Int, cs: Int, ce: Int) {
        super.onUpdateSelection(oldSs, oldSe, newSs, newSe, cs, ce)
        selStart = newSs; selEnd = newSe
        if (glideWord != null && !glideIntact()) endGlide()        // caret moved away from the glided word
        // The caret is not at the end of our composing word any more (user tapped elsewhere, or inside the word,
        // or dragged space): settle the word as typed so later edits are plain text edits at the caret.
        // Without this, the stale composing region swallowed new input and snapped the caret back to the end.
        if (taps.isNotEmpty() && android.os.SystemClock.uptimeMillis() >= ignoreSelUntil &&
            (cs < 0 || ce < 0 || newSs != newSe || newSs != ce)) {
            if (cs >= 0 && ce >= 0) finishComposing() else { taps.clear(); exact.clear(); shown.clear(); cands = emptyList() }
        }
        if (::kv.isInitialized && taps.isEmpty()) refresh()
    }

    // ---------- context helpers ----------

    private fun textBefore(n: Int = 60): String = currentInputConnection?.getTextBeforeCursor(n, 0)?.toString() ?: ""

    /** The word (letters/apostrophes) directly before the caret, or "". */
    private fun currentWord(): String {
        val t = textBefore(40)
        var i = t.length
        while (i > 0 && (t[i - 1].isLetter() || t[i - 1] == '\'')) i--
        return t.substring(i)
    }

    /** Index of the word before the cursor (ignoring the composing word), or -1 at sentence start. */
    private fun previousWordIdx(): Int {
        val l = lex ?: return -1
        val t = textBefore().trimEnd(' ', '\n')
        if (t.isEmpty() || t.last() in ".!?") return -1
        var i = t.length
        while (i > 0 && (t[i - 1].isLetter() || t[i - 1] == '\'')) i--
        return l.indexOf(t.substring(i))
    }

    private fun styled(w: String): String = when {
        capAll -> w.uppercase()
        capFirst -> w.replaceFirstChar { it.uppercase() }
        else -> w
    }

    private fun refresh() {
        if (glideWord != null) return                    // the guesses for the glided word stay until the next input
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo ?: return
        val isText = (info.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
        if (isText && kv.shift == KeyboardView.Shift.OFF && ic.getCursorCapsMode(info.inputType) != 0)
            kv.shift = KeyboardView.Shift.ONCE
        if (taps.isEmpty()) {
            kv.bestIndex = -1
            val l = lex
            kv.suggestions = when {
                !decoding -> emptyList()
                l == null -> emptyList()
                completionsForWordAtCaret(l) != null -> completionsForWordAtCaret(l)!!
                else -> {
                    val p = previousWordIdx()
                    val n = l.nextWords(p, 12)
                    val base = if (n.size >= 4) n else (n + listOf("I", "the", "and", "to")).distinct().take(4)
                    if (kv.shift != KeyboardView.Shift.OFF) base.map { it.replaceFirstChar { c -> c.uppercase() } } else base
                }
            }
        }
    }

    /** Guesses completing the word the caret touches (not composing, e.g. after tapping into text). */
    private fun completionsForWordAtCaret(l: Lexicon): List<String>? {
        val dec = decoder ?: return null
        val w = currentWord()
        if (w.isEmpty()) return null
        val key = l.alphabet.key(w)
        if (key.isEmpty()) return null
        val taps = key.map { k -> FloatArray(l.alphabet.n) { -30f }.also { it[k] = 0f } }
        val out = dec.decode(taps, -1, 12).map { it.word }.filter { !it.equals(w, ignoreCase = true) }
        return (listOf(w) + out).take(12)
    }

    // ---------- composing ----------

    private fun updateComposing() {
        val ic = currentInputConnection ?: return
        val dec = decoder ?: return
        val lx = lex ?: return
        var list = dec.decode(taps, prevIdx, 14)
        if (list.isEmpty()) return
        // Full keyboard / hold-and-slide: the input gets exactly what was typed. Guesses are only offered.
        val deliberate = exact.all { it }
        if (deliberate) {
            val typed = taps.withIndex().joinToString("") { (i, t) ->
                shown.getOrNull(i) ?: lx.alphabet.letters[t.indices.maxByOrNull { t[it] }!!].toString()
            }
            list = listOf(Candidate(typed, 0f, true)) + list.filter { !it.word.equals(typed, ignoreCase = true) }
        }
        lastRaw = list[0].word
        // learn what was typed on purpose: every letter held, or some held and the text is not a dictionary word
        lastDeliberate = deliberate || (list[0].isLiteral && exact.any { it })
        cands = list
        val best = list[0]
        val rest = list.drop(1)
        val lit = list.firstOrNull { it.isLiteral }
        // bar like Minuum: [what you typed] [best guess] [alternative]
        val left = if (lit != null && lit !== best) lit else rest.firstOrNull()
        val others = rest.filter { it !== left }
        ic.setComposingText(styled(best.word), 1)
        kv.suggestions = (listOf(left?.word ?: "", best.word) + others.map { it.word }.take(10).let { if (it.size < 2) it + List(2 - it.size) { "" } else it })
            .map { if (it.isEmpty()) it else styled(it) }
        kv.bestIndex = 1
    }

    /** Accept the composing text as is (the best candidate is already displayed). */
    private fun finishComposing() {
        if (taps.isEmpty()) return
        if (decoding && lastDeliberate && !resumed) learn(lastRaw)
        currentInputConnection?.finishComposingText()
        taps.clear(); exact.clear(); shown.clear(); cands = emptyList()
    }

    /** A word typed on purpose: unknown ones join the dictionary; used ones climb in priority. */
    private fun learn(word: String) {
        val lx = lex ?: return; val ud = user ?: return
        if (word.count { it.isLetter() } < 2) return
        val known = lx.indexOf(word)
        if (known >= 0 && !ud.has(word)) return                 // shipped word: nothing to learn
        val lp = UserDictionary.logProb(ud.bump(word))
        if (known < 0) lx.addWord(word, lp) else if (lx.logUni[known] < lp) lx.setLogProb(known, lp)
    }

    // ---------- KeyboardView.Listener ----------

    override fun onLetter(ch: Char, logp: FloatArray, exactPick: Boolean) = addTap(ch, logp, exactPick, null)

    /** A national letter (ü, ñ, ç ...) picked with hold-and-slide: exact, and typed as itself. */
    override fun onAccent(base: Char, shown: String) {
        val n = lang.alphabet.n
        addTap(base, FloatArray(n) { -30f }.also { it[lang.alphabet.index(base)] = 0f }, true, shown)
    }

    private fun addTap(ch: Char, logp: FloatArray, exactPick: Boolean, shownText: String?) {
        justAutoSpaced = false
        endGlide()
        val ic = currentInputConnection ?: return
        fun literal(): String {
            val lx = lex
            val c = shownText ?: (if (lx != null) lx.alphabet.letters[logp.indices.maxByOrNull { logp[it] }!!] else ch).toString()
            return if (kv.shift != KeyboardView.Shift.OFF) c.uppercase() else c
        }
        if (!decoding || decoder == null) { ic.commitText(literal(), 1); return }
        if (taps.isEmpty()) {
            // Caret touching an existing word (editing in the middle): insert literally, no guessing there.
            val b = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
            val a = ic.getTextAfterCursor(1, 0)?.toString().orEmpty()
            if ((b.isNotEmpty() && b.last().isLetter()) || (a.isNotEmpty() && a.first().isLetter())) {
                ic.commitText(literal(), 1)
                return
            }
            prevIdx = previousWordIdx()
            capFirst = kv.shift != KeyboardView.Shift.OFF
            capAll = kv.shift == KeyboardView.Shift.LOCK
        }
        resumed = false
        taps += logp
        exact += exactPick
        shown += shownText
        updateComposing()
    }

    override fun onText(text: String) {
        endGlide()
        // French elision (l', d', qu'): the apostrophe is already in the word, so no space after it
        if (text == " " && taps.isNotEmpty() && cands.firstOrNull()?.word?.endsWith("'") == true) { finishComposing(); return }
        finishComposing()
        val ic = currentInputConnection ?: return
        val wasAuto = justAutoSpaced
        justAutoSpaced = false
        // a space typed right after the automatic one is not needed
        if (text == " " && wasAuto && textBefore(1) == " ") return
        // ":)" / ";)" smileys: take the automatic space back before a closing bracket
        if (decoding && wasAuto && text.length == 1 && text[0] in ")]}") {
            val b = textBefore(2)
            if (b.length == 2 && b[0] in ":;" && b[1] == ' ') ic.deleteSurroundingText(1, 0)
        }
        if (decoding && text.length == 1 && text[0] in AUTO_SPACE_AFTER) {
            ic.beginBatchEdit()
            // "word ." -> "word." (also joins "..." and "?!" after the automatic space)
            val b = textBefore(2)
            if (b.length == 2 && b[1] == ' ' && !b[0].isWhitespace()) ic.deleteSurroundingText(1, 0)
            val prev = textBefore(1).lastOrNull()
            ic.commitText(text, 1)
            val after = ic.getTextAfterCursor(1, 0)?.toString().orEmpty()
            val inNumber = prev != null && prev.isDigit() && text[0] in ".,:"          // 3.14  1,000  10:30
            if (after.isEmpty() && !inNumber && prev != null && !prev.isWhitespace()) {
                ic.commitText(" ", 1)
                justAutoSpaced = true
            }
            ic.endBatchEdit()
            return
        }
        ic.commitText(text, 1)
    }

    override fun onBackspace() {
        justAutoSpaced = false
        if (glideWord != null && taps.isEmpty() && glideIntact()) {          // delete the whole glided word
            currentInputConnection?.deleteSurroundingText(glideWord!!.length + 1, 0)
            endGlide(); refresh(); return
        }
        endGlide()
        if (taps.isNotEmpty()) {
            taps.removeAt(taps.size - 1); exact.removeAt(exact.size - 1); shown.removeAt(shown.size - 1)
            if (taps.isEmpty()) {
                currentInputConnection?.setComposingText("", 1)
                currentInputConnection?.finishComposingText()
                refresh()
            } else updateComposing()
            return
        }
        val ic = currentInputConnection ?: return
        if (!ic.getSelectedText(0).isNullOrEmpty()) { ic.commitText("", 1); return }       // delete the selection
        if (ic.getTextBeforeCursor(1, 0).isNullOrEmpty()) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); return }
        // Delete directly (a DEL key event is processed later, so the text would still look unchanged below).
        ic.deleteSurroundingTextInCodePoints(1, 0)
        resumeWordAtCaret()
    }

    /**
     * After deleting a symbol or space the caret can end up right after a word. Put that word back into
     * "being typed" mode, so guesses work again and new letters extend it instead of being glued on.
     */
    private fun resumeWordAtCaret() {
        if (!decoding || taps.isNotEmpty()) return
        val lx = lex ?: return
        val ic = currentInputConnection ?: return
        val after = ic.getTextAfterCursor(1, 0)?.toString().orEmpty()
        if (after.isNotEmpty() && after[0].isLetter()) return          // caret inside a word: leave it alone
        val before = textBefore(40)
        var i = before.length
        while (i > 0 && before[i - 1].isLetter()) i--
        val w = before.substring(i)
        if (w.isEmpty() || w.length > 30) return
        if (i > 0 && (before[i - 1] == '\'' || before[i - 1] == '-')) return   // part of a compound / contraction
        val newTaps = ArrayList<FloatArray>(); val newShown = ArrayList<String?>()
        for (ch in w) {
            val key = lx.alphabet.key(ch.toString())
            if (key.isEmpty()) return
            newTaps += FloatArray(lx.alphabet.n) { -30f }.also { it[key[0]] = 0f }
            newShown += if (lx.alphabet.index(ch) >= 0) null else ch.toString()        // keep accents as typed
        }
        ic.beginBatchEdit()
        ic.deleteSurroundingText(w.length, 0)
        prevIdx = previousWordIdx()
        taps.addAll(newTaps); shown.addAll(newShown); newTaps.forEach { exact += true }
        // keep the word's capitalization for the guesses ("This" -> "Thistle", not "thistle"); all-caps stays all-caps
        capAll = w.length > 1 && w.all { !it.isLetter() || it.isUpperCase() }
        capFirst = capAll || w[0].isUpperCase()
        resumed = true
        ignoreSelUntil = android.os.SystemClock.uptimeMillis() + 400
        updateComposing()                                              // re-opens the word as composing text
        ic.endBatchEdit()
    }

    override fun onDeleteWord() {
        justAutoSpaced = false
        endGlide()
        taps.clear(); exact.clear(); shown.clear()
        val ic = currentInputConnection ?: return
        ic.finishComposingText()
        val before = ic.getTextBeforeCursor(64, 0) ?: return
        if (before.isEmpty()) return
        var i = before.length
        while (i > 0 && before[i - 1].isWhitespace()) i--
        while (i > 0 && !before[i - 1].isWhitespace()) i--
        ic.deleteSurroundingText(before.length - i, 0)
    }

    override fun onRemoveWord(word: String) {
        val lx = lex ?: return; val ud = user ?: return
        val i = lx.indexOf(word)
        if (i < 0) { Toast.makeText(this, "\"$word\" is not in the dictionary", Toast.LENGTH_SHORT).show(); return }
        lx.remove(i); ud.forget(word)
        Toast.makeText(this, "Removed \"$word\"", Toast.LENGTH_SHORT).show()
        if (taps.isNotEmpty()) updateComposing() else refresh()
    }

    override fun onLanguage(code: String) { if (code != lang.code) switchLanguage(Languages.byCode(code)) }

    override fun onSettings() {
        finishComposing()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onVoice() {
        finishComposing()
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            for (ime in imm.enabledInputMethodList) for (i in 0 until ime.subtypeCount) {
                val st = ime.getSubtypeAt(i)
                if (st.mode == "voice") { switchInputMethod(ime.id, st); return }
            }
            android.widget.Toast.makeText(this, "No voice input enabled", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { /* voice not available */ }
    }

    override fun onEnter() {
        justAutoSpaced = false
        endGlide()
        finishComposing()
        val info = currentInputEditorInfo
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noAction = ((info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (info == null || noAction || action == EditorInfo.IME_ACTION_NONE ||
            action == EditorInfo.IME_ACTION_UNSPECIFIED
        ) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        else currentInputConnection?.performEditorAction(action)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        finishComposing()                          // a word still being typed when the field is left is not lost
        super.onFinishInputView(finishingInput)
    }

    /**
     * Moves the caret by setting the selection. Never sends DPAD keys: at the end of the text they
     * move focus out of the field and the input connection is lost.
     */
    override fun onMoveCursor(steps: Int) {
        justAutoSpaced = false
        endGlide()
        finishComposing()
        val ic = currentInputConnection ?: return
        val room = if (steps > 0) ic.getTextAfterCursor(steps, 0)?.length ?: 0
                   else ic.getTextBeforeCursor(-steps, 0)?.length ?: 0
        val n = minOf(kotlin.math.abs(steps), room)
        if (n == 0) return                                   // already at the edge
        val pos = if (steps > 0) selEnd + n else selStart - n
        ic.setSelection(pos, pos)
        selStart = pos; selEnd = pos                         // later steps arrive before the editor reports back
    }

    private fun endGlide() { glideWord = null; glideAlts = emptyList() }

    /** True while the text before the caret still ends with the word a glide typed plus our space. */
    private fun glideIntact(): Boolean { val g = glideWord ?: return false; return textBefore(g.length + 1).endsWith("$g ") }

    /** A word drawn across the letters of the full keyboard: type the best match, offer the others as guesses. */
    override fun onGlide(xs: FloatArray, ys: FloatArray, n: Int, layout: GlideLayout) {
        val eng = loaded[lang.code] ?: return           // dictionary still loading
        val ic = currentInputConnection ?: return
        if (!decoding) return
        val t0 = System.nanoTime()
        finishComposing(); endGlide()
        justAutoSpaced = false
        val prevW = previousWordIdx()
        val t1 = System.nanoTime()
        val list = eng.glide.decode(xs, ys, n, layout, prevW, 8)
        val t2 = System.nanoTime()
        if (list.isEmpty()) return
        capFirst = kv.shift != KeyboardView.Shift.OFF; capAll = kv.shift == KeyboardView.Shift.LOCK
        val words = list.map { styled(it.word) }.distinct()
        ic.beginBatchEdit()
        val b = textBefore(1)
        if (b.isNotEmpty() && !b.last().isWhitespace() && b.last() !in "([{\"'\u00AB\u201C-") ic.commitText(" ", 1)
        ic.commitText(words[0] + " ", 1)
        ic.endBatchEdit()
        glideWord = words[0]; glideAlts = words
        kv.suggestions = words; kv.bestIndex = 0
        android.util.Log.d("OneRowGlide", "ipc+prev=${(t1 - t0) / 1000}us decode=${(t2 - t1) / 1000}us commit=${(System.nanoTime() - t2) / 1000}us | ${eng.glide.stats}")
    }

    override fun onSuggestion(word: String) {
        justAutoSpaced = false
        val ic = currentInputConnection ?: return
        val gw = glideWord
        if (gw != null && taps.isEmpty() && glideIntact()) {         // a guess for a glided word replaces it
            ic.beginBatchEdit(); ic.deleteSurroundingText(gw.length + 1, 0); ic.commitText("$word ", 1); ic.endBatchEdit()
            glideWord = word; kv.bestIndex = glideAlts.indexOf(word)
            return
        }
        endGlide()
        ic.beginBatchEdit()
        if (taps.isNotEmpty()) {
            // composing: the guess replaces the composing text; then only the space is added
            if (decoding && lex?.indexOf(word) == -1) learn(word.lowercase())   // picked the 'typed' slot: deliberate
            ic.setComposingText(word, 1)
            ic.finishComposingText()
            taps.clear(); exact.clear(); shown.clear()
            ic.commitText(" ", 1)
        } else {
            // Not composing (the app dropped the composing region, or the caret sits at a word's end):
            // a tapped guess replaces the word touching the caret instead of being appended to it.
            val typed = currentWord()
            if (typed.isNotEmpty()) ic.deleteSurroundingText(typed.length, 0)
            if (decoding && lex?.indexOf(word) == -1) learn(word.lowercase())   // the typed slot again: silent, no prompt
            ic.commitText("$word ", 1)
        }
        ic.endBatchEdit()
        refresh()
    }
}
