# OneRow Keyboard

An Android keyboard in the spirit of Minuum: one row of small letter keys, with the work done by prediction. You tap
sloppily, the keyboard guesses the word. Personal project, pre-release (see `CHANGELOG.md`); not affiliated with Minuum.

- **One row, sloppy taps:** a beam search over a dictionary trie with a spatial tap model picks the word; the guess bar offers alternatives.
- **Hold and slide** on a letter to type exactly that letter; **swipe shortcuts** on the letter row (delete, space, enter, full keyboard); a zoom bubble above the finger.
- **Full keyboard** with optional **swipe typing** (off by default).
- Punctuation and emoji "lenses" (hold `.` or `,`), a full emoji palette, learned and removable words, themes (default, light, dark, auto).
- Seven languages: English, French, German, Spanish, Portuguese, Russian, Ukrainian.
- Optional system spell-checker that knows all enabled keyboard languages at once.
- Layouts: normal, floating (drag and resize) and split (tablets): hold the language key and slide to the icon.

## Build

JDK 17 and the Android SDK are needed (set `ANDROID_HOME` or `sdk.dir` in `local.properties`).

```
./gradlew assembleDebug testDebugUnitTest
```

## Dictionaries

The compiled word lists (`app/src/main/assets/dict/<lang>.lex`) are **not** in this repository, because the licences of
their sources are not settled (`NOTICE-dictionaries.md`). The tests and the app need them: build them with
`tools/build_lexicon.py`; sources, commands and the rules used are in `tools/dict_src/SOURCES.md`.
The bigram files (`*_bi.tsv`, from Tatoeba, CC BY 2.0 FR) are included.

## Layout

- `app/src/main/java/com/example/onerow`: keyboard view, input method service, spell checker, settings.
- `app/src/main/java/com/example/onerow/engine`: dictionary, decoder, swipe-typing decoder.
- `tools/`: dictionary and cheat-sheet generators. `demo/`: a small notes app and scripts used for the demo video.

## Attribution

The word lists and bigrams are built from the following open data. Thank you to everyone behind it.

- **wordfreq** by Robyn Speer et al. (https://github.com/rspeer/wordfreq), word frequencies, CC BY-SA 4.0.
- **Hunspell dictionaries** from LibreOffice/dictionaries (https://github.com/LibreOffice/dictionaries), used to check
  words. Authors and licences per language: English (SCOWL), French (Grammalecte, Olivier R.), German (de_DE_frami),
  Spanish, Portuguese (Raimundo Moura; Jose Joao Almeida et al.), Russian (Alexander I. Lebedev), Italian, Dutch, Swedish, Danish, Norwegian, Icelandic, Polish, Czech, Slovak,
  Slovenian, Croatian, Hungarian, Romanian, Greek, Estonian, Latvian, Lithuanian, Catalan. Each folder in that
  repository carries its own README and licence.
- **FrequencyWords** by hermitdave (https://github.com/hermitdave/FrequencyWords), built from OpenSubtitles, CC BY-SA 4.0.
- **Tatoeba** sentences (https://tatoeba.org), CC BY 2.0 FR, for the bigram files.
- **Wiktionary** neologism and internet-slang categories (https://en.wiktionary.org), CC BY-SA 4.0.
- **dwyl/english-words** (https://github.com/dwyl/english-words), **Aptivi-Analytics/WordsList**
  (https://github.com/Aptivi-Analytics/WordsList) and **Ashpex/Slang-Word** (https://github.com/Ashpex/Slang-Word):
  extra English word and slang lists.

Details and exact sources: `NOTICE-dictionaries.md` and `tools/dict_src/SOURCES.md`.
