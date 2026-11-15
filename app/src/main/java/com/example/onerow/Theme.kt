package com.example.onerow

import android.content.res.Configuration

/** Keyboard colors. [strip] = top band, [panel] = background and highlights, [key] = key caps, [accent] = the colored parts. */
class Palette(
    val strip: Int, val panel: Int, val key: Int, val keyDown: Int,
    val accent: Int, val accentDown: Int, val ink: Int, val dim: Int,
)

object Themes {
    const val DEFAULT = "default"      // the original slate + teal look, always
    const val LIGHT = "light"
    const val DARK = "dark"
    const val AUTO = "auto"            // follows the system: light or dark

    /** Code -> label, in the order shown in settings. */
    val choices = listOf(DEFAULT to "Default", LIGHT to "Light", DARK to "Dark", AUTO to "Auto (follow system)")

    private val slate = Palette(0xFF2E383E.toInt(), 0xFF3B474E.toInt(), 0xFF46545C.toInt(), 0xFF56646C.toInt(),
        0xFF80CBC4.toInt(), 0xFF9BDAD4.toInt(), 0xFFE9EFF1.toInt(), 0xFF98A8AE.toInt())
    private val dark = Palette(0xFF1A1C1E.toInt(), 0xFF26292C.toInt(), 0xFF3A3E42.toInt(), 0xFF4B5055.toInt(),
        0xFF8AB4F8.toInt(), 0xFFAECBFA.toInt(), 0xFFE8EAED.toInt(), 0xFF9AA0A6.toInt())
    private val light = Palette(0xFFDFE2E6.toInt(), 0xFFECEEF1.toInt(), 0xFFFFFFFF.toInt(), 0xFFD3D7DC.toInt(),
        0xFF1A73E8.toInt(), 0xFF4C8DF6.toInt(), 0xFF1F2328.toInt(), 0xFF5F6872.toInt())

    fun palette(mode: String, config: Configuration): Palette = when (mode) {
        LIGHT -> light
        DARK -> dark
        AUTO -> if ((config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) dark else light
        else -> slate
    }
}
