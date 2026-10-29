# Roadmap

Living plan for CamGrid TV. Updated at the end of every working session.
Product spec: [SPEC.md](SPEC.md) · Phase status detail: [progress.md](progress.md).

_Last updated: 2026-10-29_

## Now

**Phase 0: API discovery** (in progress)
- [x] Repo, docs, license, notice, redaction checker
- [x] Playwright browser on the BitVision portal with WebSocket frame recorder
- [x] Maintainer login (CAPTCHA solved by the human)
- [x] Device list and channel list sources (server-rendered pages)
- [x] Live view, 1 channel: H.265 Annex-B over WebSocket behind a 44-byte header, not encrypted
- [x] First versions of `auth.md`, `api.md`, `streaming.md` + redacted samples
- [x] Stop-condition check: video OK; login CAPTCHA confirmed, decided: the human types it (no bypass)
- [ ] Remaining flows: main stream, 4-channel multi-view, stop, audio, snapshot ([capture plan](capture-plan.md))
- [ ] Keepalive over a 5+ min session; session cookie lifetime (how often users must sign in)

## Next

**Phase 1: `:bitvision-client`** (pure Kotlin/JVM)
- Gradle wrapper, version catalog, ktlint/detekt
- Auth + signing, device/channel listing, stream negotiation
- Unit tests against redacted samples; CLI smoke test

**Phase 2: Video proof of concept**
- WebSocket → 44-byte depacketizer → MediaCodec (HEVC) → SurfaceView
- 1 stream for 30 min stable; 4 sub-streams concurrently

## Later

**Phase 3: TV shell + login.** Compose for TV, on-TV login, LAN pairing server on :8743.
**Phase 4: Grids.** Device browser, built-in + custom layouts, editor, fullscreen, pages, tour mode.
**Phase 5: Polish.** Performance and decoder budget, `docs/install.md` with screenshots, signed release APK.

## Ideas / not scheduled
- Self-signed HTTPS mode for pairing
- Manage layouts from the phone pairing page
- Playback / recordings and PTZ (if the API supports them)

## Risks
| Risk | Impact | Mitigation |
|---|---|---|
| Video encrypted or WASM-only decoder | Blocks native playback | Stop condition; report before Phase 1 |
| Private API changes | App breaks | Keep client isolated; samples-based tests |
| CAPTCHA on every web login (**confirmed**) | User must solve a 4-char image code to sign in | Never bypass. Decided: the human types it on the TV / phone pairing page; keep the session alive |
| Low-end TV decoder limits | Fewer live tiles | Decoder budget + snapshot tiles |
| Gateway hosts are region-specific | Wrong host per user | Read hosts from the device page, never hard-code |
| Legal (BitVision IP) | Takedown | `NOTICE.md`, no copied code/assets, interoperability only |
