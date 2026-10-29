# Architecture (overview)

Short version. Full detail, decisions and dependency justifications: [`docs/architecture.md`](docs/architecture.md).

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
│   Video pipeline    Media3 or custom MediaCodec (chosen after Phase 0)  │
│   Pairing server    Ktor CIO in a foreground service, port 8743         │
└─────────────────────────────────────────────────────────────────────────┘
          │ HTTPS / WS                         ▲ HTTP (LAN only, short-lived)
          ▼                                    │
   BitVision cloud (private API)        Phone/laptop pairing page (:pairing-web)
```

## Modules

| Module | Kind | Depends on | Purpose |
|---|---|---|---|
| `:bitvision-client` | Kotlin/JVM lib | OkHttp, kotlinx.serialization, coroutines | Auth, signing, devices, channels, stream negotiation |
| `:bitvision-client-cli` | Kotlin/JVM app | `:bitvision-client` | Smoke test against the real API |
| `:app` | Android app | `:bitvision-client`, Compose TV, Hilt, Media3, Room, Ktor | The TV app |
| `pairing-web/` | static assets | — | Bundled into `:app` assets, served by the pairing server |

## Key flows

1. **Login (TV):** form → `SessionManager.login()` → client auth → tokens encrypted at rest; password discarded.
2. **Login (pairing):** TV starts Ktor :8743 → 6-digit code → 5-min single-use pairing token bound to client IP → credentials POSTed → TV logs in → server stops.
3. **Live grid:** `LayoutRepository` → visible tiles → `StreamManager` allocates decoders within budget → sub-streams; fullscreen swaps to main.

## Status

Video transport is **unknown until Phase 0** completes; the video-pipeline box is a placeholder. See `docs/streaming.md`.
