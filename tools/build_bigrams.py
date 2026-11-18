"""Builds <lang>_bi.tsv (bigram probabilities) for a compiled dictionary <dir>/<lang>.lex from Tatoeba sentences.

Only words that are in the .lex take part, so the pair file never mentions an unknown word. "^" = sentence start;
a pair is kept when it occurs at least 3 times; the 10 best followers of every word are written, as P(b | a).
Display casing is not touched (the .lex keeps its own); lookups are on lower-cased forms.

Usage: python tools/build_bigrams.py <dict_dir> <tatoeba_dir> [lang ...]
  <tatoeba_dir> holds <tatoeba code>.tsv.bz2 (https://downloads.tatoeba.org/exports/per_language/<code>/<code>_sentences.tsv.bz2)
Licence: Tatoeba sentences are CC BY 2.0 FR (see NOTICE-dictionaries.md).
"""
import bz2, collections, json, os, re, struct, sys

TAT = {"it": "ita", "nl": "nld", "sv": "swe", "da": "dan", "nb": "nob", "fi": "fin", "is": "isl", "pl": "pol", "cs": "ces",
       "sk": "slk", "sl": "slv", "hr": "hrv", "hu": "hun", "ro": "ron", "el": "ell", "et": "est", "lv": "lvs", "lt": "lit", "ca": "cat", "he": "heb"}
TOKEN = re.compile(r"[^\W\d_]+(?:['’][^\W\d_]+)*(?:-[^\W\d_]+)*")

def read_words(path):
    b = open(path, "rb").read()
    assert b[:4] == b"OLX1"
    nodes, edges, words, pool, tops = struct.unpack_from("<5i", b, 4)
    o = 24 + nodes + edges + nodes
    lens = b[o:o + words]; o += words
    out = []; p = o
    for n in lens: out.append(b[p:p + n].decode("utf-8")); p += n
    return out

def build(code, dict_dir, tat_dir):
    vocab = {w.lower() for w in read_words(f"{dict_dir}/{code}.lex")}
    big = collections.Counter(); uni = collections.Counter()
    with bz2.open(f"{tat_dir}/{TAT[code]}.tsv.bz2", "rt", encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3: continue
            prev = "^"
            for m in TOKEN.findall(parts[2]):
                low = m.replace("’", "'").replace("ʼ", "'").lower()
                if low in vocab: big[(prev, low)] += 1; uni[low] += 1; prev = low
                else: prev = "~"
    by_first = collections.defaultdict(list)
    for (a, b), c in big.items():
        if a != "~" and c >= 3: by_first[a].append((b, c))
    starts = sum(c for (a, _), c in big.items() if a == "^")
    n = 0
    with open(f"{dict_dir}/{code}_bi.tsv", "w", encoding="utf-8") as f:
        for a, lst in by_first.items():
            denom = starts if a == "^" else uni[a]
            if not denom: continue
            lst.sort(key=lambda x: -x[1])
            for b, c in lst[:10]: f.write(f"{a}\t{b}\t{c / denom:.4f}\n"); n += 1
    print(code, "pairs", n, "sentences-words", sum(uni.values()), flush=True)

if __name__ == "__main__":
    d, t = sys.argv[1], sys.argv[2]
    for code in (sys.argv[3:] or list(TAT)):
        if os.path.exists(f"{d}/{code}.lex") and os.path.exists(f"{t}/{TAT[code]}.tsv.bz2"): build(code, d, t)
