"""Cuts the keyboard out of full-screen phone screenshots for the cheat sheet.

Input : a folder of raw screenshots  <theme>_<shot>.png  (shots: mini letter punct emoji lang float bucket fullidle trail),
        taken on a blank page (card-colored background) with the keyboard showing.
Output: tools/cheatsheet_src/<theme>/<shot>.png  -- the keyboard plus HEAD pixels above it (room for the zoom bubble).
Usage : python crop_phone_shots.py raw_dir     (run from the project root)"""
import os, sys
from PIL import Image

# the blank page behind the keyboard in the shots has the card color of each theme (see make_cheatsheet.py)
BG = {"default": (38, 47, 56), "dark": (38, 41, 44), "light": (236, 238, 241)}
HEAD = 120          # px kept above the keyboard (mini shots); the zoom bubble rises into it
HEAD_FULL = 30

def far(a, b, t=5): return max(abs(x - y) for x, y in zip(a[:3], b[:3])) > t

def keyboard_top(im, bg):
    """First row (from the middle of the screen down) that is not page background = keyboard top."""
    w, h = im.size
    return next(y for y in range(h // 2, h) if far(im.getpixel((3, y)), bg))

def sat(p): return max(p[:3]) - min(p[:3])

def mini_bottom(im, top, margin=19):
    """The one-row keyboard ends a few px under the outlined space key (below the keyboard the system draws its own bar): lowest saturated row in the space key's columns."""
    low = max(y for y in range(top + 150, top + 300) if any(sat(im.getpixel((x, y))) > 60 for x in range(400, 780, 3)))
    return low + margin

def full_bottom(im, top, keycap, margin=14):
    """The expanded keyboard: lowest row of the key-cap colored enter key (x = 990)."""
    low = max(y for y in range(top + 300, top + 600) if max(abs(a - b) for a, b in zip(im.getpixel((990, y))[:3], keycap)) < 6)
    return low + margin

KEYCAP = {"default": (70, 84, 92), "dark": (58, 62, 66), "light": (255, 255, 255)}      # key-cap colors (Theme.kt)

raw = sys.argv[1]

def float_box(raw):
    """Rows of the floating window (not page background, first block below the status bar) from the dark shot, with room for its shadow."""
    fl = Image.open(f"{raw}/dark_float.png").convert("RGB")
    ys = [y for y in range(300, 2300) if any(far(fl.getpixel((x, y)), BG["dark"], 12) for x in range(60, 1020, 6))]
    end = next(ys[i] for i in range(len(ys) - 1) if ys[i + 1] - ys[i] > 50)
    return ys[0] - 90, end + 50
FLOAT_BOX = float_box(raw)
for theme in ("default", "light", "dark"):
    out = f"tools/cheatsheet_src/{theme}"; os.makedirs(out, exist_ok=True)
    mini = Image.open(f"{raw}/{theme}_mini.png").convert("RGB"); t = keyboard_top(mini, BG[theme]); b = mini_bottom(mini, t)
    full = Image.open(f"{raw}/{theme}_fullidle.png").convert("RGB"); tf = keyboard_top(full, BG[theme]); bf = full_bottom(full, tf, KEYCAP[theme])
    print(theme, "mini", t, b, "full", tf, bf)
    for shot in ("mini", "letter", "punct", "emoji", "lang", "bucket"):
        Image.open(f"{raw}/{theme}_{shot}.png").convert("RGB").crop((0, t - HEAD, 1080, b)).save(f"{out}/{shot}.png")
    # the floating keyboard: the window is at the same place in every theme, so the box comes from the dark shot (see FLOAT_BOX)
    Image.open(f"{raw}/{theme}_float.png").convert("RGB").crop((0, FLOAT_BOX[0], 1080, FLOAT_BOX[1])).save(f"{out}/float.png")
    for shot in ("fullidle", "trail"):
        Image.open(f"{raw}/{theme}_{shot}.png").convert("RGB").crop((0, tf - HEAD_FULL, 1080, bf)).save(f"{out}/{shot}.png")
    Image.open(f"{raw}/{theme}_peek.png").convert("RGB").crop((0, tf - HEAD_FULL, 1080, bf)).save(f"{out}/peek.png")
