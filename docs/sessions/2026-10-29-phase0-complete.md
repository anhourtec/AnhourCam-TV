# 2026-10-29: Phase 0 complete

- Switched to HD: `streamId` 0 = main 1920×1080, 1 = sub 720×480. Header offset 12 = height,
  16 = width.
- Stop / quality switch / channel switch: stopVideo 8232 + stopAudio 8236, media socket closed,
  then play again. Each playback uses a new media socket.
- Heartbeat every 200 s on both sockets (`heartbeatTime = 2e5` in the portal).
- Audio: 8228/8229 (G.711 inferred); no audio frames on the test camera.
- Snapshot is client-side in the portal; no network call.
- New frame type 7 (large P frame every ~75 frames).
- Device page embeds `tipsDeviceInfo` as single-quoted JSON strings; both NVRs share one gateway.
- Session is a `PHPSESSID` browser-session cookie, no remember-me; idle timeout measurement started.
- Recorder note: the WebSocket init-script recorder caps plain arrays at 600 frames; swap in the
  sampling buffer (all text frames, key frames, first 20 binary frames after each command).
- Machine ran out of disk mid-session; resumed after the maintainer freed space.
- Phase 0 gate met. Next: Phase 1 client library.
