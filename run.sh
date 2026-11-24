#!/usr/bin/env bash
# Build, auto-connect over ADB (5555 or mDNS Wireless Debugging), install, and launch Pinned.
set -euo pipefail
cd "$(dirname "$0")"

GRADLE_ARGS=(-q --console=plain)
if [ -x /data/data/com.termux/files/usr/bin/aapt2 ]; then
    GRADLE_ARGS+=("-Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2")
fi

if command -v gradle >/dev/null 2>&1 && [ -x /data/data/com.termux/files/usr/bin/aapt2 ]; then
    gradle assembleDebug "${GRADLE_ARGS[@]}"
else
    ./gradlew assembleDebug "${GRADLE_ARGS[@]}"
fi

if [ -x "$HOME/bin/adb-connect" ]; then
    export ANDROID_SERIAL="$("$HOME/bin/adb-connect")"
fi
timeout 90 adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.pinned.app/.MainActivity
