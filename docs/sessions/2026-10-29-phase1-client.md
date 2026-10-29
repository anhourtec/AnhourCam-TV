# 2026-10-29: Phase 1 client library

- Gradle 9.8.2 downloaded into a scratch folder (checksum verified) only to generate the wrapper;
  wrapper pinned with SHA-256. Versions looked up live: Kotlin 2.4.21, OkHttp 5.5.1,
  kotlinx-serialization 1.12.0, coroutines 1.11.0, JUnit 6.1.3, ktlint 1.8 / plugin 14.2,
  detekt 2.0.0-alpha.6 (only line supporting Kotlin 2.4).
- Corrections found while implementing: login posts `userName` (camelCase); code 2056 is also a
  successful login; `update-unit-type` (unitType=2) precedes login; the relay ack carries
  `nRelaySessionID` / `nRelaySockFd` and the media socket opens before 8224; `devUUID` is a
  constant; frame type 5 is probably audio.
- `:bitvision-client`: portal, page, protocol, gateway, media packages. `:bitvision-client-cli`.
- 20 tests (parsers, messages, zero-copy frame parsing, fake gateway end to end). ktlint + detekt clean.
- Live run not done: copying the browser's session cookie for the CLI was blocked by the permission
  system (credential materialization); the maintainer will log in through the CLI instead.
- Pre-commit security review: 1 Low (session file TOCTOU) fixed; wss:// enforced; cookie jar host-only; 23 tests.
