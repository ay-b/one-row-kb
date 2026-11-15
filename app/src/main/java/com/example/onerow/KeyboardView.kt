package com.example.onerow

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.example.onerow.engine.GlideLayout
import com.example.onerow.engine.SpatialModel
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * One view draws and handles everything. Layout (MINI):
 *   top band : [shift] [suggestions, or a magnifier lens while a key is held] [backspace]
 *   panel    : ONE row of 26 letters, grouped by QWERTY column and zig-zagged vertically
 *              (qaz wsx edc rfv tgb yhn ujm ik ol p), then [123] [,] [space] [.] [enter]
 * FULL = classic 3-row QWERTY (swipe up on the letter row; swipe down to return).
 * Gestures: drag on space = move cursor, drag left on backspace = delete words.
 */
class KeyboardView(context: Context, private val listener: Listener, private var lang: Lang) : View(context) {

    interface Listener {
        fun onText(text: String)
        /** A letter key was released. [logp] = ln-likelihood of each letter a..z; [exact] = picked precisely by hold-and-slide. */
        fun onLetter(ch: Char, logp: FloatArray, exact: Boolean)
        fun onBackspace()
        fun onDeleteWord()
        fun onEnter()
        fun onMoveCursor(steps: Int)
        fun onSuggestion(word: String)
        fun onVoice()
        /** A national letter picked by hold-and-slide: [base] is its plain letter (for the decoder), [shown] what to type. */
        fun onAccent(base: Char, shown: String)
        fun onLanguage(code: String)
        fun onSettings()
        /** The user picked a layout in the language key's zoom: "docked", "floating" or "split". */
        fun onMode(mode: String)
        /** A word was drawn across the letters of the full keyboard ([n] points, view pixels). */
        fun onGlide(xs: FloatArray, ys: FloatArray, n: Int, layout: GlideLayout)
        /** The user asked to delete [word] from the dictionary (hold a guess, then the bucket). */
        fun onRemoveWord(word: String)
    }

    enum class Mode { MINI, FULL, SYMBOLS, SYMBOLS_FULL, SYMBOLS_FULL2 }
    enum class Shift { OFF, ONCE, LOCK }
    private enum class Kind { LETTER, CHAR, SHIFT, BACKSPACE, SPACE, ENTER, TO_SYMBOLS, TO_LETTERS, SYM_PAGE2, SYM_PAGE1, SUGG, LANG, NONE }

    /** [dy] = vertical position of the glyph inside its cell (0 top .. 1 bottom). */
    private class Key(val kind: Kind, val label: String, val weight: Float, val hint: String? = null, val dy: Float = 0.5f, val base: Char? = null)
    private class Row(val keys: List<Key>, val heightDp: Float, val inset: Float = 0f,
                      val panel: Boolean = true, val keyBg: Boolean = false)
    private class Placed(val key: Key, val rect: RectF, val row: Int)

    var shift = Shift.OFF
        set(v) { field = v; invalidate() }
    var suggestions: List<String> = emptyList()
        set(v) { if (v != field) { suggScroll = 0f; scrollToBest = true }; field = v; invalidate() }
    private var suggScroll = 0f              // the guess bar scrolls sideways when there are more than SLOTS guesses
    private var suggScrollStart = 0f
    private var suggDragging = false
    private var suggDragX = 0f
    private fun maxSuggScroll(): Float {
        val r = placed.firstOrNull { it.key.kind == Kind.SUGG }?.rect ?: return 0f
        suggLayout(r.width())
        return maxOf(0f, suggTotal - r.width())
    }

    // Guess slots are as wide as their words (never narrower than a quarter of the bar), so long words keep their size.
    private var slotLeft = FloatArray(0)
    private var slotWidth = FloatArray(0)
    private var suggTotal = 0f
    private var laidOut: List<String>? = null
    private var laidOutBar = 0f
    private var scrollToBest = false
    private fun suggLayout(barW: Float) {
        if (laidOut === suggestions && laidOutBar == barW) return
        val n = suggestions.size
        slotLeft = FloatArray(n); slotWidth = FloatArray(n)
        text.textSize = sp(14f)
        val minW = barW / SLOTS
        var x = 0f
        for (i in 0 until n) {
            val s = suggestions[i]
            val wText = if (s.isEmpty()) 0f else text.measureText(s) + dp(20f)
            val wi = maxOf(minW, minOf(wText, barW))
            slotLeft[i] = x; slotWidth[i] = wi; x += wi
        }
        suggTotal = x; laidOut = suggestions; laidOutBar = barW
    }
    /** Slot holding Minuum's current guess (drawn as a teal pill); -1 = none. */
    var bestIndex = -1
        set(v) { field = v; invalidate() }

    /** Languages offered in the shift-hold menu. */
    var languages: List<Lang> = emptyList()
    var heightScale = 1f
        set(v) { if (v != field) { field = v; layoutKeys(width.toFloat()); requestLayout(); invalidate() } }
    var hapticsOn = true
    /** Width of the tap-scatter model, in key widths (tight / normal / loose). Normal is the original 0.9. */
    var tapSigma = 0.9f
    /** Glide typing on the full keyboard. */
    var swipeTyping = false
    /** A dedicated row of digits on the full keyboard (instead of the small digits on the top letter row). */
    var numberRow = false
        set(v) { if (v != field) { field = v; buildRows(); if (width > 0) layoutKeys(width.toFloat()); requestLayout(); invalidate() } }
    /** Split layout: the key rows fall apart into a left and a right half with a gap between (tablets). */
    var split = false
        set(v) { if (v != field) { field = v; layoutKeys(width.toFloat()); requestLayout(); invalidate() } }
    /** Offer the split layout in the language key's zoom (tablets). */
    var splitAvailable = false
    private var leftBlock: RectF? = null
    private var rightBlock: RectF? = null
    private val glideX = ArrayList<Float>(); private val glideY = ArrayList<Float>()
    private var menuFresh = false      // the finger that opened the menu is still down
    private var removeWord: String? = null   // a guess was held: show the bucket

    private var mode = Mode.MINI
    private var letterMode = Mode.MINI
    /** Told when the keyboard changes between one row and the full keyboard (true = full), so it can be remembered. */
    var onLetterMode: ((Boolean) -> Unit)? = null

    private var rows: List<Row> = emptyList()
    private var placed: List<Placed> = emptyList()
    private var rowRects: List<RectF> = emptyList()

    // touch state
    private enum class Gesture { NONE, CURSOR, DELWORD, MODE_SWITCH, PRECISE, SWIPE, MENU, GLIDE }
    private enum class Swipe { NONE, LEFT, RIGHT, UP_LEFT, UP_RIGHT, UP }
    private var pressed: Placed? = null
    private var gesture = Gesture.NONE
    private var downX = 0f
    private var downY = 0f
    private var anchorX = 0f
    private var suggDown = -1
    private var lastShiftTap = 0L
    private var lensX = 0f
    private var holdX = 0f                 // where the finger last moved; the hold timer restarts on movement
    private var preciseKeys: List<Placed> = emptyList()
    private var preciseIdx0 = 0
    private var preciseAnchorX = 0f
    private var preciseIdx = 0
    private var preciseEmoji = false
    private var emojiPopup: android.widget.PopupWindow? = null
    private var swipe = Swipe.NONE
    private val handler = Handler(Looper.getMainLooper())
    private val repeatDelete = object : Runnable {
        override fun run() { listener.onBackspace(); haptic(); handler.postDelayed(this, 45) }
    }
    /** Finger rested on a letter: switch to precise mode (magnifier; slide to choose; release types exactly it). */
    private val holdToPrecise = Runnable {
        val p = pressed ?: return@Runnable
        if (gesture != Gesture.NONE || suggDown >= 0) return@Runnable
        preciseEmoji = false
        val set = if (p.key.kind == Kind.CHAR) when (p.key.hint) {
            ",!?" -> PUNCTUATION
            "☺" -> { preciseEmoji = true; HoldSet(Emojis.lens(context), 0) }       // most used first; swipe up = full palette
            else -> null
        } else null
        if (p.key.kind == Kind.LANG) {
            val items = listOf(L_GEAR, L_DOCKED, L_FLOAT) + (if (splitAvailable) listOf(L_SPLIT) else emptyList())      // settings, then the layouts
            preciseKeys = items.map { Placed(Key(Kind.LANG, it, 1f), p.rect, p.row) }; preciseIdx0 = 0
        } else if (mode == Mode.FULL && p.key.kind == Kind.LETTER) {
            // full keyboard: [its small symbol] [national letters ...] -- not the letter itself (a tap types that).
            // The first national letter (else the symbol) is highlighted, so hold + release types it.
            val ch = p.key.label[0]
            val items = ArrayList<Key>()
            p.key.hint?.let { h ->
                items += Key(Kind.CHAR, h, 1f)
                SYMBOL_VARIANTS[h[0]]?.forEach { items += Key(Kind.CHAR, it.toString(), 1f) }
            }
            val first = items.size
            // a variant that is itself a letter of the alphabet (ъ under ь) counts as that letter for guessing
            lang.alternates[ch]?.forEach { v -> items += Key(Kind.CHAR, v.toString(), 1f, base = if (lang.alphabet.index(v) >= 0) v else ch) }
            if (items.isEmpty()) return@Runnable                  // nothing to offer: stay a plain tap
            preciseKeys = items.map { Placed(it, p.rect, p.row) }
            preciseIdx0 = if (first < items.size) first else 0
        } else if ((mode == Mode.SYMBOLS_FULL || mode == Mode.SYMBOLS_FULL2 || mode == Mode.FULL) && p.key.kind == Kind.CHAR && p.key.label.length == 1 &&
            SYMBOL_VARIANTS.containsKey(p.key.label[0])) {
            val items = listOf(p.key.label) + SYMBOL_VARIANTS.getValue(p.key.label[0]).map { it.toString() }
            preciseKeys = items.map { Placed(Key(Kind.CHAR, it, 1f), p.rect, p.row) }
            preciseIdx0 = 0
        } else if (set != null) {
            // hold '.' / ',': lens of common punctuation / emoji, the key itself (or the most common) in the middle
            preciseKeys = set.items.map { Placed(Key(Kind.CHAR, it, 1f), p.rect, p.row) }
            preciseIdx0 = set.start
        } else {
            if (p.key.kind != Kind.LETTER && !(p.key.kind == Kind.CHAR && rows[p.row].keys.size > 12)) return@Runnable
            preciseKeys = placed.filter { it.row == p.row && (it.key.kind == Kind.LETTER || it.key.kind == Kind.CHAR) }
            preciseIdx0 = preciseKeys.indexOf(p).coerceAtLeast(0)
        }
        pressed = preciseKeys[preciseIdx0]
        preciseIdx = preciseIdx0
        preciseAnchorX = holdX
        gesture = Gesture.PRECISE
        if (hapticsOn) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        invalidate(); syncLens()
    }
    private val holdGuess = Runnable {
        val p = pressed ?: return@Runnable
        if (p.key.kind != Kind.SUGG || suggDown < 0) return@Runnable
        val w = suggestions.getOrNull(suggDown)?.takeIf { it.isNotEmpty() } ?: return@Runnable
        removeWord = w; menuFresh = true; gesture = Gesture.MENU; suggDown = -1; pressed = null
        if (hapticsOn) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        invalidate()
    }
    private val repeatStart = Runnable {
        if (pressed?.key?.kind == Kind.BACKSPACE && gesture == Gesture.NONE) handler.post(repeatDelete)
    }

    // ---- look ----
    private var cStrip = 0xFF2E383E.toInt()
    private var cPanel = 0xFF3B474E.toInt()
    private var cKey = 0xFF46545C.toInt()
    private var cKeyDown = 0xFF56646C.toInt()
    private var cTeal = 0xFF80CBC4.toInt()          // the accent color (teal in the default look)
    private var cTealDown = 0xFF9BDAD4.toInt()
    private var cInk = 0xFFE9EFF1.toInt()
    private var cDim = 0xFF98A8AE.toInt()

    /** Theme code from [Themes]; "auto" is re-evaluated against the system light/dark setting. */
    var themeMode = Themes.DEFAULT
        set(v) { field = v; applyTheme() }

    fun applyTheme() {
        val p = Themes.palette(themeMode, resources.configuration)
        cStrip = p.strip; cPanel = p.panel; cKey = p.key; cKeyDown = p.keyDown
        cTeal = p.accent; cTealDown = p.accentDown; cInk = p.ink; cDim = p.dim
        stroke.color = cTeal; text.color = cInk
        invalidate()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (themeMode == Themes.AUTO) applyTheme()
    }
    private val face: Typeface = try {
        Typeface.createFromAsset(context.assets, "fonts/VictorMono-Light.ttf")
    } catch (e: Exception) { Typeface.create("sans-serif-light", Typeface.NORMAL) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = cTeal; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cInk; textAlign = Paint.Align.CENTER; typeface = face }

    override fun onDetachedFromWindow() { emojiPopup?.dismiss(); emojiPopup = null; hideLens(); super.onDetachedFromWindow() }

    /** A floating palette of every emoji above the keyboard; tapping one types it (and it climbs in the lens). */
    private fun showEmojiPalette() {
        val ctx = context
        val cols = 8
        val cell = width / cols
        val grid = android.widget.GridLayout(ctx).apply { columnCount = cols }
        fun add(e: String) {
            grid.addView(android.widget.TextView(ctx).apply {
                text = e; textSize = 24f; gravity = android.view.Gravity.CENTER
                layoutParams = android.widget.GridLayout.LayoutParams().apply { width = cell; height = cell }
                setOnClickListener { Emojis.bump(ctx, e); listener.onText(e); haptic(); emojiPopup?.dismiss(); emojiPopup = null }
            })
        }
        Emojis.used(ctx).forEach { add(it) }
        // pad the "used" row so the full list starts on a fresh line
        val used = Emojis.used(ctx).size
        repeat((cols - used % cols) % cols) { grid.addView(android.view.View(ctx), android.widget.GridLayout.LayoutParams().apply { width = cell; height = cell }) }
        Emojis.all().forEach { add(it) }
        val sv = android.widget.ScrollView(ctx).apply { addView(grid); setBackgroundColor(cStrip) }
        val h = (cell * 5.5f).toInt()
        val pw = android.widget.PopupWindow(sv, width, h, false).apply { isOutsideTouchable = true; isTouchable = true }
        try { pw.showAsDropDown(this, 0, -(height + h)); emojiPopup = pw } catch (_: Exception) {}
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity

    init { buildRows() }

    fun setLanguage(l: Lang) {
        lang = l
        removeWord = null
        buildRows()
        layoutKeys(width.toFloat())
        invalidate()
    }

    /** Starts in the full keyboard (or one row) without announcing it: used to bring back the last state. */
    fun restoreFull(full: Boolean) {
        val m = if (full) Mode.FULL else Mode.MINI
        if (letterMode == m && mode == m) return
        mode = m; letterMode = m
        buildRows()
        if (width > 0) layoutKeys(width.toFloat())
        requestLayout(); invalidate()
    }

    fun resetForNewField() {
        emojiPopup?.dismiss(); emojiPopup = null
        removeWord = null
        if (mode == Mode.SYMBOLS || mode == Mode.SYMBOLS_FULL || mode == Mode.SYMBOLS_FULL2) setMode(letterMode)
        shift = Shift.OFF
        suggestions = emptyList()
    }

    // ---------- layouts ----------

    private fun letters(s: String) = s.map { Key(Kind.LETTER, it.toString(), 1f) }

    /** Full-keyboard letters with a small standard symbol on each (digits on the top row). */
    private fun hinted(row: String, symbols: String) =
        row.mapIndexed { i, ch -> Key(Kind.LETTER, ch.toString(), 1f, hint = symbols.getOrNull(i)?.toString()) }
    private fun chars(s: String) = s.map { Key(Kind.CHAR, it.toString(), 1f) }
    /** The digits of the number row, each with a small hint of what its hold offers (superscripts, fractions). */
    private fun numberKeys() = "1234567890".mapIndexed { i, d -> Key(Kind.CHAR, d.toString(), 1f, hint = NUM_HINTS[i].toString()) }

    /** Letters grouped by keyboard column; inside a column they zig-zag high / middle / low. */
    private fun zigzag(groups: List<String>, kind: Kind): List<Key> = groups.flatMap { g ->
        g.mapIndexed { i, ch ->
            val dy = when (g.length) { 3 -> 0.34f + 0.16f * i; 2 -> 0.40f + 0.20f * i; else -> 0.40f }
            Key(kind, ch.toString(), 1f, dy = dy)
        }
    }

    private fun buildRows() {
        val langKey = Key(Kind.LANG, "", 1f)                    // tap: next language; hold: settings
        val comma = Key(Kind.CHAR, ",", 1f, hint = "☺", dy = 0.40f)
        val period = Key(Kind.CHAR, ".", 1f, hint = ",!?", dy = 0.40f)
        rows = when (mode) {
            Mode.MINI -> listOf(
                Row(listOf(Key(Kind.SHIFT, "", 1.3f), Key(Kind.SUGG, "", 6f),
                    Key(Kind.BACKSPACE, "", 1.3f)), stripH, panel = false),
                Row(zigzag(lang.columns, Kind.LETTER), rowH, inset = 0.5f),
                Row(listOf(Key(Kind.TO_SYMBOLS, "123", 1.5f), langKey, comma, Key(Kind.SPACE, "", 4f), period,
                    Key(Kind.ENTER, "", 1.5f)), ctrlH)
            )
            Mode.FULL -> listOfNotNull(
                Row(listOf(Key(Kind.SUGG, "", 1f)), stripH, panel = false),
                if (numberRow) Row(numberKeys(), fullRowH, inset = ((lang.fullRows[0].length - 10) / 2f).coerceAtLeast(0f), keyBg = true) else null,
                Row(hinted(lang.fullRows[0], if (numberRow) TOP_ROW_SYMBOLS else "1234567890"), fullRowH, keyBg = true),
                Row(hinted(lang.fullRows[1], HINTS_ROW2), fullRowH, inset = ((lang.fullRows[0].length - lang.fullRows[1].length) / 2f).coerceAtLeast(0f), keyBg = true),
                Row(listOf(Key(Kind.SHIFT, "", 1.5f)) + hinted(lang.fullRows[2], SYMBOLS_ROW3) + Key(Kind.BACKSPACE, "", 1.5f), fullRowH, keyBg = true),
                Row(listOf(Key(Kind.TO_SYMBOLS, "123", 1.5f), langKey, comma, Key(Kind.SPACE, "", 4f), period,
                    Key(Kind.ENTER, "", 1.5f)), fullRowH, keyBg = true)
            )
            // Gboard's symbols pages (full keyboard). Page 1: digits, @#$_&-+()/, [=\<] *"':;!? [bksp], ABC , space . enter
            Mode.SYMBOLS_FULL -> listOf(
                Row(listOf(Key(Kind.SUGG, "", 1f)), stripH, panel = false),
                Row(chars("1234567890"), fullRowH, keyBg = true),
                Row(chars(SYMBOLS_ROW2), fullRowH, keyBg = true),
                Row(listOf(Key(Kind.SYM_PAGE2, "=\\<", 1.5f)) + chars("*\"':;!?") + Key(Kind.BACKSPACE, "", 1.5f), fullRowH, keyBg = true),
                Row(listOf(Key(Kind.TO_LETTERS, "abc", 1.5f), langKey, comma, Key(Kind.SPACE, "", 4f), period,
                    Key(Kind.ENTER, "", 1.5f)), fullRowH, keyBg = true)
            )
            // Page 2: ~`|•√π÷×¶∆ / £¢€¥^°={}\ / [?123] %©®™✓[] [bksp] / ABC < space > enter
            Mode.SYMBOLS_FULL2 -> listOf(
                Row(listOf(Key(Kind.SUGG, "", 1f)), stripH, panel = false),
                Row(chars("~`|•√π÷×¶∆"), fullRowH, keyBg = true),
                Row(chars("£¢€¥^°={}\\"), fullRowH, keyBg = true),
                Row(listOf(Key(Kind.SYM_PAGE1, "?123", 1.5f)) + chars("%©®™✓[]") + Key(Kind.BACKSPACE, "", 1.5f), fullRowH, keyBg = true),
                Row(listOf(Key(Kind.TO_LETTERS, "abc", 1.5f), Key(Kind.CHAR, "<", 1f), Key(Kind.SPACE, "", 5f),
                    Key(Kind.CHAR, ">", 1f), Key(Kind.ENTER, "", 1.5f)), fullRowH, keyBg = true)
            )
            Mode.SYMBOLS -> listOf(
                Row(listOf(Key(Kind.NONE, "", 1.3f), Key(Kind.SUGG, "", 6f), Key(Kind.BACKSPACE, "", 1.3f)), stripH, panel = false),
                Row(zigzag("1234567890-/:;()\$&@\".,?!'#".chunked(3), Kind.CHAR), rowH, inset = 0.5f),
                Row(listOf(Key(Kind.TO_LETTERS, "abc", 1.5f), langKey, comma, Key(Kind.SPACE, "", 4f), period,
                    Key(Kind.ENTER, "", 1.5f)), ctrlH)
            )
        }
    }

    private fun setMode(m: Mode) {
        if (m == mode) return
        if (m != Mode.SYMBOLS && m != Mode.SYMBOLS_FULL && m != Mode.SYMBOLS_FULL2) { letterMode = m; onLetterMode?.invoke(m == Mode.FULL) }
        mode = m
        buildRows()
        layoutKeys(width.toFloat())   // same-height modes don't trigger onSizeChanged
        requestLayout()
        invalidate()
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val h = rows.sumOf { dp(it.heightDp).toDouble() }.toFloat() * heightScale + dp(bottomPad)
        setMeasuredDimension(MeasureSpec.getSize(wSpec), h.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) = layoutKeys(w.toFloat())

    private fun layoutKeys(w: Float) {
        val padH = dp(4f)
        val list = ArrayList<Placed>(); val rects = ArrayList<RectF>()
        var y = 0f
        val gap = if (split) w * SPLIT_GAP else 0f
        val side = (w - 2 * padH - gap) / 2
        var blockTop = -1f; var blockBottom = 0f
        for ((ri, row) in rows.withIndex()) {
            val rh = dp(row.heightDp) * heightScale
            val total = row.keys.sumOf { it.weight.toDouble() }.toFloat() + 2 * row.inset
            rects += RectF(0f, y, w, y + rh)
            if (split && row.panel) {
                // the guess strip stays whole; key rows fall apart: keys up to the middle go left, the rest right,
                // a key across the middle goes to the nearer half (the space bar is in both halves)
                if (blockTop < 0) blockTop = y
                blockBottom = y + rh
                val half = total / 2
                val left = ArrayList<Pair<Key, Float>>(); val right = ArrayList<Pair<Key, Float>>()
                var c = row.inset
                for (k in row.keys) {
                    val start = c; val end = c + k.weight; c = end
                    when {
                        end <= half -> left += k to k.weight
                        start >= half -> right += k to k.weight
                        k.kind == Kind.SPACE -> { left += k to (half - start); right += k to (end - half) }
                        (start + end) / 2 < half -> left += k to k.weight
                        else -> right += k to k.weight
                    }
                }
                fun lay(items: List<Pair<Key, Float>>, x0: Float) {
                    val unit = side / (items.sumOf { it.second.toDouble() }.toFloat() + row.inset)
                    var x = x0 + (if (x0 < w / 2) row.inset * unit else 0f)
                    for ((k, wt) in items) { list += Placed(k, RectF(x, y, x + wt * unit, y + rh), ri); x += wt * unit }
                }
                lay(left, padH); lay(right, w - padH - side)
            } else {
                val unit = (w - 2 * padH) / total
                var x = padH + row.inset * unit
                for (k in row.keys) { list += Placed(k, RectF(x, y, x + k.weight * unit, y + rh), ri); x += k.weight * unit }
            }
            y += rh
        }
        leftBlock = if (split && blockTop >= 0) RectF(padH, blockTop, padH + side, blockBottom) else null
        rightBlock = if (split && blockTop >= 0) RectF(w - padH - side, blockTop, w - padH, blockBottom) else null
        placed = list; rowRects = rects
    }

    /** The parts of the keyboard that take touches (in split layout the gap between the halves lets touches through). */
    fun touchRects(): List<android.graphics.Rect> {
        val l = leftBlock; val r = rightBlock
        if (!split || l == null || r == null || rowRects.isEmpty()) return listOf(android.graphics.Rect(0, 0, width, height))
        val s = rowRects[0]
        fun rect(f: RectF) = android.graphics.Rect(f.left.toInt(), f.top.toInt(), f.right.toInt(), f.bottom.toInt() + 1)
        return listOf(rect(RectF(0f, s.top, width.toFloat(), s.bottom)), rect(l), rect(r))
    }

    // ---------- drawing ----------

    override fun onDraw(c: Canvas) {
        if (placed.isEmpty()) layoutKeys(width.toFloat())
        if (split) { fill.color = cPanel; leftBlock?.let { c.drawRoundRect(it, dp(10f), dp(10f), fill) }; rightBlock?.let { c.drawRoundRect(it, dp(10f), dp(10f), fill) } }
        else c.drawColor(cPanel)
        fill.color = cStrip
        c.drawRect(0f, 0f, width.toFloat(), rowRects[0].bottom, fill)

        if (removeWord != null) drawRemove(c, rowRects[0])
        for (p in placed) {
            if (removeWord != null && p.row == 0) continue
            val row = rows[p.row]
            val down = pressed === p && gesture != Gesture.GLIDE
            val r = RectF(p.rect)
            val k = p.key
            when (k.kind) {
                Kind.SUGG -> { drawStrip(c, r); continue }
                Kind.NONE -> continue
                else -> {}
            }
            if (row.keyBg) {
                val b = RectF(r).apply { inset(dp(2.5f), dp(2.5f)) }
                fill.color = if (down) cKeyDown else cKey
                c.drawRoundRect(b, dp(6f), dp(6f), fill)
            }
            when (k.kind) {
                Kind.LETTER, Kind.CHAR -> {
                    val inRow = row.keys.size > 12 && !row.keyBg           // the dense one-row strip
                    if (down && inRow && gesture != Gesture.SWIPE) {
                        fill.color = (cTeal and 0x00FFFFFF) or 0x40000000
                        c.drawRoundRect(RectF(r).apply { inset(0f, dp(3f)) }, dp(5f), dp(5f), fill)
                    }
                    val s = if (k.kind == Kind.LETTER && (inRow || shift != Shift.OFF)) k.label.uppercase() else k.label
                    text.color = if (k.kind == Kind.CHAR && !inRow && k.hint != null && !k.label[0].isDigit()) cTeal else cInk
                    text.textSize = sp(if (inRow) (if (lang.alphabet.n > 28) 12f else 14f) else 20f)
                    drawCentered(c, s, r.centerX(), r.top + k.dy * r.height())
                    k.hint?.let { drawHint(c, it, r, k) }
                }
                Kind.TO_SYMBOLS, Kind.TO_LETTERS, Kind.SYM_PAGE2, Kind.SYM_PAGE1 -> {
                    text.color = cTeal; text.textSize = sp(15f)
                    drawCentered(c, k.label, r.centerX(), r.centerY())
                }
                Kind.SPACE -> if (!row.keyBg) {               // one row: an outline only, nothing filled; thicker while pressed (the full keyboard draws it like any key)
                    val b = RectF(r).apply { inset(dp(5f), r.height() * 0.17f) }
                    val sw = stroke.strokeWidth
                    stroke.strokeWidth = dp(if (down) 2.4f else 1.4f); c.drawRoundRect(b, dp(4f), dp(4f), stroke)
                    stroke.strokeWidth = sw
                }
                Kind.LANG -> {                                // current language, with a small gear: hold = settings
                    text.color = cTeal; text.textSize = sp(13f)
                    drawCentered(c, this.lang.code.uppercase(), r.centerX(), r.centerY())
                    val gs = dp(4.2f)
                    val gx = minOf(r.centerX() + r.width() * 0.34f, r.right - dp(4f) - gs)
                    val sw = stroke.strokeWidth; val keep = stroke.color
                    stroke.strokeWidth = dp(0.9f)
                    drawGear(c, RectF(gx - gs, r.top + r.height() * 0.28f - gs, gx + gs, r.top + r.height() * 0.28f + gs), 0.5f)
                    stroke.strokeWidth = sw; stroke.color = keep
                }
                Kind.SHIFT -> { drawShift(c, r); k.hint?.let { drawHint(c, it, r, k) } }
                Kind.BACKSPACE -> drawBackspace(c, r, down)
                Kind.ENTER -> drawEnter(c, r)
                else -> {}
            }
        }
        drawLensDim(c)
        drawTrail(c)
        text.color = cInk
    }

    // ---- the zoom: a bubble floating above the finger (a popup window, so it can rise above the keyboard) with a
    // pointer to the pressed key; the keyboard itself is dimmed and the pressed key stays lit ----
    private var lensPopup: android.widget.PopupWindow? = null
    private var lensContent: LensContent? = null
    private var lensKey: Placed? = null
    private var downTime = 0L
    private val lensTick = Runnable { syncLens() }          // brings the bubble up once the finger has rested
    private var lensGeo = floatArrayOf(Float.NaN, 0f, 0f, 0f)         // last popup x, y, w, h

    private inner class LensContent(ctx: Context) : View(ctx) {
        var items: List<String> = emptyList(); var hi = 0; var tailX = 0f; var hint = false   // hint: "swipe up for all emoji"
        private val p = Paint(Paint.ANTI_ALIAS_FLAG); private val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = face }
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val cardH = dp(LENS_CARD_DP); val r = dp(14f)
            val shape = Path().apply { addRoundRect(RectF(0f, 0f, w, cardH), r, r, Path.Direction.CW) }
            val tail = Path().apply { moveTo(tailX - dp(9f), cardH - dp(1f)); lineTo(tailX + dp(9f), cardH - dp(1f)); lineTo(tailX, height.toFloat() - dp(1f)); close() }
            shape.op(tail, Path.Op.UNION)
            p.style = Paint.Style.FILL; p.color = cKey; c.drawPath(shape, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.5f); p.color = cTeal; c.drawPath(shape, p)
            if (hint) {                                   // swipe-up hint at the right end of the emoji bubble
                val hx = w - dp(30f)
                p.style = Paint.Style.STROKE; p.strokeWidth = dp(1f); p.color = cDim; c.drawLine(hx - dp(24f), dp(9f), hx - dp(24f), cardH - dp(9f), p)
                p.strokeWidth = dp(2.2f); p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND; p.color = cTeal
                c.drawPath(Path().apply { moveTo(hx - dp(8f), cardH * 0.40f + dp(4f)); lineTo(hx, cardH * 0.40f - dp(4f)); lineTo(hx + dp(8f), cardH * 0.40f + dp(4f)) }, p)
                p.strokeCap = Paint.Cap.BUTT
                t.color = cDim; t.textSize = sp(10f); c.drawText("all", hx, cardH * 0.80f, t)
            }
            val step = dp(LENS_STEP_DP)
            for ((i, label) in items.withIndex()) {
                val x = dp(8f) + (i + 0.5f) * step
                if (i == hi) {
                    p.style = Paint.Style.FILL; p.color = cTeal
                    c.drawRoundRect(x - dp(16f), dp(6f), x + dp(16f), cardH - dp(6f), dp(9f), dp(9f), p)
                    t.color = cStrip; t.textSize = sp(19f)
                } else { t.color = cDim; t.textSize = sp(14f) }
                if (label == L_GEAR) {
                    val old = stroke.color; stroke.color = if (i == hi) cStrip else cDim
                    drawGear(c, RectF(x - dp(10f), cardH / 2 - dp(10f), x + dp(10f), cardH / 2 + dp(10f)))
                    stroke.color = old
                } else if (label == L_DOCKED || label == L_FLOAT || label == L_SPLIT) {
                    drawLayoutIcon(c, label, RectF(x - dp(10f), cardH / 2 - dp(8f), x + dp(10f), cardH / 2 + dp(8f)), if (i == hi) cStrip else cDim)
                } else { val fm = t.fontMetrics; c.drawText(label, x, cardH / 2 - (fm.ascent + fm.descent) / 2, t) }
            }
        }
    }

    private fun hideLens() {
        lensPopup?.let { try { it.dismiss() } catch (_: Exception) {} }
        lensPopup = null; lensContent = null; lensGeo[0] = Float.NaN
        if (lensKey != null) { lensKey = null; invalidate() }
    }

    /** Shows / moves / hides the bubble to match the touch state (called after every touch event). */
    private fun syncLens() {
        val down = if (gesture == Gesture.GLIDE) null else pressed
        // the preview bubble waits for the finger to rest (a tap is over before that); hold-and-slide shows it at once
        val waiting = gesture != Gesture.PRECISE && (gesture != Gesture.NONE || android.os.SystemClock.uptimeMillis() - downTime < PREVIEW_MS)
        if (down == null || waiting || !(down.key.kind == Kind.LETTER || down.key.kind == Kind.CHAR || down.key.kind == Kind.LANG) || down.key.label.isEmpty() || width == 0) { hideLens(); return }
        val precise = gesture == Gesture.PRECISE
        // the neighbours preview belongs to the one-row strip; on the full keyboard a key with nothing to offer just stays a plain press
        if (!precise && mode != Mode.MINI && mode != Mode.SYMBOLS) { hideLens(); return }
        val keys = if (precise) preciseKeys else placed.filter { it.row == down.row && (it.key.kind == Kind.LETTER || it.key.kind == Kind.CHAR) }
        val idx = keys.indexOf(down); if (idx < 0) { hideLens(); return }
        val span = if (precise) 3 else 2
        val from = maxOf(0, idx - span); val to = minOf(keys.size - 1, idx + span)
        val items = (from..to).map { i ->
            val k = keys[i]
            if ((k.key.kind == Kind.LETTER || k.key.base != null) && shift != Shift.OFF) k.key.label.uppercase() else k.key.label
        }
        val step = dp(LENS_STEP_DP)
        val emojiHint = precise && preciseEmoji
        val w = items.size * step + dp(16f) + (if (emojiHint) dp(46f) else 0f); val cardH = dp(LENS_CARD_DP); val tailH = dp(11f); val h = cardH + tailH
        val keyCx = down.rect.centerX()
        val left = (keyCx - w / 2).coerceIn(dp(4f), maxOf(dp(4f), width - w - dp(4f)))
        val top = down.rect.top - dp(3f) - h                                   // the pointer tip just above the key
        val content = lensContent ?: LensContent(context).also { lensContent = it }
        content.items = items; content.hi = idx - from; content.hint = emojiHint; content.tailX = (keyCx - left).coerceIn(dp(16f), w - dp(16f))
        val geo = floatArrayOf(left, top, w, h)
        if (lensPopup == null) {
            val pw = android.widget.PopupWindow(content, w.toInt(), h.toInt(), false).apply { isTouchable = false; isFocusable = false; isClippingEnabled = false }
            try { pw.showAsDropDown(this, left.toInt(), (top - height).toInt()); lensPopup = pw } catch (_: Exception) { lensContent = null; return }
        } else if (!geo.contentEquals(lensGeo)) {
            try { lensPopup?.update(this, left.toInt(), (top - height).toInt(), w.toInt(), h.toInt()) } catch (_: Exception) {}
        }
        lensGeo = geo
        content.invalidate()
        if (lensKey !== down) { lensKey = down; invalidate() }
    }

    /** Dims the keyboard while the bubble is up, keeping the pressed key lit. */
    private fun drawLensDim(c: Canvas) {
        val k = lensKey ?: return
        fill.color = 0x80000000.toInt(); c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        val b = RectF(k.rect).apply { inset(dp(2.5f), dp(2.5f)) }
        fill.color = (cTeal and 0x00FFFFFF) or 0x55000000; c.drawRoundRect(b, dp(6f), dp(6f), fill)
        val sw = stroke.strokeWidth; stroke.strokeWidth = dp(1.5f); c.drawRoundRect(b, dp(6f), dp(6f), stroke); stroke.strokeWidth = sw
    }

    /** The finger path of a glide, as a teal trail. */
    private fun drawTrail(c: Canvas) {
        if (gesture != Gesture.GLIDE || glideX.size < 2) return
        val path = Path().apply { moveTo(glideX[0], glideY[0]); for (i in 1 until glideX.size) lineTo(glideX[i], glideY[i]) }
        val sw = stroke.strokeWidth; val col = stroke.color
        stroke.strokeWidth = dp(7f); stroke.color = (cTeal and 0x00FFFFFF) or 0x70000000
        c.drawPath(path, stroke)
        stroke.strokeWidth = sw; stroke.color = col
    }

    private fun drawCentered(c: Canvas, s: String, cx: Float, cy: Float) {
        val fm = text.fontMetrics
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2, text)
    }

    /** Small hint in the key's top-right corner, shifted left as needed so it stays inside the key. */
    private fun drawHint(c: Canvas, h: String, r: RectF, k: Key) {
        val margin = dp(4f)
        val preferred = r.centerX() + r.width() * 0.34f
        val hy = r.top + r.height() * (if (k.kind == Kind.SHIFT) 0.22f else 0.28f)
        if (h == "☺") {   // draw the smiley: the font has no such glyph
            val rad = dp(4.2f)
            val hx = minOf(preferred, r.right - margin - rad)
            stroke.strokeWidth = dp(1.1f)
            c.drawCircle(hx, hy, rad, stroke)
            fill.color = cTeal
            c.drawCircle(hx - rad * 0.4f, hy - rad * 0.2f, dp(0.7f), fill)
            c.drawCircle(hx + rad * 0.4f, hy - rad * 0.2f, dp(0.7f), fill)
            c.drawArc(RectF(hx - rad * 0.5f, hy - rad * 0.2f, hx + rad * 0.5f, hy + rad * 0.55f), 20f, 140f, false, stroke)
        } else {
            text.color = if (k.kind == Kind.LETTER) cDim else cTeal; text.textSize = sp(9f)
            val half = text.measureText(h) / 2
            drawCentered(c, h, minOf(preferred, r.right - margin - half), hy)   // wide hints like ",!?" slide left
        }
    }

    private fun drawShift(c: Canvas, r: RectF) {
        val s = dp(7.5f); val cx = r.centerX(); val cy = r.centerY() - dp(1f)
        val p = Path().apply {
            moveTo(cx, cy - s); lineTo(cx + s, cy); lineTo(cx + s * 0.45f, cy); lineTo(cx + s * 0.45f, cy + s * 0.85f)
            lineTo(cx - s * 0.45f, cy + s * 0.85f); lineTo(cx - s * 0.45f, cy); lineTo(cx - s, cy); close()
        }
        stroke.strokeWidth = dp(1.8f)
        if (shift != Shift.OFF) { fill.color = cTeal; c.drawPath(p, fill) }
        c.drawPath(p, stroke)
        if (shift == Shift.LOCK) c.drawLine(cx - s * 0.45f, cy + s * 1.25f, cx + s * 0.45f, cy + s * 1.25f, stroke)
    }

    private fun drawBackspace(c: Canvas, r: RectF, down: Boolean) {
        val s = dp(9f); val cx = r.centerX(); val cy = r.centerY()
        val p = Path().apply {
            moveTo(cx - s, cy); lineTo(cx - s * 0.4f, cy - s * 0.7f); lineTo(cx + s, cy - s * 0.7f)
            lineTo(cx + s, cy + s * 0.7f); lineTo(cx - s * 0.4f, cy + s * 0.7f); close()
        }
        stroke.strokeWidth = dp(1.8f)
        if (down) { fill.color = (cTeal and 0x00FFFFFF) or 0x30000000; c.drawPath(p, fill) }
        c.drawPath(p, stroke)
        val x = s * 0.22f; val mx = cx + s * 0.3f
        c.drawLine(mx - x, cy - x, mx + x, cy + x, stroke); c.drawLine(mx - x, cy + x, mx + x, cy - x, stroke)
    }

    private fun drawEnter(c: Canvas, r: RectF) {
        val s = dp(8.5f); val cx = r.centerX(); val cy = r.centerY()
        val p = Path().apply {
            moveTo(cx + s, cy - s * 0.7f); lineTo(cx + s, cy + s * 0.2f); lineTo(cx - s, cy + s * 0.2f)
            moveTo(cx - s * 0.35f, cy - s * 0.35f); lineTo(cx - s, cy + s * 0.2f); lineTo(cx - s * 0.35f, cy + s * 0.75f)
        }
        stroke.strokeWidth = dp(2f)
        c.drawPath(p, stroke)
    }

    /** Suggestions, or a lens (pressed key + neighbours) while a key is held. */
    private fun drawStrip(c: Canvas, r: RectF) {
        if (gesture == Gesture.SWIPE && swipe != Swipe.NONE) { drawSwipeHint(c, r); return }
        suggLayout(r.width())
        if (scrollToBest) {                      // keep the highlighted best guess in view when the list is long
            scrollToBest = false
            val b = suggestions.indices.firstOrNull { it == bestIndex }
            if (b != null && b < slotLeft.size && slotLeft[b] + slotWidth[b] > r.width())
                suggScroll = minOf(maxOf(0f, suggTotal - r.width()), slotLeft[b] + slotWidth[b] - r.width())
        }
        c.save(); c.clipRect(r)
        for (i in suggestions.indices) {
            val s = suggestions[i].takeIf { it.isNotEmpty() } ?: continue
            val sw = slotWidth[i]
            val l = r.left + slotLeft[i] - suggScroll
            if (l + sw < r.left || l > r.right) continue
            if (i == bestIndex) {
                fill.color = cTeal
                c.drawRoundRect(l + dp(2f), r.top + dp(4f), l + sw - dp(2f), r.bottom - dp(4f), dp(8f), dp(8f), fill)
            } else if (suggDown == i) {
                fill.color = cPanel
                c.drawRoundRect(l + dp(2f), r.top + dp(4f), l + sw - dp(2f), r.bottom - dp(4f), dp(8f), dp(8f), fill)
            }
            text.color = if (i == bestIndex) cStrip else cInk
            text.textSize = sp(14f)
            val avail = sw - dp(8f)
            val w = text.measureText(s)
            if (w > avail) text.textSize = (sp(14f) * avail / w).coerceAtLeast(sp(8f))   // only a word wider than the whole bar shrinks
            drawCentered(c, s, l + sw / 2, r.centerY())
        }
        c.restore()
        if (suggTotal > r.width() + 1f) {                                // thin position marker: more guesses to the side
            val total = suggTotal; val vis = r.width() / total
            fill.color = cDim
            val x0 = r.left + (suggScroll / total) * r.width()
            c.drawRoundRect(x0, r.bottom - dp(3f), x0 + vis * r.width(), r.bottom - dp(1.5f), dp(1f), dp(1f), fill)
        }
    }

    /** Shows what the current swipe will do, in the strip. */
    private fun drawSwipeHint(c: Canvas, r: RectF) {
        val box = RectF(r.centerX() - dp(14f), r.centerY() - dp(10f), r.centerX() + dp(14f), r.centerY() + dp(10f))
        stroke.strokeWidth = dp(1.8f)
        when (swipe) {
            Swipe.LEFT -> drawBackspace(c, box, false)
            Swipe.RIGHT -> {
                val s = dp(9f); val cx = r.centerX(); val cy = r.centerY()
                c.drawPath(Path().apply { moveTo(cx - s, cy - s * 0.4f); lineTo(cx - s, cy + s * 0.4f); lineTo(cx + s, cy + s * 0.4f); lineTo(cx + s, cy - s * 0.4f) }, stroke)
            }
            Swipe.UP_RIGHT -> drawEnter(c, box)
            Swipe.UP_LEFT -> {   // microphone
                val cx = r.centerX(); val cy = r.centerY()
                c.drawRoundRect(cx - dp(3.5f), cy - dp(9f), cx + dp(3.5f), cy + dp(2f), dp(3.5f), dp(3.5f), stroke)
                c.drawArc(RectF(cx - dp(7f), cy - dp(5f), cx + dp(7f), cy + dp(6f)), 20f, 140f, false, stroke)
                c.drawLine(cx, cy + dp(6f), cx, cy + dp(9f), stroke)
            }
            Swipe.UP -> {   // chevron: full keyboard
                val cx = r.centerX(); val cy = r.centerY()
                c.drawPath(Path().apply { moveTo(cx - dp(8f), cy + dp(4f)); lineTo(cx, cy - dp(4f)); lineTo(cx + dp(8f), cy + dp(4f)) }, stroke)
            }
            else -> {}
        }
    }

    // ---------- touch ----------

    private fun haptic() { if (hapticsOn) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }

    private fun nearestInRow(x: Float, row: Int): Placed? =
        placed.filter { it.row == row && it.key.kind != Kind.NONE }.minByOrNull {
            if (x in it.rect.left..it.rect.right) 0f else min(abs(x - it.rect.left), abs(x - it.rect.right))
        }

    private fun keyAt(x: Float, y: Float): Placed? {
        if (rowRects.isEmpty()) return null
        var ri = rowRects.indexOfFirst { y >= it.top && y < it.bottom }
        if (ri < 0) ri = if (y < rowRects.first().top) 0 else rowRects.lastIndex
        return nearestInRow(x, ri)
    }

    private fun suggSlot(p: Placed, x: Float): Int {
        suggLayout(p.rect.width())
        val xx = x - p.rect.left + suggScroll
        for (i in slotLeft.indices) if (xx < slotLeft[i] + slotWidth[i]) return i
        return maxOf(0, slotLeft.size - 1)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean { val r = handleTouch(e); syncLens(); return r }

    private fun handleTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                emojiPopup?.dismiss(); emojiPopup = null
                downX = e.x; downY = e.y; anchorX = e.x; lensX = e.x
                if (removeWord != null) { gesture = Gesture.MENU; pressed = null; return true }
                gesture = Gesture.NONE; swipe = Swipe.NONE
                pressed = keyAt(e.x, e.y)
                val p = pressed
                if (p?.key?.kind == Kind.SUGG) {
                    suggDragging = false
                    suggDown = suggSlot(p, e.x)
                    if (suggestions.getOrNull(suggDown).isNullOrEmpty()) suggDown = -1
                    else handler.postDelayed(holdGuess, 500)
                    downX = e.x; downY = e.y
                    invalidate(); return true
                }
                if (p?.key?.kind == Kind.BACKSPACE) handler.postDelayed(repeatStart, 400)
                holdX = e.x
                handler.postDelayed(holdToPrecise, HOLD_MS)
                downTime = android.os.SystemClock.uptimeMillis(); handler.removeCallbacks(lensTick); handler.postDelayed(lensTick, PREVIEW_MS)
                haptic(); invalidate()
            }
            MotionEvent.ACTION_MOVE -> onMove(e)
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(repeatStart); handler.removeCallbacks(repeatDelete); handler.removeCallbacks(holdToPrecise); handler.removeCallbacks(holdGuess)
                if (gesture == Gesture.MENU) { removeUp(e); pressed = null; gesture = Gesture.NONE; invalidate(); return true }
                val p = pressed
                if (p?.key?.kind == Kind.SUGG) {
                    if (suggDown >= 0 && !suggDragging && suggSlot(p, e.x) == suggDown && e.y < rowRects[0].bottom)
                        suggestions.getOrNull(suggDown)?.let { listener.onSuggestion(it) }
                    suggDown = -1; suggDragging = false
                } else if (gesture == Gesture.GLIDE) {
                    finishGlide(p)
                } else if (gesture == Gesture.SWIPE) {
                    runSwipe()
                } else if (gesture == Gesture.PRECISE) {
                    p?.let { commitExact(it) }
                } else if (gesture == Gesture.NONE) {
                    p?.let { commit(it, e.x, e.y) }
                }
                pressed = null; gesture = Gesture.NONE; swipe = Swipe.NONE; invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(repeatStart); handler.removeCallbacks(repeatDelete); handler.removeCallbacks(holdToPrecise); handler.removeCallbacks(holdGuess)
                pressed = null; suggDown = -1; gesture = Gesture.NONE; swipe = Swipe.NONE; glideX.clear(); glideY.clear(); invalidate()
            }
        }
        return true
    }

    private fun onMove(e: MotionEvent) {
        val p = pressed ?: return
        if (p.key.kind == Kind.SUGG && hypot(e.x - downX, e.y - downY) > dp(14f)) handler.removeCallbacks(holdGuess)
        if (p.key.kind == Kind.SUGG) {
            if (mode == Mode.FULL && !suggDragging && e.y - downY > dp(36f) && e.y - downY > abs(e.x - downX) * 1.5f) {   // swipe down on the guess bar: back to one row
                handler.removeCallbacks(holdGuess); suggDown = -1; gesture = Gesture.MODE_SWITCH
                setMode(Mode.MINI); pressed = null; haptic(); invalidate(); return
            }
            if (!suggDragging && abs(e.x - downX) > dp(12f) && maxSuggScroll() > 0f) {
                suggDragging = true; handler.removeCallbacks(holdGuess)
                suggScrollStart = suggScroll; suggDragX = e.x
            }
            if (suggDragging) { suggScroll = (suggScrollStart - (e.x - suggDragX)).coerceIn(0f, maxSuggScroll()); invalidate() }
            return
        }
        if (gesture == Gesture.PRECISE) {
            if (preciseEmoji && downY - e.y > dp(40f)) {          // swipe up from the emoji lens: the full palette
                gesture = Gesture.MODE_SWITCH; pressed = null; preciseEmoji = false
                haptic(); invalidate(); showEmojiPalette(); return
            }
            // finger travel is amplified into the magnified lens: PRECISE_STEP dp = one letter
            val idx = (preciseIdx0 + Math.round((e.x - preciseAnchorX) / dp(PRECISE_STEP_DP))).coerceIn(0, preciseKeys.size - 1)
            if (idx != preciseIdx) { preciseIdx = idx; pressed = preciseKeys[idx]; haptic(); invalidate() }
            return
        }
        if (gesture == Gesture.NONE && abs(e.x - holdX) > dp(4f)) {   // still moving: not a hold yet
            holdX = e.x
            handler.removeCallbacks(holdToPrecise); handler.postDelayed(holdToPrecise, HOLD_MS)
        }
        val dx = e.x - downX
        val dy = e.y - downY
        val slop = dp(10f)
        when (p.key.kind) {
            Kind.SPACE -> {
                if (gesture == Gesture.NONE && abs(dx) > slop) gesture = Gesture.CURSOR
                if (gesture == Gesture.CURSOR) {
                    val step = dp(16f)
                    val n = ((e.x - anchorX) / step).toInt()
                    if (n != 0) { listener.onMoveCursor(n); anchorX += n * step; haptic() }
                }
            }
            Kind.BACKSPACE -> {
                if (gesture == Gesture.NONE && dx < -dp(24f)) {
                    gesture = Gesture.DELWORD
                    handler.removeCallbacks(repeatStart); handler.removeCallbacks(repeatDelete)
                    anchorX = downX - dp(24f)
                }
                if (gesture == Gesture.DELWORD) {
                    val step = dp(44f)
                    while (anchorX - e.x > step) { listener.onDeleteWord(); anchorX -= step; haptic() }
                }
            }
            Kind.LETTER, Kind.CHAR -> {
                if (mode == Mode.FULL && swipeTyping && p.key.kind == Kind.LETTER && (gesture == Gesture.NONE || gesture == Gesture.GLIDE)) {
                    if (gesture == Gesture.NONE && hypot(dx, dy) > dp(GLIDE_START_DP)) {
                        gesture = Gesture.GLIDE; handler.removeCallbacks(holdToPrecise)
                        glideX.clear(); glideY.clear(); glideX += downX; glideY += downY; haptic()
                    }
                    if (gesture == Gesture.GLIDE) {
                        for (h in 0 until e.historySize) { glideX += e.getHistoricalX(h); glideY += e.getHistoricalY(h) }
                        glideX += e.x; glideY += e.y; invalidate()
                    }
                    return
                }
                val dense = rows[p.row].keys.size > 12          // the one-row strip: swipes are shortcuts there
                if (dense && (gesture == Gesture.NONE || gesture == Gesture.SWIPE)) {
                    val dist = hypot(dx, dy)
                    if (gesture == Gesture.NONE && dist > dp(SWIPE_MIN_DP)) {
                        gesture = Gesture.SWIPE
                        handler.removeCallbacks(holdToPrecise)
                        haptic()
                    }
                    if (gesture == Gesture.SWIPE) {
                        swipe = classify(dx, dy)
                        if (swipe == Swipe.UP && dist > dp(SWIPE_MIN_DP * 1.8f) && mode == Mode.MINI) {
                            gesture = Gesture.MODE_SWITCH; swipe = Swipe.NONE
                            setMode(Mode.FULL); pressed = null; haptic(); return
                        }
                        invalidate(); return
                    }
                } else if (gesture == Gesture.NONE && abs(dy) > dp(36f) && abs(dy) > abs(dx) * 1.5f &&
                    dy > 0 && mode == Mode.FULL && !swipeTyping) {
                    gesture = Gesture.MODE_SWITCH
                    setMode(Mode.MINI)
                    pressed = null; haptic(); return
                }
                if (gesture == Gesture.NONE) {
                    nearestInRow(e.x, p.row)?.let { if (it !== p) { pressed = it; haptic() } }
                    lensX = e.x
                    invalidate()
                }
            }
            else -> {}
        }
    }

    /** Key centres of the letters on the current (full) layout, for the glide decoder. */
    private fun glideLayout(): GlideLayout {
        val a = lang.alphabet; val n = a.n
        val cx = FloatArray(n) { Float.NaN }; val cy = FloatArray(n) { Float.NaN }
        var w = 1f
        for (p in placed) if (p.key.kind == Kind.LETTER) {
            val i = a.index(p.key.label[0]); if (i < 0) continue
            cx[i] = p.rect.centerX(); cy[i] = p.rect.centerY(); w = p.rect.width()
        }
        for ((base, alts) in lang.alternates) for (alt in alts) {          // national letters are typed from their base key
            val k = a.index(alt); val b = a.index(base)
            if (k >= 0 && b >= 0 && cx[k].isNaN() && !cx[b].isNaN()) { cx[k] = cx[b]; cy[k] = cy[b] }
        }
        return GlideLayout(cx, cy, w)
    }

    private fun finishGlide(start: Placed?) {
        val n = glideX.size
        var len = 0f
        for (i in 1 until n) len += hypot(glideX[i] - glideX[i - 1], glideY[i] - glideY[i - 1])
        val g = glideLayout()
        if (n < 2 || len < g.keyW * 0.8f) { start?.let { commit(it, glideX.lastOrNull() ?: it.rect.centerX(), glideY.lastOrNull() ?: it.rect.centerY()) } }   // barely moved: a tap
        else {
            listener.onGlide(glideX.toFloatArray(), glideY.toFloatArray(), n, g)
            if (shift == Shift.ONCE) shift = Shift.OFF
        }
        glideX.clear(); glideY.clear()
    }

    /** Minuum's gesture shortcuts, by angle: left = delete, right = space, up-right = enter, up-left = voice. */
    private fun classify(dx: Float, dy: Float): Swipe {
        val a = Math.toDegrees(atan2((-dy).toDouble(), dx.toDouble()))   // 0 = right, 90 = up
        return when {
            a in -35.0..35.0 -> Swipe.RIGHT
            a > 35.0 && a < 70.0 -> Swipe.UP_RIGHT
            a in 70.0..110.0 -> Swipe.UP
            a > 110.0 && a < 145.0 -> Swipe.UP_LEFT
            a >= 145.0 || a <= -145.0 -> Swipe.LEFT
            else -> Swipe.NONE
        }
    }

    private fun runSwipe() {
        when (swipe) {
            Swipe.LEFT -> listener.onBackspace()
            Swipe.RIGHT -> listener.onText(" ")
            Swipe.UP_RIGHT -> listener.onEnter()
            Swipe.UP_LEFT -> listener.onVoice()
            else -> {}
        }
        swipe = Swipe.NONE
    }

    private fun bucketRect(): RectF = RectF(width * 0.64f, rowRects[0].top + dp(3f), width - dp(6f), rowRects[0].bottom - dp(3f))

    private fun removeUp(e: MotionEvent) {
        val w = removeWord ?: return
        val over = e.y <= rowRects[0].bottom && bucketRect().contains(e.x, e.y)
        if (over) { removeWord = null; menuFresh = false; listener.onRemoveWord(w); return }
        if (menuFresh) { menuFresh = false; return }     // the press that opened it: keep it open
        removeWord = null                                 // tapped elsewhere: cancel
    }

    /** The held guess on the left, a bucket on the right: drop or tap it to delete the word. */
    private fun drawRemove(c: Canvas, r: RectF) {
        val w = removeWord ?: return
        val pill = RectF(r.left + dp(8f), r.top + dp(4f), width * 0.6f, r.bottom - dp(4f))
        fill.color = cPanel; c.drawRoundRect(pill, dp(8f), dp(8f), fill)
        text.color = cInk; text.textSize = sp(14f)
        val avail = pill.width() - dp(12f); val tw = text.measureText(w)
        if (tw > avail) text.textSize = sp(14f) * avail / tw
        drawCentered(c, w, pill.centerX(), pill.centerY())
        val b = bucketRect()
        fill.color = 0x40E57373; c.drawRoundRect(b, dp(8f), dp(8f), fill)
        val red = 0xFFEF9A9A.toInt()
        val old = stroke.color; stroke.color = red; stroke.strokeWidth = dp(1.8f)
        val cx = b.centerX(); val cy = b.centerY()
        c.drawLine(cx - dp(7f), cy - dp(4.5f), cx + dp(7f), cy - dp(4.5f), stroke)          // lid
        c.drawLine(cx - dp(2.5f), cy - dp(7f), cx + dp(2.5f), cy - dp(7f), stroke)          // handle
        c.drawPath(Path().apply { moveTo(cx - dp(5.5f), cy - dp(3f)); lineTo(cx - dp(4.5f), cy + dp(8f)); lineTo(cx + dp(4.5f), cy + dp(8f)); lineTo(cx + dp(5.5f), cy - dp(3f)) }, stroke)
        c.drawLine(cx, cy - dp(1f), cx, cy + dp(5.5f), stroke)
        stroke.color = old
        text.color = cInk
    }

    /** Small pictures of the three layouts: a screen with the keyboard at the bottom / floating inside / in two halves. */
    private fun drawLayoutIcon(c: Canvas, which: String, r: RectF, color: Int) {
        val sw = stroke.strokeWidth; val col = stroke.color; val fc = fill.color
        stroke.color = color; stroke.strokeWidth = dp(1.4f); fill.color = color
        c.drawRoundRect(r, dp(2.5f), dp(2.5f), stroke)
        val w = r.width(); val h = r.height()
        when (which) {
            L_DOCKED -> c.drawRect(r.left + dp(1.5f), r.bottom - h * 0.34f, r.right - dp(1.5f), r.bottom - dp(1.5f), fill)
            L_FLOAT -> {
                c.drawRoundRect(RectF(r.left + w * 0.18f, r.top + h * 0.22f, r.left + w * 0.82f, r.top + h * 0.74f), dp(1.5f), dp(1.5f), fill)
                c.drawLine(r.right - dp(5f), r.bottom - dp(1.5f), r.right - dp(1.5f), r.bottom - dp(5f), stroke)        // the resize corner
            }
            L_SPLIT -> {
                c.drawRect(r.left + dp(1.5f), r.bottom - h * 0.34f, r.left + w * 0.40f, r.bottom - dp(1.5f), fill)
                c.drawRect(r.right - w * 0.40f, r.bottom - h * 0.34f, r.right - dp(1.5f), r.bottom - dp(1.5f), fill)
            }
        }
        stroke.strokeWidth = sw; stroke.color = col; fill.color = fc
    }

    private fun drawGear(c: Canvas, box: RectF, scale: Float = 1f) {
        val cx = box.centerX(); val cy = box.centerY(); val ro = dp(8f) * scale; val ri = dp(5.5f) * scale
        if (scale == 1f) stroke.strokeWidth = dp(1.8f)
        c.drawCircle(cx, cy, dp(2.6f) * scale, stroke)
        c.drawCircle(cx, cy, ri, stroke)
        for (k in 0 until 8) {
            val a = Math.toRadians(k * 45.0)
            c.drawLine(cx + ri * Math.cos(a).toFloat(), cy + ri * Math.sin(a).toFloat(), cx + ro * Math.cos(a).toFloat(), cy + ro * Math.sin(a).toFloat(), stroke)
        }
    }

    private fun commit(p: Placed, x: Float, y: Float) {
        when (p.key.kind) {
            Kind.LETTER -> if (mode == Mode.FULL) commitExact(p) else {   // full keyboard: exactly what you hit
                listener.onLetter(p.key.label[0], letterLogProbs(x, y), false)
                if (shift == Shift.ONCE) shift = Shift.OFF
            }
            Kind.CHAR -> listener.onText(p.key.label)
            Kind.LANG -> if (languages.size > 1) {
                val i = languages.indexOfFirst { it.code == this.lang.code }
                listener.onLanguage(languages[(i + 1) % languages.size].code)
            }
            Kind.SPACE -> listener.onText(" ")
            Kind.BACKSPACE -> listener.onBackspace()
            Kind.ENTER -> listener.onEnter()
            Kind.SHIFT -> {
                val now = System.currentTimeMillis()
                shift = when {
                    now - lastShiftTap < 350 -> Shift.LOCK
                    shift == Shift.OFF -> Shift.ONCE
                    else -> Shift.OFF
                }
                lastShiftTap = now
            }
            Kind.TO_SYMBOLS -> setMode(if (mode == Mode.FULL) Mode.SYMBOLS_FULL else Mode.SYMBOLS)   // keep the keyboard's size
            Kind.TO_LETTERS -> setMode(letterMode)
            Kind.SYM_PAGE2 -> setMode(Mode.SYMBOLS_FULL2)
            Kind.SYM_PAGE1 -> setMode(Mode.SYMBOLS_FULL)
            else -> {}
        }
    }

    private fun commitExact(p: Placed) {
        when (p.key.kind) {
            Kind.LETTER -> {
                val ch = p.key.label[0]
                listener.onLetter(ch, FloatArray(lang.alphabet.n) { -30f }.also { it[lang.alphabet.index(ch)] = 0f }, true)
                if (shift == Shift.ONCE) shift = Shift.OFF
            }
            Kind.CHAR -> if (p.key.base != null) {
                listener.onAccent(p.key.base, p.key.label)
                if (shift == Shift.ONCE) shift = Shift.OFF
            } else {
                if (p.key.label[0].code >= 0x2190 || Character.isSurrogate(p.key.label[0])) Emojis.bump(context, p.key.label)
                listener.onText(p.key.label)
            }
            Kind.LANG -> when (p.key.label) {
                L_DOCKED -> listener.onMode("docked")
                L_FLOAT -> listener.onMode("floating")
                L_SPLIT -> listener.onMode("split")
                else -> listener.onSettings()
            }
            else -> {}
        }
    }

    /** Spatial likelihood of the touch under every letter key of the current layout. */
    private fun letterLogProbs(x: Float, y: Float): FloatArray {
        val cx = FloatArray(lang.alphabet.n) { Float.NaN }; val cy = FloatArray(lang.alphabet.n) { Float.NaN }
        var w = 1f; var h = 1f
        for (p in placed) if (p.key.kind == Kind.LETTER) {
            val i = lang.alphabet.index(p.key.label[0])
            cx[i] = p.rect.centerX(); cy[i] = p.rect.centerY(); w = p.rect.width(); h = p.rect.height()
        }
        return if (mode == Mode.MINI) SpatialModel.logProbs(x, y, cx, cy, w * tapSigma, 1e6f)   // x only: one row
        else SpatialModel.logProbs(x, y, cx, cy, w * 0.6f, h * 0.6f)
    }

    /** What Gboard offers when a symbol is held (after the symbol itself). */
    private val SYMBOL_VARIANTS = mapOf(
        '1' to "¹½⅓¼⅕⅙⅛", '2' to "²⅔⅖", '3' to "³¾⅗⅜", '4' to "⁴⅘", '5' to "⁵⅚⅝", '6' to "⁶", '7' to "⁷⅞", '8' to "⁸", '9' to "⁹",
        '0' to "⁰ⁿ∅°",
        '#' to "№", '\$' to "¢£€¥₱", '%' to "‰", '-' to "—–", '+' to "±", '(' to "[{<", ')' to "]}>",
        '*' to "†‡★", '"' to "„“”«»", '\'' to "‘’‚‹›", '!' to "¡", '?' to "¿", '=' to "≠≈∞", '<' to "≤«‹", '>' to "≥»›",
    )

    private class HoldSet(val items: List<String>, val start: Int)

    private companion object {
        // the user's core set (, . " ? ! - :) sits around "."; the extras (' ; parentheses) are further out
        val PUNCTUATION = HoldSet(listOf("(", ")", "\"", "'", "-", ":", ";", "?", "!", ".", ","), 9)

        // heights in dp -- tune here (const: must exist before init { buildRows() } runs)
        const val stripH = 32f
        const val rowH = 36f
        const val ctrlH = 32f
        const val fullRowH = 42f
        const val bottomPad = 2f
        const val SLOTS = 4
        const val SYMBOLS_ROW2 = "@#\$_&-+()/"       // small symbols under the 2nd / 3rd letter rows
        const val HINTS_ROW2 = "@#\$%&-+()/"          // Gboard's small symbols under the 2nd letter row
        const val SYMBOLS_ROW3 = "*\"':;!?"
        const val TOP_ROW_SYMBOLS = "~`|•√π÷×¶∆"   // Gboard's second symbols page, first row: hints on the top letter row when the number row is on
        const val NUM_HINTS = "½²³¼⁵⁶⁷⁸⁹⁰"       // under 1234567890 on the number row
        const val SWIPE_MIN_DP = 26f      // travel before a stroke on the letter row counts as a shortcut swipe
        const val SPLIT_GAP = 0.24f         // share of the width left empty between the halves in split layout
        const val L_GEAR = "\u2699"         // the language key's zoom: settings and the three layouts (private-use code points = icons drawn in code)
        const val L_DOCKED = "\uE000"
        const val L_FLOAT = "\uE001"
        const val L_SPLIT = "\uE002"
        const val LENS_CARD_DP = 46f
        const val LENS_STEP_DP = 36f
        const val GLIDE_START_DP = 16f    // finger travel that turns a press on a letter into a glide
        const val HOLD_MS = 300L          // finger at rest this long on a letter = hold and slide (exact letter), with a buzz
        const val PREVIEW_MS = 150L       // finger at rest this long = the zoom preview bubble appears
        const val PRECISE_STEP_DP = 22f   // finger travel per letter while holding
    }
}
