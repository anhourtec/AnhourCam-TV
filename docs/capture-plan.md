# Phase 0 capture plan

Human logs in on the headed Playwright browser. Automation then exercises each flow and records
traffic. Raw captures → `captures/` (git-ignored). Redacted extracts → `samples/`.

| # | Flow | Done | Notes |
|---|---|---|---|
| 1 | Login (shape only) | [ ] | |
| 2 | Token refresh / logout / expiry | [ ] | |
| 3 | Device (NVR) list, status, details | [ ] | |
| 4 | Channel list per NVR | [ ] | |
| 5 | Live view, one channel, sub-stream | [ ] | |
| 6 | Switch to main / HD | [ ] | |
| 7 | 4-channel multi-view | [ ] | |
| 8 | Stop live view | [ ] | |
| 9 | Snapshot | [ ] | |
| 10 | Audio on/off | [ ] | |
| 11 | Playback / recordings (document only) | [ ] | |
| 12 | PTZ (document only) | [ ] | |
| 13 | Keepalive over 5+ min live session | [ ] | |

## Method
- XHR/Fetch: `browser_network_requests` + `browser_network_request` for bodies.
- WebSockets: a page-level hook (`browser_run_code_unsafe`, `page.on('websocket')`) logging
  frame direction, size and first bytes as hex — written to `captures/`, not to chat.
- JS: download portal bundles to `captures/js/` to locate signing / decoder logic.
