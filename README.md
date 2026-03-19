# BluOS Legacy Android Remote

A fullscreen kiosk remote control for BluOS players (Bluesound / NAD), targeting **Android 2.3 (API 9)** and up.

## Features

- Play / Pause / Skip / Back transport controls
- Volume up / down with live level display
- Track title, artist, album display
- Polls `/Status` every 2 seconds for live updates
- **Kiosk mode**: fullscreen, screen always on, shows over lock screen
- **Launcher**: declare as default home → Home button always returns to this app
- **Auto-start on boot** via `BOOT_COMPLETED` broadcast receiver

---

## Building

### Requirements

- Android Studio (Hedgehog / 2023.1+) **or** command-line Android SDK + JDK 11
- Android SDK with `compileSdkVersion 33` installed

### Steps

1. **Open in Android Studio** — `File → Open` → select this directory. Android Studio will sync Gradle automatically.

2. **Or build from the command line**
   ```
   # First time: Android Studio generates gradle-wrapper.jar automatically.
   # If building from CLI only, first run: gradle wrapper --gradle-version 7.5
   ./gradlew assembleDebug
   ```

3. **Output APK**: `app/build/outputs/apk/debug/app-debug.apk`

### Install via ADB

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

If USB debugging is enabled this installs directly over the cable.
On very old devices you can also copy the APK to the SD card and install from
a file manager (enable "Unknown sources" in Settings → Security first).

---

## First-time setup on the device

1. **Launch the app** — it opens to the main remote screen.
2. **Tap SETTINGS** → enter the BluOS player's local IP address (e.g. `192.168.1.50`).
   Confirm the port is `11000`. Tap **SAVE**.
3. The app begins polling immediately. Track info appears within 2 seconds.

### Setting as the default launcher (kiosk lock-down)

1. Press the physical **Home** button.
2. Android shows a "Select a launcher" dialog. Choose **BluOS Remote**.
3. Select **Always** (not "Just once").

From that point, Home always returns to the remote and reboot auto-starts it.

To exit kiosk mode and restore the original launcher:
`Settings → Applications → Manage applications → BluOS Remote → Clear defaults`

---

## BluOS API endpoints used

| Endpoint | Description |
|---|---|
| `GET /Status` | XML: state, title, artist, album, volume |
| `GET /Play` | Resume playback |
| `GET /Pause` | Pause playback |
| `GET /Skip` | Next track |
| `GET /Back` | Previous track |
| `GET /Volume?level=N` | Set volume 0–100 |

Base URL: `http://<player-ip>:11000`

---

## Project structure

```
app/src/main/
├── AndroidManifest.xml
├── java/com/bluesound/legacy/
│   ├── BluOSStatus.java       — data class for parsed status
│   ├── BluOSClient.java       — HTTP + XML parsing (no 3rd-party libs)
│   ├── BootReceiver.java      — launches app on BOOT_COMPLETED
│   ├── MainActivity.java      — kiosk UI + 2 s polling loop
│   └── SettingsActivity.java  — IP/port configuration
└── res/
    ├── layout/
    │   ├── activity_main.xml
    │   └── activity_settings.xml
    └── values/
        ├── strings.xml
        ├── colors.xml
        └── styles.xml         — Theme.Black.NoTitleBar.Fullscreen
```
