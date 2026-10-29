# Roadmap

Living plan for AnhourCam TV. Updated at the end of every working session.
Product spec: [SPEC.md](SPEC.md) · Phase status detail: [progress.md](progress.md) ·
IP cameras: [ipcam.md](ipcam.md).

_Last updated: 2026-10-29 (Phase 6 done and verified on the TV; next: release together)_

## Now

**Phase 6: Any IP camera (RTSP / ONVIF)** — make the app useful without BitVision. Plan: [ipcam.md](ipcam.md)
- [x] 6a `:ipcam` module: LAN scan, ONVIF lookup, RTSP over TCP (Basic/Digest), H.264/H.265 depacketizer, 20 tests, CLI `scan` / `onvif` / `rtsp` (scan finds the maintainer's 9 cameras)
- [x] 6a′ Real camera tested with the CLI: ONVIF needs HTTP Digest (fixed), stream paths `/0` `/1`, H.265 sub-stream at 25 fps, first frame ~0.2 s
- [x] 6b App: IP camera store (Keystore-encrypted logins), merged camera list, RTSP tiles, H.264 decoding, still images
- [x] 6c Phone/computer dashboard: scan, add by address / brand / RTSP URL, shared or per-camera login, edit, delete, test
- [x] 6d "Use IP cameras only" (no BitVision account needed)
- [x] 6e Brand presets, README + screenshots, Help → GitHub issues
- [x] 6f Real camera added from the phone and playing on the TV (1080p H.265, 25 fps, 0.5 s to first frame)
- [ ] H.264 camera check (#24), IP-only mode on a spare TV
- Rule: BitVision keeps working exactly as today after every step.
- After 6e: release (GitHub APK, Play Store) **together with the maintainer**; checklists in the local `todo.md`.

## Next

**Phase 4 leftovers**
- [ ] Encrypted on-disk device cache → instant start, live view without the portal
- [ ] Pages / tour mode
- [ ] Settings screen (language, sign out, About with the NOTICE disclaimer)

**Phase 2 leftovers**
- [ ] Hardware decoders to the largest streams first (open question #15)
- [ ] Faster recovery after a dropped frame (today waits for the next key frame, ~10 s)
- [ ] 30-minute stability run of one stream (memory + drift)

**Phase 5: Polish and release**
- [ ] Performance pass against the budgets (docs/performance.md)
- [ ] Signed release APK on GitHub Releases (enables install without a computer)

## Later

- Phase 0 carry-overs: session idle timeout, concurrent stream limit per account, audio frames
- Self-signed HTTPS for the pairing/dashboard pages
- Playback / recordings and PTZ (BitVision if the API allows; ONVIF PTZ for IP cameras)
- Audio for IP cameras (AAC/G.711 over RTSP)

## Done

- **Rename** (2026-10-29): AnhourCam TV, new logo (docs/brand), repo `anhourtec/AnhourCam-TV`.
- **README** (2026-10-29): screenshots (camera images blurred) and a step-by-step install guide.
- **Phase 4: Grid editor** (mostly done, 2026-10-29): `:layouts` model with 11 grids, layouts saved
  on the TV, multi-NVR live grid where only changed tiles restart, TV editor (hold OK), phone
  editor, several named layouts (◀ ▶ flips), still images for tiles beyond the decoders.
- **Phase 3: TV shell + sign-in** (2026-10-29): TV sign-in with CAPTCHA, phone pairing (`:pairing`),
  5 languages, modern dialogs, session kept alive.
- **Phase 2: Video proof of concept** (2026-10-29): MediaCodec HEVC to SurfaceView, 4 live tiles on
  the test TV (2 hardware + 2 software), clean stopVideo on exit.
- **Phase 1: client library + CLI** (2026-10-29): live SD/HD streaming verified.
- **Phase 0: API discovery** (2026-10-29): transport, framing, codec, commands, page data.

## Risks
| Risk | Impact | Mitigation |
|---|---|---|
| Video encrypted or WASM-only decoder | Blocks native playback | **Retired**: video is plain H.265 Annex-B |
| Private API changes | App breaks | Keep client isolated; samples-based tests; IP cameras don't depend on it |
| CAPTCHA on every web login (**confirmed**) | User solves a code to sign in | Never bypass; the human types it on TV/phone; session kept alive |
| Low-end TV decoder limits | Fewer live tiles | Decoder budget + still images |
| Gateway hosts are region-specific | Wrong host per user | Read hosts from the device page, never hard-code |
| Legal (BitVision IP) | Takedown | `NOTICE.md`, no copied code/assets, interoperability only |
| IP cameras without ONVIF or with unknown RTSP paths | Can't add some cameras | Manual RTSP URL + brand presets; device reports via GitHub issues |
| Breaking BitVision while adding IP cameras | Regression for current users | Separate `:ipcam` module, `ip:` refs, test the BitVision layout after every step |
| Camera logins on the TV | Credential exposure | Keystore-encrypted, never logged or shown back on the dashboard |
