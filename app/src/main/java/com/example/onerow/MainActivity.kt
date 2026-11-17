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
import android.net.Uri
import com.example.onerow.billing.*

/** Settings screen: setup, languages, height, haptics, and a box to try the keyboard. */
class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private val teal = 0xFF2E8B83.toInt()

    /** The cheat sheet is 11 short pictures, each followed by the legend lines that describe it (never one tall picture with a far-away legend). */
    private var cheatViews: List<ImageView> = emptyList()

    /** Pictures per theme, in order. Explicit references, so the resource shrinker keeps every one. */
    private val cheatPictures = mapOf(
        "default" to intArrayOf(R.drawable.cheatsheet_default_1, R.drawable.cheatsheet_default_2, R.drawable.cheatsheet_default_3, R.drawable.cheatsheet_default_4, R.drawable.cheatsheet_default_5, R.drawable.cheatsheet_default_6, R.drawable.cheatsheet_default_7, R.drawable.cheatsheet_default_8, R.drawable.cheatsheet_default_9, R.drawable.cheatsheet_default_10, R.drawable.cheatsheet_default_11),
        "light" to intArrayOf(R.drawable.cheatsheet_light_1, R.drawable.cheatsheet_light_2, R.drawable.cheatsheet_light_3, R.drawable.cheatsheet_light_4, R.drawable.cheatsheet_light_5, R.drawable.cheatsheet_light_6, R.drawable.cheatsheet_light_7, R.drawable.cheatsheet_light_8, R.drawable.cheatsheet_light_9, R.drawable.cheatsheet_light_10, R.drawable.cheatsheet_light_11),
        "dark" to intArrayOf(R.drawable.cheatsheet_dark_1, R.drawable.cheatsheet_dark_2, R.drawable.cheatsheet_dark_3, R.drawable.cheatsheet_dark_4, R.drawable.cheatsheet_dark_5, R.drawable.cheatsheet_dark_6, R.drawable.cheatsheet_dark_7, R.drawable.cheatsheet_dark_8, R.drawable.cheatsheet_dark_9, R.drawable.cheatsheet_dark_10, R.drawable.cheatsheet_dark_11),
    )

    /** Which legend numbers (the badges drawn on the picture) belong under which picture. Numbers match R.string.cheat_<n>. */
    private val cheatGroups = listOf(listOf(1, 2, 3, 4, 5), listOf(6), listOf(7), listOf(8), listOf(9, 10), listOf(11, 12), listOf(13, 14),
        listOf(15, 16), listOf(17), listOf(18), listOf(19))
    private val cheatTexts = listOf(R.string.cheat_1, R.string.cheat_2, R.string.cheat_3, R.string.cheat_4, R.string.cheat_5, R.string.cheat_6, R.string.cheat_7,
        R.string.cheat_8, R.string.cheat_9, R.string.cheat_10, R.string.cheat_11, R.string.cheat_12, R.string.cheat_13, R.string.cheat_14,
        R.string.cheat_15, R.string.cheat_16, R.string.cheat_17, R.string.cheat_18, R.string.cheat_19)

    /** The gesture cheat sheet drawn in the colors of the chosen keyboard theme (Auto follows the system). */
    private fun showCheatSheet() {
        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val set = when (Prefs(this).theme) {
            Themes.LIGHT -> "light"
            Themes.DARK -> "dark"
            Themes.AUTO -> if (dark) "dark" else "light"
            else -> "default"
        }
        val pics = cheatPictures.getValue(set)
        cheatViews.forEachIndexed { i, v -> v.setImageResource(pics[i]) }
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

    private lateinit var billing: BillingManager
    private var updateSub: () -> Unit = {}

    override fun onResume() { super.onResume(); if (::billing.isInitialized) { billing.refresh(withPrices = true); updateSub() } }
    override fun onDestroy() { if (::billing.isInitialized) billing.close(); super.onDestroy() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(24)) }
        val scroll = ScrollView(this).apply { addView(col); isFillViewport = true }

        col.addView(TextView(this).apply { text = getString(R.string.app_name); textSize = 24f; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) })
        col.addView(TextView(this).apply {
            text = getString(R.string.version_label, try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" })
            textSize = 12f; setTextColor(Color.GRAY)
        })

        col.addView(header(getString(R.string.header_setup)))
        col.addView(button(getString(R.string.btn_enable_keyboard)) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        col.addView(button(getString(R.string.btn_switch_keyboard)) { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() })

        // ---- subscription ----
        col.addView(header(getString(R.string.header_subscription)))
        val subStatus = TextView(this).apply { textSize = 14f; setPadding(0, 0, 0, dp(4)) }
        col.addView(subStatus)
        val buyMonthly = button(getString(R.string.btn_subscribe)) { billing.buyMonthly(this@MainActivity) }
        val buyForever = button(getString(R.string.btn_buy_forever)) { billing.buyLifetime(this@MainActivity) }
        val manage = button(getString(R.string.btn_manage_subscription)) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?sku=${Products.MONTHLY}&package=$packageName")))
        }
        col.addView(buyMonthly); col.addView(buyForever); col.addView(manage)
        updateSub = {
            val a = BillingStore.access(this)
            val snap = BillingStore.load(this)
            subStatus.text = when {
                a is Access.Locked -> getString(R.string.sub_status_locked)
                a is Access.Full && a.reason == Reason.LIFETIME -> getString(R.string.sub_status_lifetime)
                a is Access.Full && a.reason == Reason.SUBSCRIBED && a.expiresInDays != null -> resources.getQuantityString(R.plurals.sub_status_cancelled, a.expiresInDays, a.expiresInDays)
                a is Access.Full && a.reason == Reason.SUBSCRIBED -> getString(R.string.sub_status_subscribed)
                a is Access.Full -> resources.getQuantityString(R.plurals.sub_status_free, a.freeDaysLeft, a.freeDaysLeft)
                else -> ""
            }
            val owns = a is Access.Full && a.reason == Reason.LIFETIME
            val subbed = snap.subActive
            val price = billing.monthlyPrice()
            buyMonthly.text = when {
                price == null -> getString(R.string.btn_subscribe_no_price)
                price.second -> getString(R.string.btn_subscribe_trial_price, price.first)
                else -> getString(R.string.btn_subscribe_price, price.first)
            }
            buyMonthly.isEnabled = price != null
            buyForever.text = billing.lifetimePrice()?.let { getString(R.string.btn_buy_forever_price, it) } ?: getString(R.string.btn_buy_forever_no_price)
            buyForever.isEnabled = billing.lifetimePrice() != null
            buyMonthly.visibility = if (owns || subbed) View.GONE else View.VISIBLE
            buyForever.visibility = if (owns) View.GONE else View.VISIBLE
            manage.visibility = if (subbed) View.VISIBLE else View.GONE
        }
        billing = BillingManager(this) { runOnUiThread { updateSub() } }
        updateSub()

        col.addView(header(getString(R.string.header_languages)))
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
                        text = if (installed) "${l.name}  (${l.code})" else getString(R.string.lang_dictionary_missing, l.name, l.code); textSize = 16f
                        val tv = android.util.TypedValue()
                        theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
                        setTextColor(if (installed) resources.getColor(tv.resourceId, theme) else Color.GRAY)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(BinView(context).apply {
                        contentDescription = getString(R.string.cd_remove_language, l.name)
                        alpha = if (added.size > 1) 1f else 0.3f
                        setOnClickListener {
                            if (prefs.enabledCodes.size <= 1) { Toast.makeText(context, getString(R.string.toast_keep_one_language), Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                            save(prefs.enabledCodes.toSet() - l.code); refreshLanguages()
                        }
                    }, LinearLayout.LayoutParams(dp(40), dp(40)))
                })
            }
            others = Languages.all.filter { it.code !in added }
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
                others.map { l -> if (Dictionaries.has(this, l.code)) "${l.name}  (${l.code})" else getString(R.string.lang_not_installed, l.name, l.code) })
            addRow.visibility = if (others.isEmpty()) View.GONE else View.VISIBLE
        }
        plus.setOnClickListener {
            val l = others.getOrNull(spinner.selectedItemPosition) ?: return@setOnClickListener
            if (!Dictionaries.has(this, l.code)) { Toast.makeText(this, getString(R.string.toast_dictionary_not_installed, l.name), Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            save(prefs.enabledCodes.toSet() + l.code); refreshLanguages()
        }
        refreshLanguages()
        col.addView(TextView(this).apply {
            text = getString(R.string.lang_switch_hint)
            textSize = 13f; setTextColor(Color.GRAY)
        })

        col.addView(header(getString(R.string.header_theme)))
        col.addView(RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            Themes.choices.forEach { (code, labelRes) ->
                addView(RadioButton(context).apply { id = View.generateViewId(); text = getString(labelRes); textSize = 16f; tag = code; isChecked = code == prefs.theme })
            }
            setOnCheckedChangeListener { g, id -> prefs.theme = g.findViewById<View>(id).tag as String; showCheatSheet() }
        })

        col.addView(header(getString(R.string.header_guessing)))
        col.addView(TextView(this).apply {
            text = getString(R.string.guessing_desc)
            textSize = 13f; setTextColor(Color.GRAY)
        })
        col.addView(RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            listOf(getString(R.string.guessing_tight), getString(R.string.guessing_normal), getString(R.string.guessing_loose)).forEachIndexed { i, label ->
                addView(RadioButton(context).apply { id = View.generateViewId(); text = label; textSize = 16f; tag = i; isChecked = i == prefs.tolerance })
            }
            setOnCheckedChangeListener { g, id -> prefs.tolerance = g.findViewById<View>(id).tag as Int }
        })

        col.addView(header(getString(R.string.header_swipe_precision)))
        col.addView(TextView(this).apply {
            text = getString(R.string.swipe_precision_desc)
            textSize = 13f; setTextColor(Color.GRAY)
        })
        col.addView(RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            listOf(getString(R.string.swipe_precision_tight), getString(R.string.swipe_precision_normal), getString(R.string.swipe_precision_loose)).forEachIndexed { i, label ->
                addView(RadioButton(context).apply { id = View.generateViewId(); text = label; textSize = 16f; tag = i; isChecked = i == prefs.swipeTolerance })
            }
            setOnCheckedChangeListener { g, id -> prefs.swipeTolerance = g.findViewById<View>(id).tag as Int }
        })

        col.addView(header(getString(R.string.header_full_keyboard)))
        col.addView(Switch(this).apply {
            text = getString(R.string.number_row); textSize = 16f; isChecked = prefs.numberRow
            setOnCheckedChangeListener { _, on -> prefs.numberRow = on }
        })
        col.addView(TextView(this).apply {
            text = getString(R.string.number_row_desc)
            textSize = 13f; setPadding(0, 0, 0, dp(12))
        })

        col.addView(header(getString(R.string.header_spell_checker)))
        col.addView(TextView(this).apply {
            text = getString(R.string.spell_checker_desc)
            textSize = 13f; setPadding(0, 0, 0, dp(6))
        })
        val me = "$packageName/${OneRowSpellChecker::class.java.name}"
        /** Writes the setting if this app was granted WRITE_SECURE_SETTINGS (adb, once); otherwise opens the system screen and says how. */
        fun setSpell(select: Boolean) {
            try {
                if (select) { Settings.Secure.putString(contentResolver, "selected_spell_checker", me); Settings.Secure.putInt(contentResolver, "spell_checker_enabled", 1) }
                else Settings.Secure.putInt(contentResolver, "spell_checker_enabled", 0)
                Toast.makeText(this, getString(if (select) R.string.toast_spell_on else R.string.toast_spell_off), Toast.LENGTH_SHORT).show()
            } catch (e: SecurityException) {
                Toast.makeText(this, getString(R.string.toast_spell_manual, packageName), Toast.LENGTH_LONG).show()
                val intents = listOf(
                    Intent().setClassName("com.android.settings", "com.android.settings.Settings\$SpellCheckersSettingsActivity"),
                    Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                for (i in intents) { try { startActivity(i); break } catch (_: Exception) { } }
            }
        }
        col.addView(button(getString(R.string.btn_spell_on)) { setSpell(true) })
        col.addView(button(getString(R.string.btn_spell_off)) { setSpell(false) })

        col.addView(header(getString(R.string.header_proofreading)))
        val proofSwitch = Switch(this).apply {
            text = getString(R.string.proofread_button); textSize = 16f; isChecked = false; isEnabled = false; alpha = 0.4f
            setOnCheckedChangeListener { _, on -> prefs.proofread = on }
        }
        val proofNote = TextView(this).apply { textSize = 13f; setPadding(0, 0, 0, dp(12)); text = getString(R.string.proofread_checking) }
        col.addView(proofSwitch)
        col.addView(proofNote)
        // greyed out unless the phone has Google's on-device AI (Gemini Nano through AICore) for the proofreading feature
        Proofread.status(this, "en") { st ->
            val ok = st != Proofread.Status.UNAVAILABLE
            proofSwitch.isEnabled = ok; proofSwitch.alpha = if (ok) 1f else 0.4f
            proofSwitch.isChecked = ok && prefs.proofread
            if (!ok && prefs.proofread) prefs.proofread = false
            proofNote.text = getString(if (ok) R.string.proofread_available else R.string.proofread_unavailable)
        }

        col.addView(header(getString(R.string.header_feedback)))
        col.addView(Switch(this).apply {
            text = getString(R.string.vibrate); textSize = 16f; isChecked = prefs.haptics
            setOnCheckedChangeListener { _, on -> prefs.haptics = on }
        })

        col.addView(header(getString(R.string.header_gestures)))
        col.addView(Switch(this).apply {
            text = getString(R.string.swipe_typing); textSize = 16f; isChecked = prefs.swipeTyping
            setOnCheckedChangeListener { _, on -> prefs.swipeTyping = on }
        })
        col.addView(TextView(this).apply {
            text = getString(R.string.gestures_desc)
            textSize = 13f; setPadding(0, 0, 0, dp(12))
        })

        col.addView(header(getString(R.string.header_cheat_sheet)))
        // each picture, then right under it only the legend lines for the numbers drawn on it
        val views = ArrayList<ImageView>()
        for (group in cheatGroups) {
            val pic = ImageView(this).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER }
            views += pic; col.addView(pic, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
            for (n in group) col.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(4), 0, dp(2))
                addView(TextView(context).apply { text = "$n"; textSize = 14f; setTextColor(teal); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.END },
                    LinearLayout.LayoutParams(dp(26), ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(TextView(context).apply { text = getString(cheatTexts[n - 1]); textSize = 14f; setPadding(dp(10), 0, 0, 0) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
        }
        cheatViews = views
        showCheatSheet()

        col.addView(header(getString(R.string.header_crash_reports)))
        col.addView(Switch(this).apply {
            text = getString(R.string.shake_report); textSize = 16f; isChecked = prefs.shakeReport
            setOnCheckedChangeListener { _, on -> prefs.shakeReport = on }
        })
        col.addView(TextView(this).apply { text = getString(R.string.shake_report_desc); textSize = 13f; setPadding(0, 0, 0, dp(6)) })
        col.addView(button(getString(R.string.btn_show_report)) { startActivity(Intent(this, com.example.onerow.report.ReportActivity::class.java)) })

        col.addView(header(getString(R.string.header_about)))
        col.addView(button(getString(R.string.btn_attributions)) {
            val text = try { assets.open("attribution.md").bufferedReader().readText() } catch (e: Exception) { getString(R.string.attributions_missing) }
            val tv = TextView(this).apply { this.text = text; textSize = 12f; setPadding(dp(20), dp(12), dp(20), dp(12)); setTextIsSelectable(true) }
            android.app.AlertDialog.Builder(this).setTitle(getString(R.string.attributions_title)).setView(ScrollView(this).apply { addView(tv) }).setPositiveButton(getString(R.string.close), null).show()
        })

        col.addView(header(getString(R.string.header_try_it)))
        col.addView(EditText(this).apply {
            hint = getString(R.string.hint_type_here); minLines = 3; gravity = Gravity.TOP
            // like a messaging field: capitalizes the first letter of each sentence
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        })
        setContentView(scroll)
        // Android 15+ draws the page edge-to-edge: keep the last items clear of the navigation bar / gesture zone
        if (android.os.Build.VERSION.SDK_INT >= 35) scroll.setOnApplyWindowInsetsListener { _, insets ->
            val b = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
            col.setPadding(dp(20), dp(12), dp(20), dp(24) + b)
            insets
        }
    }
}
