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
│   DeviceRepository  NVRs, channels, status cache (Room)                 │
│   LayoutRepository  saved grids as JSON on a 12×12 cell grid (Room)     │
│   StreamManager     decoder budget, sub/main switching, reconnects      │
│                                                                         │
│ Data                                                                    │
│   :bitvision-client pure Kotlin/JVM — OkHttp + kotlinx.serialization    │
│   Video pipeline    WS → 44-byte depacketizer → MediaCodec → Surface   │
│   Pairing server    Ktor CIO in a foreground service, port 8743         │
└─────────────────────────────────────────────────────────────────────────┘
          │ HTTPS / WS                         ▲ HTTP (LAN only, short-lived)
          ▼                                    │
   BitVision cloud (private API)        Phone/laptop pairing page (:pairing-web)
```

## 0. Modules

| Module | Kind | Depends on | Purpose |
|---|---|---|---|
| `:bitvision-client` | Kotlin/JVM lib | OkHttp, kotlinx.serialization, coroutines | Auth, signing, devices, channels, stream negotiation |
| `:bitvision-client-cli` | Kotlin/JVM app | `:bitvision-client` | Smoke test against the real API |
| `:app` | Android app | `:bitvision-client`, Compose TV, Room, Ktor | The TV app |
| `pairing-web/` | static assets | — | Bundled into `:app` assets, served by the pairing server |

### `:bitvision-client` packages (`com.anhourtec.camgrid.client`)

| Package | Contents |
|---|---|
| `portal` | `PortalClient` (CAPTCHA, login, overview, device page, keep-alive, logout), `SessionCookieJar` (`PHPSESSID`, restorable) |
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
- **DeviceRepository** — NVR + channel lists from the client, cached in Room, status refresh.
- **LayoutRepository** — built-in templates + custom layouts (12×12 cell grid, tiles as rects,
  each tile `{nvrId, channelId}` or empty), JSON-serialized into Room. Default-layout flag.
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
| Layouts, device cache | Room |
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
| Room | app | Layouts + device cache |
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
| 2026-10-29 | `pairing-web/` in TypeScript, strict, ESLint | Zero lint/type errors rule |
| 2026-10-29 | Spec moved to `docs/SPEC.md`; public README is user-facing | Discoverability |
| 2026-10-29 | Gradle 9.8.2 wrapper (SHA-256 pinned), Kotlin 2.4.21, JDK 17 toolchain, `allWarningsAsErrors` | Current stable versions; reproducible build |
| 2026-10-29 | Package `com.anhourtec.camgrid`, no BitVision name in packages | Legal posture (NOTICE.md) |
| 2026-10-29 | detekt line length aligned to ktlint (140), guard-clause returns allowed | Tools must not disagree |
