# OneRow icon

| File | What |
|---|---|
| `icon-1-one-row.svg`, `icon-1-one-row-512.png` | **The app icon.** A single row of keys (the last one dimmed). |

Colors: slate `#2F3A44`, dark `#1F2830`, teal `#7FCFC5` (the keyboard's default theme).

The 512 px PNG is a full-bleed square: upload `icon-1-one-row-512.png` as the Play Console store icon (Play applies its own mask).

All Android icon files (adaptive layers, themed layer, legacy PNGs) and these files come from one script:
`python tools/make_icons.py` (project root, needs Pillow). Change the design there, not in the generated files.
