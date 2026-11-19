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

    /** The cheat sheet is 14 short pictures, each followed by the legend lines that describe it (never one tall picture with a far-away legend). */
    private var cheatViews: List<ImageView> = emptyList()

    /** Pictures per theme, in order. Explicit references, so the resource shrinker keeps every one. */
    private val cheatPictures = mapOf(
        "default" to intArrayOf(R.drawable.cheatsheet_default_1, R.drawable.cheatsheet_default_2, R.drawable.cheatsheet_default_3, R.drawable.cheatsheet_default_4, R.drawable.cheatsheet_default_5, R.drawable.cheatsheet_default_6, R.drawable.cheatsheet_default_7, R.drawable.cheatsheet_default_8, R.drawable.cheatsheet_default_9, R.drawable.cheatsheet_default_10, R.drawable.cheatsheet_default_11, R.drawable.cheatsheet_default_12, R.drawable.cheatsheet_default_13, R.drawable.cheatsheet_default_14),
        "light" to intArrayOf(R.drawable.cheatsheet_light_1, R.drawable.cheatsheet_light_2, R.drawable.cheatsheet_light_3, R.drawable.cheatsheet_light_4, R.drawable.cheatsheet_light_5, R.drawable.cheatsheet_light_6, R.drawable.cheatsheet_light_7, R.drawable.cheatsheet_light_8, R.drawable.cheatsheet_light_9, R.drawable.cheatsheet_light_10, R.drawable.cheatsheet_light_11, R.drawable.cheatsheet_light_12, R.drawable.cheatsheet_light_13, R.drawable.cheatsheet_light_14),
        "dark" to intArrayOf(R.drawable.cheatsheet_dark_1, R.drawable.cheatsheet_dark_2, R.drawable.cheatsheet_dark_3, R.drawable.cheatsheet_dark_4, R.drawable.cheatsheet_dark_5, R.drawable.cheatsheet_dark_6, R.drawable.cheatsheet_dark_7, R.drawable.cheatsheet_dark_8, R.drawable.cheatsheet_dark_9, R.drawable.cheatsheet_dark_10, R.drawable.cheatsheet_dark_11, R.drawable.cheatsheet_dark_12, R.drawable.cheatsheet_dark_13, R.drawable.cheatsheet_dark_14),
    )

    /** Which legend numbers (the badges drawn on the picture) belong under which picture, in the order of the pictures above. Numbers match R.string.cheat_<n>. */
    private val cheatGroups = listOf(listOf(1, 2, 3, 4, 5), listOf(6), listOf(7), listOf(17), listOf(8, 9), listOf(10, 11), listOf(12, 13),
        listOf(18, 19), listOf(14), listOf(15), listOf(16), listOf(20), listOf(21, 22), listOf(23))
    private val cheatTexts = listOf(R.string.cheat_1, R.string.cheat_2, R.string.cheat_3, R.string.cheat_4, R.string.cheat_5, R.string.cheat_6, R.string.cheat_7,
        R.string.cheat_8, R.string.cheat_9, R.string.cheat_10, R.string.cheat_11, R.string.cheat_12, R.string.cheat_13, R.string.cheat_14,
        R.string.cheat_15, R.string.cheat_16, R.string.cheat_17, R.string.cheat_18, R.string.cheat_19, R.string.cheat_20, R.string.cheat_21,
        R.string.cheat_22, R.string.cheat_23)
    /** The Help sections (each folds away): title, and the pictures (indexes into the lists above) shown in it. */
    private val helpSections = listOf(
        R.string.help_one_row to listOf(0, 1, 2, 4, 5, 6),
        R.string.help_expanded to listOf(8, 9, 10),
        R.string.help_nice to listOf(3, 7, 11, 12, 13))

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
            style = android.graphics.Paint.Style.STROKE; strokeWidth = resources.displayMetrics.density * 1.5f; color = AppStyle.palette(ctx).dim
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
        this.text = text.uppercase(); textSize = 12f; tag = AppStyle.HEADER; typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.08f; setPadding(0, dp(22), 0, dp(6))
    }

    /** A dialog in the keyboard's colors and font: optional title, scrolling content, optional close button. */
    private fun themedDialog(title: String?, content: View, close: String?): android.app.AlertDialog {
        val pal = AppStyle.palette(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(12)) }
        val dlg = android.app.AlertDialog.Builder(this).setView(box).create()
        if (title != null) box.addView(header(title).apply { setPadding(0, dp(8), 0, dp(10)) })
        box.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (close != null) box.addView(button(close) { dlg.dismiss() })
        AppStyle.apply(box, pal)
        dlg.window?.setBackgroundDrawable(AppStyle.dialogBackground(this, pal))
        dlg.show()
        return dlg
    }

    private lateinit var billing: BillingManager
    private var updateSub: () -> Unit = {}

    override fun onResume() { super.onResume(); if (::billing.isInitialized) { billing.refresh(withPrices = true); updateSub() } }
    override fun onDestroy() { if (::billing.isInitialized) billing.close(); super.onDestroy() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        var restyle: () -> Unit = {}                    // recolors the pages (set once they exist); called when the theme changes
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(24)) }
        val help = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(24)) }
        val about = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(24)) }
        val aboutScroll = ScrollView(this).apply { addView(about); isFillViewport = true; visibility = View.GONE }
        val settingsScroll = ScrollView(this).apply { addView(col); isFillViewport = true }
        val helpScroll = ScrollView(this).apply { addView(help); isFillViewport = true; visibility = View.GONE }

        col.addView(TextView(this).apply { text = getString(R.string.app_name); textSize = 24f; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) })

        col.addView(header(getString(R.string.header_setup)))
        col.addView(button(getString(R.string.btn_enable_keyboard)) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        col.addView(button(getString(R.string.btn_switch_keyboard)) { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() })

        col.addView(header(getString(R.string.header_spell_checker)))
        col.addView(TextView(this).apply {
            text = getString(R.string.spell_checker_desc)
            textSize = 13f; setPadding(0, 0, 0, dp(6))
        })
        /** Opens Android's own spell-checker screen; the user picks OneRow there (an app cannot switch it for them). */
        col.addView(button(getString(R.string.btn_spell_on)) {
            Toast.makeText(this, getString(R.string.toast_spell_pick), Toast.LENGTH_LONG).show()
            val intents = listOf(
                Intent().setClassName("com.android.settings", "com.android.settings.Settings\$SpellCheckersSettingsActivity"),
                Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            for (i in intents) { try { startActivity(i); break } catch (_: Exception) { } }
        })
        col.addView(TextView(this).apply {                                      // what happens when it was not chosen
            text = getString(R.string.spell_checker_warn)
            textSize = 13f; setPadding(0, dp(2), 0, dp(6))
        })

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
        // The language to add is chosen with our own button + list dialog: the system Spinner drew its selected item invisibly on some
        // devices (Samsung foldables, unfolded), and this one is plain text in the page's own colors on every phone.
        var pick = 0
        var others: List<Lang> = emptyList()
        val LRM = "\u200E"          // a left-to-right mark first: a language named in Hebrew must not turn its row right-to-left
        fun labelOf(l: Lang) = LRM + (if (Dictionaries.has(this, l.code)) "${l.name}  (${l.code})" else getString(R.string.lang_not_installed, l.name, l.code))
        val chooser = button("") { }.apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START; textSize = 16f; (layoutParams as LinearLayout.LayoutParams).topMargin = 0
            textDirection = View.TEXT_DIRECTION_LTR           // a language named in Hebrew must not turn the row right-to-left
        }
        fun showPick() { chooser.text = others.getOrNull(pick)?.let { labelOf(it) + "   \u25BE" } ?: "" }
        chooser.setOnClickListener {
            if (others.isEmpty()) return@setOnClickListener
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            lateinit var dlg: android.app.AlertDialog
            others.forEachIndexed { i, l ->
                list.addView(TextView(this).apply {
                    text = labelOf(l); textSize = 16f; setPadding(dp(8), dp(12), dp(8), dp(12)); if (i == pick) tag = AppStyle.SELECTED
                    textDirection = View.TEXT_DIRECTION_LTR; textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    setOnClickListener { pick = i; showPick(); dlg.dismiss() }
                })
            }
            dlg = themedDialog(null, list, null)
        }
        val plus = button("+") { }.apply { textSize = 18f; (layoutParams as LinearLayout.LayoutParams).topMargin = 0 }
        val addRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(chooser, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })
            addView(plus, LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        col.addView(addRow)
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
                        text = LRM + (if (installed) "${l.name}  (${l.code})" else getString(R.string.lang_dictionary_missing, l.name, l.code)); textSize = 16f
                        textDirection = View.TEXT_DIRECTION_LTR; textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                        if (!installed) tag = AppStyle.DIM
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
            pick = 0; showPick()
            addRow.visibility = if (others.isEmpty()) View.GONE else View.VISIBLE
        }
        plus.setOnClickListener {
            val l = others.getOrNull(pick) ?: return@setOnClickListener
            if (!Dictionaries.has(this, l.code)) { Toast.makeText(this, getString(R.string.toast_dictionary_not_installed, l.name), Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            save(prefs.enabledCodes.toSet() + l.code); refreshLanguages()
        }
        refreshLanguages()
        col.addView(TextView(this).apply {
            text = getString(R.string.lang_switch_hint)
            textSize = 13f
        })

        col.addView(header(getString(R.string.header_theme)))
        col.addView(RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            Themes.choices.forEach { (code, labelRes) ->
                addView(RadioButton(context).apply { id = View.generateViewId(); text = getString(labelRes); textSize = 16f; tag = code; isChecked = code == prefs.theme })
            }
            setOnCheckedChangeListener { g, id -> prefs.theme = g.findViewById<View>(id).tag as String; showCheatSheet(); restyle() }
        })

        col.addView(header(getString(R.string.header_guessing)))
        col.addView(TextView(this).apply {
            text = getString(R.string.guessing_desc)
            textSize = 13f
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
            textSize = 13f
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


        help.addView(header(getString(R.string.header_cheat_sheet)))
        // sections that fold away; inside, each picture and right under it only the legend lines for the numbers drawn on it
        val views = arrayOfNulls<ImageView>(cheatGroups.size)
        helpSections.forEachIndexed { si, (titleRes, pictures) ->
            val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = if (si == 0) View.VISIBLE else View.GONE }
            // the folding titles stand out: bigger and heavier than the small headers
            val bar = header(getString(titleRes)).apply { textSize = 15f; paint.style = android.graphics.Paint.Style.FILL_AND_STROKE; paint.strokeWidth = 0.7f * resources.displayMetrics.density; letterSpacing = 0.05f; setPadding(0, dp(22), 0, dp(12)); isClickable = true }
            fun label() { bar.text = (if (body.visibility == View.VISIBLE) "\u25BE  " else "\u25B8  ") + getString(titleRes).uppercase() }
            label()
            bar.setOnClickListener { body.visibility = if (body.visibility == View.VISIBLE) View.GONE else View.VISIBLE; label() }
            help.addView(bar); help.addView(body)
            for (pi in pictures) {
                val pic = ImageView(this).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER }
                views[pi] = pic; body.addView(pic, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
                for (n in cheatGroups[pi]) body.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(4), 0, dp(2))
                    addView(TextView(context).apply { text = "$n"; textSize = 14f; tag = AppStyle.HEADER; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.END },
                        LinearLayout.LayoutParams(dp(26), ViewGroup.LayoutParams.WRAP_CONTENT))
                    // (cheat_21 shows a % sign through its placeholder, so every text is formatted with "%")
                    addView(TextView(context).apply { text = getString(cheatTexts[n - 1], "%"); textSize = 14f; setPadding(dp(10), 0, 0, 0) },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                })
            }
        }
        cheatViews = views.map { it!! }
        showCheatSheet()

        help.addView(header(getString(R.string.header_try_it)))
        help.addView(EditText(this).apply {
            hint = getString(R.string.hint_type_here); minLines = 3; gravity = Gravity.TOP
            // like a messaging field: capitalizes the first letter of each sentence
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        })
        about.addView(header(getString(R.string.header_crash_reports)))
        about.addView(Switch(this).apply {
            text = getString(R.string.shake_report); textSize = 16f; isChecked = prefs.shakeReport
            setOnCheckedChangeListener { _, on -> prefs.shakeReport = on }
        })
        about.addView(TextView(this).apply { text = getString(R.string.shake_report_desc); textSize = 13f; setPadding(0, 0, 0, dp(6)) })
        about.addView(button(getString(R.string.btn_show_report)) { startActivity(Intent(this, com.example.onerow.report.ReportActivity::class.java)) })

        about.addView(header(getString(R.string.header_about)))
        about.addView(button(getString(R.string.btn_attributions)) {
            val text = try { assets.open("attribution.md").bufferedReader().readText() } catch (e: Exception) { getString(R.string.attributions_missing) }
            val tv = TextView(this).apply { this.text = text; textSize = 12f; setTextIsSelectable(true) }
            themedDialog(getString(R.string.attributions_title), tv, getString(R.string.close))
        })

        about.addView(TextView(this).apply {
            text = getString(R.string.version_label, try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" })
            textSize = 12f
        })
        // two tabs: Settings and Help (the gesture cheat sheet)
        val bars = ArrayList<View>(); val labels = ArrayList<TextView>(); var curTab = 0
        fun select(i: Int) {
            settingsScroll.visibility = if (i == 0) View.VISIBLE else View.GONE
            helpScroll.visibility = if (i == 1) View.VISIBLE else View.GONE
            aboutScroll.visibility = if (i == 2) View.VISIBLE else View.GONE
            curTab = i
            val pal = AppStyle.palette(this)
            labels.forEachIndexed { k, t -> t.setTextColor(if (k == i) pal.accent else pal.dim); t.typeface = if (k == i) Typeface.create(AppStyle.face(this), Typeface.BOLD) else AppStyle.face(this) }
            bars.forEachIndexed { k, v -> v.setBackgroundColor(if (k == i) pal.accent else Color.TRANSPARENT) }
        }
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(R.string.tab_settings, R.string.tab_help, R.string.tab_about).forEachIndexed { i, res ->
            val label = TextView(this).apply { text = getString(res).uppercase(); textSize = 14f; gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(10)) }
            val bar = View(this)
            labels += label; bars += bar
            tabs.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; isClickable = true; setOnClickListener { select(i) }
                addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        select(intent.getIntExtra("tab", 0).coerceIn(0, 2))          // the keyboard's help action opens the Help tab
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tabs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(settingsScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(helpScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(aboutScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        restyle = {
            val pal = AppStyle.palette(this)
            AppStyle.apply(root, pal)
            tabs.setBackgroundColor(pal.strip)
            AppStyle.window(this, pal)
            root.post { AppStyle.window(this, pal) }          // once more after the window is attached: the system resets the bar icons then
            select(curTab)
        }
        restyle()
        // Android 15+ draws the page edge-to-edge and the action bar floats over it: keep the tabs below the action bar (checked on every layout pass)
        if (android.os.Build.VERSION.SDK_INT >= 35) root.viewTreeObserver.addOnGlobalLayoutListener {
            val ab = findViewById<View>(resources.getIdentifier("action_bar_container", "id", "android"))
            if (ab != null && ab.visibility == View.VISIBLE && ab.height > 0) {
                val l = IntArray(2); root.getLocationOnScreen(l)
                val k = IntArray(2); ab.getLocationOnScreen(k)
                val pad = maxOf(0, k[1] + ab.height - l[1])
                if (pad != root.paddingTop) root.setPadding(0, pad, 0, root.paddingBottom)
            }
        }
        // ... and keep the last items clear of the navigation bar / gesture zone
        if (android.os.Build.VERSION.SDK_INT >= 35) root.setOnApplyWindowInsetsListener { _, insets ->
            val nav = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
            val ime = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom
            // with the keyboard open the page ends above it (edge-to-edge ignores adjustResize), so the "Try it" field is never hidden behind it
            root.setPadding(0, root.paddingTop, 0, ime)
            val b = if (ime > 0) 0 else nav
            col.setPadding(dp(20), dp(12), dp(20), dp(24) + b)
            help.setPadding(dp(20), dp(12), dp(20), dp(24) + b)
            about.setPadding(dp(20), dp(12), dp(20), dp(24) + b)
            if (ime > 0) root.post { root.findFocus()?.let { it.requestRectangleOnScreen(android.graphics.Rect(0, 0, it.width, it.height + dp(24))) } }
            insets
        }
    }
}
