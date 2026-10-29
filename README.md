<h1 align="center">CamGrid TV</h1>

<p align="center">
  <b>Watch your BitVision cameras on Google TV, Android TV and Fire TV.</b><br>
  Live NVR camera grids (4, 9, 16 and more) on the big screen. Native, fast and open source.
</p>

<p align="center">
  <a href="https://github.com/anhourtec/CamGrid-TV/stargazers"><img src="https://img.shields.io/github/stars/anhourtec/CamGrid-TV?style=flat&color=cfffcf&labelColor=15181e" alt="Stars"></a>
  <a href="https://github.com/anhourtec/CamGrid-TV/releases"><img src="https://img.shields.io/github/v/release/anhourtec/CamGrid-TV?include_prereleases&style=flat&color=cfffcf&labelColor=15181e&label=release" alt="Release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-cfffcf?style=flat&labelColor=15181e" alt="License: Apache-2.0"></a>
  <img src="https://img.shields.io/badge/platform-Google%20TV%20%C2%B7%20Android%20TV%20%C2%B7%20Fire%20TV-cfffcf?style=flat&labelColor=15181e" alt="Platforms: Google TV, Android TV, Fire TV">
  <img src="https://img.shields.io/badge/status-early%20development-ffd27f?style=flat&labelColor=15181e" alt="Status: early development">
</p>

<p align="center">
  Built and maintained by <a href="https://github.com/anhourtec"><b>anhourtec</b></a> ·
  <a href="https://github.com/anhourtec/CamGrid-TV">github.com/anhourtec/CamGrid-TV</a>
</p>

> **Unofficial.** Not affiliated with, endorsed by or supported by BitVision. BitVision and all
> related software, services and trademarks belong to their owners. See [NOTICE.md](NOTICE.md).

## Start here

| You want to | Go to |
|---|---|
| **Know if it works yet** | [Status](#status): early development, not ready to install yet. |
| **Watch BitVision cameras on a TV** | [What you get](#what-you-get) and [Supported devices](#supported-devices). |
| **Install it (when released)** | [Install](#install): sideload over Wi-Fi, no Play Store needed. |
| **Use BitVision on a phone instead** | The official apps: [Android (Google Play)](https://play.google.com/store/apps/details?id=com.gzch.lsplat.lsbtvapp&hl=en) · [iPhone/iPad (App Store)](https://apps.apple.com/in/app/bitvision/id1419463554) · [Web portal](https://bitvision.app/) |
| **Help build it** | [Contributing](CONTRIBUTING.md) and the [roadmap](docs/roadmap.md). |

[Why this exists](#why-this-exists) · [What you get](#what-you-get) · [Status](#status) ·
[Supported devices](#supported-devices) · [Install](#install) · [How it works](#how-it-works) ·
[Build and run](#build-and-run-on-your-tv-developers) · [Questions people ask](#questions-people-ask) · [Contributing](#contributing) · [Credits](#credits)

## Why this exists

BitVision has apps for [Android phones](https://play.google.com/store/apps/details?id=com.gzch.lsplat.lsbtvapp&hl=en),
[iPhone](https://apps.apple.com/in/app/bitvision/id1419463554) and the [web](https://bitvision.app/),
but **no app for TVs**. If you have BitVision NVRs or DVRs for home or shop CCTV, there has been
no good way to put your cameras on the living-room or shop-counter TV.

CamGrid TV fills that gap: sign in with **your own** BitVision account and see every camera from
every NVR on the account, in the grid layouts you know from an NVR monitor.

## What you get

| | |
|---|---|
| **NVR-style grids** | 1, 2, 4, 6, 8, 9, 10, 13, 16, 25 and 36 tiles. Bigger grids fill themselves with your next cameras. |
| **Any camera, any tile** | Mix cameras from several NVRs in one grid. On the TV: hold OK on a tile to choose a camera, move it or change the grid size. |
| **Edit from your phone** | Open the address shown on the TV, enter the code, pick the grid and a camera for every tile, save: the TV updates at once. |
| **Fullscreen in HD** | Press OK on a tile for the high-quality stream; Back returns to the grid. |
| **Easy sign-in** | Open the address shown on the TV on any phone or laptop on the same Wi-Fi, enter the 6-digit code, sign in there. No typing with the remote. |
| **Your language** | English, 中文（简体）, 日本語, Português, Русский on the TV and the phone page. |
| **Remembers everything** | Your layouts, which camera sits in which tile, grid sizes and the last screen you watched are kept on the TV. |
| **Tour mode** | Cycle through pages or layouts automatically. |
| **Remote-first** | Every action works with a basic D-pad remote. Dark, minimal, video first. |
| **Fast** | Native Kotlin with hardware H.265/H.264 decoding straight to the screen. No WebView, no browser inside. |
| **Private** | Talks only to BitVision's cloud, like the official apps. Your password is used once to sign in and is never stored. |

Screenshots will be added once the first build is ready.

## Status

**Early development.** Nothing to install yet. Progress is tracked in [docs/progress.md](docs/progress.md)
and [docs/roadmap.md](docs/roadmap.md).

| Phase | What | State |
|---|---|---|
| 0 | Learn how the BitVision web portal streams video | ✅ done |
| 1 | Kotlin client library | ✅ done |
| 2 | Native video playback proof of concept | ✅ done |
| 3 | TV app + sign-in (TV and phone pairing) | ✅ mostly done |
| 4 | Grids, layout editor (TV + phone), fullscreen, tour mode | in progress |
| 5 | Performance polish + first release APK | planned |

Watch or star the repo to hear about the first release.

## Supported devices

| Platform | Support |
|---|---|
| **Google TV** (Chromecast with Google TV, Google TV Streamer, Sony, TCL, Hisense Google TVs…) | Target |
| **Android TV** 7.1+ (NVIDIA Shield, Xiaomi Mi Box, Philips, Sharp…) | Target |
| **Amazon Fire TV** (Fire TV Stick, Stick 4K, Cube, Fire TV smart TVs) | Target (sideload) |
| Samsung Tizen, LG webOS, Roku, Apple TV | Not supported (not Android) |

How many cameras play live at once depends on your device's video decoder. Tiles beyond that
show a still image until you select them.

## Install

Sideload only; not on Google Play or the Amazon Appstore. Full guide: [docs/install.md](docs/install.md).

```bash
adb connect <TV_IP>:5555        # or adb pair / connect for Wireless debugging on Google TV
adb install -r camgrid-tv.apk
```

## Build and run on your TV (developers)

Every step we use, in order. You need a computer on the same Wi-Fi as the TV, JDK 17+ and the
Android SDK platform-tools (`adb`).

1. **Turn on developer mode on the TV.** Settings → System → About → press OK on
   *Android TV OS build* 7 times.
2. **Turn on wireless debugging.** Settings → System → Developer options → *Wireless debugging* →
   *Pair device with pairing code*. Keep that screen open.
3. **Pair and connect** (use the IP, ports and code shown on the TV):
   ```bash
   adb pair <TV_IP>:<PAIRING_PORT> <PAIRING_CODE>
   adb connect <TV_IP>:<CONNECT_PORT>   # usually automatic after pairing; check with: adb devices
   ```
4. **Build and install:**
   ```bash
   git clone https://github.com/anhourtec/CamGrid-TV && cd CamGrid-TV
   ./gradlew :app:assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
5. **Open CamGrid TV** from the TV's apps row.
6. **Sign in from your phone:** the TV shows an address like `http://192.168.x.x:8743` and a
   6-digit code. Open the address on your phone (same Wi-Fi), enter the code, then your BitVision
   email, password and the digits in the picture. The TV signs in and opens your cameras.
   (Or sign in directly on the TV with the remote.)
7. **Use it:** arrows move between tiles, OK opens a camera fullscreen (Back returns), hold OK on
   a tile for its menu (choose camera, move, clear, grid size, edit from phone, sign out).
8. **Edit from your phone:** tile menu → *Edit layout from phone or computer*; open the address on
   the card, enter its code, choose grid and cameras, *Save to TV*. Back closes the card.
9. When you're done, turn *Wireless debugging* off again (the app doesn't need it).

Quality checks before every change: `./gradlew ktlintCheck detekt test :app:lintDebug`.

## How it works

1. You sign in with your BitVision account, on the TV or from your phone through a one-time
   pairing page served by the TV on your local network.
2. CamGrid TV lists your NVRs and their channels, as the web portal does.
3. Each tile opens the camera's live stream from BitVision's cloud relay and hands the H.265/H.264
   video to the TV's hardware decoder.

Technical details: [architecture](docs/architecture.md) · [streaming](docs/streaming.md) ·
[security](docs/security.md) · [full spec](docs/SPEC.md).

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

**Why does my TV say "Waiting for a free video decoder"?**
Each live camera needs a video decoder, and TVs have only a few: a budget Google TV may play 4 at
once (2 in hardware, 2 in software). Tiles beyond that wait and start as soon as one frees up.
Streamers like the Chromecast with Google TV 4K, Google TV Streamer, NVIDIA Shield or Fire TV 4K
Max have more decoders and play more tiles live.

**Is it safe? Where does my password go?**
Only to BitVision's sign-in, once. It is never saved; only the session is kept, encrypted on the
TV. Phone pairing uses a short-lived, single-use code on your local network. See
[docs/security.md](docs/security.md).

**Is this official or affiliated with BitVision?**
No. See [NOTICE.md](NOTICE.md). Use it only with your own account and devices.

## Contributing

Issues and pull requests are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md). Security issues:
[SECURITY.md](SECURITY.md). Please never post account details, device serials, IP addresses or
camera images in issues.

## Credits

Created and maintained by **[anhourtec](https://github.com/anhourtec)**. If CamGrid TV helps
you, star the repo and share it with anyone stuck watching their CCTV on a phone.

## License

[Apache License 2.0](LICENSE) for this project's own code. BitVision, its apps, services and
trademarks belong to their respective owners. See [NOTICE.md](NOTICE.md).

<!-- Keywords: BitVision TV app, BitVision Android TV, BitVision Google TV, BitVision Fire TV, BitVision Firestick, BitVision NVR on TV, BitVision DVR TV, watch CCTV on Google TV, NVR multi-camera grid Android TV, bitvision.app TV -->
