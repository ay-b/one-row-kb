#!/usr/bin/env python3
"""usage: snap.py <name>  -> releases/store/screenshots/phone/<name>.png : a screenshot of emulator-5556 cropped to 1080 x 2160 (Play: long side at most 2x the short side)"""
import subprocess, sys, io
from PIL import Image
raw = subprocess.run(["adb", "-s", "emulator-5556", "exec-out", "screencap", "-p"], capture_output=True, check=True).stdout
im = Image.open(io.BytesIO(raw)).convert("RGB")
assert im.size == (1080, 2400), im.size
import os
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "releases", "store", "screenshots", "phone", f"{sys.argv[1]}.png")
top = int(sys.argv[2]) if len(sys.argv) > 2 else 240
im.crop((0, top, 1080, 2400)).save(out, optimize=True); print("wrote", out)
