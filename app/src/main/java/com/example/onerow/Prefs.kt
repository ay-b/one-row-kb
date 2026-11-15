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
        get() = sp.getFloat("height", 1f)
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

    /** How the keyboard sits on screen: "docked" (normal), "floating" (movable, resizable window) or "split" (two halves, tablets). */
    var layoutMode: String
        get() = (sp.getString("layout", "docked") ?: "docked").takeIf { it == "floating" || it == "split" } ?: "docked"
        set(v) = sp.edit().putString("layout", v).apply()

    /** Floating keyboard: width as a share of the screen, and where it was left (offsets, see FloatingKeyboard). */
    var floatWidth: Float
        get() = sp.getFloat("float_w", 0.8f).coerceIn(0.4f, 1f)
        set(v) = sp.edit().putFloat("float_w", v).apply()
    var floatX: Int
        get() = sp.getInt("float_x", Int.MIN_VALUE)
        set(v) = sp.edit().putInt("float_x", v).apply()
    var floatY: Int
        get() = sp.getInt("float_y", Int.MIN_VALUE)
        set(v) = sp.edit().putInt("float_y", v).apply()

    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) = sp.edit().putBoolean("haptics", v).apply()

    /** Current language, falling back to the first enabled one if it was disabled. */
    fun current(): Lang {
        val en = enabled
        return en.firstOrNull { it.code == currentCode } ?: en[0]
    }
}
