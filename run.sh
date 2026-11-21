#!/data/data/com.termux/files/usr/bin/bash
# Build, auto-connect over ADB (5555 or mDNS Wireless Debugging), install, and launch Pinned.
set -euo pipefail
cd "$(dirname "$0")"
gradle assembleDebug -q --console=plain
export ANDROID_SERIAL="$("$HOME/bin/adb-connect")"
timeout 90 adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.pinned.app/.MainActivity
