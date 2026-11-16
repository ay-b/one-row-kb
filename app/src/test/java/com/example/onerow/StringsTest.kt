package com.example.onerow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The UI is translated through Android resources (res/values-<code>/strings.xml). This test keeps every translation complete and safe:
 * same keys as the English file, the same placeholders in every string, the plural forms the language needs, and no formatting that
 * would crash at run time (a stray % or an unescaped quote).
 */
class StringsTest {
    private val res = File("src/main/res")

    /** Every keyboard language gets a translated UI. (English is the default folder.) */
    private val locales = listOf("fr", "de", "es", "pt", "ru", "uk", "it", "nl", "sv", "da", "nb", "fi", "is", "el", "pl", "cs", "sk",
        "sl", "hr", "hu", "ro", "et", "lv", "lt", "ca")

    /** CLDR plural categories that integers 0..99 need in each language; others default to one + other. `many` is allowed (never needed) in the listed ones. */
    private val required = mapOf(
        "ru" to setOf("one", "few", "many", "other"), "uk" to setOf("one", "few", "many", "other"), "pl" to setOf("one", "few", "many", "other"),
        "cs" to setOf("one", "few", "other"), "sk" to setOf("one", "few", "other"), "hr" to setOf("one", "few", "other"),
        "sl" to setOf("one", "two", "few", "other"), "lt" to setOf("one", "few", "other"), "lv" to setOf("zero", "one", "other"),
        "ro" to setOf("one", "few", "other"))
    private val manyAllowed = setOf("fr", "es", "it", "pt", "ca", "cs", "sk", "lt")

    private class Parsed(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    private fun parse(f: File): Parsed {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        val strings = LinkedHashMap<String, String>(); val plurals = LinkedHashMap<String, Map<String, String>>()
        val kids = doc.documentElement.childNodes
        for (i in 0 until kids.length) {
            val e = kids.item(i) as? Element ?: continue
            when (e.tagName) {
                "string" -> if (e.getAttribute("translatable") != "false") strings[e.getAttribute("name")] = e.textContent
                "plurals" -> {
                    val items = LinkedHashMap<String, String>(); val its = e.getElementsByTagName("item")
                    for (j in 0 until its.length) { val it = its.item(j) as Element; items[it.getAttribute("quantity")] = it.textContent }
                    plurals[e.getAttribute("name")] = items
                }
                else -> throw AssertionError("${f.path}: unexpected element <${e.tagName}>")
            }
        }
        return Parsed(strings, plurals)
    }

    private val placeholder = Regex("%(\\d+\\$)?[sd]")
    private fun placeholders(s: String) = placeholder.findAll(s).map { it.value }.sorted().toList()

    /** Text as it ends up after Android's escape processing must not contain a bare % (String.format would throw) or an unescaped quote. */
    private fun check(where: String, raw: String) {
        val stripped = raw.replace(placeholder, "").replace("%%", "")
        assertTrue("$where: a lone % in \"$raw\" (write %% for a percent sign)", !stripped.contains('%'))
        // XML text still has the backslashes of Android's escaping: every ' and " must be preceded by one
        assertTrue("$where: unescaped ' in \"$raw\"", !Regex("(?<!\\\\)'").containsMatchIn(raw))
        assertTrue("$where: unescaped \" in \"$raw\"", !Regex("(?<!\\\\)\"").containsMatchIn(raw))
        assertTrue("$where: empty or blank", raw.isNotBlank())
        assertTrue("$where: leftover TODO/English marker in \"$raw\"", !raw.contains("TODO"))
    }

    @Test fun englishIsWellFormed() {
        val en = parse(File(res, "values/strings.xml"))
        assertTrue(en.strings.size > 50)
        en.strings.forEach { (k, v) -> check("en $k", v) }
        en.plurals.forEach { (k, items) -> assertEquals("en $k", setOf("one", "other"), items.keys); items.forEach { (q, v) -> check("en $k/$q", v) } }
    }

    @Test fun everyLanguageIsComplete() {
        val en = parse(File(res, "values/strings.xml"))
        for (code in locales) {
            val f = File(res, "values-$code/strings.xml")
            assertTrue("missing ${f.path}", f.exists())
            val tr = parse(f)
            assertEquals("$code: keys differ from English (missing or extra)", en.strings.keys, tr.strings.keys)
            assertEquals("$code: plurals differ from English", en.plurals.keys, tr.plurals.keys)
            for ((k, v) in tr.strings) {
                check("$code $k", v)
                assertEquals("$code $k: placeholders differ from English", placeholders(en.strings.getValue(k)), placeholders(v))
            }
            val need = required[code] ?: setOf("one", "other")
            for ((k, items) in tr.plurals) {
                val allowed = need + if (code in manyAllowed) setOf("many") else emptySet()
                assertTrue("$code $k: needs quantities $need, has ${items.keys}", items.keys.containsAll(need) && allowed.containsAll(items.keys))
                for ((q, v) in items) {
                    check("$code $k/$q", v)
                    assertEquals("$code $k/$q: placeholders differ from English", placeholders(en.plurals.getValue(k).getValue("other")), placeholders(v))
                }
            }
        }
    }

    @Test fun noStrayTranslationFolders() {
        val dirs = res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").exists() }!!.map { it.name.removePrefix("values-") }.toSet()
        assertEquals("a values-xx folder is not in the supported list (or the list is stale)", locales.toSet(), dirs)
    }
}
