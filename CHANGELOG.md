# Changelog

Semantic versioning: MAJOR.MINOR.PATCH. The version lives in one place, `appVersion` in `app/build.gradle.kts`.
While MAJOR is 0 the app is pre-release. PATCH = bug fix, MINOR = new feature, MAJOR = breaking change (1.0.0 = first public release).
Built APKs are named `onerow-<version>-debug.apk`.

## 0.15.4
- Floating keyboard: no bar under the screen any more. The system's "hide keyboard" and "switch input method" buttons are hidden and the strip is transparent while the keyboard floats. The globe (switch input method) moved onto the floating keyboard's bar, at the right.

## 0.15.3
- Floating keyboard: the "v" (hide) sign is 40% smaller and sits half as far from the left edge; the resize corners are a little smaller so they do not cover it.

## 0.15.2
- The keyboard is direct-boot aware: its settings and files now live in device-protected storage (existing data is moved over on the first unlocked start), so Android no longer warns "won't launch until the device is unlocked after reboot" when it is enabled, and the keyboard works on the lock screen right after a reboot.

## 0.15.1
- Floating keyboard: tap the bar under the keyboard to switch resize mode on or off (the corners show only then, so they no longer sit over keys; the bar's pill turns teal while it is on).
- Fix: both left corners resized the wrong way (they were treated as right corners).

## 0.15.0
- Spell checker: a new "Spell checker" section in the settings. "Use the OneRow spell checker" (knows all the languages you added, so words in another language are not red-underlined) and "Turn spell checking off". Android lets an app change this only with a one-time permission (`adb shell pm grant com.example.onerow android.permission.WRITE_SECURE_SETTINGS`); without it the buttons open the system's spell-checker screen and say what to do. All 26 languages are now offered to the system by the checker, not only the first seven.

## 0.14.0
- Fix: after ":" or ";" the next word is no longer capitalized by the system's sentence rule.
- Fix: split layout no longer leaves a see-through hole between the halves; the gap is keyboard-colored.
- New (tablets): "left" and "right" layouts, a smaller keyboard pushed to one side of the full-width input view, for typing with one hand. Hold the language key and slide to the new icons.
- New: vertical scaling. Hold the language key, slide to the arrows icon, then drag up or down anywhere on the keyboard; the check mark ends it. Key labels scale with the height. The value is the same as the Height setting.
- Floating keyboard: the handle moved under the space bar, with a "v" at its left that hides the keyboard, and every corner resizes (width and height, labels scale). Its height is remembered separately from the docked one.

## 0.13.0
- With the dedicated number row on, the "123" pages of the full keyboard no longer shrink the keyboard: page 1 gets a row of currency signs, page 2 keeps a row of digits. (Without the number row nothing changes.)

## 0.12.4
- Faster start: the keyboard is fully ready (guesses and swipe typing) about 2.1 s after a cold start with Russian, was 4.4 s. The swipe index is built in parallel with the bigrams, its sort no longer boxes, letter lookup no longer allocates, and word lookup skips string copies.

## 0.12.3
- Startup timing is logged (tag OneRowStartup: input view build, swipe warm-up, time to ready) next to the existing OneRowEngine / OneRowGlide timings, for comparing builds.

## 0.12.2
- Build: R8 code and resource shrinking is on (experiment). No behaviour change intended.

## 0.12.1
- Fix: ":" and ";" typed after a space you pressed yourself no longer take that space back (it is probably the start of a smiley, " :)"). They still take back the automatic space after a swiped word or a picked guess; "." "," "!" "?" are unchanged.
- Fix: hyphenated words (когда-то, что-нибудь, из-за, по-русски, well-known, mother-in-law) were missing from the dictionaries, so swipes could not find them. They are in now: Russian +975, English +6,065, Ukrainian +48. (The other languages get them when their dictionaries are rebuilt with tools/build_lexicon.py.)

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
