# Install CamGrid TV on your TV

CamGrid TV isn't on Google Play or the Amazon Appstore; you install it yourself ("sideload").
It takes about ten minutes the first time.

> **Status:** there is no downloadable release yet. Until the first release is published on the
> [Releases page](https://github.com/anhourtec/CamGrid-TV/releases), install it from a computer
> (option A). Option B (no computer) works as soon as a release exists.

## What you need

- A Google TV, Android TV (7.1 or newer) or Amazon Fire TV on the same Wi-Fi as your phone.
- Your BitVision account (the one you use in the BitVision phone app).
- For option A: a computer (Mac, Windows or Linux) on the same Wi-Fi.

## Step 1: Turn on developer options on the TV

| Google TV / Android TV | Fire TV |
| --- | --- |
| Settings → **System** → **About** → press OK on **Android TV OS build** 7 times, until "You are now a developer" appears. | Settings → **My Fire TV** → **About** → press OK on your device name 7 times, until "You are now a developer" appears. |

## Option A: Install from a computer

1. **Install Android's adb tool** on the computer:
   [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools)
   (macOS with Homebrew: `brew install android-platform-tools`).
2. **Turn on debugging on the TV.**
   - *Google TV / Android TV 11+:* Settings → System → **Developer options** → **Wireless debugging**
     → **Pair device with pairing code**. Keep this screen open; it shows an address, a port and a
     6-digit code.
   - *Fire TV and older Android TV:* Settings → My Fire TV (or System) → **Developer options** →
     turn on **ADB debugging**. Find the TV's IP address under Settings → Network.
3. **Connect the computer to the TV** (use the numbers shown on your TV):
   ```bash
   # Google TV / Android TV 11+ (wireless debugging)
   adb pair <TV_IP>:<PAIRING_PORT> <PAIRING_CODE>
   adb connect <TV_IP>:<CONNECT_PORT>      # often happens automatically after pairing

   # Fire TV / older Android TV
   adb connect <TV_IP>:5555                # then press "Allow" on the TV
   ```
   `adb devices` should now list your TV.
4. **Get the app.** Download `camgrid-tv.apk` from the
   [Releases page](https://github.com/anhourtec/CamGrid-TV/releases) once available, or build it
   yourself (needs JDK 17+ and the Android SDK):
   ```bash
   git clone https://github.com/anhourtec/CamGrid-TV && cd CamGrid-TV
   ./gradlew :app:assembleDebug            # → app/build/outputs/apk/debug/app-debug.apk
   ```
5. **Install it:**
   ```bash
   adb install -r camgrid-tv.apk           # or app/build/outputs/apk/debug/app-debug.apk
   ```
6. **Turn debugging off again** when you're done (Wireless debugging / ADB debugging). The app
   doesn't need it.

## Option B: Install without a computer (once a release exists)

1. On the TV, install **Downloader** by AFTVnews from the Google Play Store or the Amazon Appstore.
2. Allow it to install apps:
   - *Google TV:* Settings → Apps → **Security & restrictions** → **Unknown sources** → turn on
     **Downloader**.
   - *Fire TV:* Settings → My Fire TV → **Developer options** → **Install unknown apps** → turn on
     **Downloader**.
3. In Downloader, enter the APK link from the
   [Releases page](https://github.com/anhourtec/CamGrid-TV/releases), download it and press
   **Install**.

## Step 2: First start

1. Open **CamGrid TV** from your apps row.
2. The TV shows an address like `http://192.168.x.x:8743` and a 6-digit code. On your phone
   (same Wi-Fi), open that address, enter the code, then your BitVision email, password and the
   digits in the picture. (You can also sign in right on the TV.)
3. Your first cameras open in a 2×2 grid.

## Using it

| Remote | What it does |
| --- | --- |
| Arrows | Move between tiles; at the edge of the grid ◀ ▶ switch layouts |
| OK | Open the camera fullscreen in HD (Back returns); on a stalled tile, try again |
| Hold OK (or Menu) | Tile menu: choose camera, move, clear, grid size, layouts, edit from phone, sign out |
| Back | Close a card or dialog, leave fullscreen, then exit |

To set everything up from your phone: hold OK → **Edit layout from phone or computer**, open the
address on the card, enter its code, choose grids and cameras, and **Save to TV**.

## Updating and removing

- **Update:** install the new APK the same way (`adb install -r …`, or download it again in
  Downloader). Your sign-in and layouts are kept.
- **Remove:** Settings → Apps → CamGrid TV → **Uninstall**.
