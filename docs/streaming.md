# Video streaming

> Status: **live view captured for 1 channel, sub-stream** (2026-10-29). Main stream, multi-view,
> audio, keepalive and teardown are still to capture. Evidence: [`samples/live-view-signalling.json`](samples/live-view-signalling.json),
> [`samples/stream-frame-headers.txt`](samples/stream-frame-headers.txt). Field names come from
> the wire; meanings marked *(inferred)* are guesses.

## Summary

| Question | Answer | Evidence |
|---|---|---|
| Transport | **Two WebSockets**: JSON signalling on `wss://<gateway>/userGateWay/1`, media on `wss://<gateway>/stream/1` | captured frames |
| Container | **Private 44-byte header + raw Annex-B** elementary stream | frame hex |
| Codec | **H.265 / HEVC** on the observed NVR (VPS NAL `40 01` after start code); portal also ships an H.264 path (`video264`, `wfs.js`) | frame hex, portal JS |
| Encryption | **None observed**: Annex-B start codes and NAL headers in clear | frame hex |
| Browser decode | H.265 via **libde265 compiled to WASM** + WebGL render; H.264 via MSE (`wfs.js`). Both open-source decoders, no private codec | portal script list |
| Native helper | Portal probes `https://127.0.0.1:53888-53897/res.bmp` for an optional local "middleware"; **live view works without it** | console + network log |
| Stop conditions (SPEC §3.5) | Video: **not hit** | — |

**Implementation path (SPEC §6):** "WS + raw H.264/H.265 (custom framing)": an own depacketizer
→ `MediaCodec` (hardware HEVC) → `SurfaceView`. No Media3 extractor needed.

## Endpoints

The gateway host and ports come from the page itself: `tipsDeviceInfo.getUserServiceAddr` on
`/home/video/show?id=<DEVICE_ID>` (keys `user_gate_url`, `ws_stream_url`, `stream_relay_ip`,
`ws_stream_port`, `user_gate_way_ip`). Observed host: `ws-jz.bitdog.com` (region-specific, *inferred*).

All JSON messages share an envelope:

```json
// client → gateway
{"functionName":"SendToRouter","requestNo":<ms-epoch>,"liveTime":30,"param":{"cmdType":<n>,"cmdID":<n>,"cmdData":{…}}}
// gateway → client
{"functionName":"ProcGateResponse"|"TransMessage","code":0,"msg":"succss","data":{"cmdType":<n>,"cmdID":<n>,"cmdData":{…}}}
```

`cmdType` 257/258 = gateway request/response; 513/514 = message routed to/from the device *(inferred)*.

## Live-view sequence (one channel)

| # | Socket | Dir | cmdID | Purpose | Key fields |
|---|---|---|---|---|---|
| 1 | userGateWay | → | 4101 | Register client with gateway | `client_name` = account username, `client_role` 6 |
| 2 | userGateWay | ← | 4102 | Registered | `client_id`, `online_status`, `trans_port` |
| 3 | userGateWay | → | 4103 | Look up device | `client_name` = device ID, `gateway_type` 1 |
| 4 | userGateWay | ← | 4104 | Device location | `client_id` (device), `client_loginip`, `client_loginport`, `client_sdk_ver` |
| 5 | userGateWay | → | 8208 | **Log in to the device** | `devSn`, `devUserName`, `devPasswd` (NVR's local credentials), `userLoginId`, gateway IP/port |
| 6 | userGateWay | ← | 8447 | Routed ack | `toClientID` |
| 7 | userGateWay | ← | 8209 | Device login result | `resultCode` 0, `devGatewayIP/Port` |
| 8 | userGateWay | ← | 8298 | P2P/direct readiness *(inferred)* | `bIsPrepared` 0 → use relay |
| 9 | stream | → | `RelayAskPro` | Open relay session | `deviceId`, `devUUID` |
| 10 | stream | ← | (binary, 20 B) | Relay ack, magic `0x12345678` LE | see samples |
| 11 | userGateWay | → | 8224 | Request channel streams via relay | `channelNo`, `streamId` -1, `connType` 6, `relayAddr/Port`, relay session + fd |
| 12 | userGateWay | ← | 8225 | Channel info | `streamInfoBitMask` 3 (2 streams *(inferred)*), `encodeType`, `frameRate` |
| 13 | userGateWay | ← | 8218 | Relay bound | `relayAddr` (IPv4 as int), `relayPort`, `relaySessionId`, `socketFd` |
| 14 | stream | → | 8216 | **Start stream** | `channelNo` 0-based, `streamId` 1 (sub, *inferred*), `sessionType` 2 (live, *inferred*) |
| 15 | stream | ← | binary | Media frames (below) | ~25 fps |

`streamList` in the portal: `0` HD, `1` standard/BD, `2` fluent. Grid tiles should request `1`
or `2`; fullscreen `0` *(to verify in capture of flow 6)*.

## Media frame format (`/stream/1`, binary)

Little-endian. One WebSocket message = one frame (access unit).

| Offset | Size | Observed | Meaning |
|---|---|---|---|
| 0 | u32 | `0x24` | constant / version *(inferred)* |
| 4 | u32 | 1 or 2 | frame type: 1 = key frame (starts with VPS/SPS/PPS), 2 = P frame |
| 8 | u32 | 0,1,2… | sequence number |
| 12 | u32 | 480 | dimension A *(inferred: height)* |
| 16 | u32 | 720 | dimension B *(inferred: width)* |
| 20 | u64 | ms epoch | capture timestamp |
| 28 | u32 | 25 | frame rate |
| 32 | u32 | 0 | reserved *(inferred)* |
| 36 | u32 | 8 | unknown (codec or stream class) *(inferred)* |
| 40 | u32 | n | payload length (= message length − 44) |
| 44 | n | `00 00 00 01 40 01 …` | Annex-B H.265 (key) / `00 00 00 01 02 01 …` (TRAIL_R) |

Observed sub-stream: 720×480 *(inferred from header; confirm via SPS)*, 25 fps, key frame ≈ 14 KB,
P frames 100–230 bytes.

## Still to capture
Main stream switch · 4-channel multi-view (one `/stream/1` per channel or multiplexed?) · stop
command · audio frames (portal ships AAC + G.711 decoders) · keepalive over 5+ min · snapshot.
