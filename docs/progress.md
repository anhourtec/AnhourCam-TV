# Progress

| Phase | Status | Gate met |
|---|---|---|
| 0 API discovery | **done** (gate met); carry-overs: session idle timeout, concurrent-stream limit, audio frames | **yes** |
| 1 bitvision-client | **in progress**: library + CLI built, 20 tests, lint clean; live CLI run pending | no |
| 2 Video PoC | not started | — |
| 3 TV shell + login | not started | — |
| 4 Grids | not started | — |
| 5 Polish | not started | — |

## Phase 0 — API discovery
- 2026-10-29: Repo scaffolding, docs, redaction checker created. Capture plan written (`capture-plan.md`).
- 2026-10-29: Logged-in capture. Device list and channels are server-rendered. Live view: JSON
  signalling on `userGateWay` WebSocket + binary media on `stream` WebSocket; private 44-byte
  header + **H.265 Annex-B, not encrypted** → hardware MediaCodec path is viable.
- 2026-10-29: **Stop condition (SPEC §3.5):** web login always requires an image CAPTCHA (`yzm`).
  Not bypassed. **Maintainer decided: the human types the CAPTCHA** (shown on TV and the pairing page). Phase 0 continues with the remaining flows.
- 2026-10-29: Main stream (1080p, streamId 0), stop/switch (8232 + 8236), audio (8228/8229),
  client-side snapshot, 200 s heartbeat, device-page data format, full command table. **Phase 0
  gate met**: api.md is enough to write a client; streaming.md identifies transport and codec with
  captured frame headers.

## Phase 1: bitvision-client
- 2026-10-29: Gradle 9.8.2 wrapper, version catalog, ktlint + detekt, Kotlin 2.4.21 with warnings
  as errors. `:bitvision-client` implements portal login (human-typed CAPTCHA), device list and
  device page parsing, signalling + media WebSockets, relay ack and zero-copy frame parsing.
  `:bitvision-client-cli` has `login`, `devices`, `stream`, `logout`. 20 unit tests pass, including
  a fake-gateway end-to-end live-view test. Remaining for the gate: one live CLI run on the
  maintainer's account.
