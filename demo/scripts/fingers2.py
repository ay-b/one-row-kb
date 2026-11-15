"""Draws a finger circle over a screen recording, frame by frame, from a touch log (no ffmpeg timing tricks).
Usage: python fingers2.py recording.mp4 events.json launch.txt out.mp4 [width height]"""
import json, subprocess, sys
import numpy as np
from PIL import Image, ImageDraw
rec, evp, launchp, out = sys.argv[1:5]
W, H = (int(sys.argv[5]), int(sys.argv[6])) if len(sys.argv) > 6 else (1080, 2400)
FPS = 30; L = float(open(launchp).read()); OFFSET = -0.02
ev = json.load(open(evp))
SC = W / 1080.0
R = int(62 * SC); S = 2 * R + 12
spr = Image.new("RGBA", (S, S), (0, 0, 0, 0)); d = ImageDraw.Draw(spr)
d.ellipse([2, 2, S - 3, S - 3], fill=(20, 24, 28, 150))
d.ellipse([6, 6, S - 7, S - 7], fill=(255, 255, 255, 120), outline=(255, 255, 255, 235), width=max(2, int(4 * SC)))
c = S // 2; k = int(14 * SC)
d.ellipse([c - k, c - k, c + k, c + k], fill=(255, 255, 255, 190))
sp = np.asarray(spr).astype(np.float32); sa = sp[..., 3:4] / 255.0; srgb = sp[..., :3]

segs = []; cur = None
for e in sorted(ev, key=lambda e: e["t"]):
    t = e["t"] - L + OFFSET; kd = e["k"]
    if kd == "tap": segs.append((t, t + 0.26, [(t, e["x"], e["y"]), (t + 0.26, e["x"], e["y"])]))
    elif kd == "swipe":
        pts = [(t, e["x"], e["y"]), (t + e["dur"], e["x1"], e["y1"])]
        segs.append((t, t + e["dur"] + 0.12, pts + [(t + e["dur"] + 0.12, e["x1"], e["y1"])]))
    elif kd == "down": cur = [(t, e["x"], e["y"])]
    elif kd == "move" and cur: cur.append((t, cur[-1][1], cur[-1][2])); cur.append((t + 0.15, e["x"], e["y"]))
    elif kd == "up" and cur:
        cur.append((t, cur[-1][1], cur[-1][2])); cur.append((t + 0.15, e["x"], e["y"])); segs.append((cur[0][0], t + 0.15, cur)); cur = None
segs.sort(key=lambda s: s[0])
dur = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=nw=1:nk=1", rec], capture_output=True, text=True).stdout)
N = int(dur * FPS) + 2
pos = [None] * N
for a, b, pts in segs:
    ts = np.array([p[0] for p in pts]); xs = np.array([p[1] for p in pts]); ys = np.array([p[2] for p in pts])
    for i in range(max(0, int(np.ceil(a * FPS))), min(N, int(np.floor(b * FPS)) + 1)):
        t = i / FPS; pos[i] = (float(np.interp(t, ts, xs)), float(np.interp(t, ts, ys)))
vf = f"fps={FPS}" + (f",scale={W}:{H}" if W != 1080 else "")
dec = subprocess.Popen(["ffmpeg", "-v", "error", "-i", rec, "-vf", vf, "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], stdout=subprocess.PIPE)
enc = subprocess.Popen(["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
                        "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p", "-movflags", "+faststart", out], stdin=subprocess.PIPE)
FB = W * H * 3; i = 0; shown = 0
buf = bytearray(FB)
while True:
    mv = memoryview(buf); got = 0
    while got < FB:
        n = dec.stdout.readinto(mv[got:])
        if not n: break
        got += n
    if got < FB: break
    p = pos[i] if i < N else None
    if p is not None:
        frame = np.frombuffer(buf, dtype=np.uint8).reshape(H, W, 3)
        x0 = int(round(p[0] * SC)) - S // 2; y0 = int(round(p[1] * SC)) - S // 2
        xa, ya = max(0, x0), max(0, y0); xb, yb = min(W, x0 + S), min(H, y0 + S)
        if xb > xa and yb > ya:
            patch = frame[ya:yb, xa:xb].astype(np.float32); a = sa[ya - y0:yb - y0, xa - x0:xb - x0]; r = srgb[ya - y0:yb - y0, xa - x0:xb - x0]
            frame[ya:yb, xa:xb] = (patch * (1 - a) + r * a).astype(np.uint8); shown += 1
    enc.stdin.write(buf); i += 1
enc.stdin.close(); enc.wait(); dec.wait()
print("frames", i, "with finger", shown, "->", out)
