# Progress

| Phase | Status | Gate met |
|---|---|---|
| 0 API discovery | **paused at stop condition**: login CAPTCHA; video path viable | no |
| 1 bitvision-client | not started | — |
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
