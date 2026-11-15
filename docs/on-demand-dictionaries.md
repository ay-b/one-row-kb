# Downloading dictionaries on demand: options

Status: **research only**. Nothing in the app downloads anything, and the app has no `INTERNET` permission. The 19
languages added in 0.11 (see `dictionary-countries.md`) are built by `tools/build_lexicon.py` into `dictionaries/`
(not tracked by git) and are not part of the APK. The app already looks for a dictionary in two places
(`Dictionaries.kt`): `assets/dict/<lang>.lex` (bundled) and `files/dict/<lang>.lex` (installed later). A language
whose dictionary is in neither place is shown in the settings as "dictionary not installed" and cannot be enabled.
So every option below only has to solve one problem: **get a `.lex` (and `_bi.tsv`) into `files/dict/`**.

## Size of the problem

The seven bundled dictionaries are 2.4 to 15 MB each (about 50 MB together). 26 languages would be roughly 130-150 MB
uncompressed, far more than anyone needs: a typical user enables two or three languages. The `.lex` files are close to
incompressible binary tries, so zip/gzip saves only a little. Per language 3-15 MB, so on-demand delivery is worth it.

## Options

| # | Option | Needs INTERNET? | Stores | Verdict |
|---|---|---|---|---|
| 1 | Bundle everything | no | APK grows to about 150 MB | Not acceptable for F-Droid / Play size limits |
| 2 | **Import a pack with the system file picker (SAF)** | **no** | any | Best first step: privacy-friendly, no permission, same idea as HeliBoard |
| 3 | **Download from GitHub Releases over HTTPS, started by the user** | yes (only for this feature) | any | Good second step; opt-in button, checksum check |
| 4 | Play Asset Delivery (on-demand asset packs) | handled by Play | Google Play only | Excellent on Play, impossible elsewhere; needs an app bundle (AAB) |
| 5 | One companion APK per language | no (installer does it) | any | Clumsy: the IME has to find and read another app's files |
| 6 | Own server / CDN | yes | any | Costs money and maintenance; GitHub Releases does the job for free |

### 2. User-imported pack (recommended first)

- Settings gets "Install dictionary file...": `ACTION_OPEN_DOCUMENT` (no permission needed), the user picks
  `xx.olexpack` (a zip with `xx.lex`, optional `xx_bi.tsv` and `meta.json`), the app validates it (magic `OLX1`, sizes,
  alphabet round-trip on a sample) and copies it into `files/dict/`.
- Packs are published as release assets in the repository; the settings screen shows the link (opens the browser).
- The app stays offline-only, which is a feature for a keyboard (it sees everything the user types).

### 3. Opt-in HTTPS download (second step)

- Add `INTERNET` and use it for one thing only: a button "Download dictionary" that fetches
  `https://github.com/ay-b/one-row-kb/releases/download/dict-<n>/<lang>.olexpack` and an `index.json` with the
  SHA-256 of every pack. Use `HttpsURLConnection` (no extra library), verify the hash, then install as in option 2.
- Pin to the release tag, never to a branch, so a pack cannot change behind the app's back.
- The keyboard process should not talk to the network at all: run the download from the settings activity, not from
  the `InputMethodService`.
- Disclose it: the permission makes some users uneasy about a keyboard. Keep a flavour or a build switch that omits it.
- F-Droid: data files are less of a problem than downloaded code, but check its inclusion policy (and whether a
  network-use anti-feature label would apply) before relying on this; the download must be user-initiated.

### 4. Play Asset Delivery

On Google Play only. One on-demand asset pack per language (`install-time` is not needed), requested with
`AssetPackManager.fetch()`; the files appear under the app's asset-pack path and can be opened directly, so
`Dictionaries.open` would need one more lookup location. Works without our own server, but only for the Play build.

## Recommendation

1. Ship the seven current languages in the APK (as now).
2. Build packs for the others with `tools/build_lexicon.py` + `tools/build_bigrams.py` (a `tools/pack_dictionaries.py`
   that zips them and writes `index.json` with hashes is the only missing tool) and attach them to a GitHub release.
3. Implement option 2 (picker), then option 3 behind an explicit button, then option 4 for a Play flavour if the app
   ever goes there.

## Open decisions for the owner

- Licences of the sources (see `NOTICE-dictionaries.md`): some Hunspell dictionaries are GPL / CC BY-NC-SA. Publishing
  packs as release assets is publishing derived data; settle this before step 2 of the recommendation.
- Whether the app may ever hold the `INTERNET` permission.
