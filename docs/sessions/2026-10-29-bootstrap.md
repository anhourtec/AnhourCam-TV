# 2026-10-29 — Bootstrap

- Read README end to end; confirmed Phase 0 is first.
- Created repo scaffolding, docs set, redaction checker + pre-commit hook.
- Added `NOTICE.md` at maintainer's request: BitVision owns its service, API, software and marks;
  project is unofficial, interoperability-only, copies no BitVision code/assets, takedown contact.
- Chose Apache-2.0 (from README's MIT/Apache shortlist): explicit patent grant + NOTICE convention.
- Connected remote github.com/anhourtec/CamGrid-TV (was empty); initial commit on `main`.
- Opened Playwright on bitvision.app; waiting for maintainer login to start captures.
- Added ROADMAP.md (Now / Next / Later, risks); it and all Markdown docs are updated every session.
- Ignored `.playwright-mcp/` output folder.
- Pre-commit security review of the docs/config-only diff: no findings.

## Later the same day: first capture
- Maintainer logged in. Captured device list, channel list and one live view (sub-stream).
- Live video: JSON signalling + binary media WebSockets; private 44-byte header + H.265 Annex-B,
  not encrypted. Hardware MediaCodec path chosen.
- Web login requires an image CAPTCHA every time: SPEC §3.5 stop condition, reported, not bypassed.
- Repo restructured for users: public README (SEO, official BitVision app links, anhourtec credit),
  build spec moved to `docs/SPEC.md`, roadmap and architecture moved into `docs/`, community files
  and issue templates added.
- Performance decisions: Kotlin native, no Hilt / Media3 / GMS, minSdk 25 for Fire TV.
