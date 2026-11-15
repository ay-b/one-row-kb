"""Gesture cheat sheet for the settings screen, one per keyboard theme, built from real phone screenshots of the
keyboard (tools/cheatsheet_src/<theme>/*.png, cut out by crop_phone_shots.py) plus drawn arrows and captions.

Usage (from the project root):  python tools/make_cheatsheet.py            -> app/src/main/res/drawable-nodpi/cheatsheet_<theme>.png
                                python tools/make_cheatsheet.py light out.png"""
import math, os, sys
from PIL import Image, ImageDraw, ImageFont

W = 1080
FONT = "app/src/main/assets/fonts/VictorMono-Light.ttf"
# page, card, accent, ink, dim, outline (the colors of the keyboard themes, see Theme.kt)
THEMES = {
    "default": dict(page=(47, 58, 68), card=(38, 47, 56), accent=(127, 207, 197), ink=(232, 238, 240), dim=(150, 166, 176), edge=(20, 26, 32)),
    "dark":    dict(page=(26, 28, 30), card=(38, 41, 44), accent=(138, 180, 248), ink=(232, 234, 237), dim=(154, 160, 166), edge=(8, 10, 12)),
    "light":   dict(page=(223, 226, 230), card=(236, 238, 241), accent=(26, 115, 232), ink=(31, 35, 40), dim=(95, 104, 114), edge=(255, 255, 255)),
}
IMG_W = 960
HEAD = 120          # px above the keyboard in the mini / lens shots (see crop_phone_shots.py)
HEAD_FULL = 30

def F(n): return ImageFont.truetype(FONT, n)

class Sheet:
    def __init__(self, theme):
        self.t = THEMES[theme]; self.theme = theme
        self.cards = []

    def src(self, name):
        return Image.open(f"tools/cheatsheet_src/{self.theme}/{name}.png").convert("RGBA")

    # ---- drawing helpers, all on an RGBA overlay laid over the screenshot (native 1080 px width) ----
    def arrow(self, d, x0, y0, x1, y1, w=8, head=30):
        a = math.atan2(y1 - y0, x1 - x0)
        for col, ww in ((self.t["edge"] + (170,), w + 7), (self.t["accent"] + (235,), w)):
            d.line([(x0, y0), (x1, y1)], fill=col, width=ww)
            for s in (-0.5, 0.5):
                d.line([(x1, y1), (x1 - head * math.cos(a + s), y1 - head * math.sin(a + s))], fill=col, width=ww)

    def finger(self, d, x, y, r=26):
        d.ellipse([x - r, y - r, x + r, y + r], fill=self.t["accent"] + (200,), outline=self.t["edge"] + (230,), width=4)

    def label(self, d, x, y, s, size=32, anchor="mm"):
        d.text((x, y), s, font=F(size), fill=self.t["accent"] + (255,), anchor=anchor, stroke_width=4, stroke_fill=self.t["edge"] + (255,))

    def card(self, title, shot, annotate, lines, head=HEAD):
        self.cards.append((title, shot, annotate, lines, head))

    def render(self):
        t = self.t; pad = 34; title_h = 64
        parts = []
        for title, shot, annotate, lines, head in self.cards:
            im = self.src(shot); ov = Image.new("RGBA", im.size, (0, 0, 0, 0))
            annotate(ImageDraw.Draw(ov), im.size, head)
            im = Image.alpha_composite(im, ov)
            im = im.resize((IMG_W, round(im.height * IMG_W / im.width)), Image.LANCZOS)
            h = title_h + im.height + 14 + 40 * len(lines) + 24
            parts.append((title, im, lines, h))
        total = 110 + sum(h + pad for *_, h in parts)
        page = Image.new("RGB", (W, total), t["page"]); d = ImageDraw.Draw(page)
        d.text((60, 46), "OneRow gestures", font=F(52), fill=t["accent"])
        y = 110
        for title, im, lines, h in parts:
            d.rounded_rectangle([30, y, W - 30, y + h], 24, fill=t["card"])
            d.text((60, y + 22), title.upper(), font=F(25), fill=t["dim"])
            page.paste(im.convert("RGB"), (60, y + title_h))
            ty = y + title_h + im.height + 12
            for ln in lines:
                d.text((60, ty), ln, font=F(30), fill=t["accent"]); ty += 40
            y += h + pad
        return page.crop((0, 0, W, y))

def build(theme):
    S = Sheet(theme)
    # geometry of the phone keyboard in the mini crop: y measured from the top of the crop (keyboard starts at HEAD)
    strip = HEAD + 44; row = HEAD + 150; ctl = HEAD + 236

    def c1(d, size, head):
        cx = 540; S.finger(d, cx, row)
        S.arrow(d, cx - 34, row, cx - 400, row); S.arrow(d, cx + 34, row, cx + 400, row)
        S.arrow(d, cx + 24, row - 24, cx + 230, row - 140); S.arrow(d, cx - 24, row - 24, cx - 230, row - 140); S.arrow(d, cx, row - 34, cx, row - 150)
        S.label(d, cx - 400, row + 44, "delete"); S.label(d, cx + 400, row + 44, "space")
        S.label(d, cx + 250, row - 168, "enter"); S.label(d, cx - 250, row - 168, "voice"); S.label(d, cx, row - 176, "full keyboard")
    S.card("Swipe on the letter row", "mini", c1, [], HEAD)

    def c2(d, size, head):
        S.arrow(d, 330, row + 38, 220, row + 38, 6); S.arrow(d, 420, row + 38, 530, row + 38, 6)
    S.card("Exact letters", "letter", c2, ["HOLD a letter, then slide: release types", "exactly that letter (no guessing)"])
    S.card("Basic punctuation", "punct", lambda d, s, h: None, ["HOLD  .  then slide to choose"])

    def c4(d, size, head):
        S.arrow(d, 328, ctl - 70, 328, 24, 7)
    S.card("Emoji", "emoji", c4, ["HOLD  ,  then slide: the emoji you use most", "come first.  Swipe UP: all emoji (tap one)"])

    def c5(d, size, head):
        S.arrow(d, 470, ctl, 320, ctl, 7); S.arrow(d, 620, ctl, 770, ctl, 7)
        S.arrow(d, 960, strip, 700, strip, 6)
    S.card("Delete and cursor", "mini", c5, ["DRAG on space: move the cursor", "DRAG left on backspace: delete word by word"])

    def c6(d, size, head):
        S.arrow(d, 250, strip + 34, 700, strip + 34, 6)
    S.card("Guesses", "bucket", c6, ["SWIPE the guess bar sideways: more guesses", "HOLD a guess, drop it on the bin: forget the word"])
    S.card("Language key", "lang", lambda d, s, h: None, ["TAP the language key: next language", "HOLD it: settings"])

    S.card("Swipe typing (full keyboard)", "trail", lambda d, s, h: None,
           ["Optional (Settings > Gestures). Draw a word across", "the letters, here  h-e-l-l-o.  Tap a guess to replace", "the word; backspace deletes the whole word."], HEAD_FULL)

    def c9(d, size, head):
        y0 = HEAD_FULL + 44
        S.arrow(d, 760, y0, 760, y0 + 190, 8)
    S.card("Back to one row", "fullidle", c9, ["SWIPE DOWN on the guess bar"], HEAD_FULL)
    return S.render()

if __name__ == "__main__":
    themes = [sys.argv[1]] if len(sys.argv) > 1 else list(THEMES)
    for th in themes:
        out = sys.argv[2] if len(sys.argv) > 2 else f"app/src/main/res/drawable-nodpi/cheatsheet_{th}.png"
        build(th).save(out, optimize=True); print("wrote", out)
