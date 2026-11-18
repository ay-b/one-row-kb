# Attributions

OneRow Keyboard's word lists and next-word predictions are compiled from the open data below. Thank you to everyone
who made it. The lists are compiled data (a compact trie of words and frequencies); they are not a copy of any one source.

## Word frequencies and candidate words

- **wordfreq**, Robyn Speer and contributors. https://github.com/rspeer/wordfreq. Data licensed CC BY-SA 4.0
  (https://creativecommons.org/licenses/by-sa/4.0/). wordfreq itself is built from many sources, among them Wikipedia,
  OpenSubtitles, news, books, web text and social media; see its NOTICE for the full list.
- **FrequencyWords**, Herman Zvonimir Dosilovic (hermitdave), from OpenSubtitles 2018.
  https://github.com/hermitdave/FrequencyWords. CC BY-SA 4.0.
- **Wiktionary** (English edition), neologism and internet-slang categories, for recent vocabulary.
  https://en.wiktionary.org. CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/); contributors are listed in each entry's history.
- **Tatoeba** sentences, for next-word (bigram) probabilities. https://tatoeba.org. CC BY 2.0 FR
  (https://creativecommons.org/licenses/by/2.0/fr/); sentences are contributed by the Tatoeba community.

## Dictionaries used to check words

Hunspell dictionaries from LibreOffice/dictionaries (https://github.com/LibreOffice/dictionaries) decide whether a rare
word is a real word. Their word lists are not copied into the app.

| Language | Dictionary | Licence |
|---|---|---|
| English | en_US, SCOWL-derived | permissive, see its README |
| French | Grammalecte, Olivier R. | MPL 2.0 |
| German | de_DE_frami | GPL v2 / v3 / LGPL |
| Spanish | es_ES | GPL 3, LGPL 3, MPL 1.1 |
| Portuguese | pt_BR, Raimundo Moura; pt_PT, Jose Joao Almeida et al. | LGPL 3 / LGPL-style |
| Russian | Alexander I. Lebedev | LGPL-style, see README_ru_RU.txt |
| Ukrainian | based on dict_uk, https://github.com/brown-uk/dict_uk | see the note below |
| Hebrew | Hspell, Nadav Har'El and Dan Kenigsberg, http://hspell.ivrix.org.il/ (LibreOffice he_IL) | GNU AGPL 3, used only as a filter (see NOTICE-dictionaries.md) |
| Other languages (offered as separate packs) | the matching LibreOffice/dictionaries entries | each pack's own licence texts: GPL, LGPL, MPL, BSD, CC BY |

Further word lists used only to check or add English words: **dwyl/english-words** (Unlicense),
**Aptivi-Analytics/WordsList**, **Ashpex/Slang-Word**.

### Ukrainian

The Ukrainian Hunspell dictionary is derived from the dict_uk project, whose dictionary data is published under
CC BY-NC-SA 4.0 and whose software under GPL 3 or later. OneRow uses it only as a filter that accepts or rejects
candidate words taken from the sources above; none of its entries or files is shipped. It is credited here with thanks
to the dict_uk authors. The owner of OneRow has decided to treat this use as not incorporating the dictionary.

## Share-alike

Several sources are CC BY-SA 4.0. The compiled word lists (`app/src/main/assets/dict/*.lex` and `*_bi.tsv`) are
therefore offered under the same licence, CC BY-SA 4.0, to the extent they are derived from those sources. The app's own
code is not affected.

## Software

- **Google Play Billing Library** and **ML Kit GenAI Proofreading** (Google), under their own terms.
