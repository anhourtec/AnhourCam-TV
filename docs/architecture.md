# Architecture

Product spec: [`SPEC.md`](SPEC.md) §4–§7. Plan: [`roadmap.md`](roadmap.md).

## Overview

```
┌───────────────────────── Android TV app (:app) ─────────────────────────┐
│ UI — Compose for TV, single Activity                                    │
│   LoginChoice → Login | Pairing → Home(layouts) → Grid → Fullscreen     │
│   + Devices browser, Layout editor, Settings                            │
│                                                                         │
│ Domain                                                                  │
│   SessionManager    tokens (Keystore-encrypted), refresh, logout        │
│   DeviceDirectory   NVRs, channels, device pages (per run)              │
│   LayoutRepository  saved grids, layouts.json (60×60-unit grid)         │
│   StreamManager     decoder budget, sub/main switching, reconnects      │
│                                                                         │
│ Data                                                                    │
│   :bitvision-client pure Kotlin/JVM — OkHttp + kotlinx.serialization    │
│   Video pipeline    WS → 44-byte depacketizer → MediaCodec → Surface   │
│   Pairing server    Ktor CIO in a foreground service, port 8743         │
└─────────────────────────────────────────────────────────────────────────┘
          │ HTTPS / WS                         ▲ HTTP (LAN only, short-lived)
          ▼                                    │
   BitVision cloud (private API)        Phone/laptop: sign-in + layout editor (:pairing)
```

## 0. Modules

| Module | Kind | Depends on | Purpose |
|---|---|---|---|
| `:bitvision-client` | Kotlin/JVM lib | OkHttp, kotlinx.serialization, coroutines | Auth, signing, devices, channels, stream negotiation |
| `:bitvision-client-cli` | Kotlin/JVM app | `:bitvision-client` | Smoke test against the real API |
| `:app` | Android app | `:bitvision-client`, `:pairing`, `:layouts`, coroutines | The TV app (Android Views) |
| `:layouts` | Kotlin/JVM lib | kotlinx.serialization | Layout model: grid templates, camera per tile, JSON codec |
| `:pairing` | Kotlin/JVM lib | — (JDK only) | LAN pairing server: strict HTTP parser, pairing rules, no-JS pages in 5 languages |
| `:ipcam` | Kotlin/JVM lib | `:bitvision-client` (FrameSink/FrameHeader only), OkHttp | Generic IP cameras: LAN scan, ONVIF lookup, RTSP over TCP, H.264/H.265 RTP depacketizer (Phase 6, [ipcam.md](ipcam.md)) |

### `:bitvision-client` packages (`com.anhourtec.camgrid.client`)

| Package | Contents |
|---|---|
| `portal` | `PortalClient` (CAPTCHA, login, overview, device page, keep-alive, logout), `SessionCookieJar` (`PHPSESSID`, restorable), `SessionKeeper` (pings every 10 min, reports expiry) |
| `page` | `DeviceListParser` (`/home/space`), `DevicePageParser` (`tipsDeviceInfo`), models; `NvrCredentials` redacts itself |
| `protocol` | `Cmd` (command IDs and constants), `Messages` (outgoing JSON), `Incoming` (parsing), `StreamQuality` |
| `gateway` | `GatewaySession` (signalling socket: register, open device, play/stop, heartbeat), `DeviceLink` |
| `media` | `MediaSession` (media socket, relay ack, frame delivery), `FrameDepacketizer` + `FrameHeader` (zero-copy, no per-frame allocation), `RelayAck`, `FrameSink` |

Live view in code: `PortalClient.overview()` + `deviceDetail()` → `GatewaySession.connect()` →
`openDevice()` → `MediaSession.open()` → `playVideo()` → `MediaSession.startStream()`; frames arrive
on `FrameSink` with the payload still in the socket buffer.

## Key flows

1. **Login (TV):** form → `SessionManager.login()` → client auth → tokens encrypted at rest; password discarded.
2. **Login (pairing):** TV starts Ktor :8743 → 6-digit code → 5-min single-use pairing token bound to client IP → credentials POSTed → TV logs in → server stops.
3. **Live grid:** `LayoutRepository` → visible tiles → `StreamManager` allocates decoders within budget → sub-streams; fullscreen swaps to main.


## 1. Layers

### UI (`:app`, Compose for TV)
- Single Activity, Compose navigation. Screens: LoginChoice, Login, Pairing, Home, Grid,
  Fullscreen, Devices, LayoutEditor, Settings.
- D-pad first: every action reachable with D-pad/OK/Back/Home. Dark theme only.
- Launch: logged in → default layout; otherwise LoginChoice.

### Domain
- **SessionManager** — owns the session. Login (password used once, never persisted), refresh per
  `docs/auth.md`, logout (clears tokens + device cache, keeps layouts unless "Reset everything").
  Emits `Flow<SessionState>`; unrecoverable auth failure → LoginChoice.
- **DeviceDirectory** — NVR + channel lists and device pages, loaded once per run (encrypted on-disk cache: TODO).
- **LayoutRepository** — `LayoutState` (layouts, default, last watched) as `layouts.json` in app-private
  storage, replaced atomically. Model and templates live in `:layouts`.
- **StreamManager** — the only owner of decoders/streams.
  - Grid tiles → sub-stream; fullscreen → main stream.
  - Decoder budget from `MediaCodecList` (+ user override). Tiles beyond budget show snapshot /
    "press OK to play".
  - Only visible tiles hold streams. Backoff reconnect (cap 30 s). Per-tile state:
    connecting / live / offline / error.
  - Audio off by default; only the focused/fullscreen tile may play audio.

### Data
- **`:bitvision-client`** — pure JVM. No Android imports (enforced by the module being a plain
  `kotlin("jvm")` module). Exposes suspend APIs: `login`, `refresh`, `listDevices`,
  `listChannels`, `openStream(channel, quality)`. Request signing lives here.
- **Video pipeline**: Phase 0 found raw H.265/H.264 Annex-B behind a private 44-byte header on a
  WebSocket ([streaming.md](streaming.md)). Path: OkHttp WebSocket → `FrameDepacketizer` (pure
  JVM, in `:bitvision-client`, reads into reused buffers) → `MediaCodec` (hardware decoder,
  configured from VPS/SPS/PPS of the first key frame) → `SurfaceView` per tile. No Media3.
- **Pairing server** — Ktor CIO inside a foreground service; see §3.

## 2. Storage

| What | Where |
|---|---|
| Session tokens | EncryptedSharedPreferences / Keystore-wrapped DataStore |
| Settings | DataStore (Preferences) |
| Layouts | `layouts.json` (app-private, atomic replace, excluded from backup) |
| Device cache | in memory per run (encrypted disk cache planned) |
| Password | **never stored** |

## 3. Pairing server security (summary — full in `docs/security.md`)

Port 8743 (configurable) · runs only while Pairing screen open, 10 min max · binds to active LAN
interface · rejects non-private source IPs · 6-digit single-use code, 5 failures → regenerate +
30 s lockout · 256-bit pairing token, 5 min, single-use, IP-bound · constant-time compares ·
rate limits + body size limits · no credential/token logging.

## 4. Dependencies and why

| Dependency | Module | Why |
|---|---|---|
| OkHttp | client | HTTP + WebSocket in one small, JVM-friendly lib |
| kotlinx.serialization | client, app | Multiplatform JSON, no reflection |
| kotlinx.coroutines | all | Async + Flow |
| JUnit 6, kotlinx-coroutines-test, OkHttp MockWebServer 3 | tests | Unit tests and a fake gateway for the full live-view handshake |
| ktlint (Gradle plugin 14.2, ktlint 1.8), detekt 2.0.0-alpha.6 | build | Lint rules; detekt 2 alpha is the only line that supports Kotlin 2.4 |
| Compose for TV (`androidx.tv:tv-material`) | app | Official TV focus/D-pad components |
| ~~Hilt~~ | — | Dropped for startup speed and APK size: manual constructor DI in one `AppGraph` |
| ~~Media3~~ | — | Not needed: private framing goes straight to `MediaCodec` |
| DataStore | app | Settings |
| Ktor server (CIO) | app | Embedded LAN pairing server, no Netty |
| Timber | app | Debug logging only |
| ZXing core (TBD) | app | QR code for pairing URL |

## 4a. Performance design

- Kotlin native Android only. Hardware decoding via `MediaCodec`, one `SurfaceView` per visible
  tile (Compose draws only chrome around it). No software or WASM decoding.
- Zero-copy-ish video path: WebSocket `ByteString` → slice past the 44-byte header →
  `MediaCodec` input buffer. No per-frame object allocation.
- Decoder budget from `MediaCodecList` capabilities (`getMaxSupportedInstances`, performance points).
- Startup: no DI framework, no reflection, lazy init of the pairing server, Baseline Profiles,
  R8 full mode. Release APK target < 8 MB.
- Budgets live in `performance.md` (Phase 5).

## 4b. Platforms

- **Google TV / Android TV** and **Amazon Fire TV** (Fire OS 6+, Android 7.1 / API 25 based).
- `minSdk 25` so older Fire TV Sticks are covered. No Google Play Services anywhere.
- Manifest: `LEANBACK_LAUNCHER` + `android:banner`, touchscreen not required. Fire TV reads the
  same leanback intent.

## 5. Decisions log

| Date | Decision | Reason |
|---|---|---|
| 2026-10-29 | Repo root is this folder (`CamGrid/`), not a nested `camgrid-tv/` | Simpler; same layout as SPEC §4.2 |
| 2026-10-29 | Raw captures live in git-ignored `captures/` | Keep secrets out of history |
| 2026-10-29 | Video path: own depacketizer → MediaCodec → SurfaceView | Phase 0: private framing + Annex-B, no container for Media3 |
| 2026-10-29 | Drop Hilt and Media3 | Startup time and APK size ("must feel instant") |
| 2026-10-29 | minSdk 25, no GMS | Fire TV (Fire OS 6/7/8) support |
| 2026-10-29 | Spec moved to `docs/SPEC.md`; public README is user-facing | Discoverability |
| 2026-10-29 | Gradle 9.8.2 wrapper (SHA-256 pinned), Kotlin 2.4.21, JDK 17 toolchain, `allWarningsAsErrors` | Current stable versions; reproducible build |
| 2026-10-29 | Package `com.anhourtec.camgrid`, no BitVision name in packages | Legal posture (NOTICE.md) |
| 2026-10-29 | detekt line length aligned to ktlint (140), guard-clause returns allowed | Tools must not disagree |
| 2026-10-29 | Live view starts from an encrypted device cache, independent of the web session | Gateway never sees the portal cookie; avoids website-style timeouts and saves ~5.7 s per start |
| 2026-10-29 | Keep gateway + device links open; open tile media sockets in parallel | Measured ~3 s per gateway/NVR handshake and ~4 s per tile |
| 2026-10-29 | Pairing server hand-written on `ServerSocket` instead of Ktor | ~0 KB of dependencies vs several MB; tiny fixed surface; every rule unit-tested on the JVM |
| 2026-10-29 | Pairing pages are plain HTML forms, no JavaScript (so no `pairing-web/` TypeScript build) | Works on any browser, strictest CSP possible |
| 2026-10-29 | compileSdk 37 / targetSdk 37 | OkHttp 5.5's Android artifact requires compileSdk 37; app still runs on API 25+ |
| 2026-10-29 | Phase 2 PoC UI uses Android Views; Compose for TV arrives with the real UI (Phase 3/4) | Fast PoC; the decoder pipeline is UI-independent |
| 2026-10-29 | In-app language (cn/en/jp/por/py, default en) independent of the system locale | Matches the portal's language list; same codes sent in its `language` cookie |
| 2026-10-29 | Layouts stored as one JSON file (`layouts.json`, atomic replace) instead of Room | A few KB of data; no annotation processing, smaller APK, faster start |
| 2026-10-29 | `:layouts` pure-JVM module (60×60-unit grid, 11 templates) shared by TV and phone editor | One tested model for both editors |
| 2026-10-29 | `GatewayPool`: one gateway session per gateway, one device login per NVR, shared by tiles | Mix cameras from several NVRs in one grid |
| 2026-10-29 | Grid updates reconcile tiles instead of rebuilding | Changing one camera restarts one stream; moves restart none |
| 2026-10-29 | TV editor in Android Views with a custom `TvDialog`; Compose for TV still deferred | Lean on the 930 MB test TV; consistent custom look |
| 2026-10-29 | Phone layout editor served by `:pairing` (layout mode), plain HTML form | Same pairing protections as sign-in; works in any browser |
| 2026-10-29 | Pairing token renews on each editor request (`touch`), still capped by the 10-minute server life | Editing can take longer than 5 minutes |
| 2026-10-29 | Still images = one key frame per camera, decoded off-budget with a software decoder | No snapshot API on the portal; the NVR starts every stream with a key frame |
| 2026-10-29 | App module gets JVM unit tests (JUnit 6) for pure code such as YUV conversion | Test without a device |
| 2026-10-29 | Product renamed to AnhourCam TV; package, applicationId, modules and repo name unchanged | Changing the applicationId would install a separate app and lose saved layouts |
| 2026-10-29 | Phase 6: generic IP cameras in a separate `:ipcam` JVM module, own RTSP client (no ExoPlayer) | Same zero-copy FrameSink → MediaCodec path as BitVision, no new dependency (~0 KB), BitVision code untouched |
| 2026-10-29 | IP cameras as `CameraRef("ip:<uuid>", 0)` | Saved layouts need no migration; mixes with BitVision cameras everywhere |
| 2026-10-29 | RTSP over TCP interleaved only (no UDP) | Works through any home network without opening ports; one socket per tile |
| 2026-10-29 | LAN scan = ONVIF WS-Discovery + TCP sweep of 554/8554 on the private /24 | The maintainer's cameras ignore WS-Discovery but answer RTSP |
| 2026-10-29 | GitHub repo renamed to `anhourtec/AnhourCam-TV` | Matches the product name |
