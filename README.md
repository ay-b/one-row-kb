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
