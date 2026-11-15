package com.example.onerow

import com.example.onerow.engine.Alphabet

/** One supported language: alphabet, one-row column grouping (zig-zag), and the full-keyboard rows. */
class Lang(
    val code: String,
    val name: String,
    val alphabet: Alphabet,
    /** Letters grouped by physical keyboard column, left to right (each group zig-zags in the one-row mode). */
    val columns: List<String>,
    /** Rows of the full keyboard (letters only; shift/backspace are added around the last row). */
    val fullRows: List<String>,
    /** National letters reachable by holding a key on the full keyboard: base letter -> variants. */
    val alternates: Map<Char, String> = emptyMap(),
)

object Languages {
    private val latin = Alphabet(Alphabet.LATIN, Alphabet.LATIN_FOLDS)
    private val cyrillic = Alphabet("абвгдежзийклмнопрстуфхцчшщъыьэюя", mapOf('ё' to "е"))
    private val ukrainian = Alphabet("абвгдежзиіїйклмнопрстуфхцчшщьюяє", mapOf('ґ' to "г"))
    private val qwerty = listOf("qaz", "wsx", "edc", "rfv", "tgb", "yhn", "ujm", "ik", "ol", "p")
    private val qwertyRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

    val all: List<Lang> = listOf(
        Lang("en", "English", latin, qwerty, qwertyRows),
        Lang("fr", "Français", latin,
            listOf("aqw", "zsx", "edc", "rfv", "tgb", "yhn", "uj", "ik", "ol", "pm"),
            listOf("azertyuiop", "qsdfghjklm", "wxcvbn"),
            mapOf('a' to "àâæ", 'c' to "ç", 'e' to "éèêë", 'i' to "îï", 'o' to "ôœ", 'u' to "ùûü", 'y' to "ÿ")),
        Lang("de", "Deutsch", latin,
            listOf("qay", "wsx", "edc", "rfv", "tgb", "zhn", "ujm", "ik", "ol", "p"),
            listOf("qwertzuiop", "asdfghjkl", "yxcvbnm"),
            mapOf('a' to "ä", 'o' to "ö", 'u' to "ü", 's' to "ß")),
        Lang("es", "Español", latin, qwerty, qwertyRows,
            mapOf('a' to "á", 'e' to "é", 'i' to "í", 'o' to "ó", 'u' to "úü", 'n' to "ñ")),
        Lang("pt", "Português", latin, qwerty, qwertyRows,
            mapOf('a' to "áàâã", 'c' to "ç", 'e' to "éê", 'i' to "í", 'o' to "óôõ", 'u' to "úü")),
        Lang("ru", "Русский", cyrillic,
            listOf("йфя", "цыч", "увс", "кам", "епи", "нрт", "гоь", "шлб", "щдю", "зж", "хэ", "ъ"),
            listOf("йцукенгшщзхъ", "фывапролджэ", "ячсмитьбю"),
            mapOf('е' to "ё", 'ь' to "ъ")),
        Lang("uk", "Українська", ukrainian,
            listOf("йфя", "ціч", "увс", "кам", "епи", "нрт", "гоь", "шлб", "щдю", "зж", "хє", "ї"),
            listOf("йцукенгшщзхї", "фівапролджє", "ячсмитьбю"),
            mapOf('г' to "ґ")),
    )

    fun byCode(code: String?): Lang = all.firstOrNull { it.code == code } ?: all[0]
}
