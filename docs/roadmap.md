# Roadmap

Living plan for CamGrid TV. Updated at the end of every working session.
Product spec: [SPEC.md](SPEC.md) · Phase status detail: [progress.md](progress.md).

_Last updated: 2026-10-29 (Phase 1)_

## Now

**Phase 2: Video proof of concept** (next)
- [ ] Android app module (AGP, minSdk 25, no GMS), leanback launcher entry
- [ ] One stream: WebSocket → depacketizer → MediaCodec (HEVC) → SurfaceView, 30 min stable
- [ ] Four sub-streams at once on a Chromecast with Google TV / Fire TV Stick class device
- [ ] Measure decoder limits and time to first frame on real TV hardware

**Phase 0 carry-overs**
- [ ] Session idle timeout (no longer critical: live view runs from cache; SessionKeeper pings)
- [ ] Concurrent stream limit per account (Phase 2)
- [ ] Audio frame format (needs a camera with a microphone)

## Next

## Later

**Phase 3: TV shell + login.** Compose for TV, on-TV login with the CAPTCHA image and number pad, LAN pairing server on :8743 (CAPTCHA passed through to the phone), encrypted device cache, SessionKeeper.
**Phase 4: Grids.** Device browser, built-in + custom layouts, editor, fullscreen, pages, tour mode.
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
