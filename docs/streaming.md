# Video streaming

> Status: **not yet captured** (Phase 0). This is the most important Phase 0 doc.

## Stream negotiation
TBD — HTTP call returning URL? WS handshake? signalling?

## Transport
TBD — WS binary / WebRTC / HLS / HTTP-FLV / other

## Container & framing
TBD — FLV / fMP4 / Annex-B / private framing (byte offsets + hex dumps in `samples/`)

## Codec
| Stream | Codec | Resolution | FPS | Evidence |
|---|---|---|---|---|
| sub | TBD | | | |
| main | TBD | | | |

## Encryption / obfuscation / WASM
TBD

## Keepalive & teardown
TBD

## Multi-view (4 channels)
TBD — one connection per channel or multiplexed?

## Implementation path (README §6)
TBD once transport is known.
