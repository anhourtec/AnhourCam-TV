# Open questions

Anything not directly observed. Code that relies on one of these carries `// INFERRED:`.

| # | Question | Status | Evidence / notes |
|---|---|---|---|
| 1 | What transport does live view use? | **resolved** | Two WebSockets, private 44-byte header + Annex-B (streaming.md) |
| 2 | Are API requests signed? | likely no | Plain `$.post` form fields; keep watching |
| 3 | Session idle timeout? | **mostly resolved** | Idle browser session expired in < 50 min (13:25 → 14:15 UTC); several sessions per account work at once (CLI + TV + browser). SessionKeeper's 10-min ping is right; live view doesn't need the session anyway |
| 4 | Is video encrypted / decoded in WASM? | **resolved: not encrypted** | Annex-B in clear; browser uses open-source libde265 WASM only because browsers lack HEVC |
| 5 | Does login require CAPTCHA? | **yes, every web login: STOP CONDITION** | `login.js` requires `yzm`. **Decided 2026-10-29: the human types it.** TV + pairing page show the CAPTCHA image; no bypass/OCR; keep the session alive |
| 6 | Codec per stream type? | **resolved** | Both H.265; main 1920×1080, sub 720×480, ~25 fps (header offsets 12 = height, 16 = width) |
| 7 | How does the gateway WS authenticate the user? | open | 4101 carries only the username; observe only, never test |
| 8 | Header offsets 0, 32, 36; frame types 5, 7 | open | Constants 0x24, 0, 8. Type 7 = large P frame every ~75 frames; treat as P. Type 5 = audio (inferred from portal) |
| 9 | Why do some channels never start? | **resolved** | CH4 (and CH13–16) fail in BitVision's own web portal too: no camera / camera offline on that input (NVR answers 8225, never 8218). Separately, the PoC leaked streams on exit (stopVideo never sent); fixed, and failures stopped piling up |
| 10 | Audio frame format? | open | 8229 says `encodeType` 2 (G.711, inferred), 16, 8-bit, mono. No audio frames on the tested camera (no mic?) |
| 11 | Does the client work end to end? | **resolved** | 2026-10-29: CLI login (human CAPTCHA), devices, SD 720×480 and HD 1920×1080 streams at ~23 fps |
| 12 | Is the CAPTCHA always digits? | open | One sample: 4 digits. Number pad first, full keyboard fallback |
| 13 | Can first-frame time be cut below ~4 s per tile? | open | Ideas: keep a warm pool of media sockets with relay acks; ask for a key frame if the protocol allows |
| 14 | Software HEVC on low-end TVs | open | MT5867 (32-bit) can't sustain 1280×720 in software; prefer snapshots beyond the hardware budget |
| 15 | Which tiles should get the hardware decoders? | open | Today: first come, first served; retries of dead channels can hold a hardware slot. Prefer the largest streams |
| 16 | How many tiles can play live on a given TV? | partly answered | Skyworth MT5867: 2 hardware + 2 software decoders = 4 (DecoderBudget.capacity). Tiles beyond wait and take over freed decoders; snapshots for the rest are planned |
| 17 | Streams left behind when the app is killed (adb install, crash) | open | Without stopVideo the NVR keeps them for a while; normal exits are clean. Consider a startup "stop all for my client" if the protocol has one |
| 18 | Phone save vs TV edit at the same moment | known | Last save wins (whole layout written back); acceptable for a single household |
