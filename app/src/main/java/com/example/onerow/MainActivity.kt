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

    /** A small bin, drawn in code (same idea as the one on the keyboard's guess bar). */
    private inner class BinView(ctx: android.content.Context) : View(ctx) {
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE; strokeWidth = dp(2).toFloat(); color = Color.GRAY
            strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
        }
        override fun onDraw(c: android.graphics.Canvas) {
            val cx = width / 2f; val cy = height / 2f; val s = minOf(width, height) * 0.26f
            c.drawLine(cx - s * 1.15f, cy - s * 0.8f, cx + s * 1.15f, cy - s * 0.8f, p)                        // lid
            c.drawLine(cx - s * 0.4f, cy - s * 0.8f, cx - s * 0.4f, cy - s * 1.2f, p)                          // handle
            c.drawLine(cx + s * 0.4f, cy - s * 0.8f, cx + s * 0.4f, cy - s * 1.2f, p)
            c.drawLine(cx - s * 0.4f, cy - s * 1.2f, cx + s * 0.4f, cy - s * 1.2f, p)
            val body = android.graphics.Path().apply {
                moveTo(cx - s * 0.9f, cy - s * 0.8f); lineTo(cx - s * 0.7f, cy + s * 1.2f)
                lineTo(cx + s * 0.7f, cy + s * 1.2f); lineTo(cx + s * 0.9f, cy - s * 0.8f)
            }
            c.drawPath(body, p)
            c.drawLine(cx - s * 0.25f, cy - s * 0.35f, cx - s * 0.2f, cy + s * 0.8f, p)                         // ribs
            c.drawLine(cx + s * 0.25f, cy - s * 0.35f, cx + s * 0.2f, cy + s * 0.8f, p)
        }
    }

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
        // the added languages, each with a bin; below them a dropdown of the others and a + to add the chosen one
        val langList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(langList)
        val spinner = Spinner(this)
        val plus = Button(this).apply { text = "+"; textSize = 18f }
        val addRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(spinner, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(plus, LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        col.addView(addRow)
        var others: List<Lang> = emptyList()
        fun save(codes: Set<String>) { prefs.enabledCodes = Languages.all.map { it.code }.filter { it in codes } }
        lateinit var refreshLanguages: () -> Unit
        refreshLanguages = {
            langList.removeAllViews()
            val added = prefs.enabledCodes.toSet()
            Languages.all.filter { it.code in added }.forEach { l ->
                val installed = Dictionaries.has(this, l.code)
                langList.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(context).apply {
                        text = "${l.name}  (${l.code})" + if (installed) "" else "  - dictionary not installed"; textSize = 16f
                        val tv = android.util.TypedValue()
                        theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
                        setTextColor(if (installed) resources.getColor(tv.resourceId, theme) else Color.GRAY)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(BinView(context).apply {
                        contentDescription = "Remove ${l.name}"
                        alpha = if (added.size > 1) 1f else 0.3f
                        setOnClickListener {
                            if (prefs.enabledCodes.size <= 1) { Toast.makeText(context, "Keep at least one language", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                            save(prefs.enabledCodes.toSet() - l.code); refreshLanguages()
                        }
                    }, LinearLayout.LayoutParams(dp(48), dp(48)))
                })
            }
            others = Languages.all.filter { it.code !in added }
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
                others.map { l -> "${l.name}  (${l.code})" + if (Dictionaries.has(this, l.code)) "" else "  - not installed" })
            addRow.visibility = if (others.isEmpty()) View.GONE else View.VISIBLE
        }
        plus.setOnClickListener {
            val l = others.getOrNull(spinner.selectedItemPosition) ?: return@setOnClickListener
            if (!Dictionaries.has(this, l.code)) { Toast.makeText(this, "The ${l.name} dictionary is not installed", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            save(prefs.enabledCodes.toSet() + l.code); refreshLanguages()
        }
        refreshLanguages()
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

        col.addView(header("Full keyboard"))
        col.addView(Switch(this).apply {
            text = "Dedicated number row"; textSize = 16f; isChecked = prefs.numberRow
            setOnCheckedChangeListener { _, on -> prefs.numberRow = on }
        })
        col.addView(TextView(this).apply {
            text = "A row of digits above the letters. Hold a digit for superscripts and fractions (\u00B3, \u00BD, \u2153, \u00BC ...)."
            textSize = 13f; setPadding(0, 0, 0, dp(12))
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
