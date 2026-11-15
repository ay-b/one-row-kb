"""Cuts the keyboard out of full-screen phone screenshots for the cheat sheet.

Input : a folder of raw screenshots  <theme>_<shot>.png  (shots: mini letter punct emoji lang bucket fullidle trail),
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

def keyboard_span(im, bg):
    """First row (from the middle of the screen down) that is not page background = keyboard top; last non-black row = bottom."""
    w, h = im.size
    top = next(y for y in range(h // 2, h) if far(im.getpixel((3, y)), bg))
    bottom = next(y for y in range(h - 1, top, -1) if im.getpixel((3, y))[:3] != (0, 0, 0)) + 1
    return top, bottom

raw = sys.argv[1]
for theme in ("default", "light", "dark"):
    out = f"tools/cheatsheet_src/{theme}"; os.makedirs(out, exist_ok=True)
    mini = Image.open(f"{raw}/{theme}_mini.png").convert("RGB"); t, b = keyboard_span(mini, BG[theme])
    full = Image.open(f"{raw}/{theme}_fullidle.png").convert("RGB"); tf, bf = keyboard_span(full, BG[theme])
    print(theme, "mini", t, b, "full", tf, bf)
    for shot in ("mini", "letter", "punct", "emoji", "lang", "bucket"):
        Image.open(f"{raw}/{theme}_{shot}.png").convert("RGB").crop((0, t - HEAD, 1080, b)).save(f"{out}/{shot}.png")
    for shot in ("fullidle", "trail"):
        Image.open(f"{raw}/{theme}_{shot}.png").convert("RGB").crop((0, tf - HEAD_FULL, 1080, bf)).save(f"{out}/{shot}.png")
