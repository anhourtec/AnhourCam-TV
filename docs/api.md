# BitVision cloud API

> Status: **Phase 0 complete** (2026-10-29). Private, undocumented API observed from `https://bitvision.app/`.
> Fields marked *(inferred)* are guesses. All examples are redacted (see [security.md](security.md)).

## Conventions
- Web origin: `https://bitvision.app`. Most data is **server-rendered HTML** with JSON embedded in
  JS globals, not a JSON REST API.
- Auth: session cookie (see [auth.md](auth.md)).
- XHRs use jQuery `$.post`, form-encoded unless noted; responses `{code, msg, data}`.

## Pages (HTML with embedded data)

| Page | Contains |
|---|---|
| `GET /home/space` | Account name (`.J-username`) and device tree: `<li id="camera-<ID>" data-status="1|0">` with `<a href="/home/video/show?id=<ID>" title="<name>">` and `<p>NVR <img…></p>`, followed by `<ol class="… channel-list" data-name="<name>">` of `<li data-channel="<0-based>" channel="<1-based>">…<span>CH1</span>` |
| `GET /home/video/show?id=<DEVICE_ID>` | Device data + gateway URLs embedded in an inline script (below); channel list CH1…CHn |
| `GET /home/video/show-playback?id=<DEVICE_ID>` | Playback page |
| `GET /home/account/exits` | Logout |

### Device page embedded data

The device page contains, in an inline `<script>`:

```js
var tipsDeviceInfo = {
    getUserServiceAddr: '<JSON string>',
    subMenuActive: '<string>',
    curDeviceInfo: '<JSON string>'
};
var channelNum = "<n>";
```

Each value is a **single-quoted JSON string**: match `curDeviceInfo:\s*'([^']*)'` and `JSON.parse` it.
A plain authenticated `GET` returns it; no JavaScript execution is needed. `/home/space` embeds the
same object for the selected device.

`curDeviceInfo` (all values strings unless noted):

| Field | Example / meaning |
|---|---|
| `id` | internal row ID (≠ `device_id`) |
| `user_id` | account ID |
| `device_id` | device serial used everywhere else (URL `id=`, `devSn`, `client_name`) |
| `device_name` | user-given name |
| `device_model` | `NVR_16`, `NVR_9` |
| `device_type_name` | `NVR` |
| `channel` | channel count, e.g. `16` (same as `channelNum`) |
| `device_status` | `1` = online *(inferred: 0 = offline)* |
| `local_user`, `local_pwd` | **NVR's own credentials (secret)**, sent in cmd 8208 |
| `server_ip`, `server_port` | device gateway address *(inferred)* |
| `cate_id`, `area_id`, `created_time` | metadata |
| `device_source` (number) | `1` *(meaning unknown)* |

`getUserServiceAddr`: `user_gate_url` (signalling WS URL), `ws_stream_url` (media WS URL),
`user_gate_way_ip`, `stream_relay_ip`, `ws_stream_port` (number). Observed host `ws-jz.bitdog.com`
for both test NVRs; always read it from here.

## XHR endpoints

| Method + path | Body | Purpose | Seen |
|---|---|---|---|
| `POST /home/account/ajax-login` | `userName`, `password`, `yzm` | Login (CAPTCHA); code 0 or 2056 = success | JS + network |
| `GET /home/account/yzm?t=<ms>` | — | CAPTCHA image | HTML |
| `POST /home/account/update-unit-type` | `unitType` (1 = local plugin, 2 = no plugin) | Sent before login; client sends 2 | JS + network |
| `POST /home/device/refresh-device-status` | `deviceId` | Refresh online state | JS |
| `POST https://www.bitdog.com/home/account/get-device-status` | `device_id` | Online state → `data.device_status` | JS |
| `POST /origin/device/get-lv-device-info` | JSON list | Device info for the local "middleware" path | JS |
| `POST /origin/device/ptz-action-control` | TBD | PTZ | JS |
| `POST /home/device-info/get-device-replay-video-type` | TBD | Playback stream types | JS |
| `POST /home/device-info/get-device-replay-rate-data` | TBD | Playback rates | JS |

Live video is not REST: see [streaming.md](streaming.md).
