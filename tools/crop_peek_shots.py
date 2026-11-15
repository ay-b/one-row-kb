"""Cuts the "symbols peek" keyboard (finger held after sliding off the 123 key) out of full-screen phone screenshots.

Input : raw_dir/<theme>_peek.png  for default, light, dark (taken in any app: the strip above the keyboard is repainted).
Output: tools/cheatsheet_src/<theme>/peek.png -- the keyboard with HEAD_FULL pixels of card color above it, like the other full-keyboard shots.
Usage : python crop_peek_shots.py raw_dir     (run from the project root)"""
import os, sys
from PIL import Image

BG = {"default": (38, 47, 56), "dark": (38, 41, 44), "light": (236, 238, 241)}   # card colors, see make_cheatsheet.py
HEAD_FULL = 30
KEYBOARD_H = 703     # height in px of the full keyboard (5 rows, 100 %) on the 1080 x 2280 phone, number row off

for theme in ("default", "light", "dark"):
    im = Image.open(f"{sys.argv[1]}/{theme}_peek.png").convert("RGB"); w, h = im.size
    bottom = next(y for y in range(h - 1, h // 2, -1) if im.getpixel((3, y))[:3] != (0, 0, 0)) + 1
    top = bottom - KEYBOARD_H        # (the guess strip of the dark theme is hardly different from the app behind it: no edge to find)
    shot = Image.new("RGB", (w, HEAD_FULL + bottom - top), BG[theme])
    shot.paste(im.crop((0, top, w, bottom)), (0, HEAD_FULL))
    out = f"tools/cheatsheet_src/{theme}"; os.makedirs(out, exist_ok=True); shot.save(f"{out}/peek.png")
    print(theme, "keyboard", top, bottom, "->", shot.size)
