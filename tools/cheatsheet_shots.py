#!/usr/bin/env python3
"""Retakes the raw keyboard screenshots for the gesture cheat sheet on emulator-5556 (1080 x 2400, density 420, gesture navigation).

Host app: a blank page in the theme's card color with an invisible text field (source in tools/cheatsheet_host/, package blank.host),
so nothing but the keyboard (and its zoom bubbles) is on screen. Usage, from the project root, with Pillow available:
    python tools/cheatsheet_shots.py RAW_DIR [theme ...]       -> RAW_DIR/<theme>_<shot>.png  (mini letter punct emoji lang bucket float fullidle trail peek)
then:  python tools/crop_phone_shots.py RAW_DIR  and  python tools/make_cheatsheet.py
Coordinates are for this emulator's resolution; if the layout changes, re-measure (see tools/store-screenshots/README.md)."""
import subprocess, sys, time, os, io, tempfile
from PIL import Image

E = "emulator-5556"; APP = "one.yb.onerow"
CARD = {"default": "#262F38", "dark": "#26292C", "light": "#ECEEF1"}      # the cheat-sheet card colors (make_cheatsheet.py)

def adb(*a, out=False):
    r = subprocess.run(["adb", "-s", E, *a], capture_output=True)
    return r.stdout if out else None
def sh(cmd): adb("shell", cmd)
def tap(x, y): sh(f"input tap {int(x)} {int(y)}")
def hold(x, y, ms): sh(f"input swipe {int(x)} {int(y)} {int(x)} {int(y)} {ms}")
def snap(path):
    im = Image.open(io.BytesIO(adb("exec-out", "screencap", "-p", out=True))).convert("RGB"); assert im.size == (1080, 2400), im.size
    im.save(path); print("  wrote", path)
def motion(kind, x, y): sh(f"input motionevent {kind} {int(x)} {int(y)}")

def prep(theme, full=False, layout="docked"):
    xml = ('<?xml version="1.0" encoding="utf-8" standalone="yes" ?><map><string name="layout">%s</string><string name="langs">en,ru</string>'
           '<string name="cur">en</string><string name="theme">%s</string><boolean name="full_keyboard" value="%s" />'
           '<boolean name="swipe_typing" value="true" /></map>') % (layout, theme, "true" if full else "false")
    with tempfile.NamedTemporaryFile("w", suffix=".xml", delete=False) as f: f.write(xml)
    adb("push", f.name, "/data/local/tmp/onerow.xml")
    sh("am force-stop blank.host"); sh(f"am force-stop {APP}")
    sh(f"run-as {APP} sh -c 'rm -f /data/user_de/0/{APP}/files/user_*.txt /data/user_de/0/{APP}/shared_prefs/onerow_emoji.xml; cp /data/local/tmp/onerow.xml /data/user_de/0/{APP}/shared_prefs/onerow.xml'")
    sh("settings put secure stylus_handwriting_enabled 0"); sh("input keyevent KEYCODE_HOME"); time.sleep(1)
    sh(f"ime enable {APP}/com.example.onerow.OneRowService"); sh(f"ime set {APP}/com.example.onerow.OneRowService")
    sh(f"am start -n blank.host/blank.Main --es bg '{CARD[theme]}'"); time.sleep(3)
    tap(540, 700); time.sleep(9)                                  # the dictionary loads in the background: early taps come out as raw letters

def bg_snap(path, delay):
    """Screenshot while a finger is held down (the caller holds in the foreground)."""
    time.sleep(delay); snap(path)

def type_row(text):
    cols = {"qaz": 88, "wsx": 206, "edc": 324, "rfv": 441, "tgb": 559, "yhn": 677, "ujm": 795, "ik": 892, "ol": 970, "p": 1032}
    pos = {c: x for k, x in cols.items() for c in k}
    for ch in text: tap(pos[ch], 2140); time.sleep(0.25)

LET_Y = 2140; CTL_Y = 2228

def one_row_shots(raw, theme):
    p = lambda n: f"{raw}/{theme}_{n}.png"
    prep(theme)
    snap(p("mini"))
    # exact letters: hold H, the bubble comes up with the finger resting
    import threading
    def held(x, y, ms, name, wait=1.8):
        t = threading.Thread(target=hold, args=(x, y, ms)); t.start(); time.sleep(wait); snap(p(name)); t.join()
        time.sleep(0.6)
    held(677, LET_Y, 3200, "letter")
    prep(theme)                                                        # releasing the held H typed it: start clean
    tap(677, LET_Y); time.sleep(0.8)                                   # type "H"
    held(858, CTL_Y, 3200, "punct")
    prep(theme)
    held(328, CTL_Y, 3200, "emoji")
    prep(theme)
    held(222, CTL_Y, 3200, "lang")
    prep(theme)
    type_row("hello"); time.sleep(0.8)
    held(448, 2049, 3200, "bucket")            # the second slot is the best guess "Hello"

def full_shots(raw, theme):
    import threading
    p = lambda n: f"{raw}/{theme}_{n}.png"
    prep(theme, layout="floating"); snap(p("float"))
    prep(theme, full=True); snap(p("fullidle"))
    # swipe typing: a finger trail through the keys of "hello" (screenshot while the finger is still down)
    pts = [(646, 1993), (275, 1883), (964, 1993), (911, 1883)]
    motion("DOWN", *pts[0])
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        for k in range(1, 9): motion("MOVE", x0 + (x1 - x0) * k / 8, y0 + (y1 - y0) * k / 8); time.sleep(0.03)
    time.sleep(0.3); snap(p("trail")); motion("UP", *pts[-1])
    prep(theme, full=True)
    # symbols from 123: slide from 123 onto the currency row
    motion("DOWN", 90, 2215); time.sleep(0.4)
    for k in range(1, 9): motion("MOVE", 90 + (276 - 90) * k / 8, 2215 + (2105 - 2215) * k / 8); time.sleep(0.05)
    time.sleep(0.6); snap(p("peek")); motion("UP", 276, 2105)

if __name__ == "__main__":
    raw = sys.argv[1]; os.makedirs(raw, exist_ok=True)
    for theme in (sys.argv[2:] or ["default", "light", "dark"]):
        print(theme); one_row_shots(raw, theme); full_shots(raw, theme)
