package com.example.onerow

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * The clipboard history: the texts copied while the keyboard is installed, newest first. It lives in memory only (nothing is written to disk,
 * so it is gone when the keyboard process ends) and skips what an app marks as sensitive (a password manager's copy).
 */
object Clips {
    private const val MAX = 30
    private const val MAX_CHARS = 5000
    private val items = ArrayList<String>()
    private var registered = false

    fun all(): List<String> = synchronized(items) { items.toList() }

    fun remove(s: String) { synchronized(items) { items.remove(s) } }

    /** Starts listening for copies (once); the text on the clipboard right now is taken too. */
    fun start(ctx: Context) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        if (!registered) {
            registered = true
            cm.addPrimaryClipChangedListener { take(cm) }
        }
        take(cm)
    }

    private fun take(cm: ClipboardManager) {
        try {
            if (!cm.hasPrimaryClip()) return
            val clip: ClipData = cm.primaryClip ?: return
            val extras = clip.description?.extras
            if (extras != null && (extras.getBoolean("android.content.extra.IS_SENSITIVE", false) || extras.getBoolean("com.android.systemui.SENSITIVE", false))) return
            val t = clip.getItemAt(0)?.text?.toString()?.take(MAX_CHARS) ?: return
            if (t.isBlank()) return
            synchronized(items) { items.remove(t); items.add(0, t); while (items.size > MAX) items.removeAt(items.size - 1) }
        } catch (_: Exception) { }      // reading the clipboard can be refused (the keyboard is not the focused app): then there is simply nothing new
    }
}
