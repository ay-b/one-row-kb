#!/bin/zsh
# usage: shotprep.sh <full:true|false> <cur lang> [langs]   -> fresh notepad with OneRow in that state, keyboard open
E=emulator-5556; A(){ adb -s $E shell "$@"; }
FULL=$1; CUR=$2; LANGS=${3:-en,ru}
A am force-stop demo.notepad; A am force-stop one.yb.onerow
A "run-as one.yb.onerow sh -c 'rm -f /data/user_de/0/one.yb.onerow/files/user_*.txt'"
A "run-as one.yb.onerow sh -c 'printf \"<?xml version=\\\"1.0\\\" encoding=\\\"utf-8\\\" standalone=\\\"yes\\\" ?><map><string name=\\\"layout\\\">docked</string><string name=\\\"langs\\\">$LANGS</string><string name=\\\"cur\\\">$CUR</string><boolean name=\\\"full_keyboard\\\" value=\\\"$FULL\\\" /><boolean name=\\\"swipe_typing\\\" value=\\\"true\\\" /></map>\" > /data/user_de/0/one.yb.onerow/shared_prefs/onerow.xml'"
A settings put secure stylus_handwriting_enabled 0
A input keyevent KEYCODE_HOME; sleep 1
A ime enable one.yb.onerow/com.example.onerow.OneRowService >/dev/null; A ime set one.yb.onerow/com.example.onerow.OneRowService >/dev/null
A am start -n demo.notepad/demo.notepad.Main >/dev/null; sleep 3
A input tap 540 700; sleep 2.5
echo "shown: $(A dumpsys input_method | grep -o 'mInputShown=[a-z]*' | head -1)"
