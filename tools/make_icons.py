"""App icons, from one description. Run from the project root:  python tools/make_icons.py   (needs Pillow)

Two designs, both drawn in a 100 x 100 box (slate square, teal keys):
  9 "guess bar"  -> THE launcher icon (default)
  1 "one row"    -> kept in the app (not selectable) and in icons/

Writes:
  icons/icon-<n>-<name>.svg           square, full bleed (source)
  icons/icon-<n>-<name>-512.png       512 x 512, square, full bleed (Play listing icon)
  app/src/main/res/drawable/ic_launcher_fg.xml, ic_launcher_mono.xml      adaptive launcher icon (9) layers
  app/src/main/res/drawable/ic_launcher_one_row_fg.xml                    icon 1 layer
  app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml, ic_launcher_one_row.xml
  app/src/main/res/mipmap-*dpi/ic_launcher.png                            legacy (Android 7) rounded-square PNGs of icon 9
  app/src/main/res/values/ic_launcher_colors.xml
"""
import os
from PIL import Image, ImageDraw

SLATE = (0x2F, 0x3A, 0x44); DARK = (0x1F, 0x28, 0x30); TEAL = (0x7F, 0xCF, 0xC5)
hexc = lambda c: "#%02X%02X%02X" % c

# (kind, x, y, w, h, radius, color-name, alpha) in the 100-box
ICONS = {
    "9-guess-bar": dict(
        name="guess_bar", cx=50, cy=49, scale=0.68,
        shapes=[("strip", 12, 22, 76, 18, 9, "dark", 1.0),
                ("pill", 21, 27, 18, 8, 4, "teal", 1.0), ("pill", 41, 27, 18, 8, 4, "teal", 1.0), ("pill", 61, 27, 18, 8, 4, "teal", 1.0)] +
               [("key", 9.5 + 14 * i, 54, 11, 22, 4, "teal", 1.0) for i in range(6)]),
    "1-one-row": dict(
        name="one_row", cx=50, cy=50, scale=0.78,
        shapes=[("key", 9.5 + 14 * i, 38, 11, 24, 4, "teal", 0.45 if i == 5 else 1.0) for i in range(6)]),
}
COLORS = {"dark": DARK, "teal": TEAL}


def rrect_path(x, y, w, h, r):
    f = lambda v: ("%.2f" % v).rstrip("0").rstrip(".")
    return (f"M{f(x + r)},{f(y)} H{f(x + w - r)} A{f(r)},{f(r)} 0 0 1 {f(x + w)},{f(y + r)} V{f(y + h - r)} A{f(r)},{f(r)} 0 0 1 {f(x + w - r)},{f(y + h)} "
            f"H{f(x + r)} A{f(r)},{f(r)} 0 0 1 {f(x)},{f(y + h - r)} V{f(y + r)} A{f(r)},{f(r)} 0 0 1 {f(x + r)},{f(y)} Z")


def svg(d):
    out = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="512" height="512">',
           f'<rect width="100" height="100" fill="{hexc(SLATE)}"/>']
    for kind, x, y, w, h, r, col, a in d["shapes"]:
        op = "" if a == 1.0 else f' opacity="{a}"'
        out.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{hexc(COLORS[col])}"{op}/>')
    out.append("</svg>")
    return "\n".join(out) + "\n"


def vector(d, mono=False):
    """Adaptive-icon foreground: 108 x 108 dp, the design scaled into the safe zone."""
    s, cx, cy = d["scale"], d["cx"], d["cy"]
    paths = []
    for kind, x, y, w, h, r, col, a in d["shapes"]:
        if mono and kind == "strip":
            continue                                           # the dark strip is only a backdrop; the themed icon keeps pills and keys
        X, Y = 54 + (x - cx) * s, 54 + (y - cy) * s
        fill = "#FF000000" if mono else hexc(COLORS[col]).replace("#", "#FF")
        alpha = f'\n        android:fillAlpha="{a}"' if a != 1.0 else ""
        paths.append(f'    <path\n        android:fillColor="{fill}"{alpha}\n        android:pathData="{rrect_path(X, Y, w * s, h * s, r * s)}"/>')
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
            + "\n".join(paths) + "\n</vector>\n")


def raster(d, size, round_corners):
    k = 4; S = size * k
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    dr = ImageDraw.Draw(im)
    dr.rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.22) if round_corners else 0, fill=SLATE + (255,))
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    u = S / 100.0
    for kind, x, y, w, h, r, col, a in d["shapes"]:
        ld.rounded_rectangle([x * u, y * u, (x + w) * u, (y + h) * u], radius=r * u, fill=COLORS[col] + (int(255 * a),))
    im = Image.alpha_composite(im, layer)
    return im.resize((size, size), Image.LANCZOS)


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w", encoding="utf-8").write(text)
    print("wrote", path)


def main():
    res = "app/src/main/res"
    for key, d in ICONS.items():
        write(f"icons/icon-{key}.svg", svg(d))
        p = f"icons/icon-{key}-512.png"; raster(d, 512, False).convert("RGB").save(p, optimize=True); print("wrote", p)
    nine, one = ICONS["9-guess-bar"], ICONS["1-one-row"]
    write(f"{res}/drawable/ic_launcher_fg.xml", vector(nine))
    write(f"{res}/drawable/ic_launcher_mono.xml", vector(nine, mono=True))
    write(f"{res}/drawable/ic_launcher_one_row_fg.xml", vector(one))
    write(f"{res}/values/ic_launcher_colors.xml", f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_bg">{hexc(SLATE)}</color>\n</resources>\n')
    write(f"{res}/mipmap-anydpi-v26/ic_launcher.xml",
          '<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
          '    <background android:drawable="@color/ic_launcher_bg"/>\n    <foreground android:drawable="@drawable/ic_launcher_fg"/>\n'
          '    <monochrome android:drawable="@drawable/ic_launcher_mono"/>\n</adaptive-icon>\n')
    write(f"{res}/mipmap-anydpi-v26/ic_launcher_one_row.xml",
          '<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
          '    <background android:drawable="@color/ic_launcher_bg"/>\n    <foreground android:drawable="@drawable/ic_launcher_one_row_fg"/>\n</adaptive-icon>\n')
    for dens, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        p = f"{res}/mipmap-{dens}/ic_launcher.png"; os.makedirs(os.path.dirname(p), exist_ok=True)
        raster(nine, px, True).save(p, optimize=True); print("wrote", p)


if __name__ == "__main__":
    main()
