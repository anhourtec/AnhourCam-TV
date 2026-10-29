# Roadmap

Living plan for CamGrid TV. Updated at the end of every working session.
Product spec: [SPEC.md](SPEC.md) · Phase status detail: [progress.md](progress.md).

_Last updated: 2026-10-29 (Phase 2)_

## Now

**Phase 2: Video proof of concept** (in progress)
- [x] Android app module (AGP 9.5, minSdk 25, no GMS), leanback launcher entry + banner
- [x] One stream: WebSocket → depacketizer → MediaCodec (HEVC) → SurfaceView
- [x] 2×2 grid on a real Google TV: 2 hardware tiles at 26 fps, 1 software tile (stalls)
- [x] Decoder limits measured: this TV has 2 hardware HEVC decoders (docs/devices)
- [x] Why CH4/CH5 time out: no camera on those inputs (fails in the official portal too)
- [x] Clean shutdown always sends stopVideo (app close, Home, screensaver)
- [x] Animated loading (grid-mark loader), per-tile connecting / no-video states with backoff retry
- [ ] Give hardware decoders to the largest streams first (open question #15)
- [ ] Faster recovery after a dropped frame (today: waits for the next key frame, ~10 s)
- [ ] 30-minute stability run of one stream (memory + drift)

**Pulled forward from Phase 3 (done):** phone pairing (`:pairing`), modern sign-in screen with
D-pad focus order, 5 languages on TV and phone page.

**Phase 0 carry-overs**
- [ ] Session idle timeout (no longer critical: live view runs from cache; SessionKeeper pings)
- [ ] Concurrent stream limit per account (Phase 2)
- [ ] Audio frame format (needs a camera with a microphone)

## Next

## Later

**Phase 3: TV shell + login.** Compose for TV, on-TV login with the CAPTCHA image and number pad, LAN pairing server on :8743 (CAPTCHA passed through to the phone), encrypted device cache, SessionKeeper.
**Phase 4: Grids.** Device browser; built-in + custom layouts and grid sizes; any camera from any NVR in any tile; rearrange tiles with the remote (hold OK → Move); fullscreen, pages, tour mode; **everything remembered** (layouts, default/last layout and page, quality, language) and restored on launch (SPEC §7.3a). **Manage layouts from a phone or laptop** via the pairing page: grid size, camera per tile from any NVR, order (SPEC §5.2).
**Phase 5: Polish.** Performance and decoder budget, `docs/install.md` with screenshots, signed release APK.

## Done

- **Phase 1: client library + CLI** (2026-10-29): live SD/HD streaming verified on the maintainer's account.
- **Phase 0: API discovery** (2026-10-29): transport, framing, codec, commands and page data documented. See [progress](progress.md).

## Ideas / not scheduled
- Self-signed HTTPS mode for pairing
- Manage layouts from the phone pairing page
- Playback / recordings and PTZ (if the API supports them)

## Risks
| Risk | Impact | Mitigation |
|---|---|---|
| Video encrypted or WASM-only decoder | Blocks native playback | **Retired**: video is plain H.265 Annex-B |
| Private API changes | App breaks | Keep client isolated; samples-based tests |
| CAPTCHA on every web login (**confirmed**) | User must solve a 4-char image code to sign in | Never bypass. Decided: the human types it on the TV / phone pairing page; keep the session alive |
| Low-end TV decoder limits | Fewer live tiles | Decoder budget + snapshot tiles |
| Gateway hosts are region-specific | Wrong host per user | Read hosts from the device page, never hard-code |
| Legal (BitVision IP) | Takedown | `NOTICE.md`, no copied code/assets, interoperability only |
