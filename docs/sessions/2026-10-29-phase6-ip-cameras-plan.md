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
