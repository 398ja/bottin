# Implementation Plan: Admin-Maintained Settings

**Branch**: `004-admin-settings` | **Date**: 2026-08-01 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/004-admin-settings/spec.md`
(mirror of `docs/superpowers/specs/2026-07-31-admin-settings-design.md`)

## Summary

Move the four pieces of deployment configuration that are really *operational data* —
the Blossom media server URL, the system relays, the profile-discovery relays, and the
API rate limit — out of environment variables and into a singleton `settings` row edited
in the bottin admin UI.

Technical approach: a new `settings` table (Flyway `V4`) with one guaranteed row, read and
written through `SettingsService` in `bottin-service`. `bottin-api` exposes it at
`GET /api/v1/settings` (API role) and `RateLimitService` reads its limit from it.
`bottin-admin-ui` gets an `/admin/settings` page that writes through `SettingsService`
directly, exactly as `AdminDomainsController` uses `DomainService`. `bottin-client-ui`
holds no database dependency, so it fetches the payload over HTTP with the credentials it
already has and caches it for 60 seconds; the browser never talks to the directory API.
The per-browser relay seeding (`APP.ensureRelaysSeeded`) is deleted and replaced by
applying system relays as a union at publish and read time, so an admin change reaches
every user at once instead of freezing into each browser's `localStorage`.

## Technical Context

**Language/Version**: Java 21 (parent `pom.xml` `java.version`), ES5-style browser JS (no build step)
**Primary Dependencies**: Spring Boot 3.4.1 (WebMVC, Data JPA, Validation, Thymeleaf),
Hibernate, Flyway 10.10.0, Jackson 2.17.0, Lombok 1.18.32, springdoc-openapi 2.4.0,
nap-spring (client NAP sessions), `RestClient` (client → directory API)
**Storage**: PostgreSQL 42.7.3 (production) / H2 2.2.224 (dev + test); schema via Flyway
migrations in `bottin-persistence/src/main/resources/db/migration`
**Testing**: JUnit 5 + Mockito + AssertJ (`mvn -q verify`), `@WebMvcTest` slices for
controllers, Vitest 1.6 + jsdom for browser JS (`bottin-client-ui`, `npm test`)
**Target Platform**: Linux containers orchestrated by `docker-compose.yml`
(`bottin-api`, `bottin-admin`, `bottin-client`, `relay`, `blossom`, `postgres`)
**Project Type**: Multi-module Maven web service + two server-rendered UIs
**Performance Goals**: Settings reads must not become a hot-path cost — the client caches
for 60 s; the API's rate limiter performs one primary-key read per rate-limited request
**Constraints**: An admin change must be visible to users within 60 s; an unreachable
directory API must degrade (serve last-known or *unconfigured*) rather than guess; no
environment-variable fallback survives for the four moved values
**Scale/Scope**: One global settings row; 4 settings; 6 modules touched; 1 new migration,
1 new REST endpoint, 1 renamed client endpoint, 1 new admin page

No NEEDS CLARIFICATION items remain — see [research.md](./research.md) for the eight
decisions that closed the gaps the spec left implicit.

## Constitution Check

*GATE: evaluated against `.specify/memory/constitution.md` v1.1.0. Re-checked after Phase 1 — still passing.*

| Principle | Verdict | Evidence |
|---|---|---|
| **I. Identity Mapping Integrity** | PASS | No code path here creates, modifies, or serves a `username@domain -> pubkey` mapping. System relays are unioned into the `relays` array sent to `POST /api/v1/records` and into kind-10002 — they extend a record's relay list, never its `names` entry. Uniqueness, key validation, and verification state are untouched. |
| **II. Protocol Compliance (NIPs)** | PASS | The `.well-known/nostr.json` shape is unchanged (`WellKnownJsonGenerator` still parses `relays_json` the same way). Including system relays in kind-10002 is exactly NIP-65's purpose — publishing to a relay while omitting it from the advertised list would make those events unfindable. |
| **III. Clean Architecture** | PASS | `SettingsData` (framework-free value object) in `bottin-core`; `SettingsEntity` + `SettingsRepository` + `V4__settings.sql` in `bottin-persistence`; `SettingsService` (the use case) in `bottin-service`; `SettingsController` + DTO in `bottin-api`; form + Thymeleaf template in `bottin-admin-ui` with no business logic. Dependencies point inward only. `bottin-client-ui` depends on `bottin-core` alone, so it reaches settings over HTTP through `DirectorySettingsClient` — an adapter, not a leaked repository. *Note: the constitution names the delivery layer `bottin-web`; the module is actually `bottin-api`. Pre-existing naming drift, not introduced here.* |
| **IV. Testing Discipline** | PASS | Six test suites planned (`SettingsServiceTest`, `SettingsControllerTest`, `RateLimitServiceTest`, `AdminSettingsControllerTest`, `DirectorySettingsClientTest`, `RelayControllerTest`) plus Vitest coverage for the relay union and the disabled-upload guard. Boundary cases named explicitly: empty relay list, blank media server, `http://` relay rejection, unreachable API with and without a warm cache. `mvn -q verify` gates every commit. |
| **V. Virtual Threads** | N/A | No new I/O fan-out. The client's single cached `RestClient` call and the API's primary-key read are both sequential. |
| **VI. Secure Coding & Code Quality** | PASS | `GET /api/v1/settings` requires `hasRole("API")`; `/admin/settings` sits behind the existing `AdminSecurityConfig`; `/api/v1/relays/system` inherits NAP protection from the `/api/v1/relays` prefix already in `nap.protected-path-prefixes`. No secret is stored in `settings` — credentials stay in the environment by design. URL schemes are validated at the trust boundary (form-level bean validation *and* `SettingsService`, so no path can persist a bad value). `SettingsNotFoundException extends BottinException` with error code, retryable flag, and an actionable suggestion. |
| **VII. Public-by-Design Data & Privacy** | PASS | The settings row holds deployment topology, not user or operational PII. Rate-limit counters stay in-memory in `RateLimitService`; only the *limit* moves to the database. |
| **VIII. Clean Code Craftsmanship** | PASS | `SettingsService.update(SettingsData)` takes one argument rather than four. No flag arguments. Relay normalisation is one named method, not a boolean-driven branch. `update()` returning the persisted state follows the Command-Query exception already established by `DomainService.create()`. |
| **Documentation Standards (Diátaxis)** | PASS | New how-to `docs/how-to/configure-deployment-settings.md`, linked from `docs/README.md`; `docs/reference/rest-api.md` gains the new endpoint; three existing documents lose their now-dead environment variables. |
| **Development Workflow** | PASS | Conventional Commits scoped per module, one commit per rollout step, `mvn -q verify` before each, version bump + `CHANGELOG.md` entry on completion. |

**Gate result: PASS — no violations to justify.**

## Project Structure

### Documentation (this feature)

```text
specs/004-admin-settings/
├── plan.md              # This file
├── spec.md              # Feature specification (mirrors docs/superpowers/specs/2026-07-31-admin-settings-design.md)
├── research.md          # Phase 0 output — eight decisions closing the spec's implicit gaps
├── data-model.md        # Phase 1 output — settings table, SettingsData, validation rules
├── quickstart.md        # Phase 1 output — operator + developer walkthrough
├── contracts/
│   ├── README.md
│   ├── settings-api.md          # GET /api/v1/settings (bottin-api)
│   ├── relays-system-api.md     # GET /api/v1/relays/system (bottin-client-ui)
│   └── admin-settings-form.md   # /admin/settings GET + POST form contract
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
bottin-core/src/main/java/xyz/tcheeric/bottin/core/
├── model/SettingsData.java                        # NEW — immutable value object, mirrors DomainData
└── exception/SettingsNotFoundException.java       # NEW — extends BottinException

bottin-persistence/src/main/
├── java/xyz/tcheeric/bottin/persistence/
│   ├── entity/SettingsEntity.java                 # NEW — singleton row, id fixed at 1
│   └── repository/SettingsRepository.java         # NEW — JpaRepository<SettingsEntity, Long>
└── resources/db/migration/V4__settings.sql        # NEW — table + seed row (V3 is current head)

bottin-service/src/main/java/xyz/tcheeric/bottin/service/
└── SettingsService.java                           # NEW — read, update, relay JSON (de)serialisation, scheme validation

bottin-api/src/main/java/xyz/tcheeric/bottin/api/
├── controller/SettingsController.java             # NEW — GET /api/v1/settings
├── dto/SettingsResponse.java                      # NEW — record; rateLimitPerMinute deliberately absent
├── ratelimit/RateLimitService.java                # MODIFY — limit from SettingsService, not @Value
└── config/SecurityConfig.java                     # MODIFY — /api/v1/settings requires hasRole("API")

bottin-admin-ui/src/main/
├── java/xyz/tcheeric/bottin/admin/
│   ├── controller/AdminSettingsController.java    # NEW — GET renders bound form, POST saves + redirects
│   └── dto/SettingsForm.java                      # NEW — bean validation, textareas as newline-separated text
└── resources/templates/
    ├── admin/settings.html                        # NEW — form, empty-state warnings, updated_at
    └── fragments/layout.html                      # MODIFY — nav entry

bottin-client-ui/src/main/
├── java/xyz/tcheeric/bottin/client/
│   ├── service/DirectorySettingsClient.java       # NEW — RestClient + 60 s cache, degrades to unconfigured
│   ├── dto/DirectorySettings.java                 # NEW — record deserialised from the API payload
│   ├── controller/RelayController.java            # MODIFY — /defaults -> /system, reads settings
│   ├── controller/OnboardingController.java       # MODIFY — blossomUrl + discoveryRelays from settings
│   ├── controller/ProfileController.java          # MODIFY — blossomUrl from settings
│   └── config/ClientProperties.java               # MODIFY — drop defaultRelays and blossomUrl
└── resources/
    ├── static/js/app.js                           # MODIFY — drop ensureRelaysSeeded; add systemRelays,
    │                                              #          effectiveWriteRelays, effectiveReadRelays
    ├── static/js/profile.js                       # MODIFY — publish to the union
    ├── static/js/profile-fetch.js                 # MODIFY — read from the union
    ├── static/js/settings-relays.js               # MODIFY — render own relays only; publish the union
    ├── static/js/onboarding-complete.js           # MODIFY — register + publish with the union
    ├── static/js/profile-image.js                 # MODIFY — one guard disabling uploads when unconfigured
    ├── templates/onboarding/step-import.html      # MODIFY — drop the hardcoded public relay list
    └── application.yml                            # MODIFY — drop blossom-url and default-relays keys

docker-compose.yml                                 # MODIFY — drop BOTTIN_BLOSSOM_URL, BOTTIN_DEFAULT_RELAYS
.env                                               # MODIFY — same two variables
docs/
├── README.md                                      # MODIFY — link the new how-to
├── how-to/configure-deployment-settings.md        # NEW — Diátaxis how-to
├── how-to/upload-profile-images.md                # MODIFY — configure in the admin UI, not the environment
├── how-to/verify-profile-and-relay-publishing.md  # MODIFY — same
├── how-to/docker-deployment.md                    # MODIFY — post-deploy configuration step
├── reference/docker-compose-configuration.md      # MODIFY — remove the two variables
└── reference/rest-api.md                          # MODIFY — document GET /api/v1/settings
```

**Structure Decision**: The existing multi-module Maven layout is kept as-is; this feature
adds one class per layer rather than a new module. The one structural constraint that
shapes the design is that `bottin-client-ui`'s POM depends on `bottin-core` only — not
`bottin-persistence` or `bottin-service` — so the client cannot read the settings table
and must go over HTTP. That is what forces `DirectorySettingsClient` and its cache to
exist, and what keeps the browser out of the directory API entirely.

## Implementation Sequence

The spec's rollout is preserved; steps 1–3 are independently deployable and change no
observable behaviour, which keeps the risky step (4) small.

| Step | Deliverable | Modules | Observable change |
|---|---|---|---|
| 1 | `V4__settings.sql`, `SettingsData`, `SettingsEntity`, `SettingsRepository`, `SettingsService`, `SettingsNotFoundException` | core, persistence, service | None |
| 2 | `GET /api/v1/settings` + security rule | api | New endpoint, no consumer |
| 3 | `RateLimitService` reads its limit from settings | api | Limit editable without restart |
| 4 | `/admin/settings` page + nav entry | admin-ui | Settings editable, still unconsumed |
| 5 | `DirectorySettingsClient` + `DirectorySettings` | client-ui | None (not yet wired) |
| 6 | `/api/v1/relays/system`, `APP.systemRelays`, `effectiveWriteRelays`, `effectiveReadRelays`; `ensureRelaysSeeded` deleted | client-ui | Relay behaviour switches to the union |
| 7 | Media server from settings + disabled-upload guard | client-ui | Uploads reflect admin config |
| 8 | Discovery relays from settings | client-ui | Login lookup reflects admin config |
| 9 | Delete `BOTTIN_BLOSSOM_URL` / `BOTTIN_DEFAULT_RELAYS` everywhere; documentation | root, client-ui, docs | Environment fallback gone |

`/speckit.tasks` expands this table into dependency-ordered tasks.

## Complexity Tracking

> No Constitution Check violations. Table intentionally empty.

The two judgement calls that could read as complexity are recorded as decisions in
[research.md](./research.md) rather than as violations:

- **R1** — the rate limiter reads the settings row per rate-limited request instead of
  memoising it, and carries a `ponytail:` comment naming the ceiling and the upgrade path.
- **R2** — `APP.effectiveReadRelays` is added alongside the spec's `effectiveWriteRelays`,
  because deleting `ensureRelaysSeeded` would otherwise leave the profile read path
  querying an empty relay list for every new user.
