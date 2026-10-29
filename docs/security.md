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

## Out of scope / never
CAPTCHA bypass, rate-limit evasion, accessing accounts or devices the user doesn't own.
