package com.example.onerow

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView

/**
 * Makes the app's own pages (settings, help, about, crash report) look like the keyboard: the chosen keyboard theme's colors and its
 * font (Victor Mono). Views are marked by tag: "h" = section header / accent text, "dim" = secondary text, "sel" = the chosen list row.
 */
object AppStyle {
    const val HEADER = "h"
    const val DIM = "dim"
    const val SELECTED = "sel"

    private var cachedFace: Typeface? = null
    fun face(ctx: Context): Typeface = cachedFace ?: (try {
        Typeface.createFromAsset(ctx.applicationContext.assets, "fonts/VictorMono-Light.ttf")
    } catch (e: Exception) { Typeface.create("sans-serif-light", Typeface.NORMAL) }).also { cachedFace = it }

    fun palette(ctx: Context): Palette = Themes.palette(Prefs(ctx).theme, ctx.resources.configuration)

    private fun isLight(c: Int) = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000 > 140
    private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density)
    private fun checked(on: Int, off: Int) = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(on, off))
    private fun enabled(on: Int, off: Int) = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf()), intArrayOf(on, off))

    /** Colors the whole view tree under [root]. Safe to call again when the theme changes. */
    fun apply(root: View, p: Palette) {
        val ctx = root.context
        val face = face(ctx)
        fun walk(v: View) {
            when (v) {
                is EditText -> {
                    v.setTextColor(p.ink); v.setHintTextColor(p.dim); v.backgroundTintList = ColorStateList.valueOf(p.accent)
                    v.setBackgroundColor(p.panel); v.typeface = face
                    v.background = GradientDrawable().apply { cornerRadius = dp(ctx, 8); setColor(p.key) }
                    v.setPadding(dp(ctx, 12).toInt(), dp(ctx, 10).toInt(), dp(ctx, 12).toInt(), dp(ctx, 10).toInt())
                }
                is CompoundButton -> {
                    v.setTextColor(p.ink); v.typeface = face
                    v.buttonTintList = checked(p.accent, p.dim)
                    if (v is android.widget.Switch) { v.thumbTintList = checked(p.accent, p.dim); v.trackTintList = checked((p.accent and 0x00FFFFFF) or 0x66000000, p.key) }
                }
                is Button -> {
                    v.typeface = face; v.setTextColor(enabled(p.ink, p.dim))
                    val fill = GradientDrawable().apply { cornerRadius = dp(ctx, 8); setColor(p.key) }
                    v.background = RippleDrawable(ColorStateList.valueOf(p.keyDown), fill, null)
                }
                is TextView -> {
                    val sp = v.textSize / (v.resources.displayMetrics.density * v.resources.configuration.fontScale)
                    when {
                        v.tag == HEADER -> { v.setTextColor(p.accent); v.typeface = Typeface.create(face, Typeface.BOLD) }
                        v.tag == SELECTED -> { v.setTextColor(p.accent); v.typeface = face }
                        v.tag == DIM || sp <= 13.5f -> { v.setTextColor(p.dim); if (v.typeface != Typeface.MONOSPACE) v.typeface = face }
                        else -> { v.setTextColor(p.ink); if (v.typeface != Typeface.MONOSPACE) v.typeface = face }
                    }
                }
            }
            v.invalidate()
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        root.setBackgroundColor(p.panel)
        walk(root)
    }

    /** Window background, system bar icons and action bar in the theme's colors. */
    fun window(a: Activity, p: Palette) {
        val w = a.window
        w.setBackgroundDrawable(ColorDrawable(p.panel))
        @Suppress("DEPRECATION") run { w.statusBarColor = p.strip; w.navigationBarColor = p.panel }
        val light = isLight(p.strip)
        if (Build.VERSION.SDK_INT >= 30) {
            val m = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            w.insetsController?.setSystemBarsAppearance(if (light) m else 0, m)
        } else @Suppress("DEPRECATION") {
            var f = w.decorView.systemUiVisibility
            f = if (light) f or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else f and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            w.decorView.systemUiVisibility = f
        }
        a.actionBar?.let { bar ->
            bar.setBackgroundDrawable(ColorDrawable(p.strip))
            val s = SpannableString(a.getString(R.string.app_name))
            s.setSpan(ForegroundColorSpan(p.ink), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(FaceSpan(face(a)), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            bar.title = s
        }
    }

    /** A rounded panel-colored background for dialogs. */
    fun dialogBackground(ctx: Context, p: Palette) = GradientDrawable().apply { cornerRadius = dp(ctx, 16); setColor(p.panel) }

    private class FaceSpan(private val f: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) { tp.typeface = f }
        override fun updateMeasureState(tp: TextPaint) { tp.typeface = f }
    }
}
