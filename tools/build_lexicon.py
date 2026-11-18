"""Builds app/src/main/assets/dict/<lang>.lex: the keyboard's word list as a compact binary letter trie.

What goes in
  * candidates: every alphabetic token of the wordfreq "large" list of the language, with its corpus frequency;
  * vetting: a word that is rare (zipf < ZIPF_TRUST) must be accepted by the language's Hunspell dictionary
    (as written, or with a capital letter: proper nouns, German nouns); common words are trusted as they are;
  * a word the dictionary rejects is still accepted when two corpora agree on it (wordfreq zipf >= 2.0 and at least
    CONSENSUS_MIN occurrences in the OpenSubtitles list of hermitdave/FrequencyWords) or, for English, when it is in the
    Aptivi/dwyl word list and zipf >= 2.0: this keeps modern words, names and slang that Hunspell does not know;
  * recent vocabulary: the Wiktionary neologism / internet-slang categories (tools/fetch_neologisms.py ->
    tools/dict_src/<lang>_neologisms.txt) are added whatever their frequency; English slang from Ashpex/Slang-Word
    only with corpus evidence (zipf >= 1.5). They are added after the size cap;
  * words that appear in <lang>_bi.tsv (the bigram file) are always included;
  * display casing: from <lang>_casing.tsv (casing recovered from corpora for the common words), else Hunspell's
    capitalized form, else lower case;
  * at most TARGET[lang] words, the most frequent first.

File format (little endian), version OLX1 -- read by engine/Lexicon.kt
  magic "OLX1"; int32 nodeCount, edgeCount, wordCount, poolBytes, topCount
  u8  childCount[nodeCount]      children per node; nodes are numbered breadth first, so the child reached by edge e
  u8  edgeLetter[edgeCount]      is node e+1 and the edges of a node are consecutive (letter indices, ascending)
  u8  wordsAt[nodeCount]         words whose letters-only key ends at the node (words are numbered in node order)
  u8  wordLen[wordCount]         UTF-8 length of each display form
  u8  pool[poolBytes]            the display forms
  i16 logUni[wordCount]          ln(frequency) * 1000
  i32 topNodes[topCount]         nodes with more than 4 words below them, ascending
  i32 topData[4 * topCount]      their 4 most frequent words, best first (-1 = none)

Usage (from the project root):
  python tools/build_lexicon.py <hunspell_dir> [--extras <dir>] [lang ...]
  <dir> (optional) holds aptivi/words-clean-alpha.txt, ashpex/slang.txt and freq_<lang>/<lang>_full.txt
  <hunspell_dir> holds en/en_US.{aff,dic} fr/fr.* de/de_DE_frami.* es/es_ES.* pt_BR/pt_BR.* pt_PT/pt_PT.* ru/ru_RU.* uk/uk_UA.*
  (LibreOffice dictionaries). Licenses of the dictionaries: see NOTICE-dictionaries.md.
"""
import glob, json, math, os, re, struct, sys, time
from collections import deque
import wordfreq
from wordfreq import top_n_list, word_frequency, zipf_frequency
from spylls.hunspell import Dictionary

OUT = "app/src/main/assets/dict"
ZIPF_TRUST = 3.0        # words at least this common are taken without a dictionary check
CONSENSUS_MIN = 30      # OpenSubtitles occurrences that, together with wordfreq zipf >= 2, vouch for a word
NEO_FLOOR = 3e-8        # frequency given to listed neologisms no corpus knows (zipf 1.5)
TOP_K = 4
HYPHEN_MIN = 30         # OpenSubtitles occurrences needed for a hyphenated word (когда-то, что-нибудь, well-known): wordfreq has none
SUB_MIN = 5             # OpenSubtitles occurrences needed for a word that only that list knows (non-bundled languages)

BASE = {
    "en": "abcdefghijklmnopqrstuvwxyz", "fr": "abcdefghijklmnopqrstuvwxyz", "de": "abcdefghijklmnopqrstuvwxyz",
    "es": "abcdefghijklmnopqrstuvwxyz", "pt": "abcdefghijklmnopqrstuvwxyz", "ru": "абвгдежзийклмнопрстуфхцчшщъыьэюя",
    "uk": "абвгдежзиіїйклмнопрстуфхцчшщьюяє",
    "he": "אבגדהוזחטיכלמנסעפצקרשת",
}
EXTRA = {"en": "'", "fr": "àâäçéèêëîïôöùûüÿœæ'-", "de": "äöüß", "es": "áéíóúüñ", "pt": "áâãàçéêíóôõú",
         "ru": "ё", "uk": "ґ'", "he": "ךםןףץ'"}
SINGLE = {"en": "ai", "fr": "ay", "de": "", "es": "aeouy", "pt": "aeo", "ru": "авикосуяжб", "uk": "аівйузоя", "he": ""}
TARGET = {"en": 150_000, "fr": 220_000, "de": 330_000, "es": 250_000, "pt": 220_000, "ru": 450_000, "uk": 350_000, "he": 350_000}
DICTS = {"en": ["en/en_US"], "fr": ["fr/fr"], "de": ["de/de_DE_frami"], "es": ["es/es_ES"],
         "pt": ["pt_BR/pt_BR", "pt_PT/pt_PT"], "ru": ["ru/ru_RU"], "uk": ["uk/uk_UA"], "he": ["he/he_IL"]}
# ---- languages whose dictionaries are not bundled in the app (see docs/on-demand-dictionaries.md) ----
# Their alphabets come from tools/languages.json (generated from Lang.kt); output goes to OPT_OUT.
OPT_OUT = "dictionaries"
NATIVE = {   # accented letters that belong to the language (everything else is a foreign word and is dropped)
    "it": "àèéìíîòóùú", "nl": "áäéèëïíóöúü", "sv": "é", "da": "é", "nb": "éèêóòôü", "fi": "šž", "is": "áéíóúý",
    "el": "άέήίόύώϊϋΐΰς", "pl": "ąćęłńóśźż", "cs": "áčďéěíňóřšťúůýž", "sk": "áäčďéíĺľňóôŕšťúýž",
    "sl": "čšž", "hr": "čćđšž", "hu": "áéíóöőúüű", "ro": "ăâîșț", "et": "šž", "lv": "āčēģīķļņšūž",
    "lt": "ąčęėįšųūž", "ca": "àçéèíïóòúü",
}
SINGLE_NEW = {"it": "aeio", "sv": "iåö", "da": "iå", "nb": "iå", "is": "áíóéý", "pl": "aiowuz", "cs": "aikosuvz",
              "sk": "aikosuvz", "sl": "aiksvz", "hr": "aiekosuz", "hu": "ae", "ro": "ae", "lv": "ai", "lt": "ai", "ca": "aiy",
              "nl": "", "fi": "", "el": "", "et": ""}
TARGET_NEW = {"it": 220_000, "nl": 200_000, "sv": 200_000, "da": 150_000, "nb": 150_000, "fi": 400_000, "is": 100_000,
              "el": 200_000, "pl": 300_000, "cs": 300_000, "sk": 200_000, "sl": 150_000, "hr": 200_000, "hu": 300_000,
              "ro": 200_000, "et": 150_000, "lv": 150_000, "lt": 150_000, "ca": 150_000}
FOLDS = {}
_lj = os.path.join(os.path.dirname(os.path.abspath(__file__)), "languages.json")
if os.path.exists(_lj):
    for _c, _d in json.load(open(_lj, encoding="utf-8")).items():
        if _c in BASE: continue
        BASE[_c] = _d["letters"]; EXTRA[_c] = NATIVE.get(_c, "") + "'-"; SINGLE[_c] = SINGLE_NEW.get(_c, "")
        TARGET[_c] = TARGET_NEW.get(_c, 150_000); FOLDS[_c] = _d["folds"]
        DICTS[_c] = None   # found at build time: <hunspell_dir>/<code>/*.aff
OLD = {"en", "fr", "de", "es", "pt", "ru", "uk", "he"}
for _c in EXTRA:
    if "-" not in EXTRA[_c]: EXTRA[_c] += "-"        # hyphenated words are words (their hyphen is not a key: Alphabet.key skips it)
def out_dir(code): return OUT if code in OLD else OPT_OUT

ELISION = {"l", "d", "j", "m", "t", "s", "n", "c", "qu", "jusqu", "lorsqu", "puisqu", "quoiqu"}

# ---- must match engine/Alphabet.kt exactly ----
def folds_for(code):
    if code in FOLDS: return dict(FOLDS[code])
    f = {}
    if code in ("en", "fr", "de", "es", "pt"):
        for to, chars in (("a", "àáâãäå"), ("c", "çćč"), ("e", "èéêëē"), ("i", "ìíîïī"), ("n", "ñń"), ("o", "òóôõöø"),
                          ("u", "ùúûüū"), ("y", "ýÿ"), ("s", "śš"), ("z", "źżž")):
            for c in chars: f[c] = to
        f["œ"] = "oe"; f["æ"] = "ae"; f["ß"] = "ss"
    if code == "ru": f["ё"] = "е"
    if code == "uk": f["ґ"] = "г"
    if code == "he":                                  # final forms count as the plain letters (the display form keeps the right one)
        for a, b in zip("ךםןףץ", "כמנפצ"): f[a] = b
    return f

def make_key(code):
    idx = {c: i for i, c in enumerate(BASE[code])}; folds = folds_for(code)
    def key(word):
        out = []
        for ch in word:
            c = ch.lower() if len(ch.lower()) == 1 else ch
            i = idx.get(c)
            if i is not None: out.append(i); continue
            for x in folds.get(c, ""):
                if x in idx: out.append(idx[x])
        return out
    return key

def hyphen_ok(w):
    """A hyphenated word worth keeping: at most three parts, no repetition (да-да) and no stutter (п-привет)."""
    parts = w.split("-")
    if len(parts) > 3 or any(not p for p in parts): return False
    return not any(a == b or (len(a) == 1 and b.startswith(a)) for a, b in zip(parts, parts[1:]))

def candidates(code, sub=None):
    """lowercase surface -> frequency, from wordfreq (same filtering rules as tools/build_dict.py) and, for the
    languages wordfreq does not cover (or only thinly), from the OpenSubtitles list [sub] (words seen >= SUB_MIN times)."""
    allowed = set(BASE[code] + EXTRA[code])
    def ok(w):
        if not w or any(ch not in allowed for ch in w): return False
        if w[0] in "'-" or w[-1] == "-": return False
        if code == "he" and (re.search("[ךםןףץ].", w) or re.search("[כמנפצ]$", w)): return False     # a final form only ever ends a word, and a word never ends in the plain form
        letters = [c for c in w if c not in "'-"]
        return len(letters) > 1 or w in set(SINGLE[code])
    vocab = {}
    wl = next((l for l in ("large", "small") if code in wordfreq.available_languages(l)), None)
    for w in (top_n_list(code, 10**7, wordlist=wl) if wl else []):
        w = w.replace("’", "'").replace("ʼ", "'")
        if code == "fr" and w in ELISION:
            vocab[w + "'"] = word_frequency(w, code, wordlist=wl); continue
        if w.endswith("'"): continue
        if ok(w): vocab[w] = word_frequency(w, code, wordlist=wl)
    if sub:
        counts = sub.get("__counts__", {})
        for w, f in sub.items():
            if w == "__counts__" or w in vocab or not ok(w): continue
            if "-" in w:                         # wordfreq has no hyphenated words at all; the subtitle list does
                if counts.get(w, 0) >= HYPHEN_MIN and hyphen_ok(w): vocab[w] = f
            elif code not in OLD and counts.get(w, 0) >= SUB_MIN: vocab[w] = f
    return vocab

def load_casing(code):
    p = f"tools/dict_src/{code}_casing.tsv"
    out = {}
    if os.path.exists(p):
        for line in open(p, encoding="utf-8"):
            w = line.rstrip("\n")
            if w: out[w.lower()] = w
    return out

def read_extras(code, extras):
    """(subtitle frequencies, English word list, English slang) from the downloaded lists; empty when [extras] is None."""
    sub, ok_en, slang = {}, set(), set()
    if not extras: return sub, ok_en, slang
    p = f"{extras}/freq_{code}/{code}_full.txt"
    if os.path.exists(p):
        total = 0; counts = {}
        for line in open(p, encoding="utf-8"):
            t = line.rstrip("\n").split(" ")
            if len(t) != 2 or not t[1].isdigit(): continue
            w = t[0].replace("’", "'").replace("ʼ", "'").lower(); n = int(t[1]); counts[w] = counts.get(w, 0) + n; total += n
        sub = {w: n / total for w, n in counts.items()}
        sub = {w: v for w, v in sub.items() if counts[w] >= 1}
        sub["__counts__"] = counts
    if code == "en":
        p = f"{extras}/aptivi/words-clean-alpha.txt"
        if os.path.exists(p): ok_en = {l.strip().lower() for l in open(p, encoding="utf-8") if l.strip()}
        p = f"{extras}/ashpex/slang.txt"
        if os.path.exists(p):
            for l in open(p, encoding="utf-8", errors="replace"):
                w = l.split("`")[0].strip().lower()
                if re.fullmatch(r"[a-z]{3,}", w): slang.add(w)
    return sub, ok_en, slang

def load_neologisms(code):
    p = f"tools/dict_src/{code}_neologisms.txt"
    return [l.split("\t")[0].strip() for l in open(p, encoding="utf-8")] if os.path.exists(p) else []

def bigram_words(code):
    s = set()
    if not os.path.exists(f"{out_dir(code)}/{code}_bi.tsv"): return s
    for line in open(f"{out_dir(code)}/{code}_bi.tsv", encoding="utf-8"):
        t = line.rstrip("\n").split("\t")
        if len(t) >= 3:
            for w in t[:2]:
                if w != "^": s.add(w)
    return s

def build_words(code, dict_dir, extras=None):
    t0 = time.time()
    sub, ok_en, slang = read_extras(code, extras); counts = sub.get("__counts__", {})
    vocab = candidates(code, sub); mandatory = bigram_words(code); casing = load_casing(code)
    names = DICTS[code] or [os.path.splitext(f)[0].replace(dict_dir + os.sep, "") for f in sorted(glob.glob(os.path.join(dict_dir, code, "*.aff")))]
    dicts = [Dictionary.from_files(os.path.join(dict_dir, d)) for d in names]
    print(f"{code}: hunspell {names or 'none'}, wordfreq {[l for l in ('large','small') if code in wordfreq.available_languages(l)][:1] or 'none'}, subtitles {len(counts)}", flush=True)
    def known(w):
        for d in dicts:
            try:
                if d.lookup(w): return True
            except Exception: pass
        return False
    kept = {}; vetted = trusted = rejected = agreed = 0
    for w, fr in vocab.items():
        z = math.log10(fr) + 9 if fr > 0 else 0
        disp = casing.get(w)
        hyphen = "-" in w and bool(dicts)         # a hyphenated word always has to pass the dictionary: the subtitle lists mix languages (Russian in the Ukrainian one)
        if (z >= ZIPF_TRUST and not hyphen) or w in mandatory:
            kept[w] = (disp or w, fr); trusted += 1; continue
        if known(w): kept[w] = (disp or w, fr); vetted += 1; continue
        cap = w[0].upper() + w[1:]
        if known(cap): kept[w] = (disp or cap, fr); vetted += 1; continue
        if z >= 2.0 and not hyphen and (w in ok_en or counts.get(w, 0) >= CONSENSUS_MIN):
            kept[w] = (disp or w, fr); agreed += 1; continue
        rejected += 1
    for w in mandatory:                                     # bigram words missing from wordfreq's list
        if w not in kept and all(ch in set(BASE[code] + EXTRA[code]) for ch in w):
            kept[w] = (casing.get(w, w), 1e-9)
    items = sorted(kept.values(), key=lambda x: -x[1])[:TARGET[code]]
    # recent vocabulary goes in after the cap
    allowed = set(BASE[code] + EXTRA[code]); have = {w.lower() for w, _ in items}; added = 0
    for w in load_neologisms(code) + sorted(w for w in slang if vocab.get(w, 0) > 0 and math.log10(vocab[w]) + 9 >= 1.5):
        lw = w.lower().replace("’", "'")
        if lw in have or not lw or any(ch not in allowed for ch in lw): continue
        fr = max(vocab.get(lw, 0), sub.get(lw, 0) if lw != "__counts__" else 0, NEO_FLOOR)
        items.append((casing.get(lw, lw), fr)); have.add(lw); added += 1
    print(f"{code}: candidates={len(vocab)} trusted={trusted} vetted-in={vetted} agreed={agreed} rejected={rejected} -> kept={len(items)-added} + recent={added} [{time.time()-t0:.0f}s]", flush=True)
    return items

def compile_trie(code, items):
    key = make_key(code)
    # words grouped by key path; trie as dict-of-dicts, then breadth-first numbering
    root = {}; words_at = {}
    nodes_children = [dict()]            # node id -> {letter: child id}  (ids assigned in creation order first)
    tmp_words = [[]]                     # node id -> [(freq, display)]
    for disp, fr in items:
        k = key(disp)
        if not k: continue
        n = 0
        for c in k:
            ch = nodes_children[n]
            if c not in ch:
                ch[c] = len(nodes_children); nodes_children.append(dict()); tmp_words.append([])
            n = ch[c]
        tmp_words[n].append((fr, disp))
    # breadth-first renumbering: children of a node become consecutive ids
    order = [0]; new_id = {0: 0}; q = deque([0]); edge_letters = []; child_count = []
    while q:
        n = q.popleft(); ch = nodes_children[n]
        child_count.append(len(ch))
        for letter in sorted(ch):
            c = ch[letter]; new_id[c] = len(order); order.append(c); q.append(c); edge_letters.append(letter)
    node_count = len(order)
    words = []; words_per = []; logq = []
    for old in order:
        lst = sorted(tmp_words[old], key=lambda x: -x[0])
        assert len(lst) < 256, "too many words at one node"
        words_per.append(len(lst))
        for fr, disp in lst:
            words.append(disp); logq.append(max(-32000, min(0, round(math.log(max(fr, 1e-12)) * 1000))))
    word_start = [0] * (node_count + 1)
    for i, c in enumerate(words_per): word_start[i + 1] = word_start[i] + c
    # per node: the TOP_K most frequent words in its subtree (stored only where the subtree holds more than TOP_K)
    edge_start = [0] * (node_count + 1)
    for i, c in enumerate(child_count): edge_start[i + 1] = edge_start[i] + c
    top = [None] * node_count; sub = [0] * node_count
    for n in range(node_count - 1, -1, -1):
        cand = list(range(word_start[n], word_start[n + 1]))
        cnt = len(cand)
        for e in range(edge_start[n], edge_start[n + 1]):
            c = e + 1; cnt += sub[c]; cand.extend(top[c])
        sub[n] = cnt
        cand.sort(key=lambda w: logq[w], reverse=True)
        top[n] = cand[:TOP_K]
    top_nodes = [n for n in range(node_count) if sub[n] > TOP_K]
    pool = bytearray(); lens = bytearray()
    for w in words:
        b = w.encode("utf-8"); assert len(b) < 256; pool += b; lens.append(len(b))
    out = bytearray(b"OLX1")
    out += struct.pack("<5i", node_count, len(edge_letters), len(words), len(pool), len(top_nodes))
    out += bytes(child_count) + bytes(edge_letters) + bytes(words_per) + bytes(lens) + bytes(pool)
    out += struct.pack(f"<{len(logq)}h", *logq)
    out += struct.pack(f"<{len(top_nodes)}i", *top_nodes)
    flat = []
    for n in top_nodes: flat += top[n] + [-1] * (TOP_K - len(top[n]))
    out += struct.pack(f"<{len(flat)}i", *flat)
    return bytes(out), dict(nodes=node_count, edges=len(edge_letters), words=len(words), pool=len(pool), tops=len(top_nodes))

if __name__ == "__main__":
    args = sys.argv[1:]; extras = None
    if "--extras" in args:
        k = args.index("--extras"); extras = args[k + 1]; del args[k:k + 2]
    dict_dir = args[0]; only = args[1:]
    for code in BASE:
        if only and code not in only: continue
        items = build_words(code, dict_dir, extras)
        data, st = compile_trie(code, items)
        os.makedirs(out_dir(code), exist_ok=True)
        path = f"{out_dir(code)}/{code}.lex"
        open(path, "wb").write(data)
        print(f"{code}: wrote {path} {len(data)/1e6:.2f} MB  {st}", flush=True)
