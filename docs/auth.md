# Authentication

> Status: **Phase 0 complete** (2026-10-29). Login request shape from the portal JS; no credentials recorded.

## Web portal login: **image CAPTCHA on every login** ⚠️

First `POST /home/account/update-unit-type` with `unitType=2` ("no plugin" mode; the portal sends
it before every login), then `POST https://bitvision.app/home/account/ajax-login`, form-encoded:

| Field | Value |
|---|---|
| `userName` | account email/username (camelCase: the form input is `username`, but `login.js` posts `userName`) |
| `password` | password as typed (sent over TLS; no client-side hashing observed in `login.js`) |
| `yzm` | 4-character code from the image at `GET /home/account/yzm?t=<ms>` (session-bound) |

`login.js` refuses to submit with an empty `yzm`, so **every web login needs a human-solved image
CAPTCHA.** This is a SPEC §3.5 stop condition. We will **not** bypass, solve automatically or OCR
it. **Decision (2026-10-29):** the human solves it, inside our app. No browser or other tool is
needed: the client fetches `/home/account/yzm` with its session cookie and shows the image (the
CLI saves and opens it; the TV shows it on the login screen; the pairing page gets it through the
TV). Verified end to end with the CLI on 2026-10-29. Observed code: 4 digits (*one sample*).

Response JSON `{code, message, data}`. **`code` 0 (`CODE_SUCCESS`) or 2056 (`LV_LOGIN_FAILED_CODE`)
both mean signed in**; anything else is an error whose text is in `message` (wrong password or
CAPTCHA). `data` also carries `ali_code`, `ali_country`, `ali_area_id`, `app_key`, `app_secret`
(a third-party cloud integration, *inferred*). The client ignores them and must never log them.

## Session
- Session is the **`PHPSESSID`** cookie on `bitvision.app`: HttpOnly, SameSite=Lax, **browser-session
  cookie (no expiry)**. There is no "remember me" option. Server-side idle timeout: still alive after
  36 min (browser session, possibly kept alive by page activity); a clean idle test with the CLI
  session started 13:24 UTC 2026-10-29 (open question #3).
- **Live view does not need it**: the gateway sockets never receive the cookie. The app keeps it
  alive with `SessionKeeper` (every 10 min) only so the device list can refresh. See SPEC §5.3.
- Restoring a saved `PHPSESSID` in a new process works (verified with the CLI).
- Other cookie: `language` (1 year, not auth).
- Logout: `GET /home/account/exits`.
- Pages are server-rendered with session data embedded (`tipsDeviceInfo`).

## Device (NVR) credentials
The live-view handshake (cmd 8208) sends the NVR's **local** username/password
(`local_user` / `local_pwd`), which the portal embeds in the device page
(`tipsDeviceInfo.curDeviceInfo`). The TV app must treat these as secrets: in memory only, never
logged, encrypted if cached.

## Account name

The gateway registration (cmd 4101) uses the account name shown in the page header
(`<a class="J-username">…</a>` on `/home/space`), exactly as the portal does. The client reads it
from the page instead of storing it.

## Gateway authentication
The gateway WebSockets are on a different host (`ws-jz.bitdog.com`), so the `PHPSESSID` cookie is
not sent to them. Registration (cmd 4101) carries only the account username; the effective
credential is the NVR's own `local_user`/`local_pwd` in cmd 8208, which only a logged-in session
can read from the device page. We reproduce exactly what the portal does and never test the
gateway beyond that *(open question #7)*.

## Request signing
None seen on portal XHRs so far (`$.post` with plain form fields).
