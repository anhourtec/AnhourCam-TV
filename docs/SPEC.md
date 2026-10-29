# CamGrid TV

> Working name. A minimal, open-source **Google TV / Android TV** app for watching cameras from NVRs registered to a **BitVision cloud account**, with NVR-style multi-camera grids.

This is the full build specification and phase plan. Read it top to bottom before writing any code. The public overview is the repository [README](../README.md).

---

## 1. What we're building

BitVision has phone apps but **no TV app**. CamGrid TV fills that gap:

- Log in to a BitVision cloud account, either **on the TV** or **from any phone/laptop on the same Wi-Fi** through a local pairing page.
- See every NVR on the account and every channel (camera) on each NVR.
- Build and save **NVR-style grid layouts** (1, 4, 6, 8, 9, 13, 16 tiles, plus custom) by picking any camera from any NVR into any tile.
- Open a tile fullscreen and switch to the high-quality stream.
- Fully **native**: Kotlin + Jetpack Compose for TV + native video playback. **No WebView, no embedded browser.**
- **Sideload only.** Not published on Google Play. Users install with Developer Mode + ADB (see §9).

### 1.1 Status and disclaimer

- Unofficial. **Not affiliated with, endorsed by, or supported by BitVision** or any device manufacturer. "BitVision" is used only to describe compatibility.
- The cloud API is **private and undocumented**. It was learned by observing the official web portal (https://bitvision.app/) while logged in with the maintainer's own account. It may change or break at any time.
- Intended for people accessing **their own devices with their own account**. Users are responsible for complying with BitVision's terms.
- Do not commit credentials, tokens, device serials, camera images, or unredacted network captures. Ever.

---

## 2. Project phases (follow in order)

| Phase | Goal | Output |
|---|---|---|
| 0 | API discovery via Playwright MCP | `docs/api.md`, `docs/streaming.md`, redacted samples |
| 1 | Kotlin API client (JVM module, testable without TV) | `:bitvision-client` module + CLI smoke test |
| 2 | Video pipeline proof of concept | One camera playing natively on an Android TV device/emulator |
| 3 | TV app shell + login (on-TV and LAN pairing) | Working auth flows |
| 4 | Device/channel browser + grid engine | Saved layouts, multi-tile live view |
| 5 | Polish, performance, docs | Release APK, install guide |

**Do not start a phase until the previous phase's acceptance criteria are met.** If Phase 0 shows the video transport is not natively playable (see §3.5), stop and report before going further.

---

## 3. Phase 0: API discovery with Playwright MCP

### 3.1 Setup

- Discovery uses **Playwright MCP** with a **headed** browser.
- The tooling opens `https://bitvision.app/`. **The human types the credentials and logs in.** Automation never types, stores, or logs the password.
- After login, the automation drives the UI to exercise every feature while capturing network traffic.

### 3.2 What to capture

Exercise these flows one at a time, capturing **all** XHR/Fetch, WebSocket (including individual frames/messages), and media requests:

1. Login (request shape only; redact credentials)
2. Session/token refresh, logout, session expiry behaviour
3. Device (NVR) list, device status (online/offline), device details
4. Channel list per NVR: names, numbers, online state, stream types available
5. Start live view on one channel (sub-stream)
6. Switch to main stream / HD
7. Start live view on 4 channels at once (multi-view)
8. Stop live view
9. Snapshot / screenshot (if available)
10. Audio on/off
11. Playback / recordings search and timeline (if available; nice-to-have, document only)
12. PTZ controls (if available; document only)
13. Any keepalive/heartbeat traffic during a 5+ minute live session

### 3.3 Analysis to perform

For each endpoint, record:

- Method, URL pattern, required headers, cookies
- Auth mechanism (cookie, bearer, signed params, nonce/timestamp signing, etc.). If requests are signed, **find the signing logic in the site's JS** (e.g. `common.js`, `space.js`, `dynamic.js`, `pubjs.js`) and document the algorithm.
- Request/response schemas, with field meanings inferred and marked `(inferred)` where uncertain
- Error responses and codes observed
- Token lifetime and refresh behaviour

For video specifically (most important):

- How a stream is requested (HTTP call → returns URL? WS handshake? signalling messages?)
- Transport: WebSocket binary, WebRTC, HLS, HTTP-FLV, or other
- Container: FLV, fMP4, raw Annex-B H.264/H.265, private framing?
- Codec, resolution, and frame rate per stream type (check SPS/PPS in the first frames)
- Whether payloads are **encrypted or obfuscated**, and whether decoding happens in JS/WASM
- Per-frame header layout if there's custom framing (document byte offsets)
- Keepalive requirements and how a stream is torn down

### 3.4 Deliverables

```
docs/
  api.md            # Every REST/XHR endpoint: auth, request, response, errors
  streaming.md      # Full video pipeline: request → transport → framing → codec
  auth.md           # Login, token lifecycle, signing algorithm (if any)
  open-questions.md # Anything unresolved or guessed
  samples/          # REDACTED JSON samples, a few redacted binary frame hex dumps
```

Redaction rules: replace tokens, session IDs, emails, device serials/IDs, IPs, and geolocation with stable placeholders (`<TOKEN>`, `<DEVICE_ID_1>`, etc.). Scan before committing.

### 3.5 Stop conditions

Stop and report to the human if any of these hold:

- Video is decoded by an obfuscated WASM/JS decoder from an encrypted/private format with no clear key path.
- The portal requires a browser plugin/ActiveX/NPAPI to play video.
- Login requires CAPTCHA or bot-detection on every session. Don't try to bypass it; report it.

### 3.6 Acceptance criteria

- `docs/api.md` is complete enough that someone could write a client without opening DevTools.
- `docs/streaming.md` identifies the transport and codec with evidence (captured frame headers).

---

## 4. Architecture

```
┌─────────────────────────── Android TV app ───────────────────────────┐
│                                                                      │
│  UI (Compose for TV)                                                  │
│   ├── Login screen (on-TV, D-pad keyboard)                           │
│   ├── Pairing screen (shows LAN URL + QR + 6-digit code)             │
│   ├── Devices & channels browser                                     │
│   ├── Grid view (live)          ├── Layout editor                    │
│   └── Fullscreen view           └── Settings                         │
│                                                                      │
│  Domain                                                              │
│   ├── SessionManager (token storage, refresh, logout)                │
│   ├── DeviceRepository (NVRs, channels, status cache)                │
│   ├── LayoutRepository (saved grids, layouts.json)                   │
│   └── StreamManager (decoder budget, sub/main switching, lifecycle)  │
│                                                                      │
│  Data                                                                │
│   ├── :bitvision-client (pure Kotlin/JVM; OkHttp + kotlinx.serial.)  │
│   ├── Video pipeline (Media3/ExoPlayer or MediaCodec, per §6)        │
│   └── LAN pairing server (Ktor embedded, CIO engine)                 │
└──────────────────────────────────────────────────────────────────────┘
```

### 4.1 Tech stack

- **Language:** Kotlin, coroutines + Flow
- **UI:** Jetpack Compose for TV (`androidx.tv:tv-material`), single Activity
- **DI:** Hilt
- **Networking:** OkHttp (REST + WebSocket), kotlinx.serialization
- **Video:** Media3/ExoPlayer where the format allows; otherwise a custom `MediaCodec` pipeline rendering to `SurfaceView`
- **LAN server:** Ktor server (CIO engine), embedded in a foreground service
- **Storage:** `layouts.json` (layouts), SharedPreferences (settings), Android Keystore + encrypted storage for session tokens
- **Min SDK:** 25 (covers Fire OS 6 Fire TV sticks and Android TV 7.1+; see architecture.md). **Target:** latest stable
- **Build:** Gradle Kotlin DSL, version catalog

### 4.2 Repository layout

```
camgrid-tv/
  app/                     # Android TV app
  bitvision-client/        # Pure Kotlin JVM library: auth, devices, stream negotiation
  bitvision-client-cli/    # Small JVM CLI for smoke-testing the client against the real API
  pairing/                 # LAN pairing server: phone sign-in + phone layout editor (pure JVM)
  layouts/                 # Layout model shared by the TV and the phone editor (pure JVM)
  docs/                    # api.md, streaming.md, auth.md, install.md, architecture.md
  scripts/                 # redaction checker, adb helper scripts
  README.md                # public overview
  LICENSE                  # Apache-2.0
  NOTICE.md                # BitVision ownership, non-affiliation, disclaimer
```

---

## 5. Authentication

Both login paths must work and produce the **same session** stored on the TV.

### 5.1 Login on the TV

- Standard form: email/username + password + the portal's 4-character image CAPTCHA, navigable with D-pad and the on-screen keyboard.
- **CAPTCHA is built into the app; users need nothing else** (no browser, no Playwright, no
  computer). The app fetches the image from `/home/account/yzm` with its own session cookie and
  shows it on the login screen at a size readable from the couch.
  - "New image" button (refetches with a fresh timestamp).
  - Code entry: a large on-screen number pad when the code is digits (observed: 4 digits, *inferred
    from one sample*; fall back to the full keyboard if a letter is ever needed).
  - Wrong code or password: show the portal's message, load a new image automatically, keep the
    email filled in, clear only the password and code.
  - Never solved automatically (no OCR, no bypass).
- Password is used once to obtain the cloud session and is **never persisted**. Only tokens are stored (encrypted).

### 5.2 Login from another device on the LAN (pairing)

Typing passwords with a TV remote is painful, so the TV runs a small local web server for pairing.

**Port:** `8743` by default (configurable in Settings). Avoid common ports.

**Flow:**

1. User opens **"Log in from phone"** on the TV.
2. TV starts the Ktor server and displays:
   - `http://<tv-lan-ip>:8743/`
   - a QR code for that URL
   - a **6-digit pairing code** (random, single-use)
3. User opens the URL on a phone or laptop on the same network.
4. The page asks for the **pairing code**. On success the server issues a **pairing token**:
   - random, 256-bit, single-use
   - valid **5 minutes**
   - bound to the client IP that entered the code
5. The page shows the BitVision login form with the CAPTCHA image. The CAPTCHA is bound to the
   **TV's** portal session, so the TV fetches it and serves it to the phone through the pairing
   server (`GET /captcha`, pairing token required, `Cache-Control: no-store`), with a "new image"
   button. Credentials and the typed code are POSTed with the pairing token; the TV performs the
   login with the same session that fetched the image.
6. The TV performs the cloud login itself, stores the session, shows "Logged in" on both screens, invalidates the token, and **shuts the server down**.

**Security requirements:**

- The server runs **only while the pairing screen is open** (or for 10 minutes max), then stops.
- Bind only to the active LAN interface (Wi-Fi/Ethernet), never to all interfaces when avoidable. Reject non-private source IPs (outside RFC1918 / link-local / IPv6 ULA).
- Pairing code: 5 wrong attempts → regenerate the code and add a 30-second lockout.
- Constant-time comparison for codes and tokens.
- Rate-limit all endpoints. Limit request body sizes.
- Plain HTTP is accepted on the LAN given the short-lived, single-use design. Document this trade-off in `docs/security.md`. Optional later: a self-signed HTTPS mode.
- Never log credentials or tokens, on the TV or in the server.

**Manage layouts from a phone or laptop (Phase 4, required):** everything the on-TV layout
editor does (§7.3) is also available on the pairing page: choose a grid size (1, 2, 4, 6, 8, 9,
10, 13, 16 …), pick the NVR and camera for every tile from any NVR on the account, set the order
(drag or arrows), rename, save, set default. On the TV: Settings → "Manage from phone" shows the
address and a fresh code; the same pairing rules apply (single-use code, 5-minute IP-bound token,
server stops when the screen closes). Changes save on the TV and the live grid updates at once.

### 5.3 Session handling: the app must never "time out" like the website

- **Live view never depends on the web session.** The gateway does not receive the portal cookie
  (verified 2026-10-29: our client streamed with no cookie on the gateway sockets). The app caches
  the device list and each device's page data (gateway URLs + NVR credentials) encrypted on the
  TV, and starts live view from that cache. An expired web session never stops video.
- **Keep the web session alive** with `SessionKeeper` (a light request every 10 minutes while the
  app runs; a WorkManager job every 15 minutes in the background), so the device list can refresh.
- **Gateway sockets** send the 200 s heartbeat and reconnect with backoff if dropped.
- **No sleep during live view:** keep the screen on and block the screensaver.
- If the web session expires anyway (e.g. the TV was unplugged for days): keep playing from the
  cache and show a small, non-blocking "Sign in again to refresh your devices" notice. Never throw
  the user out of live view. The password is never stored, because signing in again needs a CAPTCHA anyway.
- Auto-refresh per `docs/auth.md`.
- On unrecoverable auth failure: go back to the login screen and keep saved layouts.
- Settings → **Log out** clears tokens and the device cache (layouts kept unless the user chooses "Reset everything").

---

## 6. Video pipeline

Implementation depends on Phase 0 findings. Pick the matching path:

| Transport found | Implementation |
|---|---|
| HLS | Media3 `HlsMediaSource` |
| HTTP-FLV / WS-FLV | Custom Media3 `DataSource` (WebSocket → byte stream) + FLV extractor |
| WS + fMP4 | Custom Media3 `DataSource` + fragmented MP4 extractor |
| WS + raw H.264/H.265 (custom framing) | Own depacketizer → `MediaCodec` → `SurfaceView` |
| WebRTC | Native WebRTC Android library |

### 6.1 StreamManager rules

- **Grid tiles always use the sub-stream.** Fullscreen uses the main stream and switches back when leaving fullscreen.
- **Decoder budget:** query `MediaCodecList` at startup and count hardware instances plus a small
  software allowance (`DecoderBudget.capacity`). Tiles beyond it show a **still image** with a
  "Still image" badge, refreshed round-robin (open stream → first key frame → stop → decode with
  a software decoder outside the live budget), and take over a live decoder as soon as one frees.
  Never crash or show black tiles silently.
- Only tiles on screen hold streams. Pages/tours release streams for tiles not visible.
- Reconnect with exponential backoff (5 → 10 → 20 → 30 s). Per-tile states: **connecting**
  (animated grid loader), **live**, **no video** ("No video from this camera, trying again in N s");
  the tile keeps its place, because the user chose that camera. Every channel stays selectable;
  the app never hides channels on its own.
- Always send stopVideo (8232) when a tile stops, even when the app is closing (process-wide
  scope, 3 s limit). The NVR keeps streaming abandoned sessions otherwise and runs out of slots.
- Low latency: minimal buffering, drop late frames rather than building delay.
- Audio: off by default. When enabled, only the focused or fullscreen tile plays audio.

### 6.2 Phase 2 acceptance

- One channel plays natively on a real Google TV device or the Android TV emulator for 30+ minutes without leaking memory or drifting.
- Four sub-streams play simultaneously on a Chromecast with Google TV-class device (or a documented reason why not).

---

## 7. Grids and layouts

The core feature: NVR-style multi-view.

### 7.1 Built-in layouts

`1`, `2 (side by side)`, `4 (2×2)`, `6 (1 large + 5)`, `8 (1 large + 7)`, `9 (3×3)`, `10 (2 large + 8)`, `13 (1 center large + 12)`, `16 (4×4)`, `25 (5×5)`, `36 (6×6)`

Higher counts are allowed but warned about against the decoder budget (snapshots / tap to play beyond it).

### 7.2 Custom layouts

- Define a layout on a 12×12 cell grid, with tiles spanning rectangles. Store it as JSON.
- Each tile maps to `{ nvrId, channelId }` or is empty.
- Any camera from **any NVR** can go in any tile.

### 7.3 Layout editor (TV, D-pad friendly)

- Choose a template or custom shape; change the grid size of an existing layout at any time
  (cameras already placed stay in order, extra ones move to the next page).
- Select a tile → pick an NVR → pick a channel (list with names, online status and a live or
  last-known thumbnail). Any camera from **any NVR on the account** can go in any tile.
- **Rearrange with the remote:** hold OK on a tile → "Move" → arrows carry it, OK drops it (it
  swaps with the tile it lands on); "Swap camera", "Clear tile" and "Rename layout" in the same menu.
- Save, set as default (opens on app launch), delete, duplicate.

### 7.3a The app remembers everything

All of this is saved on the TV (`layouts.json` for layouts, preferences for settings) and restored on launch,
across reboots and app updates. "Log out" keeps it; only "Reset everything" clears it:

- every saved layout: grid size, tile → `{nvrId, channelId}` mapping, name, order;
- the default layout, and the last layout + page that was on screen (the app reopens exactly there);
- per-tile and per-layout stream quality overrides, audio choice, tour-mode settings;
- the app language and the pairing port;
- cached device list and last snapshot per camera (shown instantly while streams connect).

Layouts refer to cameras by NVR ID + channel number, so they survive a device-list refresh; a
camera that disappears from the account shows "Camera unavailable" instead of breaking the layout.

### 7.4 Viewing

- D-pad moves focus between tiles. OK opens fullscreen, Back returns.
- Long-press OK on a tile opens a quick menu: swap camera, stream quality, audio.
- **Pages:** if the account has more cameras than tiles, Left/Right on the edges flips pages.
- **Tour mode:** auto-cycle pages or saved layouts every N seconds (configurable).
- Minimal overlays: camera name + status dot, auto-hide after 3 s, toggled with the Menu/Info key.
- Prevent screensaver while live view is on.

---

## 8. UI / UX principles

- **Clean and minimal.** Dark theme only. Video is the content; chrome stays out of the way.
- Everything reachable with a basic remote (D-pad, OK, Back, Home). No touch assumptions.
- Clear focus indicators. Large text, readable from a couch at 3 m.
- Launch straight into the default layout if logged in; otherwise to the login choice screen ("Log in on TV" / "Log in from phone").
- Screens: Login choice → Login / Pairing → Home (layouts) → Grid → Fullscreen; plus Devices, Layout editor, Settings.
- Settings: pairing port, decoder budget override, tour interval, overlay on/off, stream defaults, log out, about (version, licenses, disclaimer).
- Must provide a TV launcher banner (`android:banner`) and `LEANBACK_LAUNCHER` intent, and declare touchscreen as not required.

---

## 9. Installation (sideload, no Play Store)

Documented in [`install.md`](install.md).

### 9.1 Enable Developer Mode on Google TV / Android TV

1. **Settings → System → About**
2. Scroll to **Android TV OS build** and press OK on it **7 times** until "You are now a developer" appears.
3. Go back to **Settings → System → Developer options**.

### 9.2 Option A: Wireless ADB from a computer (recommended)

**Google TV / Android TV 11+ (wireless debugging):**
1. Developer options → turn on **Wireless debugging** → **Pair device with pairing code**. Note the IP:port and code shown.
2. On your computer (with Android platform-tools installed):
   ```bash
   adb pair <TV_IP>:<PAIR_PORT>      # enter the pairing code
   adb connect <TV_IP>:<CONNECT_PORT>  # port shown on the Wireless debugging screen
   adb install -r camgrid-tv.apk
   ```

**Older devices (network debugging):**
1. Developer options → turn on **USB debugging** / **Network debugging**.
2. ```bash
   adb connect <TV_IP>:5555
   # Accept the "Allow debugging?" prompt on the TV
   adb install -r camgrid-tv.apk
   ```

### 9.3 Option B: Without a computer

1. Install a file downloader app on the TV from the Play Store.
2. Settings → Apps → Security & restrictions → **Unknown sources** → allow that app.
3. Download the APK from the project's GitHub Releases page and install it.

### 9.4 Updating

`adb install -r` with the new APK, or download it again. Layouts and the session are kept.

### 9.5 After installing

Turn off Wireless/USB debugging when you're done. The app does not need it to run.

---

## 10. Engineering rules

- Work phase by phase; open a short summary at the end of each phase in `docs/progress.md`.
- `:bitvision-client` must have **no Android dependencies**, so it can be unit-tested and run from the CLI.
- Unit tests for: request signing, response parsing (against redacted samples), token refresh, pairing-code/token logic, layout serialization, decoder budget logic.
- No secrets in code, logs, tests, or git history. Add a `scripts/check-redaction` pre-commit check for token-like strings, emails, and serials.
- Logging: `Timber` in debug; release builds log no network bodies.
- Keep dependencies minimal and justified.
- Code style: ktlint/detekt; small, focused files.
- When something in the private API is guessed rather than observed, mark it in code with `// INFERRED:` and list it in `docs/open-questions.md`.
- Never implement CAPTCHA bypass, rate-limit evasion, or anything that accesses accounts or devices the user doesn't own.

---

## 11. Milestone checklist

- [ ] Phase 0: `docs/api.md`, `docs/streaming.md`, `docs/auth.md` complete and redacted
- [ ] Phase 1: `bitvision-client` logs in, lists NVRs/channels, negotiates a stream (CLI demo)
- [ ] Phase 2: one native stream playing on TV; four sub-streams concurrently
- [ ] Phase 3: on-TV login + LAN pairing login on port 8743 with short-lived tokens
- [ ] Phase 4: built-in grids ✅, on-TV editor ✅, phone editor ✅, fullscreen ✅, several named layouts, pages, tour mode, snapshots
- [ ] Phase 5: performance pass, decoder budget UX, `docs/install.md`, signed release APK on GitHub Releases

---

## 12. License

Apache License 2.0 — see [`LICENSE`](../LICENSE). Copyright 2026 anhourtec and the CamGrid TV contributors, for this project's own code only. BitVision and all BitVision software, services and trademarks belong to their respective owner(s); see [`NOTICE.md`](../NOTICE.md) for the full ownership, non-affiliation and disclaimer statement. No BitVision trademarks or logos in the app name, icon, or artwork.