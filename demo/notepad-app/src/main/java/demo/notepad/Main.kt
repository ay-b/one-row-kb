package demo.notepad

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class Main : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#FFFBEF")) }
        root.addView(TextView(this).apply {
            text = "Notes"; textSize = 28f; setTextColor(Color.parseColor("#5D4B1F")); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(24), dp(56), dp(24), dp(8))
        })
        root.addView(EditText(this).apply {
            hint = "Start typing…"; textSize = 24f; setTextColor(Color.parseColor("#2B2412")); setHintTextColor(Color.parseColor("#B8AE8F"))
            setBackgroundColor(Color.TRANSPARENT); gravity = Gravity.TOP or Gravity.START
            setPadding(dp(24), dp(8), dp(24), dp(8)); setLineSpacing(0f, 1.15f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            requestFocus()
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}
