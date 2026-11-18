package com.example.onerow

import java.io.File

/** Dictionary files for the tests: the ones inside the app, then the optional languages in <project>/dictionaries. */
object TestDicts {
    val bundled = setOf("en", "fr", "de", "es", "pt", "ru", "uk", "he")

    fun file(name: String): File? = listOf("src/main/assets/dict", "../dictionaries").map { File(it, name) }.firstOrNull { it.isFile }

    /** False for an optional language whose dictionary has not been built here (the test skips it). */
    fun present(code: String) = file("$code.lex") != null || code in bundled
}
