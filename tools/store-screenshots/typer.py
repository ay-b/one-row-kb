#!/usr/bin/env python3
"""usage: typer.py row|full|ru|keypad 'text'   taps the characters on emulator-5556 (1080 x 2400, docked).
row = one-row layout (column centres), full = expanded Latin keyboard, ru = expanded Russian keyboard, keypad = the calculator keypad
(digits + - * / = only; open it first: 123, then the keypad icon). ' ' = space, ',' and '.' = the comma and period keys."""
import subprocess, sys
E = "emulator-5556"
def tap(x, y): subprocess.run(["adb", "-s", E, "shell", "input", "tap", str(x), str(y)], check=True)
COLS = {"qaz": 88, "wsx": 206, "edc": 324, "rfv": 441, "tgb": 559, "yhn": 677, "ujm": 795, "ik": 892, "ol": 970, "p": 1032}
ROW = {c: x for k, x in COLS.items() for c in k}
FULL = {}
for i, c in enumerate("qwertyuiop"): FULL[c] = (63 + 106.1 * i, 1883)
for i, c in enumerate("asdfghjkl"): FULL[c] = (116 + 106.1 * i, 1993)
for i, c in enumerate("zxcvbnm"): FULL[c] = (222 + 106.1 * i, 2105)
RU = {}
for i, c in enumerate("йцукенгшщзх"): RU[c] = (58 + 96 * i, 1883)
for i, c in enumerate("фывапролджэ"): RU[c] = (58 + 96 * i, 1993)
for i, c in enumerate("ячсмитьбю"): RU[c] = (154 + 96 * i, 2105)
KEYPAD = {"1": (116, 1872), "2": (328, 1872), "3": (540, 1872), "+": (752, 1872), "4": (116, 1962), "5": (328, 1962), "6": (540, 1962), "-": (752, 1962),
          "7": (116, 2050), "8": (328, 2050), "9": (540, 2050), "*": (752, 2050), "=": (964, 2050), "0": (328, 2138), "/": (752, 2138)}
mode, text = sys.argv[1], sys.argv[2]
for ch in text:
    if mode == "keypad": tap(*KEYPAD[ch])
    elif ch == " ": tap(593, 2213 if mode in ("full", "ru") else 2200)
    elif ch == ",": tap(328, 2213)
    elif ch == ".": tap(858, 2213)
    elif mode == "row": tap(ROW[ch], 2140)
    elif mode == "ru": tap(*RU[ch])
    else: x, y = FULL[ch]; tap(int(x), y)
