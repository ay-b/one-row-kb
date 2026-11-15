"""Builds app/src/main/assets/dict/<lang>_uni.tsv and <lang>_bi.tsv for every supported language.

Unigrams: wordfreq (token frequency), casing recovered from the corpus (German nouns, names, "I").
Bigrams: Tatoeba sentences (conversational) [+ NLTK corpora for English]; "^" = sentence start.
LICENSES (ignored for dev, MUST be resolved before release): wordfreq data mixes CC sources (see its
NOTICE); Tatoeba sentences are CC BY 2.0 FR (attribution required); NLTK corpora have own terms.

Usage: python build_dict.py <out_dir> <tatoeba_dir> [lang ...]
"""
import re, sys, bz2, collections
from wordfreq import top_n_list, word_frequency

OUT, TAT = sys.argv[1], sys.argv[2]
ONLY = sys.argv[3:]

LANGS = {
    "en": dict(wf="en", tat="eng", extra="'", n=80000, single="ai"),
    "fr": dict(wf="fr", tat="fra", extra="àâäçéèêëîïôöùûüÿœæ'-", n=60000, single="ay", elide=True),
    "de": dict(wf="de", tat="deu", extra="äöüß", n=60000, single=""),
    "es": dict(wf="es", tat="spa", extra="áéíóúüñ", n=60000, single="aeouy"),
    "pt": dict(wf="pt", tat="por", extra="áâãàçéêíóôõú", n=60000, single="aeo"),
    "ru": dict(wf="ru", tat="rus", extra="ё", n=60000, single="авикосуяжб"),
    "uk": dict(wf="uk", tat="ukr", extra="ґ'", n=60000, single="аівйузоя"),
}
BASE = {
    "en": "abcdefghijklmnopqrstuvwxyz", "fr": "abcdefghijklmnopqrstuvwxyz", "de": "abcdefghijklmnopqrstuvwxyz",
    "es": "abcdefghijklmnopqrstuvwxyz", "pt": "abcdefghijklmnopqrstuvwxyz", "ru": "абвгдежзийклмнопрстуфхцчшщъыьэюя",
    "uk": "абвгдежзиіїйклмнопрстуфхцчшщьюяє",
}
ELISION = {"l", "d", "j", "m", "t", "s", "n", "c", "qu", "jusqu", "lorsqu", "puisqu", "quoiqu"}
TOKEN = re.compile(r"[^\W\d_]+(?:['’][^\W\d_]+)*(?:-[^\W\d_]+)*")

def tokens(text, cfg):
    out = []
    for m in TOKEN.findall(text):
        m = m.replace("’", "'").replace("ʼ", "'")
        low = m.lower()
        if cfg.get("elide") and "'" in low:
            head, _, tail = low.partition("'")
            if head in ELISION and tail:
                out.append(m[: len(head)] + "'")
                m = m[len(head) + 1:]
        out.append(m)
    return out

def build(code, cfg):
    allowed = set(BASE[code] + cfg["extra"])
    def ok(w):
        if not w or any(ch not in allowed for ch in w): return False
        if w[0] in "'-" or w[-1] == "-": return False
        letters = [c for c in w if c not in "'-"]
        if len(letters) == 1 and w not in {s for s in cfg["single"]}: return False
        return True

    vocab = {}   # lowercase surface -> frequency
    for w in top_n_list(cfg["wf"], 400000):
        w = w.replace("’", "'").replace("ʼ", "'")        # one apostrophe everywhere (Ukrainian п'ять)
        if cfg.get("elide") and w in ELISION:
            vocab[w + "'"] = word_frequency(w, cfg["wf"])
            continue
        if w.endswith("'"): continue
        if ok(w): vocab[w] = word_frequency(w, cfg["wf"])
        if len(vocab) >= cfg["n"]: break

    cased = collections.defaultdict(collections.Counter)
    big = collections.Counter(); uni = collections.Counter()
    with bz2.open(f"{TAT}/{cfg['tat']}.tsv.bz2", "rt", encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3: continue
            t = tokens(parts[2], cfg)
            prev = "^"
            for i, tok in enumerate(t):
                low = tok.lower()
                if low in vocab:
                    if i > 0: cased[low][tok] += 1          # sentence-initial caps say nothing
                    big[(prev, low)] += 1; uni[low] += 1; prev = low
                else:
                    prev = "^" if False else "~"             # unknown word: breaks the chain
    surface = {}
    for low in vocab:
        c = cased.get(low)
        s = low
        if c:
            top, n = c.most_common(1)[0]
            total = sum(c.values())
            if top != low and n >= 3 and n * 2 > total: s = top
        surface[low] = s

    with open(f"{OUT}/{code}_uni.tsv", "w", encoding="utf-8") as f:
        for low, fr in vocab.items():
            f.write(f"{surface[low]}\t{fr:.3e}\n")

    by_first = collections.defaultdict(list)
    for (a, b), c in big.items():
        if a == "~" or c < 3: continue
        denom = uni[a] if a != "^" else sum(v for (x, _), v in big.items() if x == "^") if False else None
        by_first[a].append((b, c))
    starts_total = sum(c for (a, _), c in big.items() if a == "^")
    n = 0
    with open(f"{OUT}/{code}_bi.tsv", "w", encoding="utf-8") as f:
        for a, lst in by_first.items():
            denom = starts_total if a == "^" else uni[a]
            if not denom: continue
            lst.sort(key=lambda x: -x[1])
            for b, c in lst[:10]:
                f.write(f"{a}\t{b}\t{c / denom:.4f}\n"); n += 1
    print(code, "vocab", len(vocab), "bigrams", n, flush=True)

for code, cfg in LANGS.items():
    if ONLY and code not in ONLY: continue
    build(code, cfg)
