package com.example.onerow

import android.content.Context
import java.io.File
import java.io.InputStream

/**
 * Where a language's dictionary files (<code>.lex and <code>_bi.tsv) are: inside the app (assets), or installed later into
 * the app's private folder (files/dict). Languages without a dictionary cannot be enabled.
 */
object Dictionaries {
    fun dir(ctx: Context) = File(ctx.filesDir, "dict")

    private var bundled: Set<String>? = null
    private fun bundled(ctx: Context): Set<String> =
        bundled ?: (ctx.assets.list("dict")?.toSet() ?: emptySet()).also { bundled = it }

    fun has(ctx: Context, code: String) = "$code.lex" in bundled(ctx) || File(dir(ctx), "$code.lex").isFile

    /** A dictionary file by name ("it.lex", "it_bi.tsv"), or null when it is not there. */
    fun open(ctx: Context, name: String): InputStream? =
        if (name in bundled(ctx)) ctx.assets.open("dict/$name") else File(dir(ctx), name).takeIf { it.isFile }?.inputStream()
}
