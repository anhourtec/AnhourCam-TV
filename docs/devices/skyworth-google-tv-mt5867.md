# Skyworth Google TV (MediaTek MT5867 / T56), Android 14

Also the reference "Google TV" in the docs (address `.50` on the maintainer's network).

The project's first real test device (model string `SWTV_24AE_FHD`). A typical low-end Google TV:
if AnhourCam TV runs well here, it runs well on most TVs.

| | |
|---|---|
| OS | Google TV, Android 14 (API 34) |
| SoC | MediaTek MT5867 ("T56"), 4 cores, **32-bit only** (`armeabi-v7a`) |
| RAM | ~930 MB total (often < 100 MB free) |
| Panel | 1920×1080; UI rendered at 1280×720 (density 213) |
| Leanback | yes (`leanback_only`), Google Play Services present |

## Video decoders (from `/vendor/etc/media_codecs*.xml`)

| Decoder | Type | Max size | Concurrent instances |
|---|---|---|---|
| `c2.mtk.hevc.decoder` | **hardware** H.265 | 1920×1088 (1080p60) | **2** |
| `c2.mtk.avc.decoder` | hardware H.264 | 1920×1088 | 2 |
| `c2.android.hevc.decoder` | software H.265 | 4096×4096 | CPU-bound |

## Measured with AnhourCam TV (Phase 2 PoC, 2×2 grid of sub-streams)

| Tile | Stream | Decoder | Result |
|---|---|---|---|
| CH1 | 720×480 | hardware | 26 fps steady |
| CH2 | 720×480 | hardware | 26 fps steady |
| CH3 | 1280×720 | software | 18–26 fps with stalls (drops → waits for next key frame) |
| CH4 | — | — | no stream (see docs/open-questions.md #9) |

App footprint during the 3-tile grid: ~83 MB RSS / 43 MB PSS, ~37 % of one core.

**Takeaway:** 2 smooth live tiles in hardware. More tiles on this TV need snapshots, or software
decoding only for small streams. The decoder budget must count hardware instances first.

## Phase 4 grids on this TV
Live tiles at once: **4** (2 hardware + 2 software decoders). In a 3×3 or larger grid the other
tiles show "Waiting for a free video decoder · This TV plays 4 cameras at once" and start as soon
as one frees. A stronger TV/streamer (more hardware decoders) plays more tiles live.

Still images (3×3 test): tiles beyond the 4 live ones show still pictures refreshed round-robin;
software live tiles stall more while stills decode (open question #19).

## IP cameras (Phase 6, 2026-10-29)
An RTSP camera added from the phone dashboard plays fullscreen at 1920×1080 H.265 on the hardware
decoder, 25 fps, 0 dropped, first frame 0.5–1.1 s (BitVision tiles: ~12 s on this TV).
