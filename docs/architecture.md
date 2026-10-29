# Architecture (detailed)

Overview diagram: [`../ARCHITECTURE.md`](../ARCHITECTURE.md). Product spec: `README.md` §4–§7.

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
- **Video pipeline** — chosen from README §6 table once `docs/streaming.md` identifies transport.
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
| Compose for TV (`androidx.tv:tv-material`) | app | Official TV focus/D-pad components |
| Hilt | app | DI across Activity/services/ViewModels |
| Media3 ExoPlayer | app | Native playback where container allows (TBD Phase 0) |
| Room | app | Layouts + device cache |
| DataStore | app | Settings |
| Ktor server (CIO) | app | Embedded LAN pairing server, no Netty |
| Timber | app | Debug logging only |
| ZXing core (TBD) | app | QR code for pairing URL |

## 5. Decisions log

| Date | Decision | Reason |
|---|---|---|
| 2026-10-29 | Repo root is this folder (`CamGrid/`), not a nested `camgrid-tv/` | Simpler; same layout as README §4.2 |
| 2026-10-29 | Raw captures live in git-ignored `captures/` | Keep secrets out of history |
