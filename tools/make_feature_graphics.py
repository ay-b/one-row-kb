"""Google Play feature graphics (1024 x 500, 24-bit PNG, no alpha). Run from the project root:  python tools/make_feature_graphics.py  (needs Pillow)

Writes releases/store/graphics/feature/feature-<n>-<name>.png and a contact sheet feature-contact-sheet.png (only for looking, not for upload).
Colors and font are the keyboard's own (see Theme.kt, icons/README.md). The artwork is drawn here, not edited by hand: change a variant below and rerun.
"""
import os
from PIL import Image, ImageDraw, ImageFont

OUT = "releases/store/graphics/feature"
FONT = "app/src/main/assets/fonts/VictorMono-Light.ttf"
SHOT = "tools/cheatsheet_src/default/mini.png"            # a real screenshot of the one-row keyboard
SLATE = (0x2F, 0x3A, 0x44); DARK = (0x1F, 0x28, 0x30); TEAL = (0x7F, 0xCF, 0xC5); INK = (0xE8, 0xEE, 0xF0)
DIM = (0x96, 0xA6, 0xB0); LIGHT_BG = (0xE8, 0xEE, 0xF0); TEAL_DK = (0x2E, 0x8B, 0x83)
W, H, K = 1024, 500, 2                                   # drawn at K times the size, then scaled down (smooth edges)


class Canvas:
    def __init__(self, bg):
        self.im = Image.new("RGB", (W * K, H * K), bg); self.d = ImageDraw.Draw(self.im)

    def rrect(self, x, y, w, h, r, fill, outline=None, width=0):
        self.d.rounded_rectangle([x * K, y * K, (x + w) * K, (y + h) * K], radius=r * K, fill=fill, outline=outline, width=width * K)

    def text(self, x, y, s, size, fill, anchor="la"):
        self.d.text((x * K, y * K), s, font=ImageFont.truetype(FONT, size * K), fill=fill, anchor=anchor)

    def keys(self, x, y, n, w, h, gap, r, fill, dim_last=True):
        for i in range(n):
            col = fill
            if dim_last and i == n - 1:
                col = tuple(int(a * 0.45 + b * 0.55) for a, b in zip(fill, self.bg_under))
            self.rrect(x + i * (w + gap), y, w, h, r, col)

    def guess_bar(self, x, y, w, h, bar, pill):
        self.rrect(x, y, w, h, h / 2, bar)
        pw, ph = (w - 4 * 12) / 3 * 0.9, h * 0.42
        gap = (w - 3 * pw) / 4
        for i in range(3):
            self.rrect(x + gap + i * (pw + gap), y + (h - ph) / 2, pw, ph, ph / 2, pill)

    def save(self, name):
        im = self.im.resize((W, H), Image.LANCZOS)
        os.makedirs(OUT, exist_ok=True)
        p = f"{OUT}/{name}.png"; im.save(p, optimize=True); print("wrote", p, im.size)
        return im


def v1_row():
    c = Canvas(SLATE); c.bg_under = SLATE
    c.text(64, 150, "OneRow", 84, INK); c.text(64, 244, "Keyboard", 84, INK)
    c.text(66, 352, "One row. Every word.", 32, TEAL)
    c.text(66, 400, "Fast · Private · Zero bytes out", 22, DIM)
    c.guess_bar(530, 138, 430, 52, DARK, TEAL)
    c.keys(530, 214, 6, 56, 140, 19, 14, TEAL)
    return c.save("feature-1-row")


def v2_real_screen():
    c = Canvas((0x26, 0x30, 0x38)); c.bg_under = (0x26, 0x30, 0x38)
    c.text(64, 44, "OneRow", 60, INK); c.text(64, 110, "Keyboard", 60, INK)
    c.text(66, 190, "The one-line keyboard", 24, TEAL); c.text(66, 222, "that guesses what you mean.", 24, TEAL)
    shot = Image.open(SHOT).convert("RGB")
    shot = shot.crop((0, 100, shot.width, shot.height))                     # drop the empty top
    tw = 600; th = round(shot.height * tw / shot.width)                     # the whole keyboard, fully inside the picture
    x0, y0 = 380, 500 - th - 8 - 40
    c.rrect(x0, y0, tw + 8, th + 8, 22, TEAL)                               # a thin teal frame
    c.im.paste(shot.resize((tw * K, th * K), Image.LANCZOS), ((x0 + 4) * K, (y0 + 4) * K))
    return c.save("feature-2-real-screen")


def v3_centered():
    c = Canvas(SLATE); c.bg_under = SLATE
    c.text(W / 2, 112, "OneRow Keyboard", 68, INK, "mm")
    c.text(W / 2, 178, "Zero bytes out.", 34, TEAL, "mm")
    c.keys(79, 262, 7, 110, 170, 16, 20, TEAL)
    return c.save("feature-3-centered")


def v4_light():
    c = Canvas(LIGHT_BG); c.bg_under = SLATE
    c.text(64, 150, "OneRow", 84, SLATE); c.text(64, 244, "Keyboard", 84, SLATE)
    c.text(66, 352, "Fast. Private. One row.", 32, TEAL_DK)
    c.rrect(500, 80, 470, 340, 36, SLATE)
    c.guess_bar(536, 118, 398, 52, DARK, TEAL)
    c.keys(536, 200, 6, 52, 160, 17, 13, TEAL)
    return c.save("feature-4-light")


def sheet(ims):
    s = 0.5; w, h = int(W * s), int(H * s); g = 16
    sh = Image.new("RGB", (w * 2 + g * 3, h * 2 + g * 3), (60, 60, 60))
    for i, im in enumerate(ims):
        sh.paste(im.resize((w, h), Image.LANCZOS), (g + (i % 2) * (w + g), g + (i // 2) * (h + g)))
    p = f"{OUT}/feature-contact-sheet.png"; sh.save(p); print("wrote", p)


if __name__ == "__main__":
    sheet([v1_row(), v2_real_screen(), v3_centered(), v4_light()])
