# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.9.0] - 2026-08-05

### Added

- Profile search returns results. `/api/v1/search` was a stub answering an empty list
  for every query, so the page always read "No profiles found". It now searches the
  directory's own NIP-05 records — the same rows `/.well-known/nostr.json` serves, so
  for this deployment's domain reading them *is* the verification a relay-sourced
  `nip05` claim would have to be checked against. Results link to the profile and show
  the full identifier, domain included, because the domain is what distinguishes
  `alice@example.com` from a lookalike.
- Follow and unfollow, from search results and from another key's profile, published
  as the user's own NIP-02 contact list. The follow travels with their key to any
  other Nostr client rather than living here.
- Block and unblock, published as a NIP-51 mute list whose entries are NIP-44 sealed
  to the user's own key. Blocked keys are dropped from search results before they are
  rendered. Nothing about who is blocked is published in the clear: a public blocklist
  is a durable, unretractable statement about another person, and this directory ties
  keys to real handles.
- `/settings/follows` and `/settings/blocks` list what is actually published, read
  from the relays with names resolved in one batched query. The blocks page is what
  keeps an encrypted list correctable rather than write-only.

### Changed

- An unreachable directory now answers `502 DIRECTORY_UNAVAILABLE` on search rather
  than an empty `200`. "Nobody matches" is a claim about the directory's contents that
  only an answer supports; an empty result would have told a searcher the person is
  not registered.
- A list replacement now carries a timestamp strictly later than the event it
  replaces. `created_at` is in whole seconds, and NIP-01 breaks a tie on replaceable
  events by keeping the lowest event id — so a follow immediately undone could leave
  the follow standing. Found against a running relay, not by the suite.

### Removed

- **Breaking.** `POST /api/v1/follow`, `/unfollow`, `/block`, `/unblock` and
  `GET /api/v1/follows`, `/blocks`. These validated a pubkey, answered
  `{"status":"followed"}` and stored nothing, and could never do otherwise: the
  private key never leaves the browser, so the server had nothing to sign with. No
  caller existed. Not a protocol break — no public NIP shape changes.

### Security

- A follow or block is refused rather than published when the current list could not
  be read. Both event kinds are replaceable, so publishing over an unread list
  destroys it; the read distinguishes "the relays hold nothing" from "no relay
  answered" and acts only on the first. An encrypted mute list whose content will not
  decrypt is unreadable in the same sense and refused the same way. Verified against a
  stopped relay with a 40-entry list: zero entries lost.

## [0.8.0] - 2026-08-02

### Added
- The super administrator can add further administrators from the settings page. Each signs in with
  their own Nostr key and uses the whole dashboard, so two people can administer one deployment
  without sharing a private key — the shared secret that key-based sign-in existed to remove.
- Two roles. The **super administrator** is the key in `BOTTIN_ADMIN_NPUB`, exactly one per
  deployment, and the only role that may manage administrators. An **administrator** is added from
  the settings page and can do everything else. The distinction is enforced where the decision is
  made: an added administrator's session never carries the managing permission, so addressing the
  endpoints directly is refused exactly as the absent button implies.
- Removing an administrator ends any session they hold immediately, on their next request rather
  than at expiry. Revocation that waits is not revocation.
- Administrator keys are accepted as `npub1…` or hex and stored in one canonical form, so the same
  key entered either way is one administrator rather than two.
- Additions, removals, and refused management attempts are recorded in the security log with the
  administrator who acted.

### Changed
- The `admin_users` table, dormant since V1, is now used. `V5` drops its `username` and
  `password_hash` columns — the latter being `NOT NULL` made it impossible to add an administrator
  without inventing a password, for a feature whose point is that there are none. The table has
  never held a row in any deployment, so the migration destroys nothing.
- Adding a key that already administers the deployment, including your own, changes nothing and says
  so rather than failing. The state asked for already holds. It is reported rather than passed over
  in silence, so an operator who pasted the wrong key learns nothing was granted.

### Security
- The master key cannot be removed, edited, or demoted through the interface, and is refused if a
  request names it directly. Its authority stays in deployment configuration, which is what admits
  an operator when the database is empty, wrong, or freshly restored.

## [0.7.0] - 2026-08-02

### Added
- Administrators sign in to the dashboard by proving control of a Nostr key, matched against
  `BOTTIN_ADMIN_NPUB`. The key is supplied once per device, kept encrypted in the browser under a
  passphrase, and never reaches the deployment — what crosses the wire is a signed challenge.
- `bottin-web-assets`, a module holding the browser code both user interfaces need: key encryption
  and the NAP handshake. A second copy of key encryption would drift, and the copy that drifts
  silently is the one without tests.
- Rate limiting on the sign-in handshake, the dashboard's only unauthenticated surface.
- The passphrase is confirmed at first sign-in. It encrypts the key, is stored nowhere, and cannot be
  recovered, so a typo did not fail at sign-in — it locked the key away under something the
  administrator did not know, discovered on their next visit.

### Changed
- **BREAKING (deployment):** the admin dashboard no longer accepts a username and password.
  `BOTTIN_ADMIN_NPUB` must be set before upgrading or nobody can sign in. `BOTTIN_ADMIN_USER` and
  `BOTTIN_ADMIN_PASSWORD` remain in use by `bottin-api` for its HTTP Basic credentials and must not
  be removed from that service.
- The administrator holds a distinct super-administrator role rather than merely being
  authenticated, so the follow-up feature adding further administrators adds a role instead of
  introducing authorization across every route.
- Signing out ends the session **and** erases the stored key, as one action. Session expiry
  deliberately differs: it leaves the key in place so the passphrase alone resumes work.
- `nap-client.js` is the NAP handshake rather than a stub whose methods threw
  "Not implemented - Phase 2" while the working code sat inline in the client's `app.js`.

### Fixed
- The client served shared scripts from its own `static/` directory in preference to the shared
  module, and an explicit `/js/**` resource handler hid the shared location entirely. Both surfaced
  only when checking what the running application actually served.
- No key could sign in at all. The ACL resolver reads one identity supplied in two encodings; it was
  written as though the first argument named an application and refused every call. The unit tests
  asserted the same misreading, so the suite stayed green while the dashboard admitted nobody.
- Signing in succeeded and every page then returned to the sign-in form. The session filter ran
  ahead of Spring Security, which begins by replacing the security context — the principal was
  established and immediately discarded.
- Every sign-in failure was reported as "That key is not authorised for this deployment", including
  the rate limit, an unreachable deployment, and the defect above. Naming the one cause an operator
  cannot act on is why it went unlooked-for. The page now distinguishes them.

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

[Unreleased]: https://github.com/tcheeric/bottin/compare/v0.8.0...HEAD
[0.8.0]: https://github.com/tcheeric/bottin/compare/v0.7.0...v0.8.0
[0.2.1]: https://github.com/tcheeric/bottin/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/tcheeric/bottin/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/tcheeric/bottin/releases/tag/v0.1.0
