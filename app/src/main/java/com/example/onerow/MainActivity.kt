package com.example.onerow

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*

/** Settings screen: setup, languages, height, haptics, and a box to try the keyboard. */
class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private val teal = 0xFF2E8B83.toInt()

    private var cheat: ImageView? = null

    /** The gesture cheat sheet drawn in the colors of the chosen keyboard theme (Auto follows the system). */
    private fun showCheatSheet() {
        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val res = when (Prefs(this).theme) {
            Themes.LIGHT -> R.drawable.cheatsheet_light
            Themes.DARK -> R.drawable.cheatsheet_dark
            Themes.AUTO -> if (dark) R.drawable.cheatsheet_dark else R.drawable.cheatsheet_light
            else -> R.drawable.cheatsheet_default
        }
        cheat?.setImageResource(res)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun header(text: String) = TextView(this).apply {
        this.text = text.uppercase(); textSize = 12f; setTextColor(teal); typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.08f; setPadding(0, dp(22), 0, dp(6))
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(24)) }
        val scroll = ScrollView(this).apply { addView(col); isFillViewport = true }

        col.addView(TextView(this).apply { text = "OneRow Keyboard"; textSize = 24f; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) })
        col.addView(TextView(this).apply {
            text = "version " + (try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" })
            textSize = 12f; setTextColor(Color.GRAY)
        })

        col.addView(header("Setup"))
        col.addView(Button(this).apply {
            text = "1. Enable keyboard"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })
        col.addView(Button(this).apply {
            text = "2. Switch to OneRow"
            setOnClickListener { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        })

        col.addView(header("Languages"))
        val boxes = Languages.all.map { l ->
            val installed = Dictionaries.has(this, l.code)
            CheckBox(this).apply {
                text = "${l.name}  (${l.code})" + if (installed) "" else "  - dictionary not installed"; textSize = 16f
                isEnabled = installed
                isChecked = installed && l.code in prefs.enabledCodes
                setOnCheckedChangeListener { btn, on ->
                    val set = prefs.enabledCodes.toMutableSet().also { if (on) it += l.code else it -= l.code }
                    if (set.isEmpty()) { btn.isChecked = true; Toast.makeText(context, "Keep at least one language", Toast.LENGTH_SHORT).show(); return@setOnCheckedChangeListener }
                    prefs.enabledCodes = Languages.all.map { it.code }.filter { it in set }
                }
            }
        }
        boxes.forEach { col.addView(it) }
        col.addView(TextView(this).apply {
            text = "Switch language on the keyboard: tap the language key (next to the comma)."
            textSize = 13f; setTextColor(Color.GRAY)
        })

        col.addView(header("Theme"))
        col.addView(RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            Themes.choices.forEach { (code, label) ->
                addView(RadioButton(context).apply { id = View.generateViewId(); text = label; textSize = 16f; tag = code; isChecked = code == prefs.theme })
            }
            setOnCheckedChangeListener { g, id -> prefs.theme = g.findViewById<View>(id).tag as String; showCheatSheet() }
        })

        col.addView(header("Guessing"))
        col.addView(TextView(this).apply {
            text = "How far a tap may miss a letter on the one-row keyboard and still count. Tight trusts your taps more; loose forgives bigger misses."
            textSize = 13f; setTextColor(Color.GRAY)
        })
        col.addView(RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            listOf("Tight – up to ~2½ letters away", "Normal – up to ~4 letters away", "Loose – up to ~5½ letters away").forEachIndexed { i, label ->
                addView(RadioButton(context).apply { id = View.generateViewId(); text = label; textSize = 16f; tag = i; isChecked = i == prefs.tolerance })
            }
            setOnCheckedChangeListener { g, id -> prefs.tolerance = g.findViewById<View>(id).tag as Int }
        })

        col.addView(header("Height"))
        val heightLabel = TextView(this)
        col.addView(heightLabel)
        col.addView(SeekBar(this).apply {
            max = 80   // 70% .. 150%
            progress = ((prefs.heightScale * 100f) - 70f).toInt().coerceIn(0, 80)
            fun show(p: Int) { heightLabel.text = "Keyboard height: ${70 + p}%" }
            show(progress)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { show(p); prefs.heightScale = (70 + p) / 100f }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        })

        col.addView(header("Feedback"))
        col.addView(Switch(this).apply {
            text = "Vibrate on key press"; textSize = 16f; isChecked = prefs.haptics
            setOnCheckedChangeListener { _, on -> prefs.haptics = on }
        })

        col.addView(header("Gestures"))
        col.addView(Switch(this).apply {
            text = "Swipe typing on the full keyboard"; textSize = 16f; isChecked = prefs.swipeTyping
            setOnCheckedChangeListener { _, on -> prefs.swipeTyping = on }
        })
        col.addView(TextView(this).apply {
            text = "Draw a word across the letters instead of tapping them. Swipe down on the guess bar to return to one row. The cheat sheet below shows every gesture."
            textSize = 13f; setPadding(0, 0, 0, dp(12))
        })

        col.addView(header("Cheat sheet"))
        cheat = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        col.addView(cheat)
        showCheatSheet()

        col.addView(header("Try it"))
        col.addView(EditText(this).apply {
            hint = "Type here"; minLines = 3; gravity = Gravity.TOP
            // like a messaging field: capitalizes the first letter of each sentence
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        })
        setContentView(scroll)
    }
}
