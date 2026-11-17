package com.example.onerow.report

import android.app.Application
import android.content.Context
import android.os.Build
import com.example.onerow.Storage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The crash report: what the keyboard saves when it crashes and what the shake screen offers to email.
 *
 * Privacy is the point of this class. The report holds ONLY: app version, Android version, phone model, language, the time, and the
 * technical trace (class names and code locations). It never holds exception messages (they can quote typed text), nothing from the
 * text field, the clipboard, the settings or the dictionaries. It is built at crash time, so nothing else is ever written to disk.
 * The user sees the whole report before anything leaves the phone (the app itself has no internet access; it opens the mail app).
 */
object CrashReport {
    const val SUPPORT_EMAIL = "apps@y-b.one"
    const val SUBJECT = "OneRow crash report"
    /** The shake screen first shows this many characters, the rest behind "Show all". */
    const val PREVIEW_CHARS = 300
    const val MAX_FRAMES = 40
    const val MAX_CAUSES = 4
    private const val FILE = "last_crash.txt"

    class DeviceInfo(val version: String, val versionCode: Long, val androidRelease: String, val sdk: Int, val phone: String, val language: String)

    fun deviceInfo(c: Context): DeviceInfo {
        val pi = try { c.packageManager.getPackageInfo(c.packageName, 0) } catch (_: Exception) { null }
        @Suppress("DEPRECATION") val code = if (pi == null) 0L else if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
        return DeviceInfo(pi?.versionName ?: "?", code, Build.VERSION.RELEASE ?: "?", Build.VERSION.SDK_INT,
            listOf(Build.MANUFACTURER, Build.MODEL).filter { !it.isNullOrBlank() }.joinToString(" "), Locale.getDefault().toLanguageTag())
    }

    /** The technical trace of [t] and its causes: class names and code locations, never the messages. */
    fun trace(t: Throwable): String {
        val sb = StringBuilder()
        var cur: Throwable? = t
        var n = 0
        val seen = HashSet<Throwable>()
        while (cur != null && n < MAX_CAUSES && seen.add(cur)) {
            sb.append(if (n == 0) "" else "Caused by: ").append(cur.javaClass.name).append('\n')
            val frames = cur.stackTrace
            for (i in 0 until minOf(frames.size, MAX_FRAMES)) {
                val f = frames[i]
                sb.append("  at ").append(f.className).append('.').append(f.methodName).append('(').append(f.fileName ?: "Unknown").append(':').append(f.lineNumber).append(")\n")
            }
            if (frames.size > MAX_FRAMES) sb.append("  ... ").append(frames.size - MAX_FRAMES).append(" more\n")
            cur = cur.cause; n++
        }
        return sb.toString().trimEnd()
    }

    fun build(d: DeviceInfo, whenMs: Long, t: Throwable): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return "OneRow crash report\n" +
            "App: ${d.version} (${d.versionCode})\n" +
            "Android: ${d.androidRelease} (SDK ${d.sdk})\n" +
            "Phone: ${d.phone}\n" +
            "Language: ${d.language}\n" +
            "When: ${fmt.format(Date(whenMs))} UTC\n\n" +
            trace(t)
    }

    /** The first [PREVIEW_CHARS] characters (never cutting a character in half) and whether there is more. */
    fun preview(report: String): Pair<String, Boolean> {
        if (report.length <= PREVIEW_CHARS) return report to false
        var end = PREVIEW_CHARS
        if (Character.isHighSurrogate(report[end - 1])) end--
        return report.substring(0, end) + "…" to true
    }

    private fun file(c: Context) = File(Storage.of(c).filesDir, FILE)

    fun load(c: Context): String? = try { file(c).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() } } catch (_: Exception) { null }

    fun save(c: Context, report: String) { try { file(c).writeText(report) } catch (_: Exception) { } }

    /** Saves the report of every uncaught exception, then lets Android do what it always did (the crash dialog, the vitals). Must never throw. */
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            try { save(app, build(deviceInfo(app), System.currentTimeMillis(), ex)) } catch (_: Throwable) { }
            previous?.uncaughtException(thread, ex)
        }
    }
}
