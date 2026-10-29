# 2026-10-29 — Rename to AnhourCam TV; Phase 6 (any IP camera) planned

## Done
- Renamed the product to **AnhourCam TV**: new logo (docs/brand: thin all-caps wordmark in the
  AnHourTec style, the "O" is the 2×2 camera-grid mark in AnHourTec blue), app icon, TV banner,
  phone-page favicon/header, README, docs. Package/app ID unchanged (keeps sign-in and layouts).
- Google TV kept the old name/icon until `versionCode` was bumped (1 → 2, `0.2.0-preview`).
- README restored to its own structure after feedback; only the Screenshots section and the
  step-by-step install guide were added; "Built by … Victoria" line removed; Status table updated;
  Support section → GitHub issues.
- GitHub repo renamed to `anhourtec/AnhourCam-TV`; links updated (the local `origin` remote is
  repointed by the maintainer).
- Phase 6 planned: [ipcam.md](../ipcam.md). Observed a competing TV app's setup page (feature list
  only, nothing copied) and scanned the maintainer's LAN.

## Learned
- 9–10 cameras answer RTSP on :554 (`Rtsp Server`), web server `LSWebServer` (camera family BitVision
  NVRs use). No ONVIF WS-Discovery replies; ONVIF device service answers on at least one camera;
  `DESCRIBE /` without a path gets no reply → paths come from ONVIF or brand presets.
- The redaction scanner flags long decimals (e.g. generated SVG coordinates): round to 2 places.

## Started
- `:ipcam` module (step 6a): `CameraScanner`, `OnvifClient`, `RtspClient` (+`RtspUrl`), `RtspAuth`,
  `Sdp`, `RtpDepacketizer`; `FrameHeader.hevc` flag added (default true, BitVision unchanged).
  Written but **not yet built, linted or tested**.

## Later the same day: step 6a done
- `:ipcam` builds clean (ktlint/detekt 0), 20 tests incl. RFC 2617 digest vector and a fake
  loopback RTSP camera (Digest login, TCP interleaved, key frame delivered, no password on the wire).
- CLI `scan` / `onvif` / `rtsp`; `scan` found the maintainer's 9 RTSP cameras.
- Release signing reads `ANHOURCAM_KEYSTORE_*` from per-machine `~/.gradle/gradle.properties` or env;
  `*.jks`/`*.keystore`/`todo.md` git-ignored. Key creation steps live in the local `todo.md`.
- BitVision verified on the TV after the change: 4 live tiles.
- Reviews: /security-review — no findings ≥ 8 (2 low notes; username quoting fixed);
  security-audit quick run-11 — confirmed 0, needs_validation 0.
- README: contributors badge; status badge "preview".

## Real-camera test (maintainer ran the CLI)
- `onvif` first failed with "wrong user name or password": the camera answers HTTP 401 with
  `Digest qop="auth", realm="IP Camera"`; we only sent WS-Security. Fixed: answer the Digest
  challenge once (never Basic). Then ONVIF listed `/0` 1080p, `/1` 720p, `snap.jpg`.
- `rtsp` on the sub-stream: H.265 (ONVIF said H264), 25 fps, first frame ~0.2 s, on :554 and :80.
- Another camera rejects that password → per-camera logins needed.
- Decided with the maintainer: first verify a saved clip with ffmpeg (step 1), then finish 6b–6e
  without stopping, then do the release together.

## Phase 6 built (6b–6e)
- Clip check: 250/250 frames decode, 0 errors, 1280×720 HEVC (clip never viewed, deleted).
- ONVIF Digest fix committed after reviews (no findings; audit run-12).
- 6b: `IpCameraStore`, `DeviceDirectory` IP group (portal optional), `IpTileStreams`, H.264 in
  `TileDecoder`, snapshot stills via `CameraProbe.snapshot` (Digest), wrong-port fallback to 554.
- 6c: `/cameras` dashboard in `:pairing` (`CameraBackend`, `CameraRoutes`, `CameraPages`,
  `CameraStrings`), TV backend `IpCameraManager` (test before save), TV menu "IP cameras".
- 6d: "Use IP cameras only" on sign-in; menu "Sign in to BitVision"; phone card opens when empty.
- 6e: `BrandPreset`, `DigestHttp`, README features/FAQ/screenshots (made-up preview data).
- Found while screenshotting: own-login fields showed for shared login → hidden via CSS `:has()`.
- Not tested on the TV: IP-only mode (would sign the maintainer out of BitVision).

## Real-device test (maintainer, phone dashboard → TV)
- Scan from the TV found 10 cameras with ONVIF announcements.
- "Didn't answer ONVIF": Android blocked cleartext HTTP → network security config (BitVision HTTPS-only).
- App crash on "Test and save": camera clock `18:-15:36` → `Instant.parse` threw on a server thread.
  Fixed: arithmetic clock, server catches RuntimeException → 500, tile thread maps them to "no video".
- Then: camera saved; fullscreen 1920×1080 H.265, hardware decoder, 25 fps, first frame 0.5 s, 0 dropped.

## Disclaimer line removed from the app and web pages
- Maintainer's call: "Unofficial app · not affiliated with BitVision" no longer shown on the TV
  sign-in screen or the phone/computer pages; NOTICE.md, README and release pages keep it.
- Dashboard list shows a camera's address once when its name is the address.
- README screenshots retaken (web) / cleaned (TV sign-in).
