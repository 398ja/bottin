# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.6.0] - 2026-08-01

### Added
- Admin-maintained deployment settings at `/admin/settings`: the media server (Blossom) URL,
  the system relays, the profile discovery relays, and the API rate limit are now stored in a
  singleton `settings` row (Flyway `V4`) and edited in the admin UI, with validation on both the
  form and the service so no path can store an unusable value.
- `GET /api/v1/settings` (API role) serving the media server and relay topology to the client
  server. The rate limit is deliberately excluded: the API is its only consumer.
- `GET /api/v1/relays/system` on the client server, replacing `GET /api/v1/relays/defaults`.

### Changed
- **The API rate limit applies without a restart.** `RateLimitService` reads its allowance from
  the settings row rather than a startup property.
- **System relays now reach every user, including those who signed up earlier.** They are applied
  as a union at publish and read time instead of being copied into each browser's storage on first
  use, which froze the relay set per browser and put relays the user never chose into the list the
  settings page offers them to remove. The relay settings page now shows only relays the user added;
  the published kind-10002 and the NIP-05 registration still include the system relays, since events
  land there.
- An unconfigured media server disables the image pickers with "Media server not configured" instead
  of posting to an empty URL and surfacing an opaque network error. Onboarding proceeds regardless.
- Profile discovery at sign-in uses the administrator's relays plus the system relays instead of a
  list compiled into the client image.

### Removed
- **BREAKING (deployment):** `BOTTIN_BLOSSOM_URL` and `BOTTIN_DEFAULT_RELAYS` no longer have any
  effect and are removed from `docker-compose.yml`, `.env`, and `ClientProperties`. So is
  `bottin.ratelimit.requests-per-minute`. There is no environment fallback: a deployment comes up
  unconfigured by design, and the values must be set once at `/admin/settings` after deploying. See
  `docs/how-to/configure-deployment-settings.md`.
- The four public relays previously hardcoded in the onboarding import step.

### Fixed
- **The `bottin-it` integration tests run again.** Every test in that module had failed to load its
  Spring context since before this release; all 38 now pass. `BottinAutoConfiguration` was
  component-scanning `xyz.tcheeric.bottin.api` from an `@AutoConfiguration` class, which registered
  every repository twice and introduced a security filter chain too late for
  `@ConditionalOnDefaultWebSecurity` to stand down. The module also ran `ddl-auto: create-drop`
  against PostgreSQL, where Hibernate's generated `enum` DDL is invalid; it now runs the Flyway
  migrations instead, so the tests exercise the schema production uses.

### Changed (starter)
- `bottin-spring-boot-starter` no longer component-scans `xyz.tcheeric.bottin.api`. Adding the
  starter previously grafted bottin's REST controllers and an "any request" security filter chain
  into the consuming application, which broke context startup. An application that wants the REST
  layer now scans that package explicitly, as `BottinApiApplication` does. No module in this
  repository relied on the old behaviour.

## [0.3.0] - 2026-06-25

### Removed
- Removed the `nsecbunker-java` dependency (`nsecbunker-account` / `nsecbunker-core`) and its integration entirely:
  the `PersistentNip05Manager`, `PersistentAccountManager`, and SPI provider classes in `bottin-spring-boot-starter`,
  the `NsecbunkerIntegrationE2ETest` and `nsecbunkerd` Testcontainer in `bottin-tests/bottin-e2e`, and the
  associated dependency declarations. This also drops the deprecated `nostr-java` 1.x that was pulled in transitively.

### Changed
- **BREAKING (starter)**: `bottin-spring-boot-starter` no longer exposes the nsecbunker-java SPI beans
  (`Nip05Manager` / `AccountManager` and their providers). Applications that embedded the starter solely to provide
  bottin's persistence to nsecbunker-java are affected. The starter continues to auto-configure all bottin services
  and endpoints. `BottinAutoConfiguration` now activates based on a bottin persistence type rather than an
  nsecbunker class.

## [0.2.1] - 2026-02-25

### Removed
- Removed nostr-java dependency; replaced `Identity.generateRandomIdentity()` with `SecureRandom` + `HexFormat`

## [0.2.0] - 2026-01-30

### Added
- Docker image build bound to Maven deploy phase for streamlined CI/CD
- E2E testing documentation and architecture guide
- CI configuration with build and test jobs

### Changed
- Domain verification now uses `_nostr-verification` naming for better protocol alignment
- Admin UI shows both DNS and Well-Known verification options without method selection
- Verification attempts both DNS and Well-Known methods automatically
- Auto-generates verification token when viewing unverified domains
- Updated nostr-java dependency to 1.3.0
- Removed nsecbunker-account dependency from bottin-core

### Fixed
- SecurityFilterChain conflict in E2E tests
- Qodana security findings addressed
- CI workflow fixes for google-java-format and Maven wrapper
- Admin credentials now properly use environment variables
- Fixed 500 error when viewing domains list

### Security
- Removed credentials from dependabot registry configuration

## [0.1.0] - 2025-01-15

### Added
- Initial release of Bottin NIP-05 Registry Service
- REST API for NIP-05 record management
- Admin dashboard with domain and record management
- Domain verification via DNS TXT and Well-Known file methods
- PostgreSQL and H2 database support
- Spring Boot starter for easy integration
- Docker support with Jib

[Unreleased]: https://github.com/tcheeric/bottin/compare/v0.2.1...HEAD
[0.2.1]: https://github.com/tcheeric/bottin/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/tcheeric/bottin/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/tcheeric/bottin/releases/tag/v0.1.0
