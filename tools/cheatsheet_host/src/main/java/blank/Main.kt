package blank

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout

/** A blank page in one solid color (intent extra "bg", e.g. --es bg "#26292C") with an invisible text field and the keyboard open: the backdrop for the OneRow cheat-sheet screenshots. */
class Main : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val bg = Color.parseColor(intent.getStringExtra("bg") ?: "#262F38")
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        root.addView(EditText(this).apply {
            setBackgroundColor(Color.TRANSPARENT); setTextColor(bg); setHintTextColor(bg); setTextIsSelectable(false)
            highlightColor = Color.TRANSPARENT
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            requestFocus()
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        window.statusBarColor = bg; window.navigationBarColor = bg
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}
