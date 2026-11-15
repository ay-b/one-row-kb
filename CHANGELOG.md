# Changelog

Semantic versioning: MAJOR.MINOR.PATCH. The version lives in one place, `appVersion` in `app/build.gradle.kts`.
While MAJOR is 0 the app is pre-release. PATCH = bug fix, MINOR = new feature, MAJOR = breaking change (1.0.0 = first public release).
Built APKs are named `onerow-<version>-debug.apk`.

## 0.12.0
- One row: pulling up from the guess bar or from the bottom row (123, language key, comma, space, period, enter) expands the keyboard, like a straight swipe up on the letters. Sideways drags there (scroll guesses, move the cursor) are unchanged.

## 0.11.0
- New languages (26 in all): Italian, Dutch, Swedish, Danish, Norwegian, Finnish, Icelandic, Greek, Polish, Czech, Slovak, Slovenian, Croatian, Hungarian, Romanian, Estonian, Latvian, Lithuanian, Catalan. Their dictionaries are not in the app: a language whose dictionary is not installed is marked in settings and cannot be added (see docs/on-demand-dictionaries.md and docs/dictionary-countries.md).
- Settings: the languages are a list with a bin on each, and a dropdown with a + to add one.
- Smart quotes: " opens then closes (a closing quote sticks to the last symbol and adds a space); ' opens only after a space, otherwise it is an apostrophe; « and „ add a space before, » and " after.
- Optional dedicated number row on the full keyboard (Settings > Full keyboard). Holding a digit offers superscripts and fractions; with the row on, the top letter row carries Gboard's symbols (% \ | = [ ] < > { }).
- Swipe from the 123 key onto the keys: a temporary symbols layout (with currency signs on the third row); releasing types the key and goes back to the letters.
- The keyboard re-opens as it was left (one row or expanded). The space bar on the full keyboard is drawn like the other keys.
- Fix: holding a key that has nothing to offer on the full keyboard no longer shows a bubble of its neighbours.
- Fix: Russian, Ukrainian and Estonian full-keyboard layouts now follow Gboard (no ъ key, a ґ key, õ in the third row); Shift and Backspace are sized so the third row is as wide as the top row.

## 0.10.0
- Layouts: hold the language key and slide to one of three new icons next to the gear: normal (docked), floating (a window you can drag by its handle and resize by its corner, like Gboard's) and split (two halves with a gap, offered on tablets only).
- Floating keyboard: remembers its place and size; the lens, swipe shortcuts, swipe typing and the full keyboard work inside it.
- Split keyboard: the guess strip stays whole, the key rows fall apart into a left and a right half (the space bar is in both), and touches in the gap go to the app.
- The cheat sheet shows the layout icons and the floating window.

## 0.9.2
- Fix: the zoom bubble no longer flashes on every tap. It appears once the finger has rested for 150 ms (a tap is over before that) and is never shown during swipe shortcuts. Hold-and-slide (exact letter, with a buzz) still starts at 300 ms.

## 0.9.1
- Fix: the system navigation bar under the keyboard is painted in the keyboard's color (it was white on some phones, black on others), with matching icon contrast.

## 0.9.0
- Much bigger dictionaries, in every grammatical form: English 115k words, French 145k, German 330k, Spanish 168k, Portuguese 155k, Ukrainian 339k, Russian 446k (was 60-80k each). Words come from wordfreq and are vetted with Hunspell dictionaries; words two corpora agree on are kept too, and recent vocabulary (Wiktionary neologism and internet-slang lists, slang lists) is added.
- New dictionary format: a compact binary trie per language (`<lang>.lex`), loaded in 0.1-2 s instead of ~8 s and using a small fraction of the memory of the old text format. Words you add are kept in an overlay. The spell-checker service reads the same files.
- Tools: `tools/build_lexicon.py`, `tools/fetch_neologisms.py`; sources and licences are listed in `tools/dict_src/SOURCES.md` and `NOTICE-dictionaries.md` (licences still to be settled for a public release).

## 0.8.0
- The space bar is drawn as an outline only (no fill) in every theme. The emoji zoom shows a swipe-up hint ("^ all") for the full emoji palette. The settings screen has its own "Cheat sheet" section with a picture per theme, covering every swipe gesture.
- The zoom (hold-and-slide, tap preview, language key) is now a bubble floating above the finger with a pointer to the pressed key; the keyboard is dimmed and the pressed key stays lit. It no longer replaces the guess bar.
- Removed the shift-hold language / settings menu: the language key now does both (tap = next language, hold = settings).
- New: swipe typing (glide) on the full keyboard. Off by default; enable under Settings > Gestures. Draw a word across the letters; the best match is typed with a space and the other guesses appear in the guess bar (tap one to replace the word; backspace removes the whole word).
- With swipe typing on, going back to one row is a swipe down on the guess bar (it was a swipe down on the letters).

## 0.7.0
- New: OneRow can act as the system spell-checker (Settings > Languages & input > Spell checker). It accepts words from all enabled keyboard languages and your own words, so mixed-language text is no longer red-underlined.

## 0.6.3
- Reverted the per-language input-method subtypes from 0.6.2: they did not change which language the system spell-checker uses.

## 0.6.2
- The language key shows a small gear in its corner to hint that holding it opens settings.
- Fix: correct words were red-underlined by the host app. Each language is now an input-method subtype with its locale, kept in sync with the active language, so the spell-checker checks the right language.

## 0.6.1
- Tapping the typed (left) guess learns the word silently in every case, also when the word is no longer being composed. No prompts.

## 0.6.0
- New language key left of ",": tap cycles the enabled languages; hold zooms to the settings gear (release to open).

## 0.5.0
- Settings: "Guessing" tolerance (tight, normal, loose) controls how far a tap may miss a letter on the one-row keyboard and still count. Normal is the previous behavior.

## 0.4.1
- Guess bar: each guess gets a slot as wide as its word, so long words are no longer shrunk; the bar scrolls when they don't all fit, and keeps the best guess in view.

## 0.4.0
- Theme selector in settings: Default (the original slate and teal look), Light, Dark, and Auto (follows the system light/dark setting). The settings screen itself follows the system theme.

## 0.3.0
- Ukrainian (Українська): standard Ukrainian layout, dictionary and next-word prediction; hold г for ґ. Enable it in settings.

## 0.2.0
- Guess bar scrolls sideways when there are more than four guesses.
- Emoji lens on `,` ordered by use; swipe up from it opens a full emoji palette.
- `.` lens reordered so the marks are reachable.
- Cheat-sheet picture in settings; version shown in settings.
- Words typed with some held letters are kept as typed and learned; words still being typed when a field is left are learned.
- Tapping inside typed text keeps the caret where it was tapped.

## 0.1.0
- First working keyboard: one-row layout, hold-and-slide, swipe shortcuts, six languages, dictionary and prediction engine, learning and removal, Gboard-style full and symbol layouts, automatic space after punctuation.
