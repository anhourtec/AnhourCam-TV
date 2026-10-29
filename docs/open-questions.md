# Open questions

Anything not directly observed. Code that relies on one of these carries `// INFERRED:`.

| # | Question | Status | Evidence / notes |
|---|---|---|---|
| 1 | What transport does live view use (WS-FLV, HLS, WebRTC, private WS framing)? | open | Phase 0 |
| 2 | Are API requests signed? Where is the signing code? | open | Phase 0 |
| 3 | Token lifetime and refresh mechanism? | open | Phase 0 |
| 4 | Is video encrypted / decoded in WASM? | open | Phase 0 — stop condition if no key path |
| 5 | Does login ever require CAPTCHA? | open | Phase 0 — stop condition if always |
| 6 | Codec per stream type (H.264 vs H.265), resolution, fps? | open | Phase 0 |
