package com.example.onerow

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow

/**
 * The keyboard as a floating window: drag it by the handle on top, resize it with the grip in the lower-right corner.
 *
 * It is a popup window attached to the input-method window (the real input view shrinks to a sliver while the keyboard
 * floats), so it sits above the app and everything outside it still belongs to the app. The position is kept in screen
 * pixels (left, top) and the width as a share of the screen, both remembered in [Prefs].
 */
class FloatingKeyboard(private val ctx: Context, private val prefs: Prefs) {
    private val dm = ctx.resources.displayMetrics
    private fun dp(v: Float) = v * dm.density

    private var popup: PopupWindow? = null
    private var anchor: View? = null
    private var frame: FrameLayout? = null
    private var column: LinearLayout? = null
    private var handle: Handle? = null
    private var grip: Grip? = null
    private var winLeft = 0; private var winTop = 0
    private var w = 0; private var h = 0

    fun isShowing() = popup != null

    /** Shows the keyboard [kv] in a floating window, next to the window of [anchor] (the docked input view). */
    fun show(anchor: View, kv: KeyboardView, backColor: Int, hintColor: Int) {
        this.anchor = anchor
        if (frame == null) build()
        val col = column!!
        if (kv.parent !== col) { (kv.parent as? ViewGroup)?.removeView(kv); col.addView(kv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
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
    fun hide() { popup?.let { try { it.dismiss() } catch (_: Exception) { } }; popup = null }

    /** Takes the floating window down and gives the keyboard view back. */
    fun release(kv: KeyboardView) { hide(); column?.removeView(kv) }

    fun setColors(backColor: Int, hintColor: Int) {
        handle?.let { it.back = backColor; it.hint = hintColor; it.invalidate() }
        grip?.let { it.hint = hintColor; it.invalidate() }
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

    private fun remember() { prefs.floatX = winLeft; prefs.floatY = winTop; prefs.floatWidth = w.toFloat() / dm.widthPixels }

    // ---- views ----

    private fun build() {
        val f = FrameLayout(ctx)
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val hd = Handle(ctx).also { handle = it }
        col.addView(hd, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24f).toInt()))
        f.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val g = Grip(ctx).also { grip = it }
        f.addView(g, FrameLayout.LayoutParams(dp(34f).toInt(), dp(34f).toInt(), Gravity.BOTTOM or Gravity.END))
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

    /** The bar on top: drag the keyboard by it. */
    private inner class Handle(c: Context) : View(c) {
        var back = 0xFF2E383E.toInt(); var hint = 0xFF98A8AE.toInt()
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private var downX = 0f; private var downY = 0f; private var startLeft = 0; private var startTop = 0
        override fun onDraw(c: Canvas) {
            p.color = back; c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
            p.color = hint; c.drawRoundRect(width / 2f - dp(22f), height / 2f - dp(2f), width / 2f + dp(22f), height / 2f + dp(2f), dp(2f), dp(2f), p)
        }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startLeft = winLeft; startTop = winTop; return true }
                MotionEvent.ACTION_MOVE -> { winLeft = startLeft + (e.rawX - downX).toInt(); winTop = startTop + (e.rawY - downY).toInt(); clamp(); place(); return true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { remember(); return true }
            }
            return true
        }
    }

    /** The corner at the lower right: drag it to change the width. */
    private inner class Grip(c: Context) : View(c) {
        var hint = 0xFF98A8AE.toInt()
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private var downX = 0f; private var startW = 0
        override fun onDraw(c: Canvas) {
            p.color = hint; p.strokeWidth = dp(2f)
            val r = width - dp(6f); val b = height - dp(6f)
            c.drawLine(r, b - dp(14f), r - dp(14f), b, p); c.drawLine(r, b - dp(7f), r - dp(7f), b, p)
        }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; startW = w; return true }
                MotionEvent.ACTION_MOVE -> {
                    val min = (dm.widthPixels * 0.4f).toInt(); val max = dm.widthPixels - winLeft
                    w = (startW + (e.rawX - downX).toInt()).coerceIn(min, maxOf(min, max)); place(); return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { remember(); return true }
            }
            return true
        }
    }
}
