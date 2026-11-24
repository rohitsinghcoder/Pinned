# Pinned

Minimal Android app that pins reminders directly into your notification shade and keeps them there until you mark them done.

100% offline, zero internet permission, no account, no ads, and zero third-party libraries — built purely with Kotlin and Jetpack Compose (Material 3).

[![Download Latest APK](https://img.shields.io/github/v/release/rohitsinghcoder/Pinned?label=Download%20APK&style=for-the-badge)](https://github.com/rohitsinghcoder/Pinned/releases/latest)

## Features

- **Persistent pinned notifications** — Swipe a pin away (or hit *Clear All*) and it quietly re-posts itself until you mark it done in the app.
- **Survives reboots & OS timeouts** — Automatically restores pins after a reboot or app update, with a 6-hour background watchdog that refreshes pins every 24 hours so aggressive OS notification timeouts (like ColorOS/OxygenOS 72h auto-expiry) never drop them.
- **Timed full-screen reminders** — Set a quick preset, pick a time on the clock dial, or enter a custom timer. When it fires, Pinned shows a full-screen vibration-only alert (over the lock screen or over your active app) with tap-to-silence, snooze (`+5m`, `+10m`, `+15m`, or custom), *Keep pinned*, and a 0.8s *Hold to mark done* button to prevent accidental dismissal.
- **1-tap Quick Settings tile** — Add the **Pinned** tile to your Quick Settings shade to pin a thought from anywhere and see your active pin count.
- **Share-to-Pin** — Select text or a link in any app and share it directly to **Pinned**.
- **Clean, fast UI** — Keyboard-attached composer, long-press drag to reorder, confirm-before-done, 60-day completed history, and four appearance modes (**System**, **Light**, **Dark**, and **AMOLED black**).

## Download & Install (Android 8.0+)

1. Open the **[Latest Release](https://github.com/rohitsinghcoder/Pinned/releases/latest)** page on your Android phone.
2. Under **Assets**, tap **`Pinned-v1.0.apk`** (or the latest version) to download it.
3. Open the downloaded `.apk` file from your browser or Files app.
4. If Android asks, tap **Settings** and turn on **Allow from this source** (install unknown apps) for your browser or Files app, then go back and tap **Install**.
5. If **Google Play Protect** shows a warning saying the developer is unrecognized, tap **More details → Install anyway**. *(This warning appears for any independent app installed outside the Play Store. Pinned is open source and requests zero internet permission.)*
6. Open **Pinned** and grant **Notifications** permission when prompted. If you want timed full-screen reminders and maximum reliability across reboots, follow the in-app prompts for **Exact alarms**, **Display over other apps**, and **Unrestricted battery**.

### Updating later

Whenever a new version is released, simply download the newer `.apk` from **[Releases](https://github.com/rohitsinghcoder/Pinned/releases/latest)** and install it directly over your existing app—your active pins and history are preserved.

## Permissions Explained

Pinned requests **no internet permission** (`android.permission.INTERNET` is not in the manifest)—your data never leaves your phone.

| Permission | Why it's used |
|---|---|
| `POST_NOTIFICATIONS` | Display your pinned reminders in the notification shade |
| `RECEIVE_BOOT_COMPLETED` | Restore active pins and reschedule timers after a phone reboot |
| `SCHEDULE_EXACT_ALARM` | Fire timed reminders at the exact minute/second you choose |
| `USE_FULL_SCREEN_INTENT` | Show full-screen timed reminders on the lock screen |
| `SYSTEM_ALERT_WINDOW` | Show full-screen timed reminders over your current app while unlocked (optional) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent OEM battery killers from dropping your pinned notifications (optional) |
| `VIBRATE` & `WAKE_LOCK` | Vibrate and wake the screen briefly when a timed reminder fires |

## Build from Source

### Requirements
- JDK 17
- Android SDK (compileSdk 36, build-tools 36.0.0)

### Build a debug APK
```bash
git clone https://github.com/rohitsinghcoder/Pinned.git
cd Pinned
./gradlew assembleDebug
```
*(On Windows PowerShell/CMD, run `.\gradlew.bat assembleDebug`.)*

### Build a signed release APK
Create a `keystore.properties` file in the project root (already gitignored) or set the equivalent environment variables (`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`):

```properties
storeFile=/path/to/your-release.jks
storePassword=your-store-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

Then run:
```bash
./gradlew assembleRelease
```

The signed APK is generated at `app/build/outputs/apk/release/app-release.apk`.

## License

[MIT](LICENSE)
