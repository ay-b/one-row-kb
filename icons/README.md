# OneRow icons

| File | What |
|---|---|
| `icon-9-guess-bar.svg`, `icon-9-guess-bar-512.png` | **The app icon (default).** A guess bar over a row of keys. |
| `icon-1-one-row.svg`, `icon-1-one-row-512.png` | Alternative: a single row of keys. Stored in the app (`@mipmap/ic_launcher_one_row`), not selectable yet. |

Colors: slate `#2F3A44`, dark `#1F2830`, teal `#7FCFC5` (the keyboard's default theme).

The 512 px PNGs are full-bleed squares: upload `icon-9-guess-bar-512.png` as the Play Console store icon (Play applies its own mask).

All Android icon files (adaptive layers, themed layer, legacy PNGs) and these files come from one script:
`python tools/make_icons.py` (project root, needs Pillow). Change a design there, not in the generated files.
