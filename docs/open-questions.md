# Open questions

Anything not directly observed. Code that relies on one of these carries `// INFERRED:`.

| # | Question | Status | Evidence / notes |
|---|---|---|---|
| 1 | What transport does live view use? | **resolved** | Two WebSockets, private 44-byte header + Annex-B (streaming.md) |
| 2 | Are API requests signed? | likely no | Plain `$.post` form fields; keep watching |
| 3 | Session idle timeout? | open, **no longer critical** | Live view doesn't need the session (SPEC §5.3). ≥36 min observed (not a clean idle test). Clean test: CLI session idle since 13:24 UTC 2026-10-29; check with `camgrid-cli devices` |
| 4 | Is video encrypted / decoded in WASM? | **resolved: not encrypted** | Annex-B in clear; browser uses open-source libde265 WASM only because browsers lack HEVC |
| 5 | Does login require CAPTCHA? | **yes, every web login: STOP CONDITION** | `login.js` requires `yzm`. **Decided 2026-10-29: the human types it.** TV + pairing page show the CAPTCHA image; no bypass/OCR; keep the session alive |
| 6 | Codec per stream type? | **resolved** | Both H.265; main 1920×1080, sub 720×480, ~25 fps (header offsets 12 = height, 16 = width) |
| 7 | How does the gateway WS authenticate the user? | open | 4101 carries only the username; observe only, never test |
| 8 | Header offsets 0, 32, 36; frame types 5, 7 | open | Constants 0x24, 0, 8. Type 7 = large P frame every ~75 frames; treat as P. Type 5 = audio (inferred from portal) |
| 9 | Concurrent streams per account? | open | One media socket per playback confirmed; the cloud's limit on concurrent relay sessions is untested (Phase 2, own account) |
| 10 | Audio frame format? | open | 8229 says `encodeType` 2 (G.711, inferred), 16, 8-bit, mono. No audio frames on the tested camera (no mic?) |
| 11 | Does the client work end to end? | **resolved** | 2026-10-29: CLI login (human CAPTCHA), devices, SD 720×480 and HD 1920×1080 streams at ~23 fps |
| 12 | Is the CAPTCHA always digits? | open | One sample: 4 digits. Number pad first, full keyboard fallback |
| 13 | Can first-frame time be cut below ~4 s per tile? | open | Ideas: keep a warm pool of media sockets with relay acks; ask for a key frame if the protocol allows |
