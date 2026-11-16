package com.example.onerow

import android.content.Context

/** Settings shared between the keyboard service and the settings screen. */
class Prefs(context: Context) {
    private val appCtx = context.applicationContext
    private val sp = context.applicationContext.getSharedPreferences("onerow", Context.MODE_PRIVATE)

    var enabledCodes: List<String>
        get() = (sp.getString("langs", "en") ?: "en").split(',').filter { c -> Languages.all.any { it.code == c } }
            .ifEmpty { listOf("en") }
        set(v) = sp.edit().putString("langs", v.joinToString(",")).apply()

    /** Enabled languages in the canonical order (only those whose dictionary is installed). */
    val enabled: List<Lang> get() = Languages.all.filter { it.code in enabledCodes && Dictionaries.has(appCtx, it.code) }.ifEmpty { listOf(Languages.all[0]) }

    var currentCode: String
        get() = sp.getString("cur", "en") ?: "en"
        set(v) = sp.edit().putString("cur", v).apply()

    /** Keyboard height multiplier, 0.7 .. 1.5. */
    var heightScale: Float
        get() = sp.getFloat("height", 1f).coerceIn(0.5f, 1.6f)
        set(v) = sp.edit().putFloat("height", v).apply()

    /** One of [Themes]: default (original look), light, dark, auto (follows the system). */
    var theme: String
        get() = (sp.getString("theme", Themes.DEFAULT) ?: Themes.DEFAULT).takeIf { t -> Themes.choices.any { it.first == t } } ?: Themes.DEFAULT
        set(v) = sp.edit().putString("theme", v).apply()

    /** How far a tap may land from a letter and still count on the one-row keyboard: 0 tight, 1 normal, 2 loose. */
    var tolerance: Int
        get() = sp.getInt("tolerance", 1).coerceIn(0, 2)
        set(v) = sp.edit().putInt("tolerance", v.coerceIn(0, 2)).apply()

    /** Glide typing on the full keyboard (draw a word across the letters). Off by default. */
    var swipeTyping: Boolean
        get() = sp.getBoolean("swipe_typing", false)
        set(v) = sp.edit().putBoolean("swipe_typing", v).apply()

    /** How the keyboard sits on screen: "docked" (normal), "floating" (movable, resizable window), "split" (two halves, tablets) or "left" / "right" (smaller, pushed to one side, tablets). */
    var layoutMode: String
        get() = (sp.getString("layout", "docked") ?: "docked").takeIf { it in setOf("floating", "split", "left", "right") } ?: "docked"
        set(v) = sp.edit().putString("layout", v).apply()

    /** Floating keyboard: width as a share of the screen, and where it was left (offsets, see FloatingKeyboard). */
    var floatWidth: Float
        get() = sp.getFloat("float_w", 0.8f).coerceIn(0.4f, 1f)
        set(v) = sp.edit().putFloat("float_w", v).apply()
    /** Floating keyboard: its own height multiplier (corner drags change it). */
    var floatHeight: Float
        get() = sp.getFloat("float_h", 1f).coerceIn(0.5f, 1.6f)
        set(v) = sp.edit().putFloat("float_h", v).apply()
    var floatX: Int
        get() = sp.getInt("float_x", Int.MIN_VALUE)
        set(v) = sp.edit().putInt("float_x", v).apply()
    var floatY: Int
        get() = sp.getInt("float_y", Int.MIN_VALUE)
        set(v) = sp.edit().putInt("float_y", v).apply()

    /** The keyboard was last left expanded (full keyboard) rather than one row: it comes back the same way. */
    var fullKeyboard: Boolean
        get() = sp.getBoolean("full_keyboard", false)
        set(v) = sp.edit().putBoolean("full_keyboard", v).apply()

    /** A dedicated row of digits above the letters of the full keyboard (their zoom offers superscripts and fractions). */
    var numberRow: Boolean
        get() = sp.getBoolean("number_row", false)
        set(v) = sp.edit().putBoolean("number_row", v).apply()

    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) = sp.edit().putBoolean("haptics", v).apply()

    /** Current language, falling back to the first enabled one if it was disabled. */
    fun current(): Lang {
        val en = enabled
        return en.firstOrNull { it.code == currentCode } ?: en[0]
    }
}
