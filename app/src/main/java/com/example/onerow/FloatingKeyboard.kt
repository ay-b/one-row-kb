package com.example.onerow

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow

/**
 * The keyboard as a floating window: drag it by the bar under the keyboard, hide it with the "v" at that bar's left,
 * resize it by dragging any corner (width follows the horizontal drag, height the vertical one).
 *
 * It is a popup window attached to the input-method window (the real input view shrinks to a sliver while the keyboard
 * floats), so it sits above the app and everything outside it still belongs to the app. The position is kept in screen
 * pixels (left, top), the width as a share of the screen, the height as the keyboard's own scale, all in [Prefs].
 */
class FloatingKeyboard(private val ctx: Context, private val prefs: Prefs, private val onHide: () -> Unit, private val onSwitch: () -> Unit) {
    private val dm = ctx.resources.displayMetrics
    private fun dp(v: Float) = v * dm.density

    private var popup: PopupWindow? = null
    private var anchor: View? = null
    private var kv: KeyboardView? = null
    private var frame: FrameLayout? = null
    private var column: LinearLayout? = null
    private var bar: Bar? = null
    private val corners = ArrayList<Corner>()
    /** Resize mode: the corners are shown and take drags (a tap on the bar toggles it); otherwise they stay out of the keys' way. */
    private var resizing = false
    private fun setResizing(on: Boolean) {
        resizing = on
        corners.forEach { it.visibility = if (on) View.VISIBLE else View.GONE }
        bar?.invalidate()
    }
    private var winLeft = 0; private var winTop = 0
    private var w = 0; private var h = 0

    fun isShowing() = popup != null

    /** Shows the keyboard [kv] in a floating window, next to the window of [anchor] (the docked input view). */
    fun show(anchor: View, kv: KeyboardView, backColor: Int, hintColor: Int) {
        this.anchor = anchor; this.kv = kv
        if (frame == null) build()
        val col = column!!
        if (kv.parent !== col) { (kv.parent as? ViewGroup)?.removeView(kv); col.addView(kv, 0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        setColors(backColor, hintColor)
        w = (dm.widthPixels * prefs.floatWidth).toInt()
        frame!!.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        h = frame!!.measuredHeight
        winLeft = if (prefs.floatX == Int.MIN_VALUE) (dm.widthPixels - w) / 2 else prefs.floatX
        winTop = if (prefs.floatY == Int.MIN_VALUE) dm.heightPixels - h - dp(96f).toInt() else prefs.floatY
        clamp()
        if (popup != null) { place(); return }
        val a = offsets() ?: return
        val pw = PopupWindow(frame, w, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            isClippingEnabled = false; isTouchable = true; isFocusable = false
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            elevation = dp(14f)
        }
        try { pw.showAsDropDown(anchor, a.first, a.second); popup = pw } catch (_: Exception) { }
    }

    /** Takes the floating window down; the keyboard stays inside it (see [release]). */
    fun hide() { setResizing(false); popup?.let { try { it.dismiss() } catch (_: Exception) { } }; popup = null }

    /** Takes the floating window down and gives the keyboard view back. */
    fun release(kv: KeyboardView) { hide(); column?.removeView(kv) }

    fun setColors(backColor: Int, hintColor: Int) {
        bar?.let { it.back = backColor; it.hint = hintColor; it.invalidate() }
        corners.forEach { it.hint = hintColor; it.invalidate() }
    }

    // ---- placement ----

    private fun clamp() {
        winLeft = winLeft.coerceIn(0, maxOf(0, dm.widthPixels - w))
        winTop = winTop.coerceIn(dp(24f).toInt(), maxOf(dp(24f).toInt(), dm.heightPixels - maxOf(h, dp(80f).toInt())))
    }

    /** Offsets of the window's top-left from the bottom-left of the anchor view, for showAsDropDown / update. */
    private fun offsets(): Pair<Int, Int>? {
        val a = anchor ?: return null
        val loc = IntArray(2); a.getLocationOnScreen(loc)
        return (winLeft - loc[0]) to (winTop - (loc[1] + a.height))
    }

    private fun place() {
        val a = anchor ?: return; val o = offsets() ?: return
        try { popup?.update(a, o.first, o.second, w, ViewGroup.LayoutParams.WRAP_CONTENT) } catch (_: Exception) { }
    }

    private fun remember() {
        prefs.floatX = winLeft; prefs.floatY = winTop; prefs.floatWidth = w.toFloat() / dm.widthPixels
        kv?.let { prefs.floatHeight = it.heightScale }
    }

    // ---- views ----

    private fun build() {
        val f = FrameLayout(ctx)
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Bar(ctx).also { bar = it }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26f).toInt()))   // the keyboard goes above it
        f.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val sz = dp(24f).toInt()
        for ((left, top) in listOf(true to true, false to true, true to false, false to false)) {
            val g = (if (left) Gravity.START else Gravity.END) or (if (top) Gravity.TOP else Gravity.BOTTOM)
            val cv = Corner(ctx, left, top).also { corners += it; it.visibility = View.GONE }
            f.addView(cv, FrameLayout.LayoutParams(sz, sz, g))
        }
        f.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) { outline.setRoundRect(0, 0, view.width, view.height, dp(14f)) }
        }
        f.clipToOutline = true
        // the keyboard grew or shrank (one row <-> full keyboard): keep the window on screen
        f.addOnLayoutChangeListener { v, _, t, _, b, _, ot, _, ob ->
            if (popup != null && (b - t) != (ob - ot) && b - t > 0) { h = b - t; clamp(); place() }
        }
        frame = f; column = col
    }

    /** The bar under the keyboard: a "v" at the left hides the keyboard, the rest is the handle to drag it by. */
    private inner class Bar(c: Context) : View(c) {
        var back = 0xFF2E383E.toInt(); var hint = 0xFF98A8AE.toInt()
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        private var downX = 0f; private var downY = 0f; private var startLeft = 0; private var startTop = 0; private var moved = false
        private val chevronW get() = dp(56f)
        override fun onDraw(c: Canvas) {
            p.style = Paint.Style.FILL; p.color = back; c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
            p.color = if (resizing) 0xFF7FCFC6.toInt() else hint
            val half = if (resizing) dp(30f) else dp(22f)
            c.drawRoundRect(width / 2f - half, height / 2f - dp(2f), width / 2f + half, height / 2f + dp(2f), dp(2f), dp(2f), p)
            p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.8f)
            val cx = dp(27f); val cy = height / 2f
            c.drawLine(cx - dp(4.2f), cy - dp(1.8f), cx, cy + dp(2.4f), p); c.drawLine(cx + dp(4.2f), cy - dp(1.8f), cx, cy + dp(2.4f), p)
            // the globe at the right: switch input method
            val gx = width - dp(34f); val r = dp(6f); p.strokeWidth = dp(1.4f)
            c.drawCircle(gx, cy, r, p); c.drawOval(RectF(gx - r * 0.45f, cy - r, gx + r * 0.45f, cy + r), p); c.drawLine(gx - r, cy, gx + r, cy, p)      // the "v"
        }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startLeft = winLeft; startTop = winTop; moved = false; return true }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(e.rawX - downX) > dp(6f) || Math.abs(e.rawY - downY) > dp(6f)) moved = true
                    if (moved && e.x > chevronW) { winLeft = startLeft + (e.rawX - downX).toInt(); winTop = startTop + (e.rawY - downY).toInt(); clamp(); place() }
                    return true
                }
                MotionEvent.ACTION_UP -> { if (!moved) { if (e.x < chevronW) onHide() else if (e.x > width - dp(60f)) onSwitch() else setResizing(!resizing) } else remember(); return true }
                MotionEvent.ACTION_CANCEL -> { remember(); return true }
            }
            return true
        }
    }

    /** A corner of the window: drag it to resize in any direction. A small corner mark shows where to grab. */
    private inner class Corner(c: Context, private val left: Boolean, private val top: Boolean) : View(c) {
        var hint = 0xFF98A8AE.toInt()
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private var downX = 0f; private var downY = 0f
        private var sLeft = 0; private var sTop = 0; private var sW = 0; private var sH = 0; private var sScale = 1f; private var sKvH = 1f
        override fun onDraw(c: Canvas) {
            p.color = hint; p.strokeWidth = dp(2f)
            val m = dp(6f); val l = dp(12f)
            val x = if (left) m else width - m; val y = if (top) m else height - m
            val dx = if (left) l else -l; val dy = if (top) l else -l
            c.drawLine(x, y, x + dx, y, p); c.drawLine(x, y, x, y + dy, p)
        }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            val k = kv ?: return true
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; sLeft = winLeft; sTop = winTop; sW = w; sH = h
                    sScale = k.heightScale; sKvH = k.height.toFloat().coerceAtLeast(1f)
                    parent.requestDisallowInterceptTouchEvent(true); return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt(); val dy = e.rawY - downY
                    val min = (dm.widthPixels * 0.4f).toInt()
                    val right = sLeft + sW
                    w = (if (left) sW - dx else sW + dx).coerceIn(min, dm.widthPixels)
                    winLeft = if (left) right - w else sLeft
                    val wantKv = (if (top) sKvH - dy else sKvH + dy).coerceAtLeast(dp(60f))
                    val scale = (sScale * wantKv / sKvH).coerceIn(0.5f, 1.6f)
                    val kvNow = sKvH * scale / sScale
                    h = (sH + (kvNow - sKvH)).toInt()
                    winTop = if (top) sTop + sH - h else sTop
                    k.heightScale = scale
                    clamp(); place(); return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { remember(); return true }
            }
            return true
        }
    }
}
