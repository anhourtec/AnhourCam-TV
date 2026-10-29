# Contributing to CamGrid TV

Thanks for helping put BitVision cameras on the big screen.

## Before you start
- Read [docs/SPEC.md](docs/SPEC.md) (what we build) and [docs/roadmap.md](docs/roadmap.md) (what's next).
- Open an issue before large changes so we can agree on the approach.

## Ground rules
- **Never commit secrets or personal data**: no credentials, tokens, emails, device serials or IDs,
  IP addresses, camera images or unredacted network captures. Run `scripts/check-redaction`
  (install it as a pre-commit hook: see [docs/security.md](docs/security.md)).
- **No BitVision code or assets.** Don't copy their JavaScript, WASM, images or logos. See [NOTICE.md](NOTICE.md).
- Only test with **your own** account and devices. No CAPTCHA bypass, rate-limit evasion or
  probing of BitVision's servers.
- Mark guessed protocol behaviour with `// INFERRED:` and add it to [docs/open-questions.md](docs/open-questions.md).

## Build and test

Requires JDK 17+. The Gradle wrapper downloads Gradle itself (checksum-pinned).

```bash
./gradlew test                    # unit tests, including a fake-gateway live-view test
./gradlew ktlintCheck detekt      # lint (must be zero errors); ./gradlew ktlintFormat fixes formatting
./gradlew :bitvision-client-cli:installDist
bitvision-client-cli/build/install/bitvision-client-cli/bin/bitvision-client-cli login   # your own account
```

## Code
- Kotlin, native Android; no Google Play Services (Fire TV has none).
- Zero lint/type errors: `./gradlew ktlintCheck detekt lint`; for `pairing-web/`:
  `npx eslint . --max-warnings 0` and `npx tsc --noEmit`; `shellcheck scripts/*`.
- Small, focused commits with conventional subjects (`feat(app): …`, `fix(client): …`, `docs: …`).
- Performance matters: no per-frame allocations in the video path; justify every new dependency.

## Pull requests
Describe what and why, how you tested it (device model and OS), and update docs touched by the change.
