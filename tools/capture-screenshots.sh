#!/usr/bin/env bash
# Runs inside the emulator job. Fills FloatNote with sample notes and saves screenshots
# to ./shots. Uses only adb; the notes are created through the app's own Share target.
set -euo pipefail
PKG=io.github.sachin102492.floatnote.debug
APK=floating-notepad/app/build/outputs/apk/foss/debug/app-foss-debug.apk
mkdir -p shots

shot() { sleep "${2:-2}"; adb exec-out screencap -p > "shots/$1.png"; echo "captured $1"; }
share() { # title, body
  adb shell am start -W -a android.intent.action.SEND -t text/plain \
    --es android.intent.extra.SUBJECT "'$1'" --es android.intent.extra.TEXT "'$2'" \
    -n $PKG/io.github.sachin102492.floatnote.ui.CaptureActivity >/dev/null
  sleep 1
}
tap_desc() { # tap the centre of the first view whose content-desc contains $1
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null
  local b; b=$(adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep -F "content-desc=\"$1" | head -1 | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/')
  [ -n "$b" ] || { echo "no view with desc $1"; return 1; }
  set -- $b; adb shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 ))
}
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null
  local b; b=$(adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep -F "text=\"$1" | head -1 | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/')
  [ -n "$b" ] || { echo "no view with text $1"; return 1; }
  set -- $b; adb shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 ))
}

adb install -r "$APK"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell cmd uimode night no || true

# Sample notes, oldest first, so the list reads naturally.
share "Shopping" "Milk, bread, eggs
Phone recharge Rs 299
Pick up printouts"
share "Meeting - project review" "Demo moved to Friday 4 PM
Rahul sends the slides by Thursday
Ask about the lab booking"
share "Ideas" "Floating notes while watching lectures
Save quotes from articles in one tap"
share "Lecture notes - Transformers" "Efficiency is maximum when copper loss = core loss
All-day efficiency uses energy, not power
Open-circuit test gives core loss; short-circuit test gives copper loss"

# 1. Notes list
adb shell am start -W -n $PKG/io.github.sachin102492.floatnote.ui.MainActivity >/dev/null
shot 1-notes-list 3

# 2. Search
tap_desc "Search notes" || true
sleep 1; adb shell input text "efficiency"; adb shell input keyevent 111 || true
shot 2-search 2
adb shell input keyevent 4; sleep 1

# 3. Editor
adb shell am force-stop $PKG
adb shell am start -W -n $PKG/io.github.sachin102492.floatnote.ui.MainActivity >/dev/null
sleep 2
tap_text "Lecture notes" || true
shot 3-editor 3
adb shell input keyevent 4; sleep 1

# 4. Bubble floating over another app (Settings), then the notepad opened from it
adb shell am start -W -n $PKG/io.github.sachin102492.floatnote.ui.MainActivity --ez start_bubble true >/dev/null
sleep 3
adb shell am start -W -a android.settings.SETTINGS >/dev/null
shot 4-bubble-over-app 3
adb shell dumpsys window windows | grep -iE "Window #|mFrame|floatnote" | grep -iB1 -A1 floatnote | head -20 || true
if ! tap_desc "FloatNote bubble"; then
  # Fall back to the notification: tapping it opens the notepad.
  adb shell cmd statusbar expand-notifications; sleep 2
  tap_text "FloatNote is floating" || tap_text "Tap to open your note" || true
fi
shot 5-notepad-over-app 3
ls -la shots
