package com.example.onerow

import android.content.Intent
import android.widget.Toast
import java.io.File
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.view.inputmethod.EditorInfo
import com.example.onerow.billing.Access
import com.example.onerow.billing.BillingManager
import com.example.onerow.billing.BillingStore
import com.example.onerow.engine.Calc
import com.example.onerow.engine.Numbers
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
    private var wordAutoSpaced = false            // the space before the caret was added by us after a swiped word or a picked guess (not typed by the user)
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
    /** Per tap of the word being typed: was Shift down for it (parallel to [taps]). */
    private val upper = ArrayList<Boolean>()
    private var cands: List<Candidate> = emptyList()
    private var decoding = true       // false in password / non-text fields

    private var bornAt = 0L
    private fun sinceBirth() = (System.nanoTime() - bornAt) / 1_000_000

    override fun onCreate() {
        super.onCreate()
        bornAt = System.nanoTime()
        prefs = Prefs(this)
        com.example.onerow.engine.Lexicon.trace = { android.util.Log.d("OneRowStartup", it) }
        lang = prefs.current()
        loadEngine(lang)
    }

    /** Loads (off the main thread) the dictionary for [l] unless cached, then makes it current if still wanted. */
    private fun loadEngine(l: Lang) {
        loaded[l.code]?.let { lex = it.lex; decoder = it.dec; user = it.user; return }
        lex = null; decoder = null; user = null
        Thread {
            val t0 = System.nanoTime()
            val lx = Lexicon.loadBinary(l.alphabet, Dictionaries.open(this, "${l.code}.lex")!!, null)
            val ud = UserDictionary(File(Storage.filesDir(this), "user_${l.code}.txt"))
            // the user's own words outrank the shipped ones; words they removed stay gone
            for ((w, c) in ud.counts) {
                val i = lx.indexOf(w); val lp = UserDictionary.logProb(c)
                if (i < 0) lx.addWord(w, lp) else if (lx.logUni[i] < lp) lx.setLogProb(i, lp)
            }
            for (w in ud.removed) { val i = lx.indexOf(w); if (i >= 0) lx.remove(i) }
            if (l.alphabet.folds.containsKey('ё')) lx.preferYo()
            for ((w, o) in ud.preferred) lx.promote(lx.indexOf(w), lx.indexOf(o))     // corrections the user made earlier
            val eng = Engine(lx, Decoder(lx), ud)
            eng.glide.applyTolerance(prefs.swipeTolerance)
            // the bigrams and the swipe index are independent: build them side by side
            val idxThread = Thread { if (prefs.swipeTyping) eng.glide.sync() }.also { it.start() }
            val tb = System.nanoTime()
            Dictionaries.open(this, "${l.code}_bi.tsv")?.let { lx.loadBigrams(it) }
            android.util.Log.d("OneRowStartup", "${l.code}: bigrams ${(System.nanoTime() - tb) / 1_000_000} ms (parallel with the swipe index)")
            idxThread.join()
            val rt = Runtime.getRuntime()
            android.util.Log.d("OneRowEngine", "${l.code}: ${lx.words.size} words in ${(System.nanoTime() - t0) / 1_000_000} ms, heap in use ${(rt.totalMemory() - rt.freeMemory()) / 1_000_000} MB")
            val tw = System.nanoTime()
            if (prefs.swipeTyping) eng.glide.warmUp()       // a few throwaway decodes, so the first swipe does not run cold code
            android.util.Log.d("OneRowStartup", "${l.code}: swipe warm-up ${(System.nanoTime() - tw) / 1_000_000} ms, ready ${sinceBirth()} ms after service start")
            ui.post {
                loaded[l.code] = eng
                if (lang.code == l.code) { lex = lx; decoder = eng.dec; user = ud; if (::kv.isInitialized && taps.isEmpty()) refresh() }
            }
        }.start()
    }

    private fun applyPrefs() {
        val cur = prefs.current()
        kv.languages = prefs.enabled
        kv.heightScale = scaleFor(layoutMode)
        kv.hapticsOn = prefs.haptics
        kv.themeMode = prefs.theme
        kv.swipeTyping = prefs.swipeTyping
        kv.numberRow = prefs.numberRow
        syncProof()
        loaded.values.forEach { it.glide.applyTolerance(prefs.swipeTolerance) }
        if (prefs.swipeTyping) loaded[lang.code]?.let { e -> Thread { e.glide.warmUp() }.start() }   // switched on after the engine loaded
        kv.tapSigma = floatArrayOf(0.62f, 0.9f, 1.3f)[prefs.tolerance]
        if (cur.code != lang.code && !(heEnglish && cur.code == "he")) switchLanguage(cur)      // Hebrew's Shift keeps the English layout from field to field
    }

    // ---- proofreading (on-device, see Proofread) ----
    private var proofUndo: String? = null           // the text before the last fix, while "undo" is offered
    private val proofReset = Runnable { proofUndo = null; syncProof() }

    // ---- subscription ----

    private lateinit var billing: BillingManager
    private var access: Access = Access.Locked

    /** Reads what the user may do (free weeks, subscription, forever) and applies it: locked = plain typing and a message in the guess bar. */
    private fun syncAccess() {
        access = BillingStore.access(this)
        val a = access
        kv.locked = a is Access.Locked
        kv.expiringDays = (a as? Access.Full)?.expiresInDays
        if (a is Access.Locked) { exitSelMode(); keypadAuto = false; kv.selHint = false }
        if (::kv.isInitialized && taps.isEmpty()) refresh()
        syncProof()
    }

    /** Asks Google Play again when the saved answer is old (a few hours), in the background. */
    private fun refreshBillingIfStale() {
        if (!::billing.isInitialized) billing = BillingManager(this) { ui.post { syncAccess() } }
        if (System.currentTimeMillis() - BillingStore.lastChecked(this) > 6L * 60 * 60 * 1000) billing.refresh()
    }

    private fun syncProof() {
        kv.proof = when {
            access is Access.Locked -> KeyboardView.ProofState.OFF
            !prefs.proofread || !Proofread.supports(lang.code) -> KeyboardView.ProofState.OFF
            proofUndo != null -> KeyboardView.ProofState.UNDO
            else -> KeyboardView.ProofState.READY
        }
    }

    private fun say(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onProofread() {
        val ic = currentInputConnection ?: return
        proofUndo?.let { original ->                   // the circle is an "undo" for a few seconds after a fix
            ui.removeCallbacks(proofReset)
            ic.beginBatchEdit(); ic.performContextMenuAction(android.R.id.selectAll); ic.commitText(original, 1); ic.endBatchEdit()
            proofUndo = null; syncProof(); return
        }
        if (kv.proof == KeyboardView.ProofState.BUSY) return
        finishComposing(); endGlide()
        val text = (ic.getTextBeforeCursor(5000, 0)?.toString() ?: "") + (ic.getTextAfterCursor(5000, 0)?.toString() ?: "")
        if (text.isBlank()) { say(getString(R.string.toast_nothing_to_proofread)); return }
        kv.proof = KeyboardView.ProofState.BUSY
        val code = lang.code
        Proofread.status(this, code) { st ->
            when (st) {
                Proofread.Status.AVAILABLE -> Proofread.fix(this, code, text) { out ->
                    val ic2 = currentInputConnection
                    when {
                        out == null || ic2 == null -> { say(getString(R.string.toast_proofread_failed)); syncProof() }
                        out == text -> { say(getString(R.string.toast_no_mistakes)); syncProof() }
                        else -> {
                            ic2.beginBatchEdit(); ic2.performContextMenuAction(android.R.id.selectAll); ic2.commitText(out, 1); ic2.endBatchEdit()
                            proofUndo = text; syncProof()
                            ui.removeCallbacks(proofReset); ui.postDelayed(proofReset, 10_000)
                        }
                    }
                }
                Proofread.Status.DOWNLOADABLE -> { say(getString(R.string.toast_model_downloading_start)); Proofread.download(this, code) { ok -> say(getString(if (ok) R.string.toast_model_ready else R.string.toast_download_failed)); syncProof() } }
                Proofread.Status.DOWNLOADING -> { say(getString(R.string.toast_model_still_downloading)); syncProof() }
                Proofread.Status.UNAVAILABLE -> { say(getString(R.string.toast_proofread_unavailable)); syncProof() }
            }
        }
    }

    /** Hebrew has no capitals, so its Shift key is the switch to the English layout (lower case) and back: true while English stands in for Hebrew. */
    private var heEnglish = false

    override fun onShiftKey(): Boolean {
        if (lang.code != "he" && !heEnglish) return false
        val toEnglish = !heEnglish
        switchLanguage(Languages.byCode(if (toEnglish) "en" else "he"), temporary = toEnglish)
        return true
    }

    /** [temporary]: English standing in for Hebrew (see [heEnglish]); the saved language stays Hebrew and nothing is capitalized. */
    private fun switchLanguage(l: Lang, temporary: Boolean = false) {
        finishComposing(); endGlide()
        heEnglish = temporary
        if (::kv.isInitialized) { kv.shiftLangOn = temporary; kv.shift = KeyboardView.Shift.OFF }
        lang = l
        prefs.currentCode = if (temporary) "he" else l.code
        kv.setLanguage(l)
        syncProof()
        loadEngine(l)
        refresh()
    }

    override fun onCreateInputView(): View {
        val tv = System.nanoTime()
        kv = KeyboardView(this, this, lang)
        kv.languages = prefs.enabled
        kv.heightScale = prefs.heightScale
        kv.onHeightScale = { v -> if (layoutMode == "floating") prefs.floatHeight = v else prefs.heightScale = v }
        kv.onSplitScale = { v -> prefs.splitWidth = v }
        kv.onSideWidth = { v -> prefs.sideWidth = v }
        kv.hapticsOn = prefs.haptics
        kv.themeMode = prefs.theme
        kv.swipeTyping = prefs.swipeTyping
        kv.numberRow = prefs.numberRow
        kv.splitAvailable = isTablet()
        kv.restoreFull(prefs.fullKeyboard)                      // come back the way it was left
        kv.onLetterMode = { full -> prefs.fullKeyboard = full }
        syncAccess()
        kv.onKeypad = { opened -> keypadAuto = false; calcResult = null; setUndo(null); if (!opened) exitSelMode(); updateSelHint(); if (taps.isEmpty()) refresh() }
        if (::floating.isInitialized) floating.hide()
        floating = FloatingKeyboard(this, prefs, { requestHideSelf(0) }, { (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showInputMethodPicker() })
        dock = FrameLayout(this)
        autofillBar = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; visibility = View.GONE
            addView(LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }.also { autofillRow = it })
        }
        watchSystemBars()
        layoutMode = prefs.layoutMode.let { if (it in setOf("split", "left", "right") && !isTablet()) "docked" else it }
        arrange()
        android.util.Log.d("OneRowStartup", "input view built in ${(System.nanoTime() - tv) / 1_000_000} ms, ${sinceBirth()} ms after service start")
        return dock
    }

    /**
     * A tablet-sized screen (the smaller side is at least 600 dp), asked of the display as it is NOW: a foldable that was opened after the keyboard
     * started would still report the folded size through the service's own configuration.
     */
    /** The width of the screen as it is now (see [isTablet]). */
    private fun screenWidthPx(): Int =
        if (Build.VERSION.SDK_INT >= 30) (getSystemService(WINDOW_SERVICE) as android.view.WindowManager).maximumWindowMetrics.bounds.width()
        else resources.displayMetrics.widthPixels

    private fun isTablet(): Boolean {
        if (Build.VERSION.SDK_INT >= 30) {
            val b = (getSystemService(WINDOW_SERVICE) as android.view.WindowManager).maximumWindowMetrics.bounds
            val density = resources.displayMetrics.density.takeIf { it > 0f } ?: return resources.configuration.smallestScreenWidthDp >= 600
            return minOf(b.width(), b.height()) / density >= 600f
        }
        return resources.configuration.smallestScreenWidthDp >= 600
    }

    // ---- Android 15+ draws the system buttons / the home-gesture zone OVER the bottom of our window (edge-to-edge is enforced for apps
    // targeting 35+). Older Android reserves that strip itself, so we add nothing there. ----
    private var navInset = 0

    private fun watchSystemBars() {
        if (Build.VERSION.SDK_INT < 35) return
        dock.setOnApplyWindowInsetsListener { _, insets ->
            // the navigation bar or the large-screen taskbar, the gesture strip, and whatever else the system keeps tappable at the bottom
            val kinds = android.view.WindowInsets.Type.navigationBars() or android.view.WindowInsets.Type.systemGestures() or
                android.view.WindowInsets.Type.tappableElement() or android.view.WindowInsets.Type.mandatorySystemGestures()
            // ignoring visibility: a bar that is momentarily hidden / transient must not let the keys drop under it
            val hard = android.view.WindowInsets.Type.navigationBars() or android.view.WindowInsets.Type.mandatorySystemGestures()
            val b = maxOf(insets.getInsets(kinds).bottom, insets.getInsetsIgnoringVisibility(hard).bottom)
            if (b != navInset) { navInset = b; updateDockLook() }
            insets
        }
    }

    /** The input view keeps the strip under the keys clear and paints it in the keyboard's color (docked / split / one-hand; not while floating). */
    private fun updateDockLook() {
        if (!::dock.isInitialized) return
        val floatingNow = layoutMode == "floating"
        val side = layoutMode == "left" || layoutMode == "right"
        val inset = bottomInset()
        dock.setPadding(0, 0, 0, if (floatingNow) 0 else inset)
        dock.setBackgroundColor(if (side || (!floatingNow && inset > 0)) Themes.palette(prefs.theme, resources.configuration).panel else 0)
    }

    /**
     * The strip under the keys that the system draws over (navigation bar, gesture handle). The window insets are the first source; on large
     * screens (tablets, unfolded foldables) the system may not deliver them to the keyboard at all, so the display's own gesture strip and the
     * system's navigation-bar size are read as well and the largest wins. Phones keep exactly the insets they get.
     */
    private fun bottomInset(): Int {
        if (Build.VERSION.SDK_INT < 35) return 0
        var b = navInset
        if (isTablet()) b = maxOf(b, largeScreenGestureStrip())
        // phones: some (Samsung) hand the keyboard no bottom inset at all, and the keys then slide under the gesture handle;
        // only when the system gave nothing, fall back to the height it reserves for the current navigation mode
        else if (b == 0) b = systemNavHeight()
        return b
    }

    /** The system's own height for the navigation strip in the current mode (gesture handle zone, or the three-button bar). */
    private fun systemNavHeight(): Int {
        return try {
            val mode = android.provider.Settings.Secure.getInt(contentResolver, "navigation_mode", 0)
            val sys = android.content.res.Resources.getSystem()
            val id = sys.getIdentifier(if (mode == 2) "navigation_bar_gesture_height" else "navigation_bar_height", "dimen", "android")
            if (id > 0) sys.getDimensionPixelSize(id) else 0
        } catch (_: Exception) { 0 }
    }

    private fun largeScreenGestureStrip(): Int {
        var best = 0
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val m = (getSystemService(WINDOW_SERVICE) as android.view.WindowManager).maximumWindowMetrics
                val kinds = android.view.WindowInsets.Type.systemGestures() or android.view.WindowInsets.Type.mandatorySystemGestures()
                best = maxOf(best, m.windowInsets.getInsetsIgnoringVisibility(kinds).bottom)
            } catch (_: Exception) { }
        }
        try {
            // gesture navigation (navigation_mode 2): the height of the gesture handle zone the system itself reserves
            if (android.provider.Settings.Secure.getInt(contentResolver, "navigation_mode", 0) == 2) {
                val sys = android.content.res.Resources.getSystem()
                val id = sys.getIdentifier("navigation_bar_gesture_height", "dimen", "android")
                if (id > 0) best = maxOf(best, sys.getDimensionPixelSize(id))
            }
        } catch (_: Exception) { }
        return best
    }

    /** Puts the keyboard where [layoutMode] says: in the input view (docked or split) or in the floating window. */
    private fun scaleFor(mode: String) = if (mode == "floating") prefs.floatHeight else prefs.heightScale

    private fun arrange() {
        kv.split = layoutMode == "split"
        kv.splitScale = prefs.splitWidth
        kv.sideShift = when (layoutMode) { "left" -> -1; "right" -> 1; else -> 0 }
        kv.floating = layoutMode == "floating"
        kv.scaling = false
        kv.heightScale = scaleFor(layoutMode)
        val side = layoutMode == "left" || layoutMode == "right"
        kv.shrink = if (side) 0.85f else 1f
        updateDockLook()
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
                dock.addView(kv)
            }
            // one-hand layouts: the keyboard is narrower and sits against one side; the rest of the input view is panel color
            val w = if (side) (screenWidthPx() * prefs.sideWidth).toInt() else ViewGroup.LayoutParams.MATCH_PARENT
            val g = when (layoutMode) { "left" -> Gravity.START; "right" -> Gravity.END; else -> Gravity.NO_GRAVITY }
            kv.layoutParams = FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT, g or Gravity.BOTTOM)
            autofillBar?.let { bar ->
                (bar.parent as? ViewGroup)?.removeView(bar)
                dock.addView(bar, FrameLayout.LayoutParams(w, kv.stripHeightPx(), g or Gravity.TOP))
            }
        }
        dock.requestLayout()
    }

    /**
     * Shows the floating window. The input view may not be shown / attached yet at the moment we are asked (the system
     * calls us while it is still setting the window up), and the popup can fail or have been dismissed by the system, so
     * this retries for about a second until the window really is up.
     */
    private fun showFloatingSoon(attempt: Int = 0) {
        dock.postDelayed({
            if (layoutMode != "floating") return@postDelayed
            if (isInputViewShown && dock.isAttachedToWindow) {
                val p = Themes.palette(prefs.theme, resources.configuration)
                floating.show(dock, kv, p.strip, p.dim)
                if (floating.isShowing()) return@postDelayed
            }
            if (attempt < 20) showFloatingSoon(attempt + 1)
            else android.util.Log.w("OneRowFloat", "the floating window did not come up (shown=$isInputViewShown attached=${dock.isAttachedToWindow})")
        }, if (attempt == 0) 0L else 60L)
    }

    override fun onWindowShown() { super.onWindowShown(); if (::floating.isInitialized && layoutMode == "floating") showFloatingSoon() }

    override fun onWindowHidden() { super.onWindowHidden(); syncShake(false); if (::floating.isInitialized) floating.hide() }

    override fun onDestroy() { shake?.stop(); if (::floating.isInitialized) floating.hide(); super.onDestroy() }

    // ---- shake the phone (keyboard open) -> the crash report screen; the sensor runs only while the keyboard is on screen ----
    private var shake: com.example.onerow.report.ShakeListener? = null
    private fun syncShake(on: Boolean) {
        if (on && prefs.shakeReport) {
            if (shake == null) shake = com.example.onerow.report.ShakeListener(this) { ui.post { openReport() } }
            shake?.start()
        } else shake?.stop()
    }
    private fun openReport() {
        try { startActivity(android.content.Intent(this, com.example.onerow.report.ReportActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
    }

    override fun onMode(mode: String) { ui.post { setLayoutMode(mode) } }

    private fun setLayoutMode(m: String) {
        val target = if (m in setOf("split", "left", "right") && !isTablet()) "docked" else m
        if (target == layoutMode) return
        layoutMode = target; prefs.layoutMode = target
        arrange(); applyNavBar()
        kv.invalidate()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::kv.isInitialized && prefs.theme == Themes.AUTO) { kv.applyTheme(); applyNavBar() }      // system light/dark switched
        if (::kv.isInitialized && ::dock.isInitialized) {
            // a foldable opened or closed (or the tablet turned): the split layouts come and go with the screen size, and the system bars may differ
            val tablet = isTablet()
            kv.splitAvailable = tablet
            val want = prefs.layoutMode.let { if (it in setOf("split", "left", "right") && !tablet) "docked" else it }
            if (want != layoutMode) { layoutMode = want; arrange(); applyNavBar(); kv.invalidate() }
            else if (layoutMode == "left" || layoutMode == "right") arrange()
            updateDockLook()
            dock.requestApplyInsets()
        }
    }

    /** Paints the system navigation bar under the keyboard in the keyboard's color (otherwise each phone picks its own: black, white...). */
    @Suppress("DEPRECATION")
    private fun applyNavBar() {
        updateDockLook()
        val w = window?.window ?: return
        val c = Themes.palette(prefs.theme, resources.configuration).panel
        val floatingNow = layoutMode == "floating"
        // floating: no bar under the screen at all (the system's buttons in it are not ours to remove, but its color is)
        w.navigationBarColor = if (floatingNow) android.graphics.Color.TRANSPARENT else c
        if (Build.VERSION.SDK_INT >= 29) w.isNavigationBarContrastEnforced = false
        // the system's buttons (hide keyboard, switch input method) are views inside our own window: gone while floating
        val navId = resources.getIdentifier("input_method_navigation_bar_view", "id", "android")
        if (navId != 0) w.decorView.findViewById<View>(navId)?.visibility = if (floatingNow) View.GONE else View.VISIBLE
        if (Build.VERSION.SDK_INT >= 26) {
            val light = android.graphics.Color.luminance(c) > 0.5f
            val f = w.decorView.systemUiVisibility
            w.decorView.systemUiVisibility = if (light) f or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else f and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        hideAutofill()
        syncAccess(); refreshBillingIfStale(); syncShake(true)
        selStart = info.initialSelStart; selEnd = info.initialSelEnd
        endGlide()
        taps.clear(); exact.clear(); shown.clear(); upper.clear()
        applyPrefs()
        applyNavBar()
        if (layoutMode == "floating" && !floating.isShowing()) showFloatingSoon()       // insurance: the window-shown callback can come too early
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
            if (cs >= 0 && ce >= 0) finishComposing() else { taps.clear(); exact.clear(); shown.clear(); upper.clear(); cands = emptyList() }
        }
        if (::kv.isInitialized && taps.isEmpty()) refresh()
        if (::kv.isInitialized && selMode && android.os.SystemClock.uptimeMillis() - ownEditAt > 400 &&
            (minOf(newSs, newSe) != selAnchorS || maxOf(newSs, newSe) != selAnchorE)) { exitSelMode(); kv.closeKeypad() }   // the selection changed: the sum is off
        updateSelHint()
        if (::kv.isInitialized && newSs == newSe) keypadFollowsCaret()
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

    /** Letters/apostrophes directly after the caret (the rest of a word the caret sits inside), or "". */
    private fun wordTail(): String {
        val t = currentInputConnection?.getTextAfterCursor(40, 0)?.toString() ?: return ""
        var i = 0
        while (i < t.length && (t[i].isLetter() || t[i] == '\'')) i++
        return t.substring(0, i)
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

    private fun styled(w: String): String {
        val r = when {
            capAll -> w.uppercase()
            capFirst -> w.replaceFirstChar { it.uppercase() }
            else -> w
        }
        if (capAll || upper.size != taps.size || upper.none { it }) return r
        // capitals asked for letter by letter (Shift pressed in the middle of the word): the n-th key of the word is the n-th tap
        val a = lex?.alphabet ?: return r
        val sb = StringBuilder(); var k = 0
        for (c in r) {
            val m = a.key(c.toString()).size
            sb.append(if (m > 0 && upper.getOrNull(k) == true) c.uppercaseChar() else c)
            k += m
        }
        return sb.toString()
    }

    /** Set when the last action was deleting a line break: no automatic capital until something else is typed. */
    private var noAutoCap = false

    private fun refresh() {
        if (kv.keypadActive) { showCalc(); return }
        if (glideWord != null) return                    // the guesses for the glided word stay until the next input
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo ?: return
        val isText = (info.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
        // Capitalize only at the start of a field or after a finished sentence (. ! ? … or a line break, closing quotes/brackets
        // allowed in between); after ":" ";" "%" digits, commas and the like the word stays lower case. Fields that ask for
        // every word/character capitalized (names) keep the system's rule.
        val sentenceOnly = (info.inputType and (InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)) == 0
        val before = textBefore(12).trimEnd(' ', '\u00a0').trimEnd('"', '\'', ')', ']', '}', '\u00bb', '\u201d', '\u2019')
        val sentenceDone = before.isEmpty() || before.last() in ".!?\u2026\n\u3002"
        val capOk = !noAutoCap && (!sentenceOnly || sentenceDone)
        if (isText && capOk && !heEnglish && lang.code != "he" && kv.shift == KeyboardView.Shift.OFF && ic.getCursorCapsMode(info.inputType) != 0)
            kv.shift = KeyboardView.Shift.ONCE
        if (taps.isEmpty()) {
            kv.bestIndex = -1
            val l = lex
            val comp = if (decoding && l != null) completionsForWordAtCaret(l) else null      // once: the text under the caret can change between two calls
            kv.suggestions = when {
                !decoding -> emptyList()
                l == null -> emptyList()
                comp != null -> comp
                kv.expiringDays != null -> emptyList()          // a cancelled subscription: the countdown takes the place of next-word guesses
                else -> {
                    val p = previousWordIdx()
                    val n = l.nextWords(p, 12)
                    val base = if (n.size >= 4 || lang.code != "en") n else (n + listOf("I", "the", "and", "to")).distinct().take(4)      // the filler words are English
                    if (kv.shift != KeyboardView.Shift.OFF) base.map { it.replaceFirstChar { c -> c.uppercase() } } else base
                }
            }
        }
    }

    /** Guesses completing the word the caret touches (not composing, e.g. after tapping into text). */
    private fun completionsForWordAtCaret(l: Lexicon): List<String>? {
        val dec = decoder ?: return null
        val w = currentWord() + wordTail()
        if (w.isEmpty()) return null
        glideMemory[w.lowercase()]?.let { alts -> return (listOf(w) + alts.filter { !it.equals(w, ignoreCase = true) }).take(12) }    // a swiped word: the path's other readings
        val key = l.alphabet.key(w)
        if (key.isEmpty()) return null
        val taps = key.map { k -> FloatArray(l.alphabet.n) { -30f }.also { it[k] = 0f } }
        val out = dec.decode(taps, -1, 12).map { it.word }.filter { !it.equals(w, ignoreCase = true) }
        return (listOf(w) + out).take(12)
    }

    /** Whether two guesses are just forms of one word (walk/walked/walking, красный/красная); ё/е twins are not. */
    private fun sameStem(a: String, b: String): Boolean {
        val x = a.lowercase(); val y = b.lowercase()
        if (x.replace('ё', 'е') == y.replace('ё', 'е')) return false
        val m = minOf(x.length, y.length)
        var cp = 0
        while (cp < m && x[cp] == y[cp]) cp++
        return cp >= 4 && cp >= m - 1
    }

    /** Keeps the first guess, then prefers different words over more forms of one already offered; the forms fill what is left. */
    private fun diversify(words: List<String>, keep: Int = 1): List<String> {
        val out = ArrayList<String>(); val later = ArrayList<String>()
        for ((i, w) in words.withIndex()) {
            if (i < keep || out.none { sameStem(it, w) }) out += w else later += w
        }
        return out + later
    }

    /** The user picked [word] where [first] was offered first: remember it so that it ranks first from now on. */
    private fun prefer(word: String, first: String) {
        val lx = lex ?: return; val ud = user ?: return
        if (word.equals(first, ignoreCase = true) || word.isEmpty() || first.isEmpty()) return
        val c = lx.indexOf(word.lowercase()).takeIf { it >= 0 } ?: lx.indexOf(word)
        val f = lx.indexOf(first.lowercase()).takeIf { it >= 0 } ?: lx.indexOf(first)
        if (c < 0 || f < 0) return
        lx.promote(c, f)
        ud.prefer(lx.words[c], lx.words[f])
    }

    // ---------- composing ----------

    private fun updateComposing() {
        val ic = currentInputConnection ?: return
        val dec = decoder ?: return
        val lx = lex ?: return
        var list = dec.decode(taps, prevIdx, 14)
        if (list.isEmpty()) return
        run { val order = diversify(list.map { it.word }); list = list.sortedBy { order.indexOf(it.word) } }
        // Full keyboard / hold-and-slide: the input gets exactly what was typed. Guesses are only offered.
        // One-row keyboard: a single letter picked with the zoom (hold and slide) turns the guessing off for the whole word, the other letters are
        // typed as the nearest key; the next word (after a space) guesses again. The expanded keyboard types every letter exactly anyway.
        val deliberate = exact.any { it }
        if (deliberate) {
            var typed = taps.withIndex().joinToString("") { (i, t) ->
                shown.getOrNull(i) ?: lx.alphabet.letters[t.indices.maxByOrNull { t[it] }!!].toString()
            }
            if (shown.lastOrNull() == null) typed = lx.alphabet.close(typed)         // Hebrew: a plain letter at the end of a word is its final form
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
        taps.clear(); exact.clear(); shown.clear(); upper.clear(); cands = emptyList()
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

    // ---------- keypad calculator ----------

    /** The arithmetic around the caret: [run] is all of it, [before] of its characters sit before the caret, [after] after. */
    private class ExprCtx(val run: String, val before: Int, val after: Int)
    private var calcResult: String? = null
    /** The last replacement on the keypad: [inserted] went in where [removed] was. */
    private class CalcUndo(val inserted: String, val removed: String)
    private var calcUndo: CalcUndo? = null
    private var keypadAuto = false                     // the keypad opened by itself because the caret reached an expression
    private var ownEditAt = 0L                         // when a key of ours last changed the text (selection updates right after are ours)
    private fun own() { ownEditAt = android.os.SystemClock.uptimeMillis(); hideAutofill() }
    private fun setUndo(u: CalcUndo?) { calcUndo = u; kv.calcUndoReady = u != null }

    private fun isCalcChar(c: Char) = c in "0123456789.,+-*/%()"

    /** The expression the caret is in or at the end of: digits and + - * / % around it, not part of a word. */
    private fun exprAtCaret(): ExprCtx {
        val ic = currentInputConnection ?: return ExprCtx("", 0, 0)
        val bt = textBefore(60)
        var i = bt.length
        while (i > 0 && isCalcChar(bt[i - 1])) i--
        val glued = i > 0 && bt[i - 1].isLetter()                  // glued to a word (abc12): not an expression
        if (glued) i = bt.length
        var b = bt.substring(i)
        val lead = b.indexOfFirst { it != '*' && it != '/' && it != '%' }.let { if (it < 0) b.length else it }
        b = b.substring(lead)
        val at = ic.getTextAfterCursor(60, 0)?.toString().orEmpty()
        var k = 0
        while (k < at.length && isCalcChar(at[k])) k++
        var a = if (glued) "" else at.substring(0, k)
        a = a.trimEnd('.', ',')                                   // sentence punctuation after the expression
        return ExprCtx(b + a, b.length, a.length)
    }

    /** The guess bar on the keypad: the expression around the caret with "=" and the current result, when they make arithmetic sense. */
    private fun showCalc() {
        val s = if (selMode) selExpr.toString() else exprAtCaret().run
        val v = if (Calc.worthIt(s)) Calc.eval(s) else null
        if (v == null) { calcResult = null; kv.suggestions = emptyList(); kv.bestIndex = -1; return }
        val r = Calc.format(v, if (selMode) decimalSep() == ',' else s.contains(',') && !s.contains('.'))
        calcResult = r
        kv.suggestions = listOf("$s=", r); kv.bestIndex = 1
    }

    /** Text typed on the keypad goes straight into the field (no word logic); true when handled. */
    private fun calcText(t: String): Boolean {
        val ic = currentInputConnection ?: return true
        if (selMode) return selText(t)
        finishComposing(); endGlide(); justAutoSpaced = false; wordAutoSpaced = false
        setUndo(null)
        if (t == ")") {
            val x = exprAtCaret(); val bef = x.run.substring(0, x.before)
            val at = openingIndex(bef)
            if (at >= 0) {
                ic.beginBatchEdit(); ic.deleteSurroundingText(bef.length - at, 0); ic.commitText("(" + bef.substring(at) + ")", 1); ic.endBatchEdit()
                showCalc(); return true
            }
        }
        if (t == "=") {
            showCalc()
            if (calcResult != null) { calcPick("${exprAtCaret().run}="); return true }
            ic.commitText("=", 1)
        } else ic.commitText(t, 1)
        showCalc()
        return true
    }

    /** A closing bracket with no opening one: where the "(" goes, after the previous ")" and its operator, else the start; -1 when none is needed. */
    private fun openingIndex(bef: String): Int {
        if (bef.isEmpty() || bef.count { it == '(' } > bef.count { it == ')' }) return -1
        var at = 0
        for (k in bef.indices) if (bef[k] == ')' && k + 1 < bef.length && bef[k + 1] in "+-*/") at = k + 2
        return at
    }

    private fun calcBackspace(): Boolean {
        if (selMode) { if (selExpr.isNotEmpty()) selExpr.setLength(selExpr.length - 1); showCalc(); return true }
        val ic = currentInputConnection ?: return false
        if (!ic.getSelectedText(0).isNullOrEmpty()) return false
        val prev = textBefore(1)
        if (prev.isEmpty() || !isCalcChar(prev[0])) return false
        setUndo(null)
        ic.deleteSurroundingText(1, 0)
        showCalc()
        return true
    }

    /** A tap on the expression slot types "expr=result" over the whole expression; on the result slot, just the result. */
    private fun calcPick(word: String) {
        if (selMode) { selPick(word); return }
        val ic = currentInputConnection ?: return
        val x = exprAtCaret(); if (x.run.isEmpty()) return
        showCalc()
        val r = calcResult
        val text = when { word == "${x.run}=" -> if (r != null) "${x.run}=$r" else "${x.run}="; r != null && word == r -> r; else -> return }
        ic.beginBatchEdit(); ic.deleteSurroundingText(x.before, x.after); ic.commitText(text, 1); ic.endBatchEdit()
        setUndo(CalcUndo(text, x.run))
        showCalc()
    }

    override fun onCalcUndo() {
        val u = calcUndo ?: return
        val ic = currentInputConnection ?: return
        if (!textBefore(u.inserted.length).endsWith(u.inserted)) { setUndo(null); return }
        own()
        ic.beginBatchEdit(); ic.deleteSurroundingText(u.inserted.length, 0); if (u.removed.isNotEmpty()) ic.commitText(u.removed, 1); ic.endBatchEdit()
        setUndo(null)
        showCalc()
    }

    // ---- adding up the numbers of a selection ----

    private var selMode = false                    // the keypad works on [selExpr], built from the selected numbers, not on the field
    private val selExpr = StringBuilder()
    private var selAnchorS = 0; private var selAnchorE = 0

    private fun decimalSep(): Char = try { java.text.DecimalFormatSymbols.getInstance().decimalSeparator } catch (e: Exception) { '.' }
    private fun groupingSep(): Char = try { java.text.DecimalFormatSymbols.getInstance().groupingSeparator } catch (e: Exception) { ',' }

    private fun selectedNumbers(): Pair<String, Int>? {
        val t = currentInputConnection?.getSelectedText(0)?.toString() ?: return null
        if (t.isEmpty() || t.length > 2000) return null
        return Numbers.expression(t, decimalSep(), groupingSep())
    }

    /** A blinking keypad icon in the guess strip while the selection holds at least two numbers. */
    private fun updateSelHint() {
        if (!::kv.isInitialized) return
        kv.selHint = access !is Access.Locked && !selMode && !kv.keypadActive && kv.keypadAvailable && (selectedNumbers()?.second ?: 0) >= 2
    }

    private fun exitSelMode() { selMode = false; selExpr.setLength(0) }

    override fun onSelCalc() {
        val (e, n) = selectedNumbers() ?: return
        if (n < 2) return
        selAnchorS = minOf(selStart, selEnd); selAnchorE = maxOf(selStart, selEnd)
        if (!kv.openKeypad()) return
        selExpr.setLength(0); selExpr.append(e); selMode = true
        kv.selHint = false
        showCalc()
    }

    private fun selText(t: String): Boolean {
        when {
            t == ")" -> { val bef = selExpr.toString(); val at = openingIndex(bef)
                if (at >= 0) { selExpr.setLength(0); selExpr.append(bef.substring(0, at)).append('(').append(bef.substring(at)).append(')') } else selExpr.append(')') }
            t == "=" -> { showCalc(); selPick("$selExpr="); return true }
            t.length == 1 && isCalcChar(t[0]) -> selExpr.append(t)
        }
        showCalc()
        return true
    }

    /** The tapped slot (or "=") goes after the end of the selection: the whole expression with its result, or the result alone. */
    private fun selPick(word: String) {
        val ic = currentInputConnection ?: return
        showCalc()
        val r = calcResult
        val text = when { word == "$selExpr=" -> if (r != null) "$selExpr=$r" else return; r != null && word == r -> r; else -> return }
        val sel = ic.getSelectedText(0)?.toString().orEmpty()
        val end = maxOf(selStart, selEnd)
        exitSelMode()
        own()
        ic.beginBatchEdit(); ic.setSelection(end, end)
        ic.commitText((if (sel.isNotEmpty() && !sel.last().isWhitespace()) " " else "") + text, 1)
        ic.endBatchEdit()
        kv.closeKeypad()                                   // done: back to the letters
    }

    /** The caret moved (not by our own typing): open the keypad when it reached an expression, close it again when it left one. */
    private fun keypadFollowsCaret() {
        if (selMode) return
        if (android.os.SystemClock.uptimeMillis() - ownEditAt < 400 || taps.isNotEmpty()) return
        val s = exprAtCaret().run
        val expr = Calc.worthIt(s) && Calc.eval(s) != null
        if (!kv.keypadActive) { if (expr && kv.openKeypad()) keypadAuto = true }
        else if (keypadAuto && !expr) kv.closeKeypad()
    }

    // ---------- KeyboardView.Listener ----------

    override fun onLetter(ch: Char, logp: FloatArray, exactPick: Boolean) { own(); noAutoCap = false; addTap(ch, logp, exactPick, null) }

    /** A national letter (ü, ñ, ç ...) picked with hold-and-slide: exact, and typed as itself. */
    override fun onAccent(base: Char, shown: String) {
        own()
        noAutoCap = false
        val n = lang.alphabet.n
        addTap(base, FloatArray(n) { -30f }.also { it[lang.alphabet.index(base)] = 0f }, true, shown)
    }

    private fun addTap(ch: Char, logp: FloatArray, exactPick: Boolean, shownText: String?) {
        justAutoSpaced = false; wordAutoSpaced = false
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
        upper += (kv.shift != KeyboardView.Shift.OFF)          // Shift pressed for this very letter: a capital in the middle of a word (iPhone, McDonald)
        updateComposing()
    }

    override fun onText(text: String) {
        own()
        noAutoCap = false
        // a line that starts with numbers: the automatic capital is used up by the digits, the word after them stays lower case
        if (text.isNotEmpty() && text[0].isDigit() && kv.shift == KeyboardView.Shift.ONCE) kv.shift = KeyboardView.Shift.OFF
        if (kv.keypadActive && calcText(text)) return
        endGlide()
        // French elision (l', d', qu'): the apostrophe is already in the word, so no space after it
        if (text == " " && taps.isNotEmpty() && cands.firstOrNull()?.word?.endsWith("'") == true) { finishComposing(); return }
        finishComposing()
        val ic = currentInputConnection ?: return
        val wasWordAuto = wordAutoSpaced
        val wasAuto = justAutoSpaced
        justAutoSpaced = false; wordAutoSpaced = false
        // a space typed right after the automatic one is not needed
        if (text == " " && wasAuto && textBefore(1) == " ") return
        // ":)" / ";)" smileys: take the automatic space back before a closing bracket
        if (decoding && wasAuto && text.length == 1 && text[0] in ")]}") {
            val b = textBefore(2)
            if (b.length == 2 && b[0] in ":;" && b[1] == ' ') ic.deleteSurroundingText(1, 0)
        }
        if (decoding && text.length == 1 && text[0] in Quotes.ALL && smartQuote(ic, text[0])) return
        if (decoding && text.length == 1 && text[0] in AUTO_SPACE_AFTER) {
            ic.beginBatchEdit()
            // "word ." -> "word." (also joins "..." and "?!" after the automatic space)
            if (SpacePolicy.cutsSpaceBefore(text[0], textBefore(2), wasAuto || wasWordAuto)) ic.deleteSurroundingText(1, 0)
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

    /** Quotation marks, see [Quotes]. Returns true when the quote was handled here. */
    private fun smartQuote(ic: android.view.inputmethod.InputConnection, q: Char): Boolean {
        val line = textBefore(600).substringAfterLast('\n')
        val after = ic.getTextAfterCursor(1, 0)?.toString().orEmpty()
        val plan = Quotes.plan(line, after, q) ?: return false
        ic.beginBatchEdit()
        if (plan.deleteBefore > 0) ic.deleteSurroundingText(plan.deleteBefore, 0)
        ic.commitText(plan.pre + plan.quote + plan.post, 1)
        ic.endBatchEdit()
        if (plan.post.isNotEmpty()) justAutoSpaced = true
        return true
    }

    // ---- autofill (1Password, Bitwarden, Google ...): Android 11+ lets the keyboard show the password manager's suggestions in the guess bar ----
    private var autofillBar: android.widget.HorizontalScrollView? = null
    private var autofillRow: LinearLayout? = null

    private fun hideAutofill() {
        autofillRow?.removeAllViews()
        autofillBar?.visibility = View.GONE
    }

    override fun onCreateInlineSuggestionsRequest(uiExtras: android.os.Bundle): android.view.inputmethod.InlineSuggestionsRequest? {
        // asked before the input view exists on the first field after the service starts, so nothing of the view may be needed here
        if (Build.VERSION.SDK_INT < 30 || prefs.layoutMode == "floating") return null      // floating: the system's own dropdown is used
        val p = Themes.palette(prefs.theme, resources.configuration)
        val dm = resources.displayMetrics
        val h = (if (::kv.isInitialized) kv.stripHeightPx() else 0).coerceAtLeast(dp(32))
        val chip = androidx.autofill.inline.common.ViewStyle.Builder().setBackgroundColor(p.key).setPadding(dp(10), 0, dp(10), 0).build()
        val title = androidx.autofill.inline.common.TextViewStyle.Builder().setTextColor(p.ink).setTextSize(14f).build()
        val sub = androidx.autofill.inline.common.TextViewStyle.Builder().setTextColor(p.dim).setTextSize(11f).build()
        val single = androidx.autofill.inline.common.ViewStyle.Builder().setBackgroundColor(p.key).setPadding(dp(10), 0, dp(10), 0).build()
        val style = androidx.autofill.inline.v1.InlineSuggestionUi.newStyleBuilder()
            .setChipStyle(chip).setTitleStyle(title).setSubtitleStyle(sub).setSingleIconChipStyle(single).build()
        val styles = androidx.autofill.inline.UiVersions.newStylesBuilder().addStyle(style).build()
        val spec = android.widget.inline.InlinePresentationSpec.Builder(android.util.Size(dp(80), h), android.util.Size(dm.widthPixels, h)).setStyle(styles).build()
        return android.view.inputmethod.InlineSuggestionsRequest.Builder(listOf(spec)).setMaxSuggestionCount(6).build()
    }

    override fun onInlineSuggestionsResponse(response: android.view.inputmethod.InlineSuggestionsResponse): Boolean {
        val bar = autofillBar; val row = autofillRow
        val list = response.inlineSuggestions
        if (bar == null || row == null || list.isEmpty() || bar.parent == null || Build.VERSION.SDK_INT < 30) { hideAutofill(); return false }
        val h = kv.stripHeightPx().coerceAtLeast(dp(32))
        val dm = resources.displayMetrics
        val p = Themes.palette(prefs.theme, resources.configuration)
        bar.setBackgroundColor(p.strip)
        // only over the guess bar's own area: shift and backspace of the one-row layout stay usable
        val sb = kv.suggBounds()
        bar.layoutParams = (bar.layoutParams as FrameLayout.LayoutParams).also {
            it.height = h
            it.leftMargin = kv.left + (sb?.get(0) ?: 0)
            it.width = if (sb != null) (sb[1] - sb[0]) else ViewGroup.LayoutParams.MATCH_PARENT
            it.gravity = Gravity.TOP or Gravity.START
        }
        row.removeAllViews()
        // a WRAP_CONTENT width leaves the view 0 px wide, so every suggestion gets a fixed width: wide enough for a name and an e-mail address,
        // narrow enough that the next one peeks in (the row scrolls sideways)
        val w = (sb?.let { it[1] - it[0] } ?: dm.widthPixels).let { (it * 0.5f).toInt().coerceIn(dp(120), dp(260)) }
        val size = android.util.Size(w, h)
        var n = 0
        for (s in list) {
            s.inflate(this, size, mainExecutor) { v ->
                if (v == null) return@inflate
                (v.parent as? ViewGroup)?.removeView(v)
                row.addView(v, LinearLayout.LayoutParams(w, h).apply { marginEnd = dp(6) })
                bar.visibility = View.VISIBLE
            }
            n++
        }
        return n > 0
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onBackspace() {
        own()
        if (kv.keypadActive && calcBackspace()) return
        justAutoSpaced = false; wordAutoSpaced = false
        noAutoCap = false
        if (glideWord != null && taps.isEmpty() && glideIntact()) {          // delete the whole glided word
            currentInputConnection?.deleteSurroundingText(glideWord!!.length + 1, 0)
            endGlide(); refresh(); return
        }
        endGlide()
        if (taps.isNotEmpty()) {
            taps.removeAt(taps.size - 1); exact.removeAt(exact.size - 1); shown.removeAt(shown.size - 1); if (upper.isNotEmpty()) upper.removeAt(upper.size - 1)
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
        if (ic.getTextBeforeCursor(1, 0)?.toString() == "\n") { noAutoCap = true; if (kv.shift == KeyboardView.Shift.ONCE) kv.shift = KeyboardView.Shift.OFF }   // took back a line break: the caret is not at a new sentence
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
        taps.addAll(newTaps); shown.addAll(newShown); newTaps.forEach { exact += true; upper += false }
        // keep the word's capitalization for the guesses ("This" -> "Thistle", not "thistle"); all-caps stays all-caps
        capAll = w.length > 1 && w.all { !it.isLetter() || it.isUpperCase() }
        capFirst = capAll || w[0].isUpperCase()
        resumed = true
        ignoreSelUntil = android.os.SystemClock.uptimeMillis() + 400
        updateComposing()                                              // re-opens the word as composing text
        ic.endBatchEdit()
    }

    override fun onDeleteWord() {
        own()
        justAutoSpaced = false; wordAutoSpaced = false
        endGlide()
        taps.clear(); exact.clear(); shown.clear(); upper.clear()
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
        if (kv.keypadActive) return
        val lx = lex ?: return; val ud = user ?: return
        val i = lx.indexOf(word)
        if (i < 0) { Toast.makeText(this, getString(R.string.toast_word_not_in_dictionary, word), Toast.LENGTH_SHORT).show(); return }
        lx.remove(i); ud.forget(word)
        Toast.makeText(this, getString(R.string.toast_word_removed, word), Toast.LENGTH_SHORT).show()
        if (taps.isNotEmpty()) updateComposing() else refresh()
    }

    override fun onLanguage(code: String) { if (code != lang.code) switchLanguage(Languages.byCode(code)) }

    override fun onHelp() {
        finishComposing()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("tab", 1))       // the Help tab
    }

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
            android.widget.Toast.makeText(this, getString(R.string.toast_no_voice_input), android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { /* voice not available */ }
    }

    override fun onEnter() {
        own()
        if (selMode) { calcText("="); return }
        noAutoCap = false
        justAutoSpaced = false; wordAutoSpaced = false
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
        hideAutofill()
        finishComposing()                          // a word still being typed when the field is left is not lost
        syncShake(false)
        super.onFinishInputView(finishingInput)
    }

    /**
     * Moves the caret by setting the selection. Never sends DPAD keys: at the end of the text they
     * move focus out of the field and the input connection is lost.
     */
    override fun onMoveCursor(steps: Int) {
        justAutoSpaced = false; wordAutoSpaced = false
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

    /**
     * The other words a swipe path may have meant, kept for the words that were glided and chosen (lower case word -> the whole list), so that a
     * word that was guessed wrong can be tapped much later and the bar offers the path's other readings again, not just completions of its letters.
     */
    private val glideMemory = object : LinkedHashMap<String, List<String>>(64, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, List<String>>?) = size > 120
    }
    private fun rememberGlide(word: String, alts: List<String>) { if (alts.size > 1 && word.isNotEmpty()) glideMemory[word.lowercase()] = alts }

    /** True while the text before the caret still ends with the word a glide typed plus our space. */
    private fun glideIntact(): Boolean { val g = glideWord ?: return false; return textBefore(g.length + 1).endsWith("$g ") }

    /** A word drawn across the letters of the full keyboard: type the best match, offer the others as guesses. */
    override fun onGlide(xs: FloatArray, ys: FloatArray, n: Int, layout: GlideLayout) {
        own()
        noAutoCap = false
        val eng = loaded[lang.code] ?: return           // dictionary still loading
        val ic = currentInputConnection ?: return
        if (!decoding) return
        val t0 = System.nanoTime()
        finishComposing(); endGlide()
        justAutoSpaced = false; wordAutoSpaced = false
        val prevW = previousWordIdx()
        val t1 = System.nanoTime()
        val list = eng.glide.decode(xs, ys, n, layout, prevW, 16)
        val t2 = System.nanoTime()
        if (list.isEmpty()) return
        capFirst = kv.shift != KeyboardView.Shift.OFF; capAll = kv.shift == KeyboardView.Shift.LOCK
        val words = diversify(list.map { styled(it.word) }.distinct()).take(8)
        ic.beginBatchEdit()
        val b = textBefore(1)
        if (b.isNotEmpty() && !b.last().isWhitespace() && b.last() !in "([{\"'\u00AB\u201C-") ic.commitText(" ", 1)
        ic.commitText(words[0] + " ", 1)
        ic.endBatchEdit()
        wordAutoSpaced = true
        glideWord = words[0]; glideAlts = words
        rememberGlide(words[0], words)
        kv.suggestions = words.drop(1); kv.bestIndex = -1          // the typed word is not repeated in the bar: only the other, less certain guesses
        android.util.Log.d("OneRowGlide", "ipc+prev=${(t1 - t0) / 1000}us decode=${(t2 - t1) / 1000}us commit=${(System.nanoTime() - t2) / 1000}us | ${eng.glide.stats}")
    }

    /** The words a drawn path may mean, nothing typed: for the emoji search. */
    override fun glideCandidates(xs: FloatArray, ys: FloatArray, n: Int, layout: GlideLayout): List<String> {
        val eng = loaded[lang.code] ?: return emptyList()
        return eng.glide.decode(xs, ys, n, layout, -1, 6).map { it.word }
    }

    override fun onSuggestion(word: String) {
        own()
        if (kv.keypadActive) { calcPick(word); return }
        noAutoCap = false
        justAutoSpaced = false; wordAutoSpaced = false
        val ic = currentInputConnection ?: return
        val gw = glideWord
        if (gw != null && taps.isEmpty() && glideIntact()) {         // a guess for a glided word replaces it
            ic.beginBatchEdit(); ic.deleteSurroundingText(gw.length + 1, 0); ic.commitText("$word ", 1); ic.endBatchEdit()
            prefer(word, glideAlts.firstOrNull() ?: gw)
            rememberGlide(word, glideAlts)                           // the chosen reading keeps the path's other readings
            endGlide(); wordAutoSpaced = true
            refresh()                                                // back to next-word prediction
            return
        }
        endGlide()
        ic.beginBatchEdit()
        if (taps.isNotEmpty()) {
            // composing: the guess replaces the composing text; then only the space is added
            if (lex?.alphabet?.key(word)?.size == taps.size) prefer(word, cands.firstOrNull()?.word ?: "")   // a correction, not a longer completion
            if (decoding && lex?.indexOf(word) == -1) learn(word.lowercase())   // picked the 'typed' slot: deliberate
            ic.setComposingText(word, 1)
            ic.finishComposingText()
            taps.clear(); exact.clear(); shown.clear(); upper.clear()
            ic.commitText(" ", 1)
        } else {
            // Not composing (the app dropped the composing region, or the caret sits at a word's end):
            // a tapped guess replaces the word touching the caret instead of being appended to it.
            // Tapped inside the word: the whole word is replaced, not just the part before the caret.
            val typed = currentWord(); val tail = wordTail()
            glideMemory[(typed + tail).lowercase()]?.let { rememberGlide(word, it) }       // swapping between the readings of a swipe keeps the list
            if (typed.isNotEmpty() || tail.isNotEmpty()) ic.deleteSurroundingText(typed.length, tail.length)
            if (decoding && lex?.indexOf(word) == -1) learn(word.lowercase())   // the typed slot again: silent, no prompt
            val next = ic.getTextAfterCursor(1, 0)?.toString().orEmpty()
            ic.commitText(if (tail.isNotEmpty() && next.isNotEmpty() && !next[0].isLetterOrDigit()) word else "$word ", 1)
        }
        ic.endBatchEdit()
        wordAutoSpaced = true
        refresh()
    }
}
