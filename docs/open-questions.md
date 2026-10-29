# Open questions

Anything not directly observed. Code that relies on one of these carries `// INFERRED:`.

| # | Question | Status | Evidence / notes |
|---|---|---|---|
| 1 | What transport does live view use? | **resolved** | Two WebSockets, private 44-byte header + Annex-B (streaming.md) |
| 2 | Are API requests signed? | likely no | Plain `$.post` form fields; keep watching |
| 3 | Session cookie name, lifetime, refresh? | open | Need a long-running session observation |
| 4 | Is video encrypted / decoded in WASM? | **resolved: not encrypted** | Annex-B in clear; browser uses open-source libde265 WASM only because browsers lack HEVC |
| 5 | Does login require CAPTCHA? | **yes, every web login: STOP CONDITION** | `login.js` requires `yzm`. **Decided 2026-10-29: the human types it.** TV + pairing page show the CAPTCHA image; no bypass/OCR; keep the session alive |
| 6 | Codec per stream type? | partial | Sub-stream H.265 720×480 25 fps (inferred dims); main stream TBD |
| 7 | How does the gateway WS authenticate the user? | open | 4101 carries only the username; observe only, never test |
| 8 | Header offsets 0, 32, 36 of media frames | open | Constant 0x24, 0, 8 so far |
| 9 | One `/stream/1` socket per channel for multi-view? | open | Capture flow 7 |
| 10 | Audio codec on the wire (AAC vs G.711)? | open | Portal ships both decoders |
