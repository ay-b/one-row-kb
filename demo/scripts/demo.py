import subprocess, time, random, sys
import os
SERIAL = os.environ.get("SERIAL", "emulator-5556")
ADB = ["adb", "-s", SERIAL]
random.seed(7)
import json
EVENTS = []          # every touch, for drawing fingers over the recording afterwards
def sh(*a): subprocess.run(ADB + ["shell"] + list(a), capture_output=True)
def logged(kind, x, y, **kw):
    t0 = time.time(); return t0, kw
def save_events(path):
    json.dump(EVENTS, open(path, "w"))
def _t(): return time.time()

ORDER = "qazwsxedcrfvtgbyhnujmikolp"
GROUPS = ["qaz", "wsx", "edc", "rfv", "tgb", "yhn", "ujm", "ik", "ol", "p"]
YS = [2125, 2143, 2165]
ROWY = 2227          # space / comma / dot / enter row
SUGGY = 2048         # suggestion bar and backspace
def pos(ch):
    i = ORDER.index(ch); g = next(g for g in GROUPS if ch in g)
    return 50 + 39.2 * i, YS[g.index(ch)]
def tap(x, y):
    t0 = _t(); sh("input", "tap", str(int(x)), str(int(y))); t1 = _t()
    EVENTS.append({"k": "tap", "t": (t0 + t1) / 2, "x": int(x), "y": int(y)})
def touch(action, x, y):
    t0 = _t(); sh("input", "motionevent", action, str(int(x)), str(int(y))); t1 = _t()
    EVENTS.append({"k": action.lower(), "t": (t0 + t1) / 2, "x": int(x), "y": int(y)})
def swipe(x0, y0, x1, y1, dur_ms):
    t0 = _t(); sh("input", "swipe", str(int(x0)), str(int(y0)), str(int(x1)), str(int(y1)), str(int(dur_ms)))
    EVENTS.append({"k": "swipe", "t": t0 + 0.15, "x": int(x0), "y": int(y0), "x1": int(x1), "y1": int(y1), "dur": dur_ms / 1000.0})

def letter(ch, noise=9):
    x, y = pos(ch.lower()); tap(x + random.gauss(0, noise), y + random.gauss(0, 5))
def word(w, gap=0.10):
    for ch in w: letter(ch); time.sleep(gap)
def hold(ch, t=0.5):
    x, y = pos(ch.lower()); x, y = int(x), int(y)
    touch("DOWN", x, y); time.sleep(t); touch("UP", x, y); time.sleep(0.12)
def holdword(w):
    for ch in w: hold(ch)
def space(): tap(540, ROWY); time.sleep(0.2)
def comma_tap(): tap(222, ROWY)
def dot_tap(): tap(858, ROWY)
def guess(slot): tap([265, 448, 632, 817][slot], SUGGY)
def backspace(n=1):
    for _ in range(n): tap(990, SUGGY); time.sleep(0.12)
def delete_word(): swipe(990, SUGGY, 770, SUGGY, 350)
def drag_space(chars, dur=500):
    step = 42; x0 = 540; x1 = x0 + int(chars * step)
    swipe(x0, ROWY, x1, ROWY, dur)
def snap(name): 
    sh("screencap", "-p", "/sdcard/s.png"); subprocess.run(ADB + ["pull", "/sdcard/s.png", f"/tmp/cs/{name}.png"], capture_output=True)
def emoji_lens(slot_swipe_px, hold_t=0.7):
    touch("DOWN", 222, ROWY); time.sleep(hold_t)
    touch("MOVE", 222 + slot_swipe_px, ROWY); time.sleep(0.5)
    touch("UP", 222 + slot_swipe_px, ROWY); time.sleep(0.3)

def get_text():
    sh("uiautomator", "dump", "/sdcard/u.xml")
    r = subprocess.run(ADB + ["shell", "cat", "/sdcard/u.xml"], capture_output=True, text=True).stdout
    import re
    m = re.search(r'<node[^>]*?text="([^"]*)"[^>]*?class="android.widget.EditText"', r)
    import html
    return html.unescape(m.group(1)) if m else "?"

def front():
    r = subprocess.run(ADB + ["shell", "dumpsys", "activity", "activities"], capture_output=True, text=True).stdout
    for line in r.splitlines():
        if "topResumedActivity" in line: return line
    return ""
def guard():
    """Never type into anything but the demo notepad."""
    if "demo.notepad" not in front():
        print("ABORT: foreground is not the notepad:", front().strip(), flush=True); raise SystemExit(2)
