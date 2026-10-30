# Xiaomi Mi Box 4K (Amlogic), Android TV 9

The project's second test device (model string `MIBOX4`, device `oneday`): an older Android TV
streamer, tested on 2026-10-30. Useful as the "older Android" check: Android 9 lacks many newer
JDK methods that the desktop-built modules could accidentally use.

| | |
|---|---|
| OS | Android TV 9 (API 28), Xiaomi build, Google Play present |
| SoC | Amlogic, `gxl` platform (S905X family), **32-bit apps** (`armeabi-v7a`) |
| RAM | 2 GB (`MemTotal` 2,034,840 kB) |
| Output | 1920×1080 UI (4K video capable) |
| Debugging | Developer options → USB debugging also opens **network ADB on port 5555**: `adb connect <ip>:5555`, accept "Allow USB debugging?" on the TV. No pairing code (that is Android 11+). |

## Video decoders (from `/vendor/etc/media_codecs.xml`)

| Decoder | Type | Max size | Concurrent instances (declared) |
|---|---|---|---|
| `OMX.amlogic.hevc.decoder.awesome` | **hardware** H.265 | 3840×2160 | 9 |
| `OMX.amlogic.avc.decoder.awesome` | **hardware** H.264 | 3840×2160 | 9 |
| `OMX.google.hevc.decoder` | software H.265 | 4096×4096 | CPU-bound |
| `OMX.google.h264.decoder` | software H.264 | 2048×2048 | CPU-bound |

The declared 9 instances is an upper bound from the vendor file, not a measured number; the
decoder budget treats it as 9 hardware slots (+ 2 software). How many tiles really stay smooth is
still to be measured (open question #28).

## Measured with AnhourCam TV (2026-10-30)

- Install, launch, sign-in screen (1080p), phone pairing page and BitVision sign-in from a phone:
  **work**.
- App footprint with a 2×2 grid: ~98 MB RSS / 34 MB PSS, ~28 % of one core.
- 2×2 BitVision grid while the Google TV was streaming the **same NVR channels** at the same time:

| Tile | Stream | Decoder | Result |
|---|---|---|---|
| HOME CH1 | 720×480 SD | hardware | 26 fps, first picture after 19 s |
| HOME CH3 | SD | hardware | live in 5 s |
| admin CH1 | 720×576 SD | hardware | 20 fps, 1 drop; picture black until the first key frame, later normal at 21 fps |
| HOME CH2, admin CH2 | SD | — | "No video" at first: both were also playing on the Google TV (NVR stream limit, #29) |
| HOME CH4 | — | — | no camera on that input (as on every device, #9) |

### Both TVs at once (same account, 2026-10-30)
Both stayed signed in. With the Google TV playing 4 tiles (2 HOME, 2 admin), the Mi Box's 6-tile
grid played its **admin** NVR tiles at 10–21 fps on hardware decoders, while its **HOME** NVR tiles
froze at 0 fps: the HOME NVR cannot feed every stream to two TVs at once (#29). With one TV the same
tiles play normally.

## Bugs this device found (fixed)

1. **Crash when the phone opened the pairing page**: `ByteArrayOutputStream.toString(Charset)` is
   Android 13+; the TV crashed with `NoSuchMethodError`. A full scan found 12 more calls newer
   than the app's minimum (Android 7.1): `URLDecoder.decode(String, Charset)` (13+), `java.time`
   and `java.util.Base64` (8+). All replaced; `scripts/check-android-api.py` now runs in
   `./gradlew check` so the pure-JVM modules can never use APIs newer than minSdk again.
2. **"Use IP cameras only" was clipped** below the on-TV sign-in card on 960×540 dp screens; moved
   to the bottom of the phone column.

## Setup gotcha on the maintainer's network

The Mi Box first reported an address (`<IP_1>`) that an IP camera also uses (same address, ARP showed
the camera's MAC prefix): the Mi Box was unreachable. Fixed by giving it a free static address.
