[![CamGrid TV](.github/screenshots/menu.png)](https://github.com/anhourtec/CamGrid-TV)

<p align="center">
  <a href="https://github.com/anhourtec/CamGrid-TV">
    <h1 align="center">CamGrid TV</h1>
  </a>
  <p align="center">
    Watch your BitVision cameras on Google TV, Android TV and Fire TV.
    Live NVR-style grids, any camera from any recorder in any tile, set up from the remote or
    from your phone, and remembered on the TV. Native, fast and open source.
    <br />
    <br />
    <a href="#install-on-your-tv">Install</a>
    ·
    <a href="#features">Features</a>
    ·
    <a href="#screenshots">Screenshots</a>
    ·
    <a href="docs/README.md">Docs</a>
    ·
    <a href="docs/roadmap.md">Roadmap</a>
    ·
    <a href="https://github.com/anhourtec/CamGrid-TV/releases">Releases</a>
  </p>
</p>

<div align="center">

![Kotlin 2.4](https://img.shields.io/badge/Kotlin-2.4-7F52FF?labelColor=black&style=for-the-badge&logo=kotlin)
![Android TV 7.1+](https://img.shields.io/badge/Android_TV-7.1%2B-2563EB?labelColor=black&style=for-the-badge&logo=android)
![Google TV and Fire TV](https://img.shields.io/badge/Google_TV_·_Fire_TV-supported-2563EB?labelColor=black&style=for-the-badge)
![License Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-2563EB?labelColor=black&style=for-the-badge)

</div>

Built by [AnHourTec](https://anhourtec.com) in Victoria, BC, Canada.

> **Unofficial.** Not affiliated with, endorsed by or supported by BitVision. BitVision and all
> related software, services and trademarks belong to their owners. See [NOTICE.md](NOTICE.md).
> BitVision has apps for [Android phones](https://play.google.com/store/apps/details?id=com.gzch.lsplat.lsbtvapp&hl=en),
> [iPhone](https://apps.apple.com/in/app/bitvision/id1419463554) and the [web](https://bitvision.app/),
> but none for TVs. CamGrid TV fills that gap for your own account and cameras.

## Features

- **NVR-style grids:** 1, 2, 4, 6, 8, 9, 10, 13, 16, 25 and 36 tiles. Growing a grid fills the new
  tiles with your next cameras; shrinking it keeps your choices for later.
- **Any camera, any tile:** mix cameras from several NVRs in one grid. Hold OK on a tile to choose
  its camera (grouped by NVR), move it, clear it or change the grid size.
- **Several layouts:** "Home", "Shop", "Night"… switch with ◀ ▶ at the edge of the grid and pick
  the one that opens first.
- **Set up from your phone:** open the address shown on the TV, enter its code, then pick grids,
  cameras and layouts in a phone-friendly page. The TV updates the moment you save.
- **Sign in from your phone:** no typing with the remote. The BitVision picture code (CAPTCHA) is
  shown right in the app.
- **Fast and lean:** native Kotlin, hardware H.265/H.264 decoding straight to the screen, no
  WebView. Only the tiles that change restart.
- **Never an empty tile:** tiles beyond what your TV can play live show still images that refresh
  every few seconds, and go live as soon as a decoder frees up.
- **Remembers everything:** layouts, grid sizes, which camera sits where, and the last screen you
  watched.
- **Five languages:** English, 中文（简体）, 日本語, Português, Русский, on the TV and the phone page.
- **Private:** talks only to BitVision's cloud, like the official apps. Your password is used once
  to sign in and is never stored.

## Screenshots

|          ![Sign in](.github/screenshots/sign-in.png)           |       ![Camera grid](.github/screenshots/grid.png)        |
| :-----------------------------------------------------------: | :-------------------------------------------------------: |
|          _Sign in from your phone, or on the TV_              |         _A 2×2 grid of cameras from two NVRs_             |
|          ![Tile menu](.github/screenshots/menu.png)            |      ![Grid size](.github/screenshots/grid-size.png)      |
|          _Hold OK on a tile for its menu_                     |         _Every grid shape as a preview_                   |
|          ![Layouts](.github/screenshots/layouts.png)           | ![Edit from phone](.github/screenshots/phone-card.png)    |
|          _Several saved layouts_                              |         _Edit the layout from your phone_                 |

| ![Phone: enter the code](.github/screenshots/web-code.png) | ![Phone: layout editor](.github/screenshots/web-editor-phone.png) | ![Computer: layout editor](.github/screenshots/web-editor-desktop.png) |
| :---: | :---: | :---: |
| _Phone: enter the TV's code_ | _Phone: grid, cameras, layouts_ | _Computer: the same editor_ |

TV screenshots come from a real Google TV; **camera images are blurred for privacy** and the TV's
address is hidden. Phone and computer screenshots use made-up camera names.

## Install on your TV

CamGrid TV isn't on Google Play or the Amazon Appstore; you install it yourself ("sideload").
It takes about ten minutes the first time.

> **Status:** there is no downloadable release yet. Until the first release is published on the
> [Releases page](https://github.com/anhourtec/CamGrid-TV/releases), install it from a computer
> (option A). Option B (no computer) works as soon as a release exists.

### What you need

- A Google TV, Android TV (7.1 or newer) or Amazon Fire TV on the same Wi-Fi as your phone.
- Your BitVision account (the one you use in the BitVision phone app).
- For option A: a computer (Mac, Windows or Linux) on the same Wi-Fi.

### Step 1: Turn on developer options on the TV

| Google TV / Android TV | Fire TV |
| --- | --- |
| Settings → **System** → **About** → press OK on **Android TV OS build** 7 times, until "You are now a developer" appears. | Settings → **My Fire TV** → **About** → press OK on your device name 7 times, until "You are now a developer" appears. |

### Option A: Install from a computer

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

### Option B: Install without a computer (once a release exists)

1. On the TV, install **Downloader** by AFTVnews from the Google Play Store or the Amazon Appstore.
2. Allow it to install apps:
   - *Google TV:* Settings → Apps → **Security & restrictions** → **Unknown sources** → turn on
     **Downloader**.
   - *Fire TV:* Settings → My Fire TV → **Developer options** → **Install unknown apps** → turn on
     **Downloader**.
3. In Downloader, enter the APK link from the
   [Releases page](https://github.com/anhourtec/CamGrid-TV/releases), download it and press
   **Install**.

### Step 2: First start

1. Open **CamGrid TV** from your apps row.
2. The TV shows an address like `http://192.168.x.x:8743` and a 6-digit code. On your phone
   (same Wi-Fi), open that address, enter the code, then your BitVision email, password and the
   digits in the picture. (You can also sign in right on the TV.)
3. Your first cameras open in a 2×2 grid.

### Using it

| Remote | What it does |
| --- | --- |
| Arrows | Move between tiles; at the edge of the grid ◀ ▶ switch layouts |
| OK | Open the camera fullscreen in HD (Back returns); on a stalled tile, try again |
| Hold OK (or Menu) | Tile menu: choose camera, move, clear, grid size, layouts, edit from phone, sign out |
| Back | Close a card or dialog, leave fullscreen, then exit |

To set everything up from your phone: hold OK → **Edit layout from phone or computer**, open the
address on the card, enter its code, choose grids and cameras, and **Save to TV**.

### Updating and removing

- **Update:** install the new APK the same way (`adb install -r …`, or download it again in
  Downloader). Your sign-in and layouts are kept.
- **Remove:** Settings → Apps → CamGrid TV → **Uninstall**.

## Supported devices

| Platform | Support |
| --- | --- |
| **Google TV** (Chromecast with Google TV, Google TV Streamer, Sony, TCL, Hisense, Skyworth…) | Yes |
| **Android TV** 7.1+ (NVIDIA Shield, Xiaomi Mi Box, Philips, Sharp…) | Yes |
| **Amazon Fire TV** (Fire TV Stick, Stick 4K, Cube, Fire TV smart TVs) | Yes (sideload) |
| Samsung Tizen, LG webOS, Roku, Apple TV | No (not Android) |

How many tiles play live at once depends on your TV's video decoders; the rest show refreshing
still images. Tested devices and results: [docs/devices](docs/devices/).

## Questions people ask

**Is there a BitVision app for Android TV, Google TV or Fire TV?**
Not an official one, as far as we know. CamGrid TV is an unofficial, open-source app that does
this job.

**Can I watch BitVision cameras on my TV?**
That's what this project is for: live view of BitVision NVR/DVR cameras on Google TV, Android TV
and Fire TV (Firestick), with multi-camera grids.

**Can I install the BitVision phone app on my TV instead?**
You can try to sideload the phone APK, but phone apps are made for touch screens and usually work
poorly with a TV remote. CamGrid TV is built for the remote from the start.

**Does it work with my NVR or DVR brand?**
If your recorder shows up in your BitVision account (the BitVision app or
[bitvision.app](https://bitvision.app/)), CamGrid TV aims to support it.

**Does it need my NVR's IP address or port forwarding?**
No. Like the official apps, it uses your BitVision cloud account.

**Do I need a browser or a computer to sign in?**
No. BitVision asks for a short picture code (CAPTCHA) when you sign in. CamGrid TV shows that
picture on the TV, or on your phone if you sign in from your phone, and you type the digits. Nothing
else to install.

**Will it log me out after a while, like the website?**
No. Your cameras keep playing even if the website session expires, and the app keeps the session
alive in the background. You only sign in again if the TV was off for a long time, and even then
live view keeps working.

**Why do some tiles show "Still image"?**
Each live camera needs a video decoder, and TVs have only a few: a budget Google TV may play 4 at
once (2 in hardware, 2 in software). Tiles beyond that show a still picture that refreshes every
few seconds, and go live as soon as a decoder frees up.
Streamers like the Chromecast with Google TV 4K, Google TV Streamer, NVIDIA Shield or Fire TV 4K
Max have more decoders and play more tiles live.

**Is it safe? Where does my password go?**
Only to BitVision's sign-in, once. It is never saved; only the session is kept, encrypted on the
TV. Phone pairing uses a short-lived, single-use code on your local network. See
[docs/security.md](docs/security.md).

**Is this official or affiliated with BitVision?**
No. See [NOTICE.md](NOTICE.md). Use it only with your own account and devices.

## Built with

- [Kotlin](https://kotlinlang.org/) 2.4 and coroutines, native Android (Views), no WebView
- Android **MediaCodec** hardware decoding straight to `SurfaceView`
- [OkHttp](https://square.github.io/okhttp/) 5 for HTTPS and WebSockets
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) for JSON
- A small built-in web server (plain `ServerSocket`, no framework) for the phone pages
- Gradle 9 with ktlint, detekt and Android Lint (zero issues policy), JUnit 6

## Project structure

```
app/                   Android TV app (screens, tiles, decoding, layouts on the TV)
bitvision-client/      Pure Kotlin client: portal sign-in, devices, live-view protocol
bitvision-client-cli/  Command-line tool to test the client on a computer
pairing/               LAN server for phone sign-in and the phone layout editor
layouts/               Layout model shared by the TV and the phone editor
docs/                  Spec, architecture, protocol, security, roadmap, devices, session logs
scripts/               Secret scanner used before every commit
.github/screenshots/   README screenshots (camera images blurred)
```

## Development

```bash
./gradlew test                                  # unit tests (all modules)
./gradlew ktlintCheck detekt :app:lintDebug     # lint (must report zero issues)
./gradlew :app:assembleDebug                    # build the TV app
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Full specification: [docs/SPEC.md](docs/SPEC.md) · architecture: [docs/architecture.md](docs/architecture.md)
· protocol notes: [docs/streaming.md](docs/streaming.md) · security: [docs/security.md](docs/security.md).

## Contributing

Issues and pull requests are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md). Security issues:
[SECURITY.md](SECURITY.md). Please never post account details, device serials, IP addresses or
camera images in issues.

## License

[Apache License 2.0](LICENSE) for this project's own code. BitVision, its apps, services and
trademarks belong to their respective owners. See [NOTICE.md](NOTICE.md).

## Support

- Questions and bugs: [GitHub issues](https://github.com/anhourtec/CamGrid-TV/issues)
- Made by [AnHourTec](https://anhourtec.com). If CamGrid TV helps you, star the repo and share it
  with anyone stuck watching their CCTV on a phone.

<!-- Keywords: BitVision TV app, BitVision Android TV, BitVision Google TV, BitVision Fire TV, BitVision Firestick, BitVision NVR on TV, BitVision DVR TV, watch CCTV on Google TV, NVR multi-camera grid Android TV, bitvision.app TV -->
