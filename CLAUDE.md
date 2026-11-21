# Pinned

Minimal Android app that pins reminders as notifications that stay until marked done in the app.
Kotlin + Jetpack Compose (Material 3), no third-party libraries. Built and run entirely on the
phone (OnePlus 15, Termux).

## Build & run
- `./run.sh` — builds the debug APK, auto-connects via `~/bin/adb-connect`, installs, and launches.
- `~/bin/adb-connect` prefers `localhost:5555` (works offline); if unreachable (e.g. after reboot), it auto-discovers the Wireless Debugging port via mDNS (`_adb-tls-connect._tcp.local`), connects, and promotes it to `tcpip 5555`. No manual port typing needed as long as Wireless Debugging is on.
- Installing via the system installer (termux-open / Files app) fails with "problem parsing the
  package" — always install with adb.

## Layout (`app/src/main/java/com/pinned/app/`)
- `MainActivity.kt` — all UI: list (drag to reorder, confirm-before-done, reminder badges), composer panel attached to the keyboard (with optional full-screen reminder presets/time picker), Done history (today/yesterday), appearance (System/Light/Dark/AMOLED).
- `Notifier.kt` — ongoing notifications; `sync()` re-posts missing pins and refreshes pins ≥24h old to prevent ColorOS's 72h auto-timeout from expiring them.
- `Receivers.kt` — re-post on swipe (plus sibling healing), restore after boot/app update, and 6h `SyncJobService` (`JobScheduler`) watchdog.
- `Reminders.kt` — exact-alarm scheduling (`setAlarmClock`), silent high-priority full-screen notifications, and unlocked full-screen launch via `SYSTEM_ALERT_WINDOW`.
- `ReminderActivity.kt` — full-screen vibration-only reminder screen with `+5m/+10m/+15m` snooze, Keep pinned, and Mark done.
- `Store.kt` — pins (with optional `remindAt`), done history and theme in SharedPreferences as JSON.
