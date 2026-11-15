package com.example.onerow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LanguagesTest {
    /** Every layout must offer exactly the letters of its alphabet (no dictionary needed for this). */
    @Test fun layoutsCoverTheAlphabets() {
        for (l in Languages.all) {
            val letters = l.alphabet.letters.toSet()
            assertTrue("${l.code}: duplicate letters", l.alphabet.letters.length == letters.size)
            assertEquals("${l.code}: one-row columns", letters, l.columns.joinToString("").toSet())
            assertEquals("${l.code}: one-row columns repeat a letter", letters.size, l.columns.joinToString("").length)
            val rows = l.fullRows.joinToString("")
            assertEquals("${l.code}: full keyboard rows repeat a letter", rows.toSet().size, rows.length)
            val held = l.alternates.values.joinToString("").toSet()
            for (c in letters) assertTrue("${l.code}: '$c' is neither on the full keyboard nor under another key", c in rows || c in held)
            for (c in rows) assertTrue("${l.code}: '$c' on the full keyboard is not a letter of the alphabet", c in letters || c in l.alphabet.folds)
            for ((base, variants) in l.alternates) assertTrue("${l.code}: alternates of '$base'", l.alphabet.index(base) >= 0 && variants.isNotEmpty())
        }
    }

    /** tools/languages.json is generated from the app's language definitions, so the dictionary tools cannot drift from them. */
    @Test fun toolsFileMatchesTheApp() {
        fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        val sb = StringBuilder("{\n")
        for ((i, l) in Languages.all.withIndex()) {
            val folds = l.alphabet.folds.entries.sortedBy { it.key }.joinToString(", ") { q(it.key.toString()) + ": " + q(it.value) }
            val alts = l.alternates.entries.sortedBy { it.key }.joinToString(", ") { q(it.key.toString()) + ": " + q(it.value) }
            sb.append("  ${q(l.code)}: {\"name\": ${q(l.name)}, \"letters\": ${q(l.alphabet.letters)}, \"folds\": {$folds}, ")
            sb.append("\"columns\": [${l.columns.joinToString(", ") { q(it) }}], \"fullRows\": [${l.fullRows.joinToString(", ") { q(it) }}], \"alternates\": {$alts}}")
            sb.append(if (i < Languages.all.size - 1) ",\n" else "\n")
        }
        val now = sb.append("}\n").toString()
        val f = File("../tools/languages.json")
        if (System.getenv("UPDATE_LANGUAGES_JSON") == "1") f.writeText(now)
        assertEquals("tools/languages.json is out of date: run the tests once with UPDATE_LANGUAGES_JSON=1", f.readText(), now)
    }
}
