# Roadmap

Living plan for CamGrid TV. Updated at the end of every working session.
Product spec: [SPEC.md](SPEC.md) · Phase status detail: [progress.md](progress.md).

_Last updated: 2026-10-29_

## Now

**Phase 1: `:bitvision-client`** (pure Kotlin/JVM): starting
- [ ] Gradle wrapper, version catalog, ktlint + detekt, `allWarningsAsErrors`
- [ ] Session: login with a human-typed CAPTCHA, `PHPSESSID` handling, logout
- [ ] Device list (`/home/space`) and device page parser (`tipsDeviceInfo`)
- [ ] Signalling WebSocket: envelope, heartbeat, open-device and play/stop commands
- [ ] Media WebSocket + 44-byte frame depacketizer (zero-copy)
- [ ] Unit tests against redacted samples; CLI smoke test (saves CAPTCHA image, user types it)

**Phase 0 carry-overs**
- [ ] Session idle timeout (how often users must sign in)
- [ ] Concurrent stream limit per account (Phase 2)
- [ ] Audio frame format (needs a camera with a microphone)

## Next

**Phase 2: Video proof of concept**
- WebSocket → 44-byte depacketizer → MediaCodec (HEVC) → SurfaceView
- 1 stream for 30 min stable; 4 sub-streams concurrently

## Later

**Phase 3: TV shell + login.** Compose for TV, on-TV login, LAN pairing server on :8743.
**Phase 4: Grids.** Device browser, built-in + custom layouts, editor, fullscreen, pages, tour mode.
**Phase 5: Polish.** Performance and decoder budget, `docs/install.md` with screenshots, signed release APK.

## Done

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
