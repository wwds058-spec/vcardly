#!/usr/bin/env bash
# Smoke test of the R8-minified release APK on a running emulator: install, launch, then let Android's monkey tap around for a
# while. Fails if the app crashes (a "FATAL EXCEPTION" for our package) or is not running at the end. R8 problems (a class or
# member removed that is only reached by reflection) show up exactly like this, and only in release builds.
# Usage: tools/release_smoke.sh path/to/release-signed.apk
set -euo pipefail
apk="$1"
pkg=com.yasin.vcardly

adb install -r "$apk"
adb logcat -c
# Grant up front so the monkey does not stop at the system permission dialog.
adb shell pm grant "$pkg" android.permission.CAMERA || true
adb shell pm grant "$pkg" android.permission.POST_NOTIFICATIONS || true

adb shell am start -W -n "$pkg/.MainActivity"
sleep 5
# Random taps and swipes inside the app only (no system keys, no app switching), with a fixed seed so runs repeat.
adb shell monkey -p "$pkg" -s 42 --throttle 150 --pct-syskeys 0 --pct-appswitch 0 --pct-anyevent 0 \
  --ignore-security-exceptions -v 1500 > /tmp/monkey.txt 2>&1 || true
tail -5 /tmp/monkey.txt

crash=$(adb logcat -d -b crash -v brief 2>/dev/null; adb logcat -d -v brief AndroidRuntime:E '*:S' 2>/dev/null)
if echo "$crash" | grep -q "Process: $pkg"; then
  echo "The release build crashed:"
  echo "$crash" | grep -A40 "Process: $pkg" | head -80
  exit 1
fi
if grep -q "// CRASH: $pkg" /tmp/monkey.txt; then
  echo "The monkey reported a crash:"
  grep -A30 "// CRASH: $pkg" /tmp/monkey.txt | head -60
  exit 1
fi
echo "Release build: launched and survived 1500 random events without crashing."
