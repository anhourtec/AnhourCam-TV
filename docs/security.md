# Security

## Secrets hygiene
- Never commit credentials, tokens, session IDs, emails, device serials/IDs, IPs, geolocation,
  camera images, or unredacted captures.
- `scripts/check-redaction` runs as a git pre-commit hook; run it manually before every commit.
  Install the hook on a fresh clone:
  `printf '#!/usr/bin/env bash\nexec "$(git rev-parse --show-toplevel)/scripts/check-redaction"\n' > .git/hooks/pre-commit && chmod +x .git/hooks/pre-commit`
- Known protocol constants that look like IDs go in `scripts/redaction-allowlist.txt` (exact strings only).
- Raw captures live in git-ignored `captures/`.
- Placeholders: `<TOKEN>`, `<SESSION_ID>`, `<EMAIL>`, `<USER_ID>`, `<DEVICE_ID_n>`, `<SERIAL_n>`, `<IP_n>`, `<LAT>`, `<LON>`.

## On-device secrets
- Password used once, never persisted. Session encrypted at rest (Keystore).
- NVR local credentials from the device page (`local_user`/`local_pwd`) are secrets: memory only, never logged.
- Release builds log no network bodies.

## Client library and CLI controls
- Gateway URLs must be `wss://` (`DevicePageParser`): the gateway receives the NVR's password
  (cmd 8208), so plain `ws://` is refused. All observed gateway URLs are `wss://`.
- `SessionCookieJar` only stores and sends cookies for the portal host; a restored `PHPSESSID` is
  host-only, `Secure` and `HttpOnly`. The gateway sockets use a client without the cookie jar.
- `NvrCredentials` redacts itself in `toString`; no HTTP logging interceptor is installed.
- CLI session file: directory `0700`, file created `0600` via temp file + atomic move (never
  briefly world-readable). Passwords are read from the console without echo and never saved.
- CLI strips control characters from portal-supplied text before printing (no terminal escape injection).
- Gradle distribution pinned by SHA-256; wrapper jar verified against the official checksum.

## LAN pairing server (SPEC §5.2): implemented in `:pairing`

Implementation notes: plain-HTML pages with **no JavaScript** (CSP `default-src 'none'`, forms only);
strict HTTP/1.1 parser (8 KB head, 4 KB body, no chunked, one request per connection, 10 s read
timeout, 4 concurrent connections); `Host` header must be the TV's own IP (DNS-rebinding defence);
pairing token in an `HttpOnly; SameSite=Strict` cookie, never in URLs; 30 requests/min per IP;
login form values are never logged. 17 unit tests cover the rules below.
- Port 8743, runs only while Pairing screen is open (≤10 min), binds to active LAN interface.
- Rejects non-private sources (outside RFC1918, link-local, IPv6 ULA).
- 6-digit single-use code; 5 failures → regenerate + 30 s lockout.
- Pairing token: 256-bit random, single-use, 5 min, bound to client IP.
- Constant-time comparisons; rate limits; body size limits; no secret logging.

### Trade-off: plain HTTP on the LAN
Credentials cross the LAN in plaintext during pairing. Accepted because the server is
short-lived, single-use, IP-bound and LAN-only. A LAN attacker sniffing at that exact moment
could capture the password. Mitigation later: optional self-signed HTTPS mode.

## Phone layout editor (pairing server, layout mode)
- Started only from the TV menu ("Edit layout from phone or computer"); the TV shows a corner card
  with the address and a fresh code. Same rules as sign-in: single-use code, lockout, IP-bound
  token in an `HttpOnly; SameSite=Strict` cookie, Host-header check, no JavaScript, strict CSP.
- An unpaired POST to `/layout` is redirected to the code page; sign-in routes (`/captcha`,
  `/login`) are off in layout mode.
- Only built-in grid IDs, existing layout IDs and cameras the account actually has are accepted;
  everything shown is HTML-escaped (camera, NVR and layout names). New / Make default / Delete use
  the same paired form; the last layout can't be deleted and a default always remains.
- The token renews on each editor request but the server still stops after 10 minutes, when the
  card is closed (Back), or when the app leaves the screen.
- The page shows camera names and NVR names only; never NVR credentials or session cookies.

## IP cameras (Phase 6)

- **Logins**: the shared IP-camera password and per-camera passwords are stored only on the TV,
  encrypted with the Keystore key (`SecretStore`), never in `ipcams.json`, layouts, logs or pages.
  Password fields on the dashboard are never filled back ("Saved. Leave empty to keep it.").
  `CameraForm.toString()` omits the password. Credentials typed inside an RTSP URL are moved into
  the camera's own login; stored and sent URLs never contain them.
- **Wire**: RTSP and ONVIF answer Digest challenges; credentials are only sent after a 401. Basic
  (password readable on the LAN) is used only when the camera itself required it when it was added
  (`basicLogin`), and the dashboard warns about it. ONVIF HTTP never uses Basic. ONVIF requests
  always go to the host the user typed, even if the camera reports another address.
- **Saved passwords never leave as Basic**: Basic is allowed at save time only for a password
  typed into that same form; a kept password is reused only if the camera's address is unchanged
  (else "type the password again"); snapshot URLs with a login inside are rejected. Accepted
  residual risk: a *paired* user (who could read the TV's code) can add a camera at an address they
  control with the shared login and receive a Digest/WS-Security response (a hash, not the
  password); pairing already grants control over the TV's cameras.
- **Scan**: only the TV's own private (RFC 1918) /24, TCP connects to 554/8554 plus one ONVIF
  WS-Discovery multicast probe. Nothing leaves the local network.
- **Dashboard** (`/cameras*`): same pairing code + token + Host/private-IP guard as the layout
  editor, only while the TV's card is open; every field validated (`CameraRoutes`: host pattern,
  `rtsp://` / `http(s)://` only, lengths, no control characters, known brand ids and camera ids);
  every value HTML-escaped; no JavaScript (the own-login fields hide with CSS `:has()`).
- **Camera replies** are untrusted: SDP/ONVIF/WS-Discovery parsed with bounded regexes (no XML
  parser, so no XXE), RTSP header and body sizes capped, snapshot images capped at 4 MB.

## Out of scope / never
CAPTCHA bypass, rate-limit evasion, accessing accounts or devices the user doesn't own.
