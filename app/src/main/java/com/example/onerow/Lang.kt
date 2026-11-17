package com.example.onerow

import com.example.onerow.engine.Alphabet

/** One supported language: alphabet, one-row column grouping (zig-zag), and the full-keyboard rows. */
class Lang(
    val code: String,
    val name: String,
    val alphabet: Alphabet,
    /** Letters grouped by physical keyboard column, left to right (each group zig-zags in the one-row mode). */
    val columns: List<String>,
    /**
     * Rows of the full keyboard (letters only; shift/backspace are added around the last row). A letter of the alphabet may be left
     * out when it is reachable by holding a key ([alternates], e.g. Russian ъ under ь); a character that is not in the alphabet but
     * folds onto a letter (Ukrainian ґ) may be added.
     */
    val fullRows: List<String>,
    /** National letters reachable by holding a key on the full keyboard: base letter -> variants. */
    val alternates: Map<Char, String> = emptyMap(),
    /** Small symbols under the third letter row, one per key left to right (a space = none); null = the standard `* " ' : ; ! ?`. */
    val row3Symbols: String? = null,
)

object Languages {
    private val latin = Alphabet(Alphabet.LATIN, Alphabet.LATIN_FOLDS)
    private val cyrillic = Alphabet("абвгдежзийклмнопрстуфхцчшщъыьэюя", mapOf('ё' to "е"))
    private val ukrainian = Alphabet("абвгдежзиіїйклмнопрстуфхцчшщьюяє", mapOf('ґ' to "г"))
    private val qwerty = listOf("qaz", "wsx", "edc", "rfv", "tgb", "yhn", "ujm", "ik", "ol", "p")
    private val qwertyRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val qwertz = listOf("qay", "wsx", "edc", "rfv", "tgb", "zhn", "ujm", "ik", "ol", "p")
    private val qwertzRows = listOf("qwertzuiop", "asdfghjkl", "yxcvbnm")
    private val qwerty9 = listOf("qaz", "wsx", "edc", "rfv", "tgb", "yhn", "ujm", "ik", "ol")     // the first nine columns of the QWERTY layouts with extra letters at the right
    private val swedish = Alphabet(Alphabet.LATIN + "åäö", Alphabet.LATIN_FOLDS)                    // Swedish, Finnish
    private val danish = Alphabet(Alphabet.LATIN + "æøå", Alphabet.LATIN_FOLDS)                     // Danish, Norwegian
    private val icelandic = Alphabet(Alphabet.LATIN + "ðþæö", Alphabet.LATIN_FOLDS)
    private val estonian = Alphabet(Alphabet.LATIN + "õäöü", Alphabet.LATIN_FOLDS)
    private val greek = Alphabet("αβγδεζηθικλμνξοπρστυφχψω", mapOf('ς' to "σ", 'ά' to "α", 'έ' to "ε", 'ή' to "η", 'ί' to "ι", 'ό' to "ο",
        'ύ' to "υ", 'ώ' to "ω", 'ϊ' to "ι", 'ϋ' to "υ", 'ΐ' to "ι", 'ΰ' to "υ"))

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
            listOf("йцукенгшщзх", "фывапролджэ", "ячсмитьбю"),            // ъ is under ь (hold), as on Gboard
            mapOf('е' to "ё", 'ь' to "ъ"),
            row3Symbols = "*\"':;!ъ?%"),                                    // ъ is the small hint on ь, "?" moved to б (the key right of ь), % on ю (hold: % and ‰)
        Lang("uk", "Українська", ukrainian,
            listOf("йфя", "ціч", "увс", "кам", "епи", "нрт", "гоь", "шлб", "щдю", "зж", "хє", "ї"),
            listOf("йцукенгшщзхї", "фівапролджє", "ячсмитьбюґ"),
            mapOf('г' to "ґ"),
            row3Symbols = "*\"':;! ?%"),                                     // "?" on б (the key right of ь), nothing on ь, % on ю, as in Russian
        // ---- more languages (dictionaries are not part of the app: see docs/on-demand-dictionaries.md) ----
        Lang("it", "Italiano", latin, qwerty, qwertyRows,
            mapOf('a' to "à", 'e' to "èé", 'i' to "ìí", 'o' to "òó", 'u' to "ùú")),
        Lang("nl", "Nederlands", latin, qwerty, qwertyRows,
            mapOf('a' to "áä", 'e' to "éèëê", 'i' to "íï", 'o' to "óö", 'u' to "úü")),
        Lang("sv", "Svenska", swedish, qwerty9 + listOf("pö", "åä"), listOf("qwertyuiopå", "asdfghjklöä", "zxcvbnm"),
            mapOf('e' to "é")),
        Lang("da", "Dansk", danish, qwerty9 + listOf("pæ", "åø"), listOf("qwertyuiopå", "asdfghjklæø", "zxcvbnm"),
            mapOf('e' to "é")),
        Lang("nb", "Norsk", danish, qwerty9 + listOf("pø", "åæ"), listOf("qwertyuiopå", "asdfghjkløæ", "zxcvbnm"),
            mapOf('e' to "é")),
        Lang("fi", "Suomi", swedish, qwerty9 + listOf("pö", "åä"), listOf("qwertyuiopå", "asdfghjklöä", "zxcvbnm"),
            mapOf('s' to "š", 'z' to "ž")),
        Lang("is", "Íslenska", icelandic, qwerty9 + listOf("pæ", "ðþö"), listOf("qwertyuiopð", "asdfghjklæþ", "zxcvbnmö"),
            mapOf('a' to "á", 'e' to "é", 'i' to "í", 'o' to "ó", 'u' to "ú", 'y' to "ý")),
        Lang("el", "Ελληνικά", greek, listOf("αζ", "σχ", "εδψ", "ρφω", "τγβ", "υην", "θξμ", "ικ", "ολ", "π"),
            listOf("ερτυθιοπ", "ασδφγηξκλ", "ζχψωβνμ"),
            mapOf('σ' to "ς", 'α' to "ά", 'ε' to "έ", 'η' to "ή", 'ι' to "ίϊ", 'ο' to "ό", 'υ' to "ύϋ", 'ω' to "ώ")),
        Lang("pl", "Polski", latin, qwerty, qwertyRows,
            mapOf('a' to "ą", 'c' to "ć", 'e' to "ę", 'l' to "ł", 'n' to "ń", 'o' to "ó", 's' to "ś", 'z' to "źż")),
        Lang("cs", "Čeština", latin, qwertz, qwertzRows,
            mapOf('a' to "á", 'c' to "č", 'd' to "ď", 'e' to "éě", 'i' to "í", 'n' to "ň", 'o' to "ó", 'r' to "ř", 's' to "š", 't' to "ť",
                'u' to "úů", 'y' to "ý", 'z' to "ž")),
        Lang("sk", "Slovenčina", latin, qwertz, qwertzRows,
            mapOf('a' to "áä", 'c' to "č", 'd' to "ď", 'e' to "é", 'i' to "í", 'l' to "ĺľ", 'n' to "ň", 'o' to "óô", 'r' to "ŕ", 's' to "š",
                't' to "ť", 'u' to "ú", 'y' to "ý", 'z' to "ž")),
        Lang("sl", "Slovenščina", latin, qwertz, qwertzRows,
            mapOf('c' to "č", 's' to "š", 'z' to "ž")),
        Lang("hr", "Hrvatski", latin, qwertz, qwertzRows,
            mapOf('c' to "čć", 'd' to "đ", 's' to "š", 'z' to "ž")),
        Lang("hu", "Magyar", latin, qwertz, qwertzRows,
            mapOf('a' to "á", 'e' to "é", 'i' to "í", 'o' to "óöő", 'u' to "úüű")),
        Lang("ro", "Română", latin, qwerty, qwertyRows,
            mapOf('a' to "ăâ", 'i' to "î", 's' to "ș", 't' to "ț")),
        Lang("et", "Eesti", estonian, qwerty9 + listOf("pö", "üä", "õ"), listOf("qwertyuiopü", "asdfghjklöä", "zxcvbnmõ"),
            mapOf('s' to "š", 'z' to "ž")),
        Lang("lv", "Latviešu", latin, qwerty, qwertyRows,
            mapOf('a' to "ā", 'c' to "č", 'e' to "ē", 'g' to "ģ", 'i' to "ī", 'k' to "ķ", 'l' to "ļ", 'n' to "ņ", 's' to "š", 'u' to "ū", 'z' to "ž")),
        Lang("lt", "Lietuvių", latin, qwerty, qwertyRows,
            mapOf('a' to "ą", 'c' to "č", 'e' to "ęė", 'i' to "į", 's' to "š", 'u' to "ųū", 'z' to "ž")),
        Lang("ca", "Català", latin, qwerty, qwertyRows,
            mapOf('a' to "à", 'c' to "ç", 'e' to "éè", 'i' to "íï", 'o' to "óò", 'u' to "úü")),
    )

    fun byCode(code: String?): Lang = all.firstOrNull { it.code == code } ?: all[0]
}
