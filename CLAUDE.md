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
- `MainActivity.kt` — all UI: list (drag to reorder, confirm-before-done, tappable reminder badges), composer panel attached to the keyboard (with full-screen reminder presets, Google Clock time dial & custom timer numpad), History (60-day retention), appearance (System/Light/Dark/AMOLED), Share-to-Pin.
- `Notifier.kt` — ongoing notifications; `sync()` re-posts missing pins and refreshes pins ≥24h old to prevent ColorOS's 72h auto-timeout from expiring them.
- `Receivers.kt` — re-post on swipe (plus sibling healing), restore after boot/app update, and 6h `SyncJobService` (`JobScheduler`) watchdog.
- `Reminders.kt` — exact-alarm scheduling (`setAlarmClock`), clean button-free lock-screen full-screen notifications, and routing unlocked reminders to `ReminderOverlayService`.
- `ReminderActivity.kt` & `ReminderOverlayService.kt` — full-screen vibration-only reminder UI (Activity for lock screen, `TYPE_APPLICATION_OVERLAY` for unlocked takeover) with tap-to-silence, `+5m/+10m/+15m/Custom` snooze, Keep pinned, and 0.8s Hold-to-mark-done.
- `QuickPinTileService.kt` — Quick Settings tile to open the composer in 1 tap from anywhere.
- `Store.kt` — pins (with optional `remindAt`), 60-day completed history, and theme in SharedPreferences as JSON.
