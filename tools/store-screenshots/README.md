# Play screenshots: how they were made

Phone screenshots for the store listing are captured on an emulator (1080 x 2400, Android 15, gesture navigation) with the small demo
notepad (`demo/notepad-app`, package `demo.notepad`) as the host app, so no personal data is ever on screen.

- `shotprep.sh <full:true|false> <lang> [langs]` : fresh start of the notepad with OneRow selected and the keyboard open (clears learned words).
  **Wait about 9 seconds before typing**: the dictionary loads in the background and early taps come out as raw letters.
- `typer.py row|full|ru|keypad "text"` : types by tapping (one-row column centres, expanded Latin, expanded Russian, calculator keypad). To open the
  keypad: expanded keyboard, tap 123 at (90, 2215), then the keypad icon at (222, 2215). The swipe-typing trail is `adb shell input motionevent` DOWN/MOVE
  along a smoothed path through the key centres (h e l o), screenshot while the finger is still down, then UP.
- `snap.py <name> [top]` : screenshot cropped to 1080 x 2160 (Play: the long side at most twice the short side) into `releases/store/screenshots/phone/`.

Needs Pillow for `snap.py` and `adb`. Coordinates are for this emulator's resolution; re-measure if the layout changes.
