# Dictionary sources and how to rebuild

The keyboard's word lists are `app/src/main/assets/dict/<lang>.lex` (compact binary tries) next to `<lang>_bi.tsv`
(bigrams). `.lex` files are built by `tools/build_lexicon.py`; the format is described at the top of that script.

## Sources

| What | Where | Used for | Licence (see also NOTICE-dictionaries.md) |
|---|---|---|---|
| wordfreq "large" lists | https://github.com/rspeer/wordfreq (`pip install wordfreq`) | candidate words, frequencies | data CC BY-SA 4.0 (mixed sources) |
| Hunspell dictionaries | https://github.com/LibreOffice/dictionaries : `en/en_US`, `fr_FR/dictionaries/fr`, `de/de_DE_frami`, `es/es_ES`, `pt_BR/pt_BR`, `pt_PT/pt_PT`, `ru_RU/ru_RU`, `uk_UA/uk_UA` (`.aff` + `.dic`) | vetting rare words (via the `spylls` library) | per language, see NOTICE |
| OpenSubtitles frequency lists | https://github.com/hermitdave/FrequencyWords `content/2018/<lang>/<lang>_full.txt` | corroborating modern words (second corpus) | CC BY-SA 4.0 (OpenSubtitles) |
| Aptivi WordsList | https://github.com/Aptivi-Analytics/WordsList `Processed/words-clean-alpha.txt` (derived from dwyl/english-words `words_alpha.txt`, https://github.com/dwyl/english-words) | extra English vetting | Unlicense (dwyl) / see repo |
| Ashpex Slang-Word | https://github.com/Ashpex/Slang-Word `slang.txt` | English slang and acronyms (with corpus evidence only) | GPL-3.0 (list used as data; check before release) |
| Wiktionary categories | MediaWiki API on en.wiktionary.org: `Category:<Language>_neologisms`, `Category:<Language>_internet_slang` (index: https://en.wiktionary.org/wiki/Category:Neologisms_by_language; bulk dumps: https://dumps.wikimedia.org/enwiktionary/latest/) | recent vocabulary, stored in `tools/dict_src/<lang>_neologisms.txt` by `tools/fetch_neologisms.py` | CC BY-SA 4.0 |
| Tatoeba sentences | https://tatoeba.org (`downloads.tatoeba.org`, per-language `.tsv.bz2`) | bigrams (`<lang>_bi.tsv`, by `tools/build_dict.py`) | CC BY 2.0 FR |
| `tools/dict_src/<lang>_casing.tsv` | derived from the older Tatoeba-based build | display casing (names, German nouns, "I") | n/a |

Researched but not used: OpenCorpora (Russian, CC BY-SA 3.0), VESUM (Ukrainian, CC BY-NC-SA 4.0), UniMorph, Morphalou/Lefff.

## Rebuild

```
pip install wordfreq spylls numpy
# 1. download the Hunspell files (above) into <hunspell_dir>/{en,fr,de,es,pt_BR,pt_PT,ru,uk}/
# 2. optional extras: <extras>/aptivi/words-clean-alpha.txt, <extras>/ashpex/slang.txt, <extras>/freq_<lang>/<lang>_full.txt
python tools/fetch_neologisms.py                                   # Wiktionary -> tools/dict_src/<lang>_neologisms.txt
python tools/build_lexicon.py <hunspell_dir> --extras <extras>     # -> app/src/main/assets/dict/<lang>.lex   (about 15 min)
```

Rules (details in the script): rare words must pass Hunspell, or be corroborated by two corpora; common words are
trusted; the Wiktionary neologism lists are added regardless of frequency; at most 150k (en) / 220k (fr, pt) /
250k (es) / 330k (de) / 350k (uk) / 450k (ru) words per language before the neologisms are added.

The Wiktionary categories do not carry dates, so "words of the last 10-15 years" is approximate.

## Optional languages (not bundled)

it nl sv da nb fi is el pl cs sk sl hr hu ro et lv lt ca: configured by `tools/languages.json` (exported from `Lang.kt`).
Hunspell: LibreOffice/dictionaries `it/it_IT`, `nl_NL`, `sv_SE/dictionaries`, `da_DK`, `no` (nb_NO), `is`, `pl_PL`, `cs_CZ`,
`sk_SK`, `sl_SI`, `hr_HR`, `hu_HU`, `ro`, `el_GR`, `et_EE`, `lv_LV`, `lt_LT` (`lt`), `ca/dictionaries`. No Finnish dictionary.
```
python tools/build_lexicon.py <hunspell_dir> --extras <extras> it nl ...     # -> dictionaries/<lang>.lex
python tools/build_bigrams.py dictionaries <tatoeba_dir>                    # -> dictionaries/<lang>_bi.tsv
```
`<hunspell_dir>/<lang>/` holds the `.aff` + `.dic`; `<extras>/freq_<lang>/<lang>_full.txt` is FrequencyWords (Norwegian is
`no` in that repository). Tatoeba codes: ita nld swe dan nob fin isl pol ces slk slv hrv hun ron ell est lvs lit cat.
See docs/dictionary-countries.md and docs/on-demand-dictionaries.md.

## Hyphenated words
wordfreq has no hyphenated words in any language, so `tools/build_lexicon.py` takes them from the OpenSubtitles list (at least 30
occurrences, at most three parts, no repetitions like да-да and no stutters like п-привет). Where a Hunspell dictionary exists
the word must pass it (this also drops Russian words from the Ukrainian subtitle list). The hyphen is part of the displayed
word only; the key a swipe or tap must match skips it (Alphabet.key), exactly like an apostrophe.
