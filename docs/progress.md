# Progress

| Phase | Status | Gate met |
|---|---|---|
| 0 API discovery | **done** (gate met); carry-overs: session idle timeout, concurrent-stream limit, audio frames | **yes** |
| 1 bitvision-client | **done**: live CLI run streamed SD and HD; 25 tests, lint clean | **yes** |
| 2 Video PoC | **in progress**: 3 live tiles on a real Google TV; stream-limit question open | no |
| 3 TV shell + login | **partly done early**: on-TV login, LAN phone pairing, 5 languages | — |
| 4 Grids | **in progress**: TV + phone editors, saved multi-NVR layouts | — |
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
- 2026-10-29: **Phase 1 gate met.** Maintainer signed in through the CLI (CAPTCHA shown by our
  client, no browser). `devices` listed both NVRs; `stream` delivered 720×480 at 23.9 fps and
  1920×1080 at 23.2 fps. Step timings recorded in streaming.md. Gateway works without the portal
  cookie, so live view can run from a cache. Added `SessionKeeper`.

## Phase 2: video proof of concept
- 2026-10-29: `:app` (AGP 9.5, compileSdk 37, minSdk 25, no GMS, 2.4 MB debug APK). TileDecoder:
  socket buffer → MediaCodec input (one copy) → SurfaceView, low-latency mode, drop-to-key-frame.
  DecoderBudget picks hardware first. On the Skyworth Google TV (docs/devices): CH1+CH2 hardware
  26 fps, CH3 (1280×720) software with stalls, CH4 no stream (open question #9). 83 MB RSS.
- Pulled forward from Phase 3 at the maintainer's request: LAN pairing server (`:pairing`, pure
  JVM, 17 tests), modern TV sign-in screen with D-pad focus order and no auto keyboard, 5
  languages on TV and phone page (portal's `language` cookie kept in sync). Maintainer signed the
  TV in from a phone successfully.
- 2026-10-29 (later): fixed a stream leak (stopVideo was never sent on exit) that made channels
  fail after restarts; streams also stop on Home/screensaver. CH4/CH13–16 confirmed dead in the
  official portal. Animated grid loader + per-tile connecting/no-video states with backoff; every
  channel stays selectable (user decides). Security review of pairing + app: no confirmed
  findings; follow-ups applied (pairing stops on Home, duplicate-header rejection, wider redaction).
  45 tests, 0 lint issues.

## Phase 4: grid editor
- 2026-10-29: `:layouts` module (6 tests), saved layouts, multi-NVR live grid with tile reconciling,
  on-TV editor with modern dialogs (icons, NVR-grouped camera picker, visual grid-size picker),
  scaled tile labels, decoder hand-off queue. Verified on the TV: cameras from two NVRs in one grid.
- 2026-10-29 (later): phone/laptop layout editor (pairing server in layout mode, token renews
  while editing), TV corner card with address + code, camera options show "NVR · channel".
  Maintainer edited the TV layout from a phone. 54 tests, 0 lint issues.
- 2026-10-29 (later): still images for tiles beyond the TV's live decoders: the stream is opened
  just long enough for its first key frame, stopped, and decoded off the live budget (software
  decoder → YUV → 320×180 bitmap). Cached per camera and shown behind the loader while connecting.
  Verified on the TV in a 3×3 grid. 57 tests (3 new JVM tests for the YUV conversion), 0 lint.
- 2026-10-29 (later): named layouts. Model ops (create, select, cycle, default, delete; the default
  always survives a delete), TV "Layouts" dialog, ◀ ▶ at the grid edge flips layouts, phone editor
  with layout pills and New / Make default / Delete. Fixed cut-off tile messages (text now sized in
  onMeasure). 59 tests, 0 lint.
- 2026-10-29 (later): README rebuilt with screenshots (TV images blurred for privacy, web images
  from a local preview with made-up cameras) and a step-by-step install guide (Google TV and
  Fire TV; adb now, Downloader once a release exists). Fixed the phone editor widening the page.
