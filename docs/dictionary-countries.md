# Which languages got dictionaries, and why

Task: "dictionaries for every country with over EUR 1000 median monthly income per working person".

## The rule and its weak point

A world table of *median* monthly earnings per worker does not exist in one consistent source. What exists is the
**average gross monthly wage** (UNECE for Europe/Central Asia, OECD average annual wages for the rest, both reprinted on
Wikipedia, "List of countries by average wage", 2024). The median is typically about 0.8 of the average, so a median of
EUR 1000 (about USD 1,080) corresponds to an average of about **USD 1,350 gross per month**. That is the cut-off used.
Gross vs. net, purchasing power and informal work are all ignored; countries close to the line are marked *borderline*.
This is a language-coverage decision, not an economic statement: treat the numbers as approximate.

## Countries above the line, and their keyboards

| Language (code) | Countries above the line where it is the main language | Status |
|---|---|---|
| English, French, German, Spanish, Portuguese | US, UK, Canada, Australia, NZ, Ireland, France, Belgium, Germany, Austria, Switzerland, Spain, Portugal | already bundled |
| Italian (it) | Italy | **new** |
| Dutch (nl) | Netherlands, Belgium (Flanders) | **new** |
| Swedish (sv) | Sweden, Finland (minority) | **new** |
| Danish (da) | Denmark | **new** |
| Norwegian Bokmal (nb) | Norway | **new** |
| Finnish (fi) | Finland | **new** |
| Icelandic (is) | Iceland | **new** |
| Greek (el) | Greece, Cyprus (*borderline*) | **new** |
| Polish (pl) | Poland | **new** |
| Czech (cs) | Czechia | **new** |
| Slovak (sk) | Slovakia | **new** |
| Slovenian (sl) | Slovenia | **new** |
| Croatian (hr) | Croatia (*borderline*) | **new** |
| Hungarian (hu) | Hungary (*borderline*) | **new** |
| Romanian (ro) | Romania (*borderline*) | **new** |
| Estonian (et) | Estonia | **new** |
| Latvian (lv) | Latvia (*borderline*) | **new** |
| Lithuanian (lt) | Lithuania | **new** |
| Catalan (ca) | Catalonia, Andorra, Valencia, Balearics (part of Spain) | **new** |

Russian and Ukrainian are bundled although Russia and Ukraine are below the line (they were there first).

## Above the line but not done, and why

| Language | Countries | Reason |
|---|---|---|
| Japanese, Korean, Chinese | Japan, South Korea, China (borderline), Taiwan, Singapore | no letter alphabet: needs a kana/hangul/pinyin conversion engine, not a letter trie |
| Hebrew, Arabic | Israel, UAE, Saudi Arabia, Qatar, Kuwait | right-to-left layout is not supported yet |
| Maltese, Irish, Luxembourgish | Malta, Ireland, Luxembourg | no usable frequency data (and the countries are served by English / French / German) |
| Turkish | Turkey | below the line, or borderline at best |
| Bulgarian, Serbian | Bulgaria, Serbia | below / borderline; Cyrillic layouts would be easy to add next |

## How the dictionaries are built

`tools/build_lexicon.py <hunspell_dir> --extras <dir> <lang ...>` (the same pipeline as the bundled languages):
candidates from wordfreq where it really covers the language (large list, else small) plus the OpenSubtitles list of
FrequencyWords (words seen at least 5 times); common words are trusted, rare ones must pass the LibreOffice Hunspell
dictionary or be corroborated by both corpora (Finnish has no Hunspell dictionary: rare words need the corroboration).
`wordfreq` does **not** cover Danish, Icelandic, Slovak, Croatian and Estonian (it silently substitutes a neighbouring
language), which is why FrequencyWords is the candidate source there. Then `tools/build_bigrams.py` writes
`<lang>_bi.tsv` from Tatoeba. Output goes to `dictionaries/` (ignored by git); the layouts, alphabets and folds of the
languages are in `Lang.kt`, exported to `tools/languages.json` (checked by `LanguagesTest`).

Quality caveat: unlike the first seven, these were not tuned or tried by a native reader; casing is mostly lower case
(Hunspell supplies capitals for proper nouns only when the lower-case form is unknown).
