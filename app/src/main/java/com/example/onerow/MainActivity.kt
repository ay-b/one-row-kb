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
            style = android.graphics.Paint.Style.STROKE; strokeWidth = resources.displayMetrics.density * 1.5f; color = Color.GRAY
            strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
        }
        override fun onDraw(c: android.graphics.Canvas) {
            val cx = width / 2f; val cy = height / 2f; val s = minOf(width, height) * 0.16f
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

    /** One look for every button on the page: flat, rounded, a soft grey fill that works on light and dark. */
    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f
        stateListAnimator = null; elevation = 0f
        val fill = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(0x33808080) }
        background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x44808080), fill, null)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
        setOnClickListener { onClick() }
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
        col.addView(button("1. Enable keyboard") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        col.addView(button("2. Switch to OneRow") { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() })

        col.addView(header("Languages"))
        // the added languages, each with a bin; below them a dropdown of the others and a + to add the chosen one
        val langList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(langList)
        val spinner = Spinner(this)
        val plus = button("+") { }.apply { textSize = 18f; (layoutParams as LinearLayout.LayoutParams).topMargin = 0 }
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
                    }, LinearLayout.LayoutParams(dp(40), dp(40)))
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

        col.addView(header("Spell checker"))
        col.addView(TextView(this).apply {
            text = "Android underlines words in red using its own spell checker, which only knows one language. OneRow has its own: it knows every language you added here (and the words you taught it), so only really unknown words get marked."
            textSize = 13f; setPadding(0, 0, 0, dp(6))
        })
        val me = "$packageName/${OneRowSpellChecker::class.java.name}"
        /** Writes the setting if this app was granted WRITE_SECURE_SETTINGS (adb, once); otherwise opens the system screen and says how. */
        fun setSpell(select: Boolean) {
            try {
                if (select) { Settings.Secure.putString(contentResolver, "selected_spell_checker", me); Settings.Secure.putInt(contentResolver, "spell_checker_enabled", 1) }
                else Settings.Secure.putInt(contentResolver, "spell_checker_enabled", 0)
                Toast.makeText(this, if (select) "OneRow spell checker selected" else "Spell checking is off", Toast.LENGTH_SHORT).show()
            } catch (e: SecurityException) {
                Toast.makeText(this, "Pick \"OneRow Keyboard\" in the screen that opens, or run once on a computer: adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS", Toast.LENGTH_LONG).show()
                val intents = listOf(
                    Intent().setClassName("com.android.settings", "com.android.settings.Settings\$SpellCheckersSettingsActivity"),
                    Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                for (i in intents) { try { startActivity(i); break } catch (_: Exception) { } }
            }
        }
        col.addView(button("Use the OneRow spell checker") { setSpell(true) })
        col.addView(button("Turn spell checking off") { setSpell(false) })

        col.addView(header("Proofreading"))
        val proofSwitch = Switch(this).apply {
            text = "Proofread button"; textSize = 16f; isChecked = false; isEnabled = false; alpha = 0.4f
            setOnCheckedChangeListener { _, on -> prefs.proofread = on }
        }
        val proofNote = TextView(this).apply { textSize = 13f; setPadding(0, 0, 0, dp(12)); text = "Checking whether this phone can do it..." }
        col.addView(proofSwitch)
        col.addView(proofNote)
        // greyed out unless the phone has Google's on-device AI (Gemini Nano through AICore) for the proofreading feature
        Proofread.status(this, "en") { st ->
            val ok = st != Proofread.Status.UNAVAILABLE
            proofSwitch.isEnabled = ok; proofSwitch.alpha = if (ok) 1f else 0.4f
            proofSwitch.isChecked = ok && prefs.proofread
            if (!ok && prefs.proofread) prefs.proofread = false
            proofNote.text = if (ok) "A check circle at the right end of the guess bar: tap it to fix the grammar and spelling of what you typed in the field, tap again to undo. English, French, German, Italian and Spanish. Runs on the phone; the first use may download a model."
                             else "Not available on this phone: it needs Google's on-device AI (Gemini Nano), which only some newer phones have."
        }

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
