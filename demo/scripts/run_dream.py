import sys, os
from demo import *
DRY = os.environ.get("DRY") == "1"
STAGE = int(os.environ.get("STAGE", "99"))     # stop early while rehearsing
def S(n):
    if DRY: snap(n)
def pause(t): time.sleep(t)
def T(label=""):
    if DRY: print(label, repr(get_text()), flush=True)
def w(word, gap=0.11):
    guard()
    for ch in word: letter(ch, 9); time.sleep(gap)
def shift(): tap(90, SUGGY); pause(0.25)
def run(words, end=None, shifted=(), fix=None):
    for i, word in enumerate(words):
        if word in shifted: shift()
        w(word); pause(0.12)
        if fix and word == fix[0]: pause(0.7); S("fix"); guess(fix[1]); pause(0.8); continue   # tap the offered guess
        if i < len(words) - 1: space()
    if end == ".": dot_tap()
    elif end == ",": comma_tap()
    pause(0.5)

random.seed(7)
pause(2.5)
# --- sentence 1: sloppy typing; 'go' comes out as 'to' -> tap the right guess in the bar
S1 = "I am happy to join with you today in what will go down in history as the greatest demonstration for freedom in the history of our nation".split()
for i, word in enumerate(S1):
    w(word); pause(0.12)
    if word == "go": pause(0.9); guess(0); pause(0.9); continue
    if i < len(S1) - 1: space()
dot_tap(); pause(0.6); T("1")
if STAGE <= 1: raise SystemExit
# --- emoji from the lens (hold comma, slide), then a space
emoji_lens(385); pause(0.8); space(); pause(0.4); S("emo1"); T("2")
if STAGE <= 2: raise SystemExit
# --- sentences 2 and 3 (shift before the proper nouns)
run("Five score years ago".split(), ","); run("a great American".split(), ",")
run("in whose symbolic shadow we stand today".split(), ",")
run("signed the Emancipation Proclamation".split(), ".", shifted=("Emancipation", "Proclamation")); T("3")
# --- full emoji palette: hold comma, swipe up, pick one
pause(0.5)
touch("DOWN", 222, ROWY); pause(0.8)
touch("MOVE", 222, ROWY - 50); touch("MOVE", 222, ROWY - 150); touch("MOVE", 222, ROWY - 260)
pause(0.4); touch("UP", 222, ROWY - 260); pause(1.8); S("pal")
tap(1012, 1468); pause(1.0); space(); T("4")
# --- last sentence
run("This momentous decree came as".split(), None); pause(1.0); S("typed"); T("5")
if STAGE <= 5: raise SystemExit
# --- replace a word: tap just after "momentous", delete the word, type another one
TAP_X, TAP_Y = int(os.environ.get("TAPX", "395")), int(os.environ.get("TAPY", "1216"))
tap(TAP_X, TAP_Y); pause(1.0); S("caret")
delete_word(); pause(1.0); S("deleted")
run(["historic"], None); pause(1.0); S("retyped"); T("6")
tap(540, 1500); pause(1.2); S("final"); T("7")      # tap below the text: caret to the end
pause(2.0)

save_events(os.environ.get("EVENTS", "/tmp/cs/events.json"))
EXPECT = ("I am happy to join with you today in what will go down in history as the greatest demonstration for freedom in the history of our nation. \U0001F64F "
          "Five score years ago, a great American, in whose symbolic shadow we stand today, signed the Emancipation Proclamation. \U0001F607 "
          "This historic decree came as")
got = get_text()
print("RESULT", "PASS" if got == EXPECT else "FAIL", flush=True)
if got != EXPECT: print(repr(got)); raise SystemExit(1)
