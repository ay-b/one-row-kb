"""Gesture cheat sheet for the settings screen, one per keyboard theme, built from real phone screenshots of the
keyboard (tools/cheatsheet_src/<theme>/*.png, cut out by crop_phone_shots.py) plus drawn arrows. The picture holds NO words: every
arrow carries a number, and the numbered legend is text in the app (res/values*/strings.xml, keys cheat_1..cheat_19), so it can be translated.

Usage (from the project root):  python tools/make_cheatsheet.py            -> app/src/main/res/drawable-nodpi/cheatsheet_<theme>_<card>.png (11 per theme)
                                python tools/make_cheatsheet.py light some_dir"""
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

    def num(self, d, x, y, n, r=25):
        """A numbered badge (the key to the legend text under the picture)."""
        d.ellipse([x - r - 4, y - r - 4, x + r + 4, y + r + 4], fill=self.t["edge"] + (235,))
        d.ellipse([x - r, y - r, x + r, y + r], fill=self.t["accent"] + (255,))
        d.text((x, y + 1), str(n), font=F(32), fill=self.t["edge"] + (255,), anchor="mm", stroke_width=1, stroke_fill=self.t["edge"] + (255,))

    def narrow(self, d, x0, y0, x1, y1, n, w=8, head=30, at=0.5):
        """An arrow with its number on it (at = how far along the shaft the badge sits)."""
        self.arrow(d, x0, y0, x1, y1, w, head)
        self.num(d, x0 + (x1 - x0) * at, y0 + (y1 - y0) * at, n)

    def card(self, shot, annotate, head=HEAD):
        self.cards.append((shot, annotate, head))

    def render(self):
        """One picture per card (each is shown in the app right above its own legend lines, so it never scrolls apart from them)."""
        t = self.t; gap = 14; inner = 22
        pieces = []
        for shot, annotate, head in self.cards:
            im = self.src(shot); ov = Image.new("RGBA", im.size, (0, 0, 0, 0))
            annotate(ImageDraw.Draw(ov), im.size, head)
            im = Image.alpha_composite(im, ov)
            im = im.resize((IMG_W, round(im.height * IMG_W / im.width)), Image.LANCZOS)
            h = im.height + 2 * inner
            page = Image.new("RGBA", (W, h + 2 * gap), (0, 0, 0, 0)); d = ImageDraw.Draw(page)      # transparent around the card: the app's themed background shows
            d.rounded_rectangle([30, gap, W - 30, gap + h], 24, fill=t["card"] + (255,))
            page.paste(im.convert("RGB"), (60, gap + inner))
            pieces.append(page)
        return pieces

def build(theme):
    S = Sheet(theme)
    # geometry of the phone keyboard in the mini crop: y measured from the top of the crop (keyboard starts at HEAD)
    strip = HEAD + 42; row = HEAD + 133; ctl = HEAD + 221

    def c1(d, size, head):                                                        # 1..5: swipes from the middle of the letter row
        cx = 540; S.finger(d, cx, row)
        S.narrow(d, cx - 34, row, cx - 400, row, 1)                               # left: delete
        S.narrow(d, cx - 24, row - 24, cx - 230, row - 140, 2)                    # up-left: voice
        S.narrow(d, cx, row - 34, cx, row - 150, 3, at=0.55)                      # up: full keyboard
        S.narrow(d, cx + 24, row - 24, cx + 230, row - 140, 4)                    # up-right: enter
        S.narrow(d, cx + 34, row, cx + 400, row, 5)                               # right: space
    S.card("mini", c1, HEAD)

    def c2(d, size, head):                                                        # 6: exact letters
        S.arrow(d, 420, row + 38, 530, row + 38, 6); S.narrow(d, 330, row + 38, 220, row + 38, 6, 6)
    S.card("letter", c2)

    S.card("punct", lambda d, s, h: S.num(d, 560, 150, 7))                        # 7: punctuation popup

    def c4(d, size, head):                                                        # 8: emoji
        S.narrow(d, 352, ctl - 70, 352, 24, 8, 7, at=0.75)
    S.card("emoji", c4)

    def c5(d, size, head):                                                        # 9, 10: cursor and word delete
        S.arrow(d, 620, ctl, 770, ctl, 7); S.narrow(d, 470, ctl, 320, ctl, 9, 7)
        S.narrow(d, 960, strip, 700, strip, 10, 6)
    S.card("mini", c5)

    def c6(d, size, head):                                                        # 11, 12: guess bar, bin
        S.narrow(d, 250, strip + 34, 700, strip + 34, 11, 6)
        S.num(d, 1010, 168, 12)
    S.card("bucket", c6)

    def c7(d, size, head):                                                        # 13, 14: language key
        S.num(d, 300, 345, 13); S.num(d, 62, 150, 14)
    S.card("lang", c7)

    def c7b(d, size, head):                                                       # 15, 16: floating keyboard
        S.arrow(d, 622, 391, 710, 391, 6); S.narrow(d, 458, 391, 370, 391, 15, 6)  # drag the handle (the bar under the keys) to move
        S.narrow(d, 768, 404, 735, 458, 16, 6)                                     # the resize mark next to the globe
    S.card("float", c7b)

    S.card("trail", lambda d, s, h: S.num(d, 640, 235, 17), HEAD_FULL)            # 17: swipe typing

    def c8(d, size, head):                                                        # 18: symbols from 123
        y = HEAD_FULL + 471
        S.finger(d, 90, y); S.narrow(d, 118, y - 18, 250, HEAD_FULL + 372, 18, 7)
    S.card("peek", c8, HEAD_FULL)

    def c9(d, size, head):                                                        # 19: back to one row
        y0 = HEAD_FULL + 44
        S.narrow(d, 760, y0, 760, y0 + 190, 19, 8)
    S.card("fullidle", c9, HEAD_FULL)
    return S.render()

if __name__ == "__main__":
    themes = [sys.argv[1]] if len(sys.argv) > 1 else list(THEMES)
    outdir = sys.argv[2] if len(sys.argv) > 2 else "app/src/main/res/drawable-nodpi"
    for th in themes:
        for n, piece in enumerate(build(th), 1):
            out = f"{outdir}/cheatsheet_{th}_{n}.png"
            piece.save(out, optimize=True); print("wrote", out, piece.size)
