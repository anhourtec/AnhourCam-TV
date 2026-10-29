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
it. **Decision (2026-10-29):** the human solves it. The TV login screen and the phone pairing page fetch
`/home/account/yzm` with the pending session cookie, show the image, and the user types the 4
characters. The app then keeps that session alive as long as possible so sign-in is rare.

Response JSON `{code, message, data}`. **`code` 0 (`CODE_SUCCESS`) or 2056 (`LV_LOGIN_FAILED_CODE`)
both mean signed in**; anything else is an error whose text is in `message` (wrong password or
CAPTCHA). `data` also carries `ali_code`, `ali_country`, `ali_area_id`, `app_key`, `app_secret`
(a third-party cloud integration, *inferred*). The client ignores them and must never log them.

## Session
- Session is the **`PHPSESSID`** cookie on `bitvision.app`: HttpOnly, SameSite=Lax, **browser-session
  cookie (no expiry)**. There is no "remember me" option. How long it survives server-side when
  idle is still being measured (open question #3). The app keeps it alive with periodic light
  requests while running.
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
