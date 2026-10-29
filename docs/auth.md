# Authentication

> Status: login request shape captured from the portal JS (no credentials recorded), 2026-10-29.

## Web portal login: **image CAPTCHA on every login** ⚠️

`POST https://bitvision.app/home/account/ajax-login`, form-encoded:

| Field | Value |
|---|---|
| `username` | account email/username |
| `password` | password as typed (sent over TLS; no client-side hashing observed in `login.js`) |
| `yzm` | 4-character code from the image at `GET /home/account/yzm?t=<ms>` (session-bound) |

`login.js` refuses to submit with an empty `yzm`, so **every web login needs a human-solved image
CAPTCHA.** This is a SPEC §3.5 stop condition. We will **not** bypass, solve automatically or OCR
it. **Decision (2026-10-29):** the human solves it. The TV login screen and the phone pairing page fetch
`/home/account/yzm` with the pending session cookie, show the image, and the user types the 4
characters. The app then keeps that session alive as long as possible so sign-in is rare.

Response: `code` = success or `LV_LOGIN_FAILED_CODE`; `data.ali_code`, `data.ali_country` stored
in `sessionStorage` *(meaning unknown)*.

## Session
- Session is a **cookie** on `bitvision.app` (no bearer token observed). Name/lifetime TBD.
- Logout: `GET /home/account/exits`.
- Pages are server-rendered with session data embedded (`tipsDeviceInfo`).

## Device (NVR) credentials
The live-view handshake (cmd 8208) sends the NVR's **local** username/password
(`local_user` / `local_pwd`), which the portal embeds in the device page
(`tipsDeviceInfo.curDeviceInfo`). The TV app must treat these as secrets: in memory only, never
logged, encrypted if cached.

## Gateway authentication
Gateway registration (cmd 4101) carries only the account username; how the gateway ties the
socket to the logged-in session is not yet understood *(open question #7)*. Not to be tested
beyond observing our own session.

## Request signing
None seen on portal XHRs so far (`$.post` with plain form fields).
