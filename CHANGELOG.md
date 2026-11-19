# Changelog

Semantic versioning: MAJOR.MINOR.PATCH. The version lives in one place, `appVersion` in `app/build.gradle.kts`.
While MAJOR is 0 the app is pre-release. PATCH = bug fix, MINOR = new feature, MAJOR = breaking change (1.0.0 = first public release).
Built APKs are named `onerow-<version>-debug.apk`.

## 0.40.2
- Help: the folding section titles (One-row keyboard, Expanded keyboard, Nice features) are a little bigger and bolder than the other headers.

## 0.40.1
- The small hint on the Shift key is now x₂, the same sign as in its zoom bubble (it said "sub").

## 0.40.0
- Subscripts: hold Shift for half a second and pick the x₂ zoom; the digits, + - = ( ) and the letters that have a subscript form (a e h i j k l m n o p r s t u v x) are then typed as subscripts (H₂O, xₙ), every other key as usual. Tap Shift again or the space bar to go back to normal; The Shift key carries a small x₂ hint (not in Hebrew, where Shift switches to English) and shows a small ₂ while the subscripts are on. Swipe typing is off meanwhile.
- The zoom of every digit key (hold 1 ... 0) now ends with the matching subscript digit (₁ ... ₀), after the superscripts and fractions.

## 0.39.0
- Space key: hold it still for half a second and a "?" zoom appears; release (or slide onto it and release) to open the app's Help page. A small hint in the key's up-right corner (where the other keys have theirs) says "hold for help" (translated into the 25 other languages, from memory and not checked by native speakers); it is only drawn where it fits. A tap and the slide for the cursor work as before.

## 0.38.0
- Swipe typing: when a swiped word was guessed wrong, tap into the word at any time (also after typing on) and the guess bar offers the other readings of the swipe path (The, Three, Thee, There ...) instead of only completions of its letters. Picking one swaps the word and the list stays, so you can go back. The last 120 swiped words are remembered; a word that was typed, not swiped, behaves as before.

## 0.37.0
- Hebrew: Shift is now the switch to the English layout. Tap it and the English layout (lower case, no automatic capitals) takes Hebrew's place and stays, with the Shift key lit, until Shift is tapped again and Hebrew returns. It stays across text fields; the language key still moves on to the next language as usual. Hebrew has no capitals, so Shift had nothing else to do there (the number row's symbols on Shift are therefore not available in the Hebrew layout), and Hebrew no longer shows Shift lit at the start of a sentence.

## 0.36.0
- One-row keyboard: picking a letter with the zoom (hold and slide) now turns the guessing off for that whole word: what you typed stays as typed (the zoomed letter exactly, the other letters as the nearest key) and nothing is corrected. The guesses are still offered in the bar to tap. After the space the next word guesses again. Before, the guess replaced the word unless every letter had been zoomed.

## 0.35.1
- Russian and Ukrainian: every vowel offers its stressed form when held on the expanded keyboard (а́ е́ и́ о́ у́ ы́ э́ ю́ я́; Ukrainian also і́ є́ ї́), written as the letter plus the combining acute accent. Russian е still offers ё first. A variant can now be a letter with a combining mark.

## 0.35.0
- Accented vowels: on the expanded keyboard every vowel of a Latin-script language (also English) now offers the common accented forms when held: a à á â ä æ ã å ā, e è é ê ë ē ė ę, i ì í î ï ī į, o ò ó ô ö œ ø õ ō, u ù ú û ü ū, y ý ÿ. The language's own letters stay first (French é è ê ë, Spanish á, German ä ...), so releasing straight away still types what it did before; the rest follow and the list scrolls. A form that is already a key of the language (å in Swedish) or offered under another letter is not repeated. Cyrillic, Greek and Hebrew are unchanged.

## 0.34.1
- Fix: Shift pressed in the middle of a word is no longer ignored. The letter tapped with Shift down is typed as a capital (iPhone, McDonald, AbCd) in the word being typed and in the guesses; before, the Shift was used up by the tap and the letter came out lower case. Backspace keeps the capitals and the letters in step.

## 0.34.0
- Number row: Shift turns the digits into their PC (US) symbols, `1 2 3 4 5 6 7 8 9 0` -> `! @ # $ % ^ & * ( )`. Tap Shift for one symbol, double-tap to keep them (caps lock). Only Shift pressed on purpose does it: the automatic capital at the start of a sentence leaves the digits alone. Holding a symbol key gives no zoom; without Shift everything is as before.

## 0.33.0
- Password managers (1Password, Bitwarden, Google, Proton Pass and any other Android autofill service): on Android 11 and newer their suggestions now appear in the guess bar of the keyboard, in the keyboard's colors, like on Gboard. Tap one to fill the login. The suggestions go away as soon as you type or press backspace. In the one-row layout the shift and backspace keys beside the bar stay usable. Floating layout and Android 10 and older keep the system's own dropdown. Needs the password manager's "inline suggestions" option, where it has one (Android's autofill settings).
- Library added: androidx.autofill (Apache 2.0), used only to style the suggestions.

## 0.32.1
- Fix (Hebrew swipe typing): a word that ends in a final letter was missed when the finger ended on the final key (ם ן ך ף), because the swipe was compared with the path to the plain letter's key (מ נ כ פ). The final-form keys are now where such a word's path ends (ending on the plain key still works too). In a test of 400 common final-ending words, 3% were found in the top three before and 99% now.

## 0.32.0
- New language: Hebrew (עברית). Add it under Settings > Languages. Both layouts follow the standard Israeli keyboard (the letters keep their physical places, not mirrored): the one-row keyboard has 22 letters in 9 columns, the expanded keyboard has the usual three rows plus `/` and `'` (for ג'ינס, צ'ק).
- Final letters (ך ם ן ף ץ): the guesses and the dictionary know them, a plain letter at the end of a word becomes its final form by itself (type מ, get ם), and they are also keys / hold-alternates (hold כ for ך, מ for ם, נ for ן, פ for ף, צ for ץ). Guessing and swipe typing work the same as in the other languages; the dictionary has 350,000 words and 23,000 word pairs.
- ₪ is the national currency sign on the symbol keys in Hebrew.
- The Hebrew word list is vetted against the Hspell dictionary (AGPL 3, used only as a filter and not shipped; credited in Settings > About).
- Fix: the next-word fallback words (I, the, and, to) are only used for English; other languages show only real guesses.
- The language list shows languages named in Hebrew left-aligned like the rest.
- The OneRow spell checker also offers Hebrew to the system.

## 0.31.7
- Fix (Samsung, gesture navigation): the keyboard slid down under the gesture handle. Phones that give the keyboard no bottom inset now fall back to the height the system reserves for the current navigation mode, and the inset ignores a bar that is momentarily hidden. Phones that deliver the inset are unchanged.

## 0.31.6
- Emoji search: the magnifier and the x in the search bar are about half their size, in proportion to the search text (the x still closes the search from a generous touch area).

## 0.31.5
- 123 pages: choosing a symbol in the hold-zoom (hold a key, slide, release) now returns to the letters (abc) afterwards, like sliding from 123 does. A plain tap still stays on the page, and the calculator keypad is not affected.

## 0.31.4
- Emoji search: swipe typing works in the search field (the best word of the drawn path is added to the search, with a space between words); nothing is typed into the text.

## 0.31.3
- Emoji palette: floats in from the screen edges (14 dp each side) with a little inner padding, and its background is the keyboard's own panel color.
- Emoji search: the keyboard icons (shift, backspace, enter, language and emoji marks) no longer blink in step with the search cursor: the search field now leaves the shared drawing paints as it found them.

## 0.31.2
- Russian and Ukrainian word lists: "том" (and its look-alike function words) is stored in lower case, so it follows the capitalization rules (capital at the start of a sentence, lower case in the middle) instead of always being offered as "Том". Changed, in both: Том, Тому; Russian also: Ну, Ой, Ага, Ох, Ах, Ха, Ого, Эй, Ай, Хм, Итак, Впрочем, Неужели, Интернет; Ukrainian also: Томів, Самі, Вибач, Нумо. Names that are also words (Тома, Бог, Союз ...) are left as they were. Done in the .lex files in place (same byte sizes) and in tools/dict_src/<lang>_casing.tsv, so a rebuild gives the same result.

## 0.31.1
- Help tab: the "Try it" field is no longer hidden behind the keyboard (Android 15+ ignored the resize): the page now ends above the keyboard and scrolls the field into view, also with the full-size keyboard.

## 0.31.0
- Full emoji palette: one column narrower, so it keeps a margin to the screen edges, with rounded upper corners.
- Full emoji palette: search. While the palette is open the guess bar turns into a search field (magnifier, blinking cursor, an x at the right end that closes the search and brings the guess bar back). A one-row keyboard switches to the expanded one meanwhile (exact letters) and returns afterwards. The keys type into the search, not the text; backspace edits it; the palette shows the emoji whose English Unicode name has a word starting with each word you typed ("tear", "face tear", "heart"), plus a few everyday words ("lol", "love", "thanks"). Tapping an emoji types it and closes the search.
- Full emoji palette: the missing symbols block (fire, bell, lock, arrows-and-signs emoji and more) is in the list now, so e.g. 🔥 is found.

## 0.30.0
- Help tab: the cheat sheet is split into three sections that fold away by tapping the title (one-row keyboard open, the others closed): One-row keyboard, Expanded keyboard and Nice features. The emoji and floating-keyboard entries moved to Nice features, and the legend numbers were renumbered to run in the order shown.
- Help tab, Nice features: new calculator pictures and legends (the keypad key on the 123 page, the sum with its result and the undo key, and adding up selected numbers). 3 new pictures per theme, 7 new texts in all 26 languages (translations from memory, not native-checked).
- The picture tools (tools/cheatsheet_shots.py) also take the calculator shots.
- Help tab, "Try it" field: fixed its background (it was filled bright accent blue since 0.29.0, hiding the text).

## 0.29.4
- Help tab, floating keyboard: the two legend lines now match the picture and the real behaviour (drag the bar under the keys to move; tap the crossed-arrows mark next to the globe to show the corners, then drag a corner to resize). Updated in all 26 languages (translations from memory, not native-checked).

## 0.29.3
- Help tab: all 33 cheat-sheet pictures retaken from the current keyboard (even thin zoom-bubble outline, current floating-keyboard bar, current letter bubble), in all three themes. The picture-making tools (tools/cheatsheet_shots.py, tools/cheatsheet_host) are now in the repository.

## 0.29.2
- Zoom bubble (hold a key): the outline is now the same thin width all the way round (the left, top and right edges were half-clipped, the bottom and the tail were not).

## 0.29.1
- Help tab: the cheat-sheet pictures no longer have a dark frame around them; they are transparent around the rounded card, so the page's own theme color shows through.

## 0.29.0
- The settings, help, about and crash-report pages and their dialogs now look like the keyboard: the chosen keyboard theme's colors (they change at once when you pick a theme) and the keyboard's font, rounded key-like buttons, matching action bar, tab bar and status-bar icons.

## 0.28.1
- Settings tabs: fixed a large empty gap above the tab bar on some phones.

## 0.28.0
- The settings page is split into three tabs: Settings, Help (gesture cheat sheet and the "Try it" field) and About (version, crash reports, attributions).
- Release folder no longer keeps R8 mapping files (the app bundle already carries the mapping for Google Play).

## 0.27.1
- Settings > Languages: the language to add is chosen with a button that shows the chosen language as plain text (tap it for the list) instead of the system dropdown, which drew its selected item invisibly on some devices (Samsung Galaxy Fold, unfolded).

## 0.27.0
- One-hand ("side-shift") keyboards on tablets: horizontal scaling. In the scaling mode (language key zoom > the arrows icon) a sideways drag makes the keyboard narrower or wider (35% to 95% of the screen width, the free edge follows the finger); up / down still changes the height, the first clear direction of a drag decides. The width is remembered.

## 0.26.5
- Foldables / tablets: the strip under the keys (gesture handle) no longer depends only on the insets the system hands to the keyboard. On large screens the keyboard also reads the display's gesture strip and the system's gesture-navigation height and keeps clear of the largest of them. Phones are unchanged.

## 0.26.4
- Foldables: the split / one-hand layouts are now offered, and the keyboard re-arranges itself, when the phone is unfolded after the keyboard started (the screen size was only read once, at start, and kept the folded value). Screen size for these layouts is read from the display as it is now.
- Bottom strip: besides the navigation bar and the gesture strip, the keyboard now also keeps clear of whatever else the system marks as tappable at the bottom (the large-screen taskbar), and asks for fresh insets when the screen changes.

## 0.26.3
- The lifetime purchase button now says "Buy this major version forever" (with the price, or "price not available yet"), in all 26 languages; the "(version 1)" in the label is gone since "this major version" says it.

## 0.26.2
- Settings: the Spell checker section (explanation and the "Use the OneRow spell checker" button) moved to the top, right under the two Setup buttons, with a new note: if OneRow is not chosen, Android's own checker underlines in red every word that is not in its single language. The note is translated into all 25 languages.

## 0.26.1
- National currency: in the euro languages (French, German, Italian, Dutch, Finnish, Greek, Slovak, Slovenian, Croatian, Estonian, Latvian, Lithuanian, Catalan) and in Russian (₽) and Ukrainian (₴) the symbol keys show the national currency sign instead of `$` (the small hint on D, the key on the symbols page, the first sign of the currency row). Holding it offers that sign first, then `$ ¢ £ € ¥ ₱`. English, Spanish, Portuguese and the languages whose currency has a longer name (kr, zł, Kč, Ft, lei) keep `$`.

## 0.26.0
- Split keyboard (tablets): horizontal scaling. In the scaling mode (language key zoom > the arrows icon) a sideways drag makes the two halves, and so the keys, narrower (down to half); dragging up or down still changes the height. The first clear direction of a drag decides which one. The width is remembered. Each half shows a horizontal arrow while scaling.

## 0.25.10
- Floating keyboard: a small crossed double-arrow mark on the bottom bar (between the handle and the globe) shows that tapping there toggles resizing (the corner grips); it lights up while resizing is on. A narrow window squeezes the handle to make room.

## 0.25.9
- Split keyboard (tablets): the two halves keep the same key size in portrait and in landscape. In landscape the gap between the halves is simply wider (before, the keys grew with the screen width).
- Floating keyboard: the vertical-scaling button is now also in the language key's zoom (drag down = taller, up = lower; the check mark ends it). The size is remembered like the corner drags.

## 0.25.8
- Swipe typing: the guess bar after a glide no longer repeats the word that was just typed; it lists only the other, less certain guesses (tap one to replace the word).

## 0.25.7
- Russian and Ukrainian full keyboard: `%` is now the small hint on the ю key; hold ю to type `%` (or slide to `‰`).

## 0.25.6
- Removed the `WRITE_SECURE_SETTINGS` permission and the adb instructions. The Spell checker section now has one button that opens Android's own spell-checker screen with a plain hint ("Pick OneRow Keyboard on the screen that opens"); the "Turn spell checking off" button and its toasts are gone (Android's screen does that). New hint translated into all 25 languages. Privacy policy draft updated.

## 0.25.5
- Removed the alternative "guess bar" icon (it looked like a face): its files, the generator entry and the unused copy that was kept inside the app. The app icon is unchanged (one row of keys). The Play feature graphics are rebuilt around the real app icon.

## 0.25.4
- Fix (since 0.23.2, which moved to target SDK 35/36): on Android 15+ the system draws the navigation buttons / home-gesture zone over the bottom of the keyboard. The keyboard now keeps that strip clear and paints it in its own color, so the hide-keyboard and switcher buttons are back below the keys, no key sits under the system controls, and a swipe along the space bar no longer starts the app switcher. Same for the bottom of the Settings page. Older Android (which reserves the strip itself) and the floating keyboard are unchanged.

## 0.25.3
- The app icon is now the "one row" design (a single row of keys). The "guess bar" icon is kept in the app (not selectable) and in `icons/`.

## 0.25.2
- The cheat sheet is no longer one tall picture with a legend at the very end: it is 11 short pictures, each followed right away by only the legend lines for the numbers drawn on it, so a picture and its explanation are always on screen together. `tools/make_cheatsheet.py` now writes `cheatsheet_<theme>_<n>.png` (11 per theme).

## 0.25.1
- New app icon: a guess bar over a row of keys (adaptive icon with a themed-icon layer for Android 13+, plain PNGs for Android 7). The alternative "one row" icon is kept in the app (not selectable) and both designs, as SVG and 512 px PNG, are in the `icons/` folder. Rebuild everything with `tools/make_icons.py`.

## 0.25.0
- New: crash reports. When the keyboard crashes it saves a small report on the phone (app, Android and phone model, language, time, and the technical trace: class names and code locations only, never messages or anything typed). Shake the phone while the keyboard is open to see it: a short explainer, the first 300 characters with "Show all", and a Send button that opens the mail app addressed to apps@y-b.one. The app still has no internet permission; nothing leaves the phone unless the user sends the email. Settings has a switch for the shake and a button that opens the same screen.
- New: the crash report screen is translated like the rest of the UI (the report text itself stays English).

## 0.24.2
- Ukrainian full keyboard: the small "?" moved from ь to б, like in Russian (ь has no small symbol).

## 0.24.1
- Russian full keyboard: the small "?" moved from ь to б, and ь now shows ъ as its small symbol (hold ь still types ъ, no duplicate in the zoom).

## 0.24.0
- New: the interface (settings, toasts, the lock and countdown messages) speaks the phone's language in 25 languages (Android resources, `res/values-<code>/strings.xml`); every other language falls back to English. The translations were written without a native speaker: please have the billing and lock texts checked.
- The cheat sheet picture has no words any more: numbers on the arrows, and the numbered legend is text under the picture, so it is translated too. Rebuild the picture with `tools/make_cheatsheet.py`.
- First start: if the phone's language is one the keyboard supports and has a dictionary for, it is added at once and the keyboard opens in it (English stays).
- French is in the Play bundle (en, ru, uk, fr).
- `StringsTest` keeps every translation complete: same keys, same placeholders, the plural forms each language needs.

## 0.23.3
- Play Billing Library 8.0.0 (Play requires 8+); target and compile SDK 36 (Play requires 36).

## 0.23.2
- Play bundle: release build type (R8, upload-key signing from `~/.android/onerow-upload.properties`, outside the repo) so `./gradlew :app:bundleRelease` makes an `.aab`; target and compile SDK raised to 35, which Play requires for new apps.

## 0.23.1
- A cancelled subscription: "Subscription expiring in N days" now replaces the next-word guesses before anything is typed (it showed only when the bar was otherwise empty).

## 0.23.0
- New: payments through Google Play. The first 14 days after installing are free; then start the subscription's 1-month free trial, subscribe (EUR 1 a month, auto-renewing) or buy forever for version 1 (EUR 10). Settings has a new Subscription section.
- When the trial is over the keyboard locks: plain typing only (no gestures, zoom, swipe typing, keypad, language switching, layouts); the guess bar says "Trial has expired, please subscribe" with a gear that opens the settings. A cancelled subscription keeps working to the end of its period and counts the days down in the guess bar before anything is typed.
- The application id is now one.yb.onerow (a new app for Android: settings and learned words do not carry over from com.example.onerow).
- Credits and licences: attribution.md (also shown under Settings > About), NOTICE-dictionaries.md settled. See docs/billing.md for the Play Console setup.

## 0.22.3
- Fix: a line that starts with numbers (typed after Enter) no longer capitalizes the first word after them; the digits use up the automatic capital.

## 0.22.2
- Fix: the keyboard could crash (and vanish) when the text under the caret changed while the guess bar was being built, as happened with brackets and an active selection.

## 0.22.1
- Adding up a selection: after the result or the expression is dropped into the text, the keyboard goes back to the letters.

## 0.22.0
- New: adding up a selection. Select text that holds at least two numbers and a small keypad icon pulses at the right end of the guess bar. Tap it: the keypad opens with the numbers added up (a number with a minus, like -5, is taken as subtraction: "25 for a kg with -5 discounted" is 25-5); numbers follow the regional settings (1,234.5 / 1.234,5 / 1 234,5). Keys edit that expression; the field is not touched. Tapping the result puts the result after the selection, tapping the expression (or "=" or Enter) puts "expression=result" there. Changing the selection ends it.

## 0.21.3
- Keypad: a held key's zoom shows only the extra symbols (just ")" on the period key), not the key's own value; the first one is highlighted, so hold and release types it.

## 0.21.2
- Keypad: the `,` and `.` keys show a small ( and ) in their corner, the symbols you get by holding them.

## 0.21.1
- Trimmed back: on the keypad the only hold-and-slide additions are the brackets: hold `,` and slide to `(`, hold `.` and slide to `)`. Nothing on the other keys. The calculator understands brackets, nothing more.

## 0.21.0
- Keypad: hold-and-slide lists and brackets in the calculator. A ")" typed with no "(" before it adds the "(" by itself: after the previous ")" and its operator ((1+2)*3+4) becomes (1+2)*(3+4)), else at the start of the expression (2+3) becomes (2+3)).

## 0.20.0
- Keypad follows the caret: put the caret in or at the end of an arithmetic expression in the text (not a lone number) and the keypad opens by itself with the expression and its result in the guess bar; move the caret away and it closes again (a keypad you opened yourself stays). Tapping the expression or result replaces the whole expression around the caret.
- Keypad: with the caret at the end of a lone number, typing a digit or an operator continues that number (23, then 3 gives 233; 23, then + gives 23+ with its result).

## 0.19.1
- Keypad: an undo key right of the space bar takes back the last replacement (tapped expression or result, or the "=" key) and puts the typed expression back in the field, so you can keep editing it. It is dim when there is nothing to undo.

## 0.19.0
- New: keypad. On the first 123 page (full keyboard) the language key is now a keypad key (a small 3x3 grid): it opens a numeric pad with digits, a math column (+ - * /), and backspace, %, = and enter beside it. "123" on the pad goes back; Enter leaves to the letters.
- New: calculator. While you type on the keypad and the text makes arithmetic sense, the guess bar shows the typed expression with "=" and the current result. Tap the result to replace what you typed with it; tap the expression to replace it with "expression=result". % works like a phone calculator (200+10% is 220); a trailing operator is ignored; the "=" key types "=" and the result.

## 0.18.0
- Guesses: the bar tries to offer different words, not only more forms of the same word (walk, walked, walking; красный, красная); the extra forms come after the others. Swipe typing asks for twice as many candidates to choose from.
- Russian: a word with ё is recognized and offered before its е spelling (всё before все, ещё before еще); the е form stays as the variant.
- Choosing another guess for a swiped word (or another word of the same length for tapped letters) is remembered: it becomes the first guess the next time, and the choice is kept between sessions.

## 0.17.6
- Fix: with the caret inside a word, the guesses are for the whole word and a tapped guess replaces all of it (it used to replace only the part before the caret and leave the rest behind).

## 0.17.5
- On the 123 pages, Enter now switches back to the letters.

## 0.17.4
- Picking one of the guesses for a swiped word now returns the guess bar to next-word predictions (the alternatives used to stay until the next input).

## 0.17.3
- Fix: after deleting a line break (backspace over Enter) the next word is no longer auto-capitalized; capitals resume once something else is typed.

## 0.17.2
- Zoom bubbles: the scroll chevron is hidden (not dimmed) on a side where the list has already reached its end.

## 0.17.1
- Fix: automatic capital letter only at the start of a field or after a finished sentence (. ! ? … or a new line); no longer after %, :, ;, digits or commas. Fields that ask for capitalized words (names) are unchanged.
- The Height slider is gone from the settings; the vertical-scaling control (hold the language key) replaces it.

## 0.17.0
- New setting "Swipe typing precision" (Tight / Normal / Loose), next to the one-row guessing one. Loose forgives a rough curve: in a simulation with sloppy curves it finds the intended word in the top three 43% of the time against 25% for Normal, and stays at 99% on careful curves. Normal is the old behaviour.

## 0.16.1
- Fix: the zoom on keys near the right edge (the "0" of the number row, for one) could not reach the values to the right of the key: there was no room to slide right. Such lists are now walked leftwards, and the bubble is mirrored to match.
- Zoom bubbles that hold more than they show now have a chevron at each side, bright on the side where the list continues.

## 0.16.0
- New: proofreading. Turn on "Proofread button" in the settings and a small teal check circle appears at the right end of the guess bar. Tap it to have the whole field corrected (grammar and spelling); the circle turns into an undo arrow for 10 seconds. English, French, German, Italian and Spanish. It uses Google's on-device AI (ML Kit GenAI proofreading, Gemini Nano through AICore): nothing leaves the phone, the app gets no internet permission of its own, and the switch is greyed out on phones that do not have it.

## 0.15.6
- Settings: the notes under the spell-checker buttons are gone (messages show as a short toast instead), and every button on the page has the same flat look.

## 0.15.5
- Fix: the floating keyboard sometimes did not come up when called. It is shown only once the input view is really up, retried for about a second if the system is not ready, rebuilt if the system dismissed its window, and also requested when a field starts input. (Logged under the tag OneRowFloat if it still fails.)

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
