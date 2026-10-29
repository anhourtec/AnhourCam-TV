# BitVision cloud API

> Status: **partial** (2026-10-29). Private, undocumented API observed from `https://bitvision.app/`.
> Fields marked *(inferred)* are guesses. All examples are redacted (see [security.md](security.md)).

## Conventions
- Web origin: `https://bitvision.app`. Most data is **server-rendered HTML** with JSON embedded in
  JS globals, not a JSON REST API.
- Auth: session cookie (see [auth.md](auth.md)).
- XHRs use jQuery `$.post`, form-encoded unless noted; responses `{code, msg, data}`.

## Pages (HTML with embedded data)

| Page | Contains |
|---|---|
| `GET /home/space` | Device list: links `/home/video/show?id=<DEVICE_ID>`, device name + type (NVR) |
| `GET /home/video/show?id=<DEVICE_ID>` | `tipsDeviceInfo.curDeviceInfo` JSON (`device_id`, `device_status`, `local_user`, `local_pwd`, `cate_id`, `device_type_name`, …) and `tipsDeviceInfo.getUserServiceAddr` JSON (gateway/stream URLs); channel list CH1…CHn |
| `GET /home/video/show-playback?id=<DEVICE_ID>` | Playback page |
| `GET /home/account/exits` | Logout |

## XHR endpoints

| Method + path | Body | Purpose | Seen |
|---|---|---|---|
| `POST /home/account/ajax-login` | `username`, `password`, `yzm` | Login (CAPTCHA) | JS + network |
| `GET /home/account/yzm?t=<ms>` | — | CAPTCHA image | HTML |
| `POST /home/account/update-unit-type` | TBD | Unit/region preference *(inferred)* | network |
| `POST /home/device/refresh-device-status` | `deviceId` | Refresh online state | JS |
| `POST https://www.bitdog.com/home/account/get-device-status` | `device_id` | Online state → `data.device_status` | JS |
| `POST /origin/device/get-lv-device-info` | JSON list | Device info for the local "middleware" path | JS |
| `POST /origin/device/ptz-action-control` | TBD | PTZ | JS |
| `POST /home/device-info/get-device-replay-video-type` | TBD | Playback stream types | JS |
| `POST /home/device-info/get-device-replay-rate-data` | TBD | Playback rates | JS |

Live video is not REST: see [streaming.md](streaming.md).
