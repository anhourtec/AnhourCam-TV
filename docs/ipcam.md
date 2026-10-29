# Phase 6: any IP camera (RTSP / ONVIF)

Goal: AnhourCam TV is useful **without a BitVision account**. Any IP camera or NVR on the home
network that speaks RTSP (most do) can be added and mixed into the same grids as BitVision
cameras. **The BitVision path must not change or regress**: IP cameras are a second camera
source next to it, not a rewrite.

_Status: done (2026-10-29). Verified on the test TV: camera added from a phone, fullscreen 1920×1080 H.265 on a hardware decoder, 25 fps, first frame 0.5 s, 0 dropped. See [roadmap](roadmap.md)._

## What users get

- **Automatic scan** of the home network for cameras (ONVIF discovery + RTSP port check).
- **Four ways to add a camera**, all from the phone/computer dashboard (pairing page):
  1. **Found by the scan**: pick it, enter the login, done (ONVIF finds the stream addresses).
  2. **By address**: name, IP address or hostname, user name, password (ONVIF lookup).
  3. **By brand**: pick a common brand, enter IP + login; we fill in its usual RTSP path.
  4. **Any RTSP stream**: name, `rtsp://` address, optional snapshot (JPEG) address, login.
- **One login for all cameras, or one per camera**: a "shared login" (user + password) used by
  every camera that has no login of its own; each camera can override it.
- IP cameras appear in every camera picker (TV and phone) under their own group ("IP cameras"),
  next to BitVision NVRs, and work in all grids, layouts, fullscreen and still images.
- **Main / sub stream**: grids use the camera's smallest stream, fullscreen the largest (same
  rule as BitVision SD/HD).
- The app also works **with no BitVision account at all**: the sign-in screen gets
  "Use IP cameras only"; BitVision can be added later from the menu.
- **Help** links point to [GitHub issues](https://github.com/anhourtec/AnhourCam-TV/issues).

## What we learned (2026-10-29, maintainer's own network)

- A competing TV app (observed only to see what users expect; nothing copied) offers: detect
  automatically / manual address (name, IP or host, user, password) / brand+model list / any
  RTSP URL (+ optional snapshot URL) / support. Its setup page works only while the TV's setup
  screen is open — our pairing server already works that way, with a code and token on top.
- The maintainer's LAN has 9–10 hosts answering RTSP on port 554 (`Server: Rtsp Server`), all
  with a web server on port 80 (`LSWebServer`, the camera family BitVision NVRs use).
- **None answered the ONVIF WS-Discovery multicast probe**, so the scan must also sweep the
  subnet's RTSP ports. At least one answers ONVIF `GetSystemDateAndTime` on
  `/onvif/device_service`; another closes the connection there (ONVIF elsewhere or absent →
  manual RTSP address / brand path).
- `DESCRIBE rtsp://<ip>:554/` with no login gets **no reply** on these cameras: the stream
  path is required (ONVIF `GetStreamUri` or a brand template).
- **ONVIF needs HTTP Digest** (`WWW-Authenticate: Digest qop="auth", realm="IP Camera"`) on every
  call except `GetCapabilities` / `GetSystemDateAndTime`; WS-Security alone gets HTTP 401. The
  client answers the Digest challenge once (never Basic). Verified with the CLI on a real camera.
- ONVIF on that camera lists `mainStream` 1920×1080 at `rtsp://<ip>:80/0` and `secondStream`
  1280×720 at `rtsp://<ip>:80/1`, snapshot `http://<ip>:80/snap.jpg`. It reports the RTSP URL
  on **port 80**, but RTSP works on **both 80 and 554**.
- ONVIF says `H264`, but the RTSP stream is **H.265**: trust the SDP, never the ONVIF encoding.
- RTSP sub-stream over TCP: **25 fps, first frame ~0.2 s** after connecting (BitVision: ~4 s).
- **TV scan finds all 10 cameras and their ONVIF announcements** (WS-Discovery works from the TV,
  not from the maintainer's Mac).
- **Android blocks plain HTTP** for apps targeting recent versions: ONVIF/snapshots failed until a
  network security config allowed cleartext (BitVision domains stay HTTPS-only).
- One camera reports its ONVIF clock as `18:-15:36` (negative minute): parse clock fields
  arithmetically; a malformed reply must never crash the app.
- On the TV: first IP frame **0.5 s**, 1080p H.265 hardware decode, 25 fps, 0 dropped.
- Cameras on one network can have **different passwords** (one camera rejected the login that
  works on others), so per-camera logins are needed, not only a shared one.

## Design

```
            ┌──────────── :app ────────────┐
 TV grid → TileRunner ──┬─ BitVision: GatewayPool + MediaSession (unchanged)
                        └─ IP camera:  IpCameraSource → RtspClient (:ipcam)
                                   both → FrameSink → TileDecoder (MediaCodec, H.265 or H.264)
 phone dashboard (:pairing) ── CameraRoutes ── IpCameraBackend (:app) ── IpCameraStore
```

- **`:ipcam`** (pure JVM, no Android; depends on `:bitvision-client` only for `FrameSink`/
  `FrameHeader`):
  - `CameraScanner`: WS-Discovery probe + TCP check of ports 554/8554 on the TV's own /24.
    Private subnets only.
  - `OnvifClient`: GetSystemDateAndTime (clock offset) → GetCapabilities → GetProfiles →
    GetStreamUri / GetSnapshotUri, WS-Security UsernameToken digest.
  - `RtspClient`: OPTIONS / DESCRIBE / SETUP / PLAY over **TCP interleaved** (no UDP ports),
    Basic + Digest auth only after a 401, GET_PARAMETER keep-alive, TEARDOWN on close.
    Credentials never in URLs sent on the wire or logged.
  - `Sdp` (video track, sprop parameter sets), `RtpDepacketizer` (H.264 RFC 6184 single/
    STAP-A/FU-A; H.265 RFC 7798 single/AP/FU) → Annex-B access units, one reused buffer.
- **Camera identity**: IP cameras reuse `CameraRef(deviceId, channelNo)` with
  `deviceId = "ip:<uuid>"`, `channelNo = 0`, so saved layouts need no migration and BitVision
  refs are untouched.
- **Storage** (`:app`): `ipcams.json` (name, host, stream URLs, snapshot URL, brand, login mode)
  and credentials (shared + per camera) **encrypted with the existing Keystore `SecretStore`**,
  never in `ipcams.json`, layouts, logs or the pairing page (password fields show "saved").
- **Decoders**: `FrameHeader.hevc` picks `video/hevc` or `video/avc`; `DecoderBudget` learns
  AVC decoders too (separate counts per codec).
- **Resolution**: RTP has no width/height; ONVIF profiles give it, else 1920×1080 is used to
  configure the decoder (decoders adapt to the SPS). (inferred; see open question #21)
- **Dashboard pages** (`:pairing`, same code + token + Host/IP guard, no JavaScript):
  Cameras list (edit / delete / test) · Scan · Add by address · Add by brand · Add RTSP URL ·
  Shared login. Only reachable while the TV's "Cameras" card is open.
- **TV**: menu → "IP cameras" opens the card with the address + code (like "Edit from phone").
  Sign-in screen → "Use IP cameras only".

## Plan (each step builds, passes lint/tests and keeps BitVision working)

1. **6a `:ipcam` module** — scanner, ONVIF, RTSP, depacketizer, unit tests; CLI commands
   `scan`, `onvif <host> <user>`, `rtsp <url> <user> [seconds]` (password typed at the prompt)
   to verify against the maintainer's cameras from a computer. _(done: 20 tests; `scan` found all 9 cameras on the maintainer's LAN)_
2. **6b App source** _(done)_ — `IpCameraStore` (`ipcams.json` + SecretStore passwords),
   `DeviceDirectory` adds an "IP cameras" group, `TileRunner` → `IpTileStreams` (RTSP into the
   tile decoder, wrong-port fallback to 554), H.264 via the TV's AVC decoder, still images from the
   camera's snapshot URL (HTTP Digest, ≤ 640 px wide).
3. **6c Dashboard** _(done)_ — `/cameras` pages in `:pairing` (`CameraRoutes`, `CameraPages`,
   `CameraStrings` ×5 languages), backend `IpCameraManager` in `:app` (scan, ONVIF, brand, URL,
   test-then-save, shared/own login). TV menu → "IP cameras" opens the phone card.
4. **6d No-account mode** _(done)_ — "Use IP cameras only" on the sign-in screen; menu offers
   "Sign in to BitVision" later. With no cameras yet the phone card opens by itself.
5. **6e Polish** _(done)_ — brand presets (`BrandPreset`: generic /0 /1, Hikvision, Dahua,
   Reolink, Uniview, Tapo, Axis), README features + dashboard screenshots (local preview, made-up
   cameras), Help → GitHub issues on the dashboard.

## Testing with real cameras (CLI)

```bash
./gradlew :bitvision-client-cli:installDist
CLI=bitvision-client-cli/build/install/bitvision-client-cli/bin/bitvision-client-cli
$CLI scan                                   # cameras on this computer's network
$CLI onvif <camera-ip> admin                # password at a hidden prompt → stream + snapshot addresses
$CLI rtsp rtsp://<camera-ip>:554/<path> admin 10 [--save clip.h264]   # plays 10 s, prints fps/bitrate
```
Passwords are typed at a hidden prompt and never saved, printed or put in URLs on the wire.

## Rules for this phase

- Never break BitVision: run the TV with the BitVision layout after every step.
- Test only against the maintainer's own cameras on their own LAN. Camera passwords are typed
  by the maintainer (CLI prompt or dashboard), never put in chat, files or logs.
- Brand presets are our own list of well-known RTSP path patterns; never copy another app's
  camera database, texts or assets.
