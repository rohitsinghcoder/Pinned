#!/data/data/com.termux/files/usr/bin/bash
# Build, install over wireless adb, and launch Pinned.
set -e
cd "$(dirname "$0")"
gradle assembleDebug -q --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.pinned.app/.MainActivity
