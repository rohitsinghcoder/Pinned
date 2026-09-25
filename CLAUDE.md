# Pinned

Minimal Android app that pins reminders as notifications that stay until marked done in the app.
Kotlin + Jetpack Compose (Material 3), no third-party libraries. Built and run entirely on the
phone (OnePlus 15, Termux).

## Build & run
- `./run.sh` — builds the debug APK, installs it over adb (`localhost:5555`) and launches it.
- adb runs in TCP mode on a fixed port. After a phone reboot it must be re-enabled: connect once
  via the Wireless debugging port, then `adb tcpip 5555`.
- Installing via the system installer (termux-open / Files app) fails with "problem parsing the
  package" — always install with adb.

## Layout (`app/src/main/java/com/pinned/app/`)
- `MainActivity.kt` — all UI: list (drag to reorder, confirm-before-done), composer panel attached
  to the keyboard, Done history (today/yesterday), appearance (System/Light/Dark/AMOLED).
- `Notifier.kt` — ongoing notifications; a delete intent re-posts a pin if it is swiped away.
- `Receivers.kt` — re-post on swipe, restore after boot/app update.
- `Store.kt` — pins, done history and theme in SharedPreferences as JSON.
