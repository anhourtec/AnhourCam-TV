# Video streaming

> Status: **Phase 0 complete** (2026-10-29). Captured on two NVRs (16 and 9 channels): sub and
> main stream, channel switch, stop, audio on/off, snapshot, heartbeat. Evidence:
> [`samples/live-view-signalling.json`](samples/live-view-signalling.json),
> [`samples/stream-control-commands.json`](samples/stream-control-commands.json),
> [`samples/stream-frame-headers.txt`](samples/stream-frame-headers.txt). Command names come from
> the portal's command table; meanings marked *(inferred)* are guesses.

## Summary

| Question | Answer |
|---|---|
| Transport | **Two WebSockets**: JSON signalling on `user_gate_url` (`…/userGateWay/1`), binary media on `ws_stream_url` (`…/stream/1`) |
| Sessions | One signalling socket per device; **one new media socket per channel playback** |
| Container | **Private 44-byte header + raw Annex-B** elementary stream, one access unit per message |
| Codec | **H.265 / HEVC** on both NVRs; portal also has an H.264 path (`wfs.js` / MSE) |
| Streams | `streamId` **0 = HD (main), 1080p** · **1 = BD (sub), 720×480** · 2 = "fluent" (listed in portal, not offered by these NVRs) |
| Encryption | **None.** Start codes and NAL headers in clear |
| Browser decode | libde265 (open-source) compiled to WASM + WebGL, only because browsers lack HEVC |
| Snapshot | **Client-side**: portal grabs the decoded frame to JPEG; no network call |
| Keepalive | `HeartBeat` message on **both** sockets every **200 s** |
| Native helper | Optional local "middleware" probed on `127.0.0.1:53888-53897`; not needed |
| Stop conditions (SPEC §3.5) | Video: **none hit** |

**Implementation path (SPEC §6):** "WS + raw H.264/H.265 (custom framing)": own depacketizer →
`MediaCodec` (hardware HEVC) → `SurfaceView`. No Media3 extractor.

## Message envelope

```json
// client → gateway
{"functionName":"SendToRouter","requestNo":<ms-epoch>,"liveTime":30,"param":{"cmdType":<n>,"cmdID":<n>,"cmdData":{…}}}
// gateway → client
{"functionName":"ProcGateResponse"|"TransMessage","code":0,"msg":"succss","data":{"cmdType":<n>,"cmdID":<n>,"cmdData":{…}}}
// keepalive, both sockets, every 200 s
{"functionName":"HeartBeat","requestNo":<ms-epoch>,"liveTime":30}
```

`cmdType` **257/258** = gateway request/response. **513/514** = message routed to/from the device.
For 513 messages, `cmdData` repeats routing fields (`msg_id` incrementing per request, `cmd_id`,
`from_client_id`, `to_client_id`, `to_client_name`, `to_client_role`, `to_client_loginip`,
`to_client_loginport`, `to_gate_type`) plus a command-specific `msg_data`. `msg` really is spelled
`succss`.

## Command table

| ID | Name in portal | Dir | `msg_data` (observed) |
|---|---|---|---|
| 4101 / 4102 | login / loginRes | → / ← | gateway registration: `client_name` (account username), `client_role` 6 → `client_id`, `trans_port` |
| 4103 / 4104 | getClientInfo | → / ← | device lookup by device ID → device `client_id`, `client_loginip/port`, `client_sdk_ver` |
| 8208 / 8209 | createSession | → / ← | device login: `devSn`, `devUserName`, `devPasswd`, `userLoginId`, gateway ip/port/type, `userRole`, `cookie` → `resultCode`, `devGatewayIP/Port` |
| 8298 | — | ← | `bIsPrepared`, `nAddr`, `nPort`: direct-connection readiness *(inferred)*; 0 → relay |
| 8224 / 8225 | playVideo | → / ← | `channelNum`, `connType` 6, `relayAddr`, `relayPort`, `req_info[{channelNo, streamId, nRelaySessionID, nRelaySockFd, reserve}]` → `rsp_info[{channelInfo, channelNo, resultCode, streamInfoBitMask, encodeType, frameRate}]` |
| 8217 / 8218 | createRelaySession | → / ← | ← `resultCode`, `channelNo`, `relayAddr` (IPv4 as int), `relayPort`, `relaySessionId`, `socketFd` |
| 8216 | createSubSession | → (stream socket) | `userLoginId`, `cmdType` 1, `channelNo`, `streamId`, `sessionType` 2 (live), `recordType` null |
| 8232 / 8233 | stopVideo | → / ← | `channelNum`, `req_info[{channelNo, streamId, nRelaySessionID, nRelaySockFd, reserve}]` → `rsp_info[{channelInfo, resultCode}]` |
| 8228 / 8229 | playAudio | → / ← | `channelNo`, `connType` 6 → `sampleRate` 16, `encodeType` 2, `bitWidth` 8, `channelNum` 1 |
| 8236 | stopAudio | → | `channelNo` → ack via 8448 |
| 8448 | sdkRes | ← | generic ack: `responseType` (the request ID), `resultCode`, `channelNo` |
| 8447 | — | ← | routing ack: `toClientID` |
| 8272 / 8273 | getInfo | → / ← | not captured |
| 8268 / 8269 | setDeviceParam | → / ← | not captured |
| 8284 | setPtz | → | `channelNo`, `ptzCmd` (document only) |
| 8230 | playMixedPlay | → | not captured |
| 8248 / 8249 | searchPlayback | → / ← | playback, document only |
| 8252 / 8253, 8262, 8264 | playbackVideo, playbackVideoEnd, stopPlaybackVideo | | playback, document only |

The media socket opens with `{"functionName":"RelayAskPro","param":{"deviceId":…, "devUUID":"1481703573"}}`
(`devUUID` is a constant in the portal source, not per device) and gets a 20-byte binary **relay
ack** (little-endian):

| Offset | Size | Value |
|---|---|---|
| 0 | u32 | magic `0x12345678` |
| 4 | u32 | `0x111` (message type) |
| 8 | u32 | `8` (body length) |
| 12 | u32 | **`nRelaySessionID`** |
| 16 | u32 | **`nRelaySockFd`** |

The two values are what the client then sends in 8224 and 8232 (verified on two captured sessions).

## Sequences

**Open device** (once per NVR, signalling socket): 4101 → 4102 → 4103 → 4104 → 8208 → 8447 → 8209 (+ 8298).

**Play a channel** (`channelNo` is 0-based; CH1 = 0):
1. open a **new** media socket → `RelayAskPro` → 20-byte relay ack (`nRelaySessionID`, `nRelaySockFd`).
2. signalling: **8224** `{channelNo, streamId, nRelaySessionID, nRelaySockFd}` → 8447 → **8225**
   (channel info) → **8218** (relay session bound).
3. media socket: **8216** `{channelNo, streamId, sessionType: 2}` → binary frames follow within ~1 s.

`msg_id` is one counter per device link, shared by both sockets (8208 = 1, 8224 = 2, 8216 = 3 …).
Implemented in `bitvision-client` (`GatewaySession`, `MediaSession`).

**Always stop explicitly.** If a client disappears without stopVideo (8232), the NVR keeps
pushing that stream into a dead relay session for a long time, and NVRs have few stream slots.
Observed 2026-10-29: after a few app restarts without stops, other channels stopped starting.

**Stop / switch quality / switch channel:** 8232 (stopVideo, same `req_info`) + 8236 (stopAudio)
→ 8233 + 8448, and the client closes the media socket (code 1006 observed). Then "play" again
with the new `channelNo` / `streamId`. Quality switch = stop + play with the other `streamId`.

**Audio:** 8228 → 8229 describes the audio (`encodeType` 2 = G.711 *(inferred: the portal ships a
G.711 → PCM decoder)*). No audio frames arrived on the tested channel (likely no microphone), so
the audio frame type on the media socket is **not yet observed**. Off = 8236.

## Media frame format (media socket, binary)

Little-endian, one WebSocket message = one access unit.

| Offset | Size | Observed | Meaning |
|---|---|---|---|
| 0 | u32 | `0x24` | constant / version *(inferred)* |
| 4 | u32 | 1, 2, 7 | frame type: **1** key frame (VPS+SPS+PPS+IDR), **2** P frame, **7** large P frame every ~75 frames (≈3 s), decodes as a normal P frame (*inferred*: "virtual I-frame"); **5** = audio *(inferred: portal sets `streamIsAudio = 5`; not yet seen)* |
| 8 | u32 | 0,1,2… | sequence number per media session |
| 12 | u32 | 480 / 1080 | **height** |
| 16 | u32 | 720 / 1920 | **width** |
| 20 | u64 | ms epoch | capture timestamp |
| 28 | u32 | 25–26 | frame rate (varies slightly; measured) |
| 32 | u32 | 0 | reserved *(inferred)* |
| 36 | u32 | 8 | unknown, constant (codec or stream class) *(inferred)* |
| 40 | u32 | n | payload length (= message length − 44) |
| 44 | n | `00 00 00 01 40 01 …` (key) / `00 00 00 01 02 01 …` (P) | Annex-B H.265 |

Sizes: sub-stream key ≈ 13–14 KB, P 100–230 B; main stream key ≈ 60 KB, P ≈ 1.5 KB, type-7 ≈ 7.5 KB.

## Measured latency (CLI, 2026-10-29, India → `ws-jz` gateway)

| Step | Time | When it happens in the app |
|---|---|---|
| Portal: account + device page | 3.8 s + 1.9 s | Only when refreshing the cached device list |
| Gateway: connect + register | 1.9 s | Once per gateway, kept open |
| Gateway: open device (8208) | 1.2 s | Once per NVR, kept open |
| Media socket + relay ack | 1.5 s | Per tile, all tiles in parallel |
| playVideo → relay bound | 1.1 s | Per tile, in parallel |
| 8216 → first frame | ~1.5 s | Per tile (waits for the camera's key frame) |

Streams: sub 720×480 ~23 fps ~30–60 kbit/s; main 1920×1080 ~23 fps ~510 kbit/s.
A warm grid (gateway + NVR already open) needs ~4 s per tile, in parallel; show the last snapshot
meanwhile. The **gateway did not need the portal cookie** (none is sent on these sockets).

## Multi-view for the TV app

The portal shows one channel at a time. Each playback is its own media socket and relay session,
so a grid = N media sockets (one per tile) on one signalling socket per NVR. `8224` takes
`channelNum` + a `req_info` **array**, which suggests several channels per request *(inferred)*.
How many concurrent relay sessions the cloud allows per account is **unverified**; test it in
Phase 2 with our own account (open question #9).
