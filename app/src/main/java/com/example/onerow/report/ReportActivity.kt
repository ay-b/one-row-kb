package com.example.onerow.report

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.onerow.R

/**
 * The shake screen: says what a crash report is, shows the report (the first [CrashReport.PREVIEW_CHARS] characters, the rest behind
 * "Show all"), and lets the user send it. "Send" only opens the mail app with the report filled in; the app has no internet access.
 */
class ReportActivity : Activity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)          // the screen has its own title
        val report = CrashReport.load(this)
        var body0: TextView? = null
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(8)) }
        col.addView(TextView(this).apply { text = getString(R.string.report_title); textSize = 20f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) })
        if (report == null) {
            col.addView(TextView(this).apply { text = getString(R.string.report_none); textSize = 15f; setPadding(0, dp(12), 0, dp(8)) })
        } else {
            col.addView(TextView(this).apply { text = getString(R.string.report_explainer); textSize = 14f; setPadding(0, dp(10), 0, dp(10)) })
            val (head, more) = CrashReport.preview(report)
            val body = TextView(this).apply {
                text = head; textSize = 12f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true)
                setPadding(dp(10), dp(8), dp(10), dp(8)); setBackgroundColor(0x22808080)
            }
            col.addView(body); body0 = body
            if (more) {
                var open = false
                col.addView(Button(this).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
                    text = getString(R.string.report_show_all); isAllCaps = false
                    setOnClickListener {
                        open = !open
                        body.text = if (open) report else head
                        text = getString(if (open) R.string.report_show_less else R.string.report_show_all)
                    }
                })
            }
        }
        // only the text scrolls; Close and Send stay in view however long the report is
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.END; setPadding(dp(16), dp(4), dp(16), dp(10)) }
        buttons.addView(Button(this).apply { text = getString(R.string.close); isAllCaps = false; setOnClickListener { finish() } }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(8) })
        if (report != null) buttons.addView(Button(this).apply { text = getString(R.string.report_send); isAllCaps = false; setOnClickListener { send(report) } })
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        val pal = com.example.onerow.AppStyle.palette(this)
        com.example.onerow.AppStyle.apply(root, pal)
        window.setBackgroundDrawable(com.example.onerow.AppStyle.dialogBackground(this, pal))
        root.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        body0?.apply { setBackgroundColor(pal.key); setTextColor(pal.ink) }
    }

    /** Opens the mail app with the whole report (never just the preview). The user presses send there. */
    private fun send(report: String) {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(CrashReport.SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, CrashReport.SUBJECT)
            putExtra(Intent.EXTRA_TEXT, report)
        }
        try { startActivity(i); finish() } catch (_: ActivityNotFoundException) { Toast.makeText(this, getString(R.string.report_no_mail), Toast.LENGTH_LONG).show() }
    }
}
