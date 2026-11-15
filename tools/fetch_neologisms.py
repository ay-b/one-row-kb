"""Fetches recent vocabulary from Wiktionary: the members of  Category:<Language>_neologisms  and
Category:<Language>_internet_slang  (English Wiktionary, MediaWiki API), for the seven keyboard languages.

Writes tools/dict_src/<lang>_neologisms.txt, one "word<TAB>category" per line (single-word titles only).
Index of the categories: https://en.wiktionary.org/wiki/Category:Neologisms_by_language
Bulk alternative (no API): https://dumps.wikimedia.org/enwiktionary/latest/
Wiktionary text is CC BY-SA 4.0. The API rate-limits quickly: send a User-Agent, pause between calls, back off.
Note: the categories do not say when a word was coined, so "the last 10-15 years" is only approximate.

Usage (from the project root):  python tools/fetch_neologisms.py
"""
import json, os, re, sys, time, urllib.parse, urllib.request

UA = "OneRowKeyboard-dictionary-build/1.0 (open-source keyboard project)"
API = "https://en.wiktionary.org/w/api.php"
NAMES = {"en": "English", "fr": "French", "de": "German", "es": "Spanish", "pt": "Portuguese", "ru": "Russian", "uk": "Ukrainian"}
CATEGORIES = ("{L}_neologisms", "{L}_internet_slang")
PAUSE = 2.0

def call(params):
    url = API + "?" + urllib.parse.urlencode(dict(params, format="json", action="query"))
    for attempt in range(8):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:                       # rate limit (429 / HTML page) or network: wait and retry
            wait = 5 * (2 ** attempt)
            print(f"  retry in {wait}s ({type(e).__name__})", flush=True); time.sleep(wait)
    raise SystemExit("giving up on " + url)

def members(category):
    out = []; cont = {}
    while True:
        j = call(dict(list="categorymembers", cmtitle="Category:" + category, cmtype="page", cmlimit=500, **cont))
        out += [m["title"] for m in j["query"]["categorymembers"]]
        if "continue" not in j: return out
        cont = {"cmcontinue": j["continue"]["cmcontinue"]}; time.sleep(PAUSE)

if __name__ == "__main__":
    os.makedirs("tools/dict_src", exist_ok=True)
    for code, name in NAMES.items():
        rows = []
        for pat in CATEGORIES:
            cat = pat.format(L=name)
            titles = members(cat); time.sleep(PAUSE)
            single = [t for t in titles if re.fullmatch(r"[^\W\d_]+(['’\-][^\W\d_]+)*", t)]
            print(f"{code} {cat}: {len(titles)} pages, {len(single)} single words", flush=True)
            rows += [(t, cat) for t in single]
        seen = set(); lines = []
        for t, c in rows:
            if t not in seen: seen.add(t); lines.append(f"{t}\t{c}\n")
        open(f"tools/dict_src/{code}_neologisms.txt", "w", encoding="utf-8").writelines(lines)
        print(f"{code}: wrote {len(lines)} words", flush=True)
