# Dictionary data: notices

The word lists shipped in `app/src/main/assets/dict/` are compiled from the sources below (see
`tools/dict_src/SOURCES.md`). **Licences are NOT yet resolved for a public release**: this file records what is
known so it can be settled before one.

- **wordfreq** (Robyn Speer et al.): frequencies and candidate words. Data licensed CC BY-SA 4.0; it is itself built from
  Wikipedia, subtitles, news, books, Reddit, Twitter and web text (see its NOTICE).
- **Hunspell dictionaries from LibreOffice/dictionaries**, used only to vet words (their word lists are not copied
  verbatim, but the accepted words come from them):
  - English: SCOWL-derived en_US (permissive, see its README).
  - French: Grammalecte / Olivier R., MPL 2.0.
  - German: de_DE_frami, GPL v2 / v3 / LGPL (see README_de_DE_frami.txt).
  - Spanish: es_ES (GPL 3, LGPL 3, MPL 1.1, see LICENSE.md in that folder).
  - Portuguese: pt_BR (Raimundo Moura, LGPLv3); pt_PT (Jose Joao Almeida et al., LGPL-style, see its LICENSES.txt).
  - Russian: Alexander I. Lebedev, LGPL-style (see README_ru_RU.txt).
  - Ukrainian: based on the dict_uk project (https://github.com/brown-uk/dict_uk); the project describes its data as
    CC BY-NC-SA 4.0 and its software as GPL: **non-commercial restriction, unresolved**.
- **Hunspell dictionaries of the optional languages** (it nl sv da nb is pl cs sk sl hr hu ro el et lv lt ca; Finnish has
  none), also from LibreOffice/dictionaries, used only for vetting. A scan of their README / licence files found, per
  language, mentions of: GPL (it, nb, cs, sk, sl, hr, hu, ro, el, et, lv, ca), LGPL (sv, da, pl, sl, hr, hu, et, lv, ca),
  MPL (almost all), BSD (nl, lt), CC BY 3.0 (nl), CC BY 4.0 / public domain (is). That is a keyword scan, **not a legal
  reading**: each language's own licence text stays authoritative. The packs for these languages are not shipped yet.
- **hermitdave/FrequencyWords** (OpenSubtitles 2018 frequency lists): corroborating counts, CC BY-SA 4.0.
- **Aptivi-Analytics/WordsList** and **dwyl/english-words**: extra English vetting list.
- **Ashpex/Slang-Word**: English slang and acronyms (the repository is GPL-3.0).
- **Wiktionary** neologism and internet-slang categories (CC BY-SA 4.0).
- **hermitdave/FrequencyWords** is also the main candidate list for the optional languages wordfreq does not cover (da, is, sk, hr, et).
- **Tatoeba** sentences (CC BY 2.0 FR, attribution required): bigram probabilities.

Remember CC BY-SA "share alike" and attribution duties before distributing the app or the `.lex` files.
