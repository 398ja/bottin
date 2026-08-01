---
description: "Task list for Admin-Maintained Settings"
---

# Tasks: Admin-Maintained Settings

**Input**: Design documents from `/specs/004-admin-settings/`
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Included. The spec has an explicit **Testing** section naming six Java suites plus
JS coverage, so test tasks are generated and ordered before their implementation.

**Organization**: Tasks are grouped by user story so each can be implemented, tested, and
deployed independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story the task belongs to (US1–US6)
- Every task names its exact file path

## Path Conventions

Multi-module Maven project. Java sources live at
`<module>/src/main/java/xyz/tcheeric/bottin/<pkg>/`, tests at `<module>/src/test/java/...`,
browser JS at `bottin-client-ui/src/main/resources/static/js/` with Vitest specs at
`bottin-client-ui/src/test/js/`.

## Derived User Stories

The spec is a design document rather than a user-story document, so the stories below are
derived from its **Rollout** section and its stated value. Each is independently deployable,
which is the property the spec's rollout was already built around.

| Story | Priority | Value |
|---|---|---|
| **US1** — Operator edits deployment settings in the admin UI | P1 🎯 MVP | Settings become editable at all; today they require editing compose and recreating containers |
| **US2** — API rate limit reflects the configured value | P2 | An operator can change the limit without a restart |
| **US3** — Client server reads deployment settings over HTTP | P3 | The mechanism every client-side story below depends on |
| **US4** — Every publish and read reaches the system relays | P4 | Fixes the worst defect: relays copied into each browser at first use never change again |
| **US5** — Media server comes from settings, degrading when unset | P5 | Uploads reflect admin config; an unconfigured server disables controls instead of erroring |
| **US6** — Login profile discovery uses the configured relays | P6 | Removes the relay list hardcoded into the client image |

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish a green baseline and confirm no build-file changes are needed.

- [x] T001 Record a green baseline: run `mvn -q verify` from the repository root and `npm test` in `bottin-client-ui/`, and confirm `bottin-persistence/src/main/resources/db/migration/` still has `V3__profile_reach.sql` as its head so the `V4` slot is free
- [x] T002 [P] Confirm no POM changes are required for this feature: `bottin-api/pom.xml` and `bottin-admin-ui/pom.xml` already declare `bottin-service`, and `bottin-client-ui/pom.xml` declares `bottin-core` only — record in the commit message that the client's lack of a persistence dependency is what forces the HTTP path in US3

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The settings row and the service that owns it. Every user story reads or writes through `SettingsService`.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [x] T003 [P] Create `SettingsData` immutable value object in `bottin-core/src/main/java/xyz/tcheeric/bottin/core/model/SettingsData.java` — Lombok `@Value` + `@Builder(toBuilder = true)`, fields `blossomUrl`, `defaultRelays`, `discoveryRelays`, `rateLimitPerMinute`, `updatedAt`, mirroring `DomainData`; Javadoc on `defaultRelays` stating these are the deployment's system relays (see research.md R3)
- [x] T004 [P] Create `SettingsNotFoundException` in `bottin-core/src/main/java/xyz/tcheeric/bottin/core/exception/SettingsNotFoundException.java` extending `BottinException` with error code `SETTINGS_NOT_FOUND`, `retryable = false`, and the migration-oriented suggestion from data-model.md, following `DomainNotFoundException`
- [x] T005 [P] Create the migration in `bottin-persistence/src/main/resources/db/migration/V4__settings.sql` with the table, the `settings_singleton` `CHECK (id = 1)` constraint, and the seed `INSERT` for row 1, exactly as in data-model.md
- [x] T006 Create `SettingsEntity` in `bottin-persistence/src/main/java/xyz/tcheeric/bottin/persistence/entity/SettingsEntity.java` — `@Id` with a `SINGLETON_ID = 1L` constant and **no** `@GeneratedValue`, `@Builder.Default` JSON columns defaulting to `"[]"` as in `Nip05RecordEntity:59-61`, `@PrePersist`/`@PreUpdate` stamping `updatedAt`, plus `toSettingsData()` and `fromSettingsData(SettingsData)` (depends on T003, T005)
- [x] T007 Create `SettingsRepository extends JpaRepository<SettingsEntity, Long>` in `bottin-persistence/src/main/java/xyz/tcheeric/bottin/persistence/repository/SettingsRepository.java` with no custom query methods (depends on T006)
- [x] T008 Write `SettingsServiceTest` in `bottin-service/src/test/java/xyz/tcheeric/bottin/service/SettingsServiceTest.java` with a mocked `SettingsRepository`, covering JSON round-trip for both relay lists, rejection of a non-`ws`/`wss` scheme naming the offending URL, duplicate collapsing with order preserved, blank-line dropping, `SettingsNotFoundException` when row 1 is absent, and `updatedAt` advancing on update; add a `SettingsService` skeleton whose methods throw `UnsupportedOperationException` so the suite compiles, and confirm it FAILS (depends on T007)
- [x] T009 Implement `SettingsService` in `bottin-service/src/main/java/xyz/tcheeric/bottin/service/SettingsService.java` — injected `ObjectMapper`, `find()` returning `SettingsData` (throwing `SettingsNotFoundException`), `update(SettingsData)` taking one argument, and a `normalizeRelays` helper using a `LinkedHashSet` as in `Nip05RecordService:193-203`; confirm `SettingsServiceTest` PASSES (depends on T008)
- [~] T010 [P] Add `SettingsRepositoryIT` in `bottin-tests/bottin-it/src/test/java/xyz/tcheeric/bottin/it/SettingsRepositoryIT.java` asserting that the migration seeds row 1 with `rate_limit_per_minute = 30`, both relay lists `'[]'` and `blossom_url` null, and that inserting a second row violates `settings_singleton` (depends on T007) — **WRITTEN BUT BLOCKED, see T010a**
- [ ] T010a Fix the pre-existing `bottin-it` context failure that blocks every integration test in the module: `bottin-tests/bottin-it/src/test/java/xyz/tcheeric/bottin/it/TestApplication.java` scans `xyz.tcheeric.bottin.api`, which contains `BottinApiApplication` — itself `@SpringBootApplication` with `@EnableJpaRepositories(basePackages = "xyz.tcheeric.bottin.persistence.repository")` — so both configurations register the same repository beans and the context fails with `BeanDefinitionOverrideException` on `domainRepository`. Exclude `BottinApiApplication` from `TestApplication`'s component scan with an `ASSIGNABLE_TYPE` exclude filter, then re-run every `*IT` in the module and triage whatever the restored context reveals

**Checkpoint**: The settings row exists and is readable and writable from Java. No observable behaviour has changed.

**Phase 2 implementation notes** — two deviations from the task text above, both recorded rather than silently absorbed:

- **T006**: `SettingsEntity` has **no** `toSettingsData()` / `fromSettingsData()`. The entity holds raw JSON while `SettingsData` holds `List<String>`, so entity-side conversion would have to deserialise — dragging Jackson into `bottin-persistence` and contradicting the same task's "JSON serialisation belongs to the service" rule. The mapping lives in `SettingsService`, which already owns the `ObjectMapper`. [data-model.md](./data-model.md) corrected to match.
- **T008**: "`updatedAt` advancing on update" moved to T010. `updatedAt` is stamped by a JPA `@PreUpdate` callback, which never fires against a mocked repository — asserting it in a unit test would have tested nothing. It is verified in `SettingsRepositoryIT` where Hibernate actually runs. `SettingsServiceTest` covers the singleton-update case instead.

`SettingsServiceTest` ended at 10 tests rather than the 6 cases listed, having gained: nothing is persisted when a relay is rejected (a bad submission must not partially apply), and a blank media server is stored as `null` so "unconfigured" has one representation rather than two.

---

## Phase 3: User Story 1 — Operator edits deployment settings in the admin UI (Priority: P1) 🎯 MVP

**Goal**: An operator can view and change the media server, system relays, discovery relays, and rate limit at `/admin/settings`, with validation and a record of when it last changed.

**Independent Test**: Log into the admin UI, open `/admin/settings`, save a media server URL and two relay lines, reload — the values persist and `updatedAt` advances. Submitting a blank media server or an `http://` relay shows a field error and changes nothing.

### Tests for User Story 1

- [x] T011 [US1] Write `AdminSettingsControllerTest` in `bottin-admin-ui/src/test/java/xyz/tcheeric/bottin/admin/controller/AdminSettingsControllerTest.java` — `@WebMvcTest(AdminSettingsController.class)`, `@Import(AdminSecurityConfig.class)`, mocked `SettingsService`, following `AdminDomainsControllerTest`; cover the six cases in `contracts/admin-settings-form.md` (unauthenticated redirect, bound form render, valid save + flash, blank media server rejected without reaching the service, `http://` relay rejected without reaching the service, `rateLimitPerMinute = 0` rejected) and confirm it FAILS

### Implementation for User Story 1

- [x] T012 [P] [US1] Create `SettingsForm` in `bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/dto/SettingsForm.java` — Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor` as in `CreateDomainForm`, with the four fields and the bean validation constraints tabulated in `contracts/admin-settings-form.md`; relay textareas are newline-separated `String`s validated by `^\s*((wss?://\S+)\s*)*$`
- [x] T013 [US1] Create `AdminSettingsController` in `bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/controller/AdminSettingsController.java` — `@RequestMapping("/admin/settings")`, `GET` binding `settingsForm` and `updatedAt` from `settingsService.find()`, `POST` re-rendering `admin/settings` on binding errors and otherwise calling `settingsService.update` then redirecting with a `success` flash; catch `IllegalArgumentException` from the service into an `error` flash naming the offending URL; log `admin_settings_updated` and `admin_settings_update_failed` in the structured style used by `AdminDomainsController` (depends on T012)
- [x] T014 [US1] Create `bottin-admin-ui/src/main/resources/templates/admin/settings.html` — Tailwind form matching `admin/domains.html` conventions, `head`/`navigation`/`alerts`/`footer` fragments from `fragments/layout.html`, four bound inputs with field-level error rendering, and labels saying **system relays** rather than "default relays" (depends on T013)
- [x] T015 [US1] Add the operator guidance to `bottin-admin-ui/src/main/resources/templates/admin/settings.html`: the two empty-state warnings ("No media server set — image uploads are disabled for all users" and "No system relays set — user events publish only to relays each user adds themselves"), the "Takes effect within a minute" note beside the save button, the rendered `updatedAt`, and the hint that the media server URL must be reachable **from the browser**, not over the compose network (depends on T014)
- [x] T016 [P] [US1] Add a `Settings` nav link after `Domains` in `bottin-admin-ui/src/main/resources/templates/fragments/layout.html`, matching the existing anchor styling
- [ ] T017 [US1] Run `mvn -q verify -pl bottin-admin-ui -am`, confirm `AdminSettingsControllerTest` PASSES, and commit as `feat(admin-ui): add admin-maintained settings page` (depends on T011–T016)

**Checkpoint**: Settings are editable end to end. Nothing consumes them yet, so this is deployable on its own.

**US1 implementation notes**:

- `AdminSettingsControllerTest` ended at 7 tests rather than 6, gaining: a rejection raised by `SettingsService` (the second enforcement point, for callers that do not come through this form) is reported to the operator instead of surfacing as a 500.
- The textarea-to-list translation lives on `SettingsForm` (`from` / `toSettingsData`) rather than in the controller, keeping the controller free of logic per the constitution's delivery-layer rule. Trimming and de-duplication stay in the service, which owns them for every caller.
- A rejected submission re-renders `admin/settings` rather than redirecting, so field errors render next to their inputs and the operator keeps what they typed. `AdminDomainsController` redirects with a flash instead — acceptable there, where one field is being added, but it would discard four fields here.
- The validation-error path does not re-read settings just to repopulate `updatedAt`; the template guards it with `th:if` and shows "Never saved" otherwise. That avoids a pointless query on every rejected submission.

---

## Phase 4: User Story 2 — API rate limit reflects the configured value (Priority: P2)

**Goal**: `RateLimitService` reads its limit from the settings row, so an operator can change it in the admin UI and have it apply without restarting `bottin-api`.

**Independent Test**: Set the rate limit to `1` in `/admin/settings`, then call `GET /api/v1/profiles/{npub}/reach` twice without restarting anything — the second call returns `429`.

### Tests for User Story 2

- [ ] T018 [US2] Write `RateLimitServiceTest` in `bottin-api/src/test/java/xyz/tcheeric/bottin/api/ratelimit/RateLimitServiceTest.java` with a mocked `SettingsService`, covering: the limit is read from settings; requests beyond it are rejected; `getRemainingRequests` reflects the configured limit; and a limit changed between calls takes effect on the next call with no restart; confirm it FAILS

### Implementation for User Story 2

- [ ] T019 [US2] Modify `bottin-api/src/main/java/xyz/tcheeric/bottin/api/ratelimit/RateLimitService.java` — replace the `@Value("${bottin.ratelimit.requests-per-minute:30}")` field with a `@RequiredArgsConstructor`-injected `SettingsService` and a private `limit()` method reading `settingsService.find().getRateLimitPerMinute()`, used by both `isAllowed` and `getRemainingRequests`; keep `cleanupThreshold` as `@Value`; add the `ponytail:` comment from research.md R1 naming the per-request read as the accepted ceiling and a 60-second memo as the upgrade path (depends on T018)
- [ ] T020 [US2] Run `mvn -q verify -pl bottin-api -am` and fix any `bottin-api` test that constructed `RateLimitService` directly or relied on the removed property; `ProfileStatsControllerTest` mocks the bean and should need no change — confirm this rather than assuming (depends on T019)
- [ ] T021 [US2] Confirm `RateLimitServiceTest` PASSES and commit as `feat(api): read the rate limit from admin-maintained settings` (depends on T020)

**Checkpoint**: The rate limit is operator-controlled at runtime. US1 and US2 both work independently.

---

## Phase 5: User Story 3 — Client server reads deployment settings over HTTP (Priority: P3)

**Goal**: `bottin-api` exposes the settings payload to authenticated API callers, and `bottin-client-ui` reads it through a cached client that degrades rather than guessing.

**Independent Test**: `curl -u api:… http://localhost:8080/api/v1/settings` returns the three keys and no `rateLimitPerMinute`; the same call without credentials returns `401`. With `bottin-api` stopped and no warm cache, `DirectorySettingsClient` returns unconfigured values instead of throwing.

### Tests for User Story 3

- [ ] T022 [P] [US3] Write `SettingsControllerTest` in `bottin-api/src/test/java/xyz/tcheeric/bottin/api/controller/SettingsControllerTest.java` with a mocked `SettingsService`, covering the four cases in `contracts/settings-api.md` (payload shape with `rateLimitPerMinute` absent, `blossomUrl` as JSON `null` when unconfigured, empty relay lists as `[]` not `null`, `401` without the API role); confirm it FAILS
- [ ] T023 [P] [US3] Write `DirectorySettingsClientTest` in `bottin-client-ui/src/test/java/xyz/tcheeric/bottin/client/service/DirectorySettingsClientTest.java` covering the four degradation rules in research.md R5 (cache serves within the TTL, refetches after it, an unreachable API with a warm cache serves the stale value, an unreachable API with no cache yields unconfigured rather than an exception); confirm it FAILS

### Implementation for User Story 3

- [ ] T024 [P] [US3] Create `SettingsResponse` record in `bottin-api/src/main/java/xyz/tcheeric/bottin/api/dto/SettingsResponse.java` with `blossomUrl`, `defaultRelays`, `discoveryRelays` and a static `from(SettingsData)` factory, mirroring `ProfileReachResponse.from`; `rateLimitPerMinute` is deliberately absent
- [ ] T025 [US3] Create `SettingsController` in `bottin-api/src/main/java/xyz/tcheeric/bottin/api/controller/SettingsController.java` — `@RestController @RequestMapping("/api/v1/settings")`, one `@GetMapping` returning `SettingsResponse.from(settingsService.find())`, annotated with springdoc `@Tag`/`@Operation`/`@ApiResponses` as in `ProfileStatsController` (depends on T024)
- [ ] T026 [US3] Add `.requestMatchers("/api/v1/settings").hasRole("API")` to `apiFilterChain` in `bottin-api/src/main/java/xyz/tcheeric/bottin/api/config/SecurityConfig.java`, alongside the existing `records` and `domains` matchers (depends on T025)
- [ ] T027 [P] [US3] Create the `DirectorySettings` record in `bottin-client-ui/src/main/java/xyz/tcheeric/bottin/client/dto/DirectorySettings.java` with `blossomUrl`, `defaultRelays`, `discoveryRelays` and a static `unconfigured()` returning `(null, List.of(), List.of())`; no Jackson annotations — the parent POM sets `<parameters>true</parameters>` (research.md R4)
- [ ] T028 [US3] Create `DirectorySettingsClient` in `bottin-client-ui/src/main/java/xyz/tcheeric/bottin/client/service/DirectorySettingsClient.java` — a `RestClient` built from `ClientProperties` exactly as `DirectoryRegistrationService`'s constructor does (`directoryUrl` base, Basic auth from `directoryUsername`/`directoryPassword`), a 60-second in-memory cache, and the four degradation rules from research.md R5; log `directory_settings_fetch_failed` at WARN with the fallback taken (depends on T027)
- [ ] T029 [US3] Run `mvn -q verify`, confirm both new suites PASS, and commit as two commits — `feat(api): serve admin-maintained settings at /api/v1/settings` and `feat(client-ui): read deployment settings from the directory API` (depends on T022, T023, T026, T028)

**Checkpoint**: Settings are readable by the client server. Nothing in the browser has changed yet.

---

## Phase 6: User Story 4 — Every publish and read reaches the system relays (Priority: P4)

**Goal**: Replace per-browser relay seeding with a union applied at publish and read time, so an admin change reaches every user at once and system relays never appear in a user's own list.

**Independent Test**: With system relays configured and a brand-new identity whose relay list is empty, saving a profile publishes successfully; Settings → Relays shows "no relays added yet"; the published kind-10002 contains the system relays.

### Tests for User Story 4

- [ ] T030 [US4] Rewrite `bottin-client-ui/src/test/java/xyz/tcheeric/bottin/client/controller/RelayControllerTest.java` for `/api/v1/relays/system` with a mocked `DirectorySettingsClient` — returns configured system relays as plain strings, returns `{"relays": []}` when none are configured — replacing the `@TestPropertySource(properties = "bottin.client.default-relays=…")` fixture and the `shouldDropBlankDefaultRelayEntries` / `shouldReturnEmptyArrayWhenNoDefaultRelaysConfigured` tests; confirm it FAILS
- [ ] T031 [US4] Rewrite the `ensureRelaysSeeded` block in `bottin-client-ui/src/test/js/app-session.test.js` as tests for `APP.systemRelays`, `APP.effectiveWriteRelays`, and `APP.effectiveReadRelays` — union with the user's relays first, de-duplication by URL, `[]` on fetch failure, and the assertion that **neither** effective-relay function writes to `localStorage`; confirm it FAILS

### Implementation for User Story 4

- [ ] T032 [US4] Modify `bottin-client-ui/src/main/java/xyz/tcheeric/bottin/client/controller/RelayController.java` — replace `@GetMapping("/defaults")` with `@GetMapping("/system")` returning `{"relays": [...plain URLs]}` from `directorySettingsClient.current().defaultRelays()`, and drop the `ClientProperties` dependency; leave the other mappings untouched (depends on T030)
- [ ] T033 [US4] Modify `bottin-client-ui/src/main/resources/static/js/app.js` — delete `ensureRelaysSeeded` (lines 119–137) and add `systemRelays()`, `effectiveWriteRelays(userId)`, and `effectiveReadRelays(userId)` per the browser contract in `contracts/relays-system-api.md`, sharing one private union helper; `systemRelays()` resolves to `[]` rather than rejecting on failure (depends on T031, T032)
- [ ] T034 [P] [US4] Modify `bottin-client-ui/src/main/resources/static/js/profile.js:140` to use `APP.effectiveWriteRelays(userId)`, which already yields URL strings, and remove the now-redundant `filter`/`map` and the stale "Seed the default relay list on first use" comment
- [ ] T035 [P] [US4] Modify `bottin-client-ui/src/main/resources/static/js/onboarding-complete.js:69` to use `app.effectiveWriteRelays(userId)`, dropping the local `filter`/`map`, and update the `APP` stub in `bottin-client-ui/src/test/js/onboarding-complete.test.js:29` accordingly
- [ ] T036 [P] [US4] Modify `bottin-client-ui/src/main/resources/static/js/profile-fetch.js:71` to use `app.effectiveReadRelays(userId)`, dropping the local `filter`/`map`, and update the stubs in `bottin-client-ui/src/test/js/profile-fetch.test.js:89` and `:120`
- [ ] T037 [US4] Modify `bottin-client-ui/src/main/resources/static/js/settings-relays.js` — `init()` (line 121) reads `APP.loadRelays(userId)` so only the user's own relays render, and `publishRelays()` (line 97) unions the system relays in as read+write entries for both the kind-10002 tags and the publish targets, per `contracts/relays-system-api.md` (depends on T033)
- [ ] T038 [US4] Add a Vitest spec asserting that the relay settings page renders only the user's own relays while `publishRelays` still targets the union, in `bottin-client-ui/src/test/js/settings-relays.test.js` (depends on T037)
- [ ] T039 [US4] Run `npm test` in `bottin-client-ui/` and `mvn -q verify`, confirm all suites PASS, and commit as `feat(client-ui): apply system relays at publish time instead of seeding browsers` (depends on T034–T038)

**Checkpoint**: The relay behaviour is admin-controlled and reaches existing users. This is the story that fixes the spec's headline defect.

---

## Phase 7: User Story 5 — Media server comes from settings, degrading when unset (Priority: P5)

**Goal**: The Blossom URL is injected server-side from settings, and an unconfigured media server disables upload controls with a stated reason rather than posting to an empty URL.

**Independent Test**: With a media server configured, uploading an avatar works. With it cleared in `/admin/settings` and the 60-second cache expired, the file inputs on both `/profile/edit` and the onboarding profile step are disabled showing "Media server not configured", and onboarding still proceeds to the next step.

### Tests for User Story 5

- [ ] T040 [US5] Add a test to `bottin-client-ui/src/test/js/profile-image.test.js` asserting that `ProfileImage.bind` with a blank `blossomUrl` disables the file input, writes "Media server not configured" into the error slot, and registers no `change` listener; confirm it FAILS

### Implementation for User Story 5

- [ ] T041 [US5] Add the guard at the top of `bind(config)` in `bottin-client-ui/src/main/resources/static/js/profile-image.js` — when `config.blossomUrl` is blank, disable `config.fileInputId`, show the reason in `config.errorId`, and return before registering the listener; one guard covers both call sites per research.md R7 (depends on T040)
- [ ] T042 [P] [US5] Modify `bottin-client-ui/src/main/java/xyz/tcheeric/bottin/client/controller/ProfileController.java:29` to read `blossomUrl` from `DirectorySettingsClient` instead of `ClientProperties`, and update `bottin-client-ui/src/test/java/xyz/tcheeric/bottin/client/controller/ProfileControllerTest.java` — replacing the `@TestPropertySource(properties = "bottin.client.blossom-url=…")` fixture with a mocked client — keeping the existing assertions on the `blossomUrl` model attribute and the rendered `id="blossom-url"` span
- [ ] T043 [P] [US5] Modify `bottin-client-ui/src/main/java/xyz/tcheeric/bottin/client/controller/OnboardingController.java:54` and `:94` to read `blossomUrl` from `DirectorySettingsClient`, and update `bottin-client-ui/src/test/java/xyz/tcheeric/bottin/client/controller/OnboardingControllerTest.java` to match
- [ ] T044 [US5] Run `npm test` in `bottin-client-ui/` and `mvn -q verify`, confirm PASS, and commit as `feat(client-ui): take the media server from admin settings` (depends on T041–T043)

**Checkpoint**: Image uploads follow admin configuration and fail informatively when it is missing.

---

## Phase 8: User Story 6 — Login profile discovery uses the configured relays (Priority: P6)

**Goal**: The public relay list hardcoded into `step-import.html` is deleted; profile lookup at login queries the admin's discovery relays plus the system relays.

**Independent Test**: Set discovery relays in `/admin/settings`, sign in with an nsec whose profile is published on one of them — the profile is found. Clear both relay lists and the same sign-in still succeeds, just without a profile.

### Tests for User Story 6

- [ ] T045 [US6] Add a test to `bottin-client-ui/src/test/java/xyz/tcheeric/bottin/client/controller/OnboardingControllerTest.java` asserting that `GET /onboarding/step/import` exposes a `discoveryRelays` model attribute holding the discovery relays unioned with the system relays, and an empty value when neither is configured; confirm it FAILS

### Implementation for User Story 6

- [ ] T046 [US6] Modify `bottin-client-ui/src/main/java/xyz/tcheeric/bottin/client/controller/OnboardingController.java:96-100` — replace the `defaultRelays` model attribute with `discoveryRelays`, built from `DirectorySettingsClient` as discovery relays unioned with system relays, de-duplicated; server-side injection is required because this step runs before any NAP session exists (research.md R8) (depends on T045)
- [ ] T047 [US6] Modify `bottin-client-ui/src/main/resources/templates/onboarding/step-import.html` — delete the hardcoded `DISCOVERY_RELAYS` constant (lines 55–58) and the `DEFAULT_RELAYS` concatenation (lines 59–64), rename the span at line 49 from `configured-relays` to `discovery-relays` bound to `${discoveryRelays}`, and read the relay list straight from it; update the surrounding comments, which currently describe the deleted public-relay behaviour (depends on T046)
- [ ] T048 [US6] Run `mvn -q verify`, confirm PASS, and commit as `feat(client-ui): take profile discovery relays from admin settings` (depends on T047)

**Checkpoint**: All six stories are functional. Every consumer now reads settings; the environment variables are dead but still present.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Delete the superseded configuration and bring documentation in line. This is the spec's rollout step 5 and must run only after every consumer has switched.

- [ ] T049 Remove `defaultRelays` and `blossomUrl` from `bottin-client-ui/src/main/java/xyz/tcheeric/bottin/client/config/ClientProperties.java` and update its class Javadoc, which currently documents both
- [ ] T050 Remove the `blossom-url` and `default-relays` keys and their comments from `bottin-client-ui/src/main/resources/application.yml` (depends on T049)
- [ ] T051 [P] Remove `BOTTIN_BLOSSOM_URL` and `BOTTIN_DEFAULT_RELAYS` from the `bottin-client` service in `docker-compose.yml:90-91`, along with the now-inaccurate "Browser-facing URLs" comment above them
- [ ] T052 [P] Remove `BOTTIN_DEFAULT_RELAYS` and `BOTTIN_BLOSSOM_URL` from `.env:30-31`
- [ ] T053 Sweep for dead references with `grep -rn "relays/defaults\|ensureRelaysSeeded\|BOTTIN_BLOSSOM_URL\|BOTTIN_DEFAULT_RELAYS\|blossom-url\|default-relays" --include=* . | grep -v docs/superpowers` and resolve every hit; historical plans and specs under `docs/superpowers/` are records of what was and are left alone (depends on T049–T052)
- [ ] T054 [P] Write the how-to `docs/how-to/configure-deployment-settings.md` (Diátaxis how-to: `#` heading, purpose statement, the four fields, the browser-reachability warning, the 60-second propagation note) and link it from the How-To section of `docs/README.md`
- [ ] T055 [P] Document `GET /api/v1/settings` in `docs/reference/rest-api.md` — auth requirement, response shape, and the deliberate absence of `rateLimitPerMinute`
- [ ] T056 [P] Update `docs/reference/docker-compose-configuration.md` to drop the two retired variables and point at `/admin/settings`, keeping the table of variables that deliberately stay in the environment
- [ ] T057 [P] Update `docs/how-to/docker-deployment.md` with the post-deploy configuration step — the stack now comes up unconfigured by design
- [ ] T058 [P] Update `docs/how-to/upload-profile-images.md:27-30` and `:47` to configure the media server in `/admin/settings` rather than via `BOTTIN_BLOSSOM_URL`
- [ ] T059 [P] Update `docs/how-to/verify-profile-and-relay-publishing.md:24`, `:31`, and `:42` to configure system relays in `/admin/settings` rather than via `BOTTIN_DEFAULT_RELAYS`
- [ ] T060 Run the full [quickstart.md](./quickstart.md) developer verification — `mvn -q verify`, `npm test`, the two `curl` checks, the rate-limit `429` check, and the degradation check with `bottin-api` stopped (depends on T053)
- [ ] T061 Bump the project version in the parent `pom.xml` per semantic versioning and add the `CHANGELOG.md` entry derived from this branch's Conventional Commits, then run `graphify update .` to refresh the knowledge graph (depends on T060)
- [ ] T062 Update the task's card on the kan `bottin` board with the commit ids and a note, per the `kan-tracking` skill (depends on T061)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup — **BLOCKS all user stories**
- **US1 (Phase 3)**: Depends on Foundational only
- **US2 (Phase 4)**: Depends on Foundational only
- **US3 (Phase 5)**: Depends on Foundational only
- **US4 (Phase 6)**: Depends on Foundational **and US3** — the browser union needs `DirectorySettingsClient` to have something to serve
- **US5 (Phase 7)**: Depends on Foundational **and US3** — same reason
- **US6 (Phase 8)**: Depends on Foundational **and US3** — same reason
- **Polish (Phase 9)**: Depends on US4, US5, and US6 — deleting the environment variables before every consumer has switched would break the client

### User Story Dependencies

US1, US2, and US3 are mutually independent and can be built in any order or in parallel.
US4, US5, and US6 each depend on US3 but **not on each other** — they touch different files
and can be built in parallel once US3 lands.

```
Setup ─▶ Foundational ─┬─▶ US1 (admin page)      ─────────────────┐
                       ├─▶ US2 (rate limit)      ─────────────────┤
                       └─▶ US3 (settings over HTTP) ─┬─▶ US4 ─────┤
                                                     ├─▶ US5 ─────┼─▶ Polish
                                                     └─▶ US6 ─────┘
```

This is the spec's own rollout: steps 1–3 (Foundational, US2, US3, US1) are independently
deployable and change no behaviour, which keeps the risky step — US4's browser switch-over —
small.

### Within Each User Story

- Tests are written first and must FAIL before implementation
- Models before services, services before endpoints, endpoints before browser code
- The story's verification task closes the story before the next priority begins

### Parallel Opportunities

- **Phase 2**: T003, T004, T005 are three different files with no ordering between them
- **Phase 5**: T022 and T023 are independent test suites in different modules; T024 and T027 are independent records
- **Phase 6**: T034, T035, T036 are three different JS files, each with its own spec
- **Phase 7**: T042 and T043 are two different controllers
- **Phase 9**: T051, T052, and T054–T059 are eight independent files
- Once Phase 5 completes, US4, US5, and US6 can be staffed in parallel

---

## Parallel Example: Phase 2 Foundational

```bash
# Three independent files, no ordering between them:
Task: "Create SettingsData in bottin-core/src/main/java/xyz/tcheeric/bottin/core/model/SettingsData.java"
Task: "Create SettingsNotFoundException in bottin-core/src/main/java/xyz/tcheeric/bottin/core/exception/SettingsNotFoundException.java"
Task: "Create the migration in bottin-persistence/src/main/resources/db/migration/V4__settings.sql"
```

## Parallel Example: User Story 4

```bash
# Three different browser modules, each with its own Vitest spec:
Task: "Modify profile.js:140 to use APP.effectiveWriteRelays(userId)"
Task: "Modify onboarding-complete.js:69 to use app.effectiveWriteRelays(userId)"
Task: "Modify profile-fetch.js:71 to use app.effectiveReadRelays(userId)"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1: Setup (T001–T002)
2. Phase 2: Foundational (T003–T010) — **blocks everything**
3. Phase 3: User Story 1 (T011–T017)
4. **STOP and VALIDATE**: log in, save settings, reload, confirm `updatedAt` advanced and invalid input is rejected
5. Deployable: settings are editable, nothing consumes them, no behaviour changed

### Incremental Delivery

1. Setup + Foundational → the settings row exists
2. + US1 → operators can configure (**MVP**)
3. + US2 → the rate limit is live and restart-free
4. + US3 → the client server can read settings
5. + US4 → relays are admin-controlled and reach existing users (**the headline fix**)
6. + US5 → media server is admin-controlled and degrades cleanly
7. + US6 → discovery relays are admin-controlled
8. + Polish → the environment variables are gone and the docs match

Deploy after each of steps 2–7; each adds value without breaking the previous.

### Parallel Team Strategy

1. Everyone completes Setup + Foundational together
2. Then: Developer A takes US1 (admin UI), Developer B takes US2 + US3 (API and client server)
3. Once US3 lands: Developer A takes US4 (the largest), B takes US5, C takes US6
4. Polish is done by whoever finishes last, since it depends on all three

---

## Notes

- `[P]` tasks touch different files and have no dependency on an incomplete task
- Prefer one commit per task or per tight logical group; avoid grouped commits (repo convention)
- `mvn -q verify` from the repository root must pass before every commit; browser changes also need `npm test` in `bottin-client-ui/`
- Verify each test FAILS before writing the implementation it covers
- Stop at any checkpoint to validate a story independently
- The naming split is intentional: the column and JSON key say `defaultRelays`, the UI and docs say **system relays** (research.md R3)
