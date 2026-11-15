#!/bin/bash
e(){ adb -s emulator-5556 "$@"; }
e shell am force-stop demo.notepad
e shell am force-stop com.example.onerow
e shell run-as com.example.onerow rm -f files/user_en.txt shared_prefs/onerow_emoji.xml
e shell ime set com.example.onerow/.OneRowService >/dev/null
e shell am start -n demo.notepad/demo.notepad.Main >/dev/null
sleep 2.5
e shell ime set com.example.onerow/.OneRowService >/dev/null
e shell input tap 540 700
sleep 1.5
e shell dumpsys input_method | grep mCurMethodId
e shell dumpsys activity activities | grep topResumedActivity
