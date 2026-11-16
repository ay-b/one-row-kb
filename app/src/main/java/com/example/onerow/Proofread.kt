package com.example.onerow

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.proofreading.Proofreader
import com.google.mlkit.genai.proofreading.ProofreaderOptions
import com.google.mlkit.genai.proofreading.Proofreading
import com.google.mlkit.genai.proofreading.ProofreadingRequest

/**
 * Grammar fixing with Google's on-device proofreading (ML Kit GenAI, Gemini Nano through the system's AICore). Nothing
 * leaves the phone. It only exists on phones that have AICore with the feature: everywhere else [status] says
 * [Status.UNAVAILABLE] and the setting is greyed out. Needs Android 8 (the library's minimum); the app itself still runs
 * on Android 7, it just never touches this class there.
 */
object Proofread {
    enum class Status { UNAVAILABLE, DOWNLOADABLE, DOWNLOADING, AVAILABLE }

    /** The languages the model knows: keyboard language code -> ML Kit language. */
    private val languages = mapOf(
        "en" to ProofreaderOptions.Language.ENGLISH, "fr" to ProofreaderOptions.Language.FRENCH,
        "de" to ProofreaderOptions.Language.GERMAN, "it" to ProofreaderOptions.Language.ITALIAN,
        "es" to ProofreaderOptions.Language.SPANISH)

    fun supports(code: String) = Build.VERSION.SDK_INT >= 26 && code in languages
    private val main = Handler(Looper.getMainLooper())
    private val clients = HashMap<String, Proofreader>()

    private fun client(ctx: Context, code: String): Proofreader? {
        if (Build.VERSION.SDK_INT < 26) return null
        val lang = languages[code] ?: return null
        return clients.getOrPut(code) {
            Proofreading.getClient(ProofreaderOptions.builder(ctx.applicationContext).setInputType(ProofreaderOptions.InputType.KEYBOARD).setLanguage(lang).build())
        }
    }

    /** Whether the model can be used for [code] on this phone (answers on the main thread). */
    fun status(ctx: Context, code: String, done: (Status) -> Unit) {
        val c = try { client(ctx, code) } catch (e: Throwable) { null }
        if (c == null) { done(Status.UNAVAILABLE); return }
        try {
            val f = c.checkFeatureStatus()
            f.addListener({
                val s = try {
                    when (f.get()) {
                        FeatureStatus.AVAILABLE -> Status.AVAILABLE
                        FeatureStatus.DOWNLOADABLE -> Status.DOWNLOADABLE
                        FeatureStatus.DOWNLOADING -> Status.DOWNLOADING
                        else -> Status.UNAVAILABLE
                    }
                } catch (e: Throwable) { Status.UNAVAILABLE }
                main.post { done(s) }
            }, { it.run() })
        } catch (e: Throwable) { done(Status.UNAVAILABLE) }
    }

    /** Starts the one-time model download; [done] gets true when it has finished. */
    fun download(ctx: Context, code: String, done: (Boolean) -> Unit) {
        val c = try { client(ctx, code) } catch (e: Throwable) { null }
        if (c == null) { done(false); return }
        try {
            c.downloadFeature(object : DownloadCallback {
                override fun onDownloadStarted(bytes: Long) {}
                override fun onDownloadProgress(bytes: Long) {}
                override fun onDownloadCompleted() { main.post { done(true) } }
                override fun onDownloadFailed(e: GenAiException) { main.post { done(false) } }
            })
        } catch (e: Throwable) { done(false) }
    }

    /** Corrects [text]; [done] gets the corrected text (the same text when nothing needed fixing) or null on failure. */
    fun fix(ctx: Context, code: String, text: String, done: (String?) -> Unit) {
        val c = try { client(ctx, code) } catch (e: Throwable) { null }
        if (c == null) { done(null); return }
        try {
            val f = c.runInference(ProofreadingRequest.builder(text).build())
            f.addListener({
                val out = try { f.get().results.firstOrNull()?.text } catch (e: Throwable) { null }
                main.post { done(out) }
            }, { it.run() })
        } catch (e: Throwable) { done(null) }
    }
}
