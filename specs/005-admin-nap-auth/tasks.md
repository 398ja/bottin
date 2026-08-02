---
description: "Task list for Admin sign-in with a Nostr key"
---

# Tasks: Admin sign-in with a Nostr key

**Input**: Design documents from `/specs/005-admin-nap-auth/`
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Included. The spec carries acceptance scenarios for all five stories, every contract has
a Verification section, and Principle IV requires them. Test tasks are ordered before the
implementation they cover.

**Organization**: Tasks are grouped by user story so each can be implemented and tested
independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: Which user story the task belongs to (US1–US5)
- Every task names its exact file path

## Path Conventions

`bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/…` for Java, `src/test/java/…` for its
tests, `src/main/resources/templates/admin/` for pages. Browser JS currently lives in
`bottin-client-ui/src/main/resources/static/js/`; T012 relocates the shared parts.

## User Stories

| Story | Priority | Delivers |
|---|---|---|
| **US1** — First sign-in with a key | P1 🎯 MVP | The dashboard is reachable by key instead of password |
| **US2** — Any other key is refused | P2 | The change is safe, not merely different |
| **US3** — Unconfigured deployment admits nobody | P3 | The safe failure direction is guaranteed |
| **US4** — Returning admin unlocks with a passphrase | P4 | The everyday path; a raw key is handled once, not every session |
| **US5** — Signing out removes the key from the device | P5 | "Signed out" means it |

---

## Phase 1: Setup

**Purpose**: A green baseline and the one dependency this feature needs.

- [X] T001 Record a green baseline: run `mvn -q verify` from the repository root and `npm test` in `bottin-client-ui/`, and note the counts so any later failure is attributable to this feature rather than inherited
- [X] T002 Add the `nap-spring` dependency to `bottin-admin-ui/pom.xml`, matching the version `bottin-client-ui/pom.xml` already declares so the two applications cannot drift onto different protocol versions

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The NAP plumbing, the single role decision, and the shared browser crypto. Every story depends on all of it.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

**⚠️ The dashboard is unreachable during this phase.** T011 removes form login because Spring Security's filter chain owns `/admin/**` and would redirect to the form regardless of any NAP session. US1 makes it reachable again. Do not deploy from a mid-phase state.

- [X] T003 [P] Create `AdminPermissions` in `bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/config/AdminPermissions.java` holding the permission keys (`admin:read`, `admin:write`, `admin:manage-admins`) and role keys (`super-admin`, `admin`) as constants, so the registry, the resolver, and every `@RequiresPermission` refer to one definition — see data-model.md
- [X] T004 Write `ConfiguredAdminAclResolverTest` in `bottin-admin-ui/src/test/java/xyz/tcheeric/bottin/admin/security/ConfiguredAdminAclResolverTest.java` covering the four outcomes in research.md R1 — configured key matches (allowed, role `super-admin`), configured key differs (denied), no key configured (denied, distinct reason), configured value unusable (denied, distinct reason) — plus that an `npub` and its hex form resolve identically; add a skeleton resolver so the suite compiles, and confirm it FAILS
- [X] T005 Implement `ConfiguredAdminAclResolver` in `bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/security/ConfiguredAdminAclResolver.java` implementing `AclResolver.resolve(appId, pubkey)`, reading `bottin.admin.npub`, decoding NIP-19 via nostr-java and comparing on 64-character lowercase hex; return `AclDecision.allowed(List.of(SUPER_ADMIN), …)` or `AclDecision.denied(reason)`; this is the single role decision point FR-015 requires; confirm the suite PASSES (depends on T003, T004)
- [X] T006 Create `AdminNapConfig` in `bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/config/AdminNapConfig.java` declaring the `PermissionRegistry` bean via `PermissionRegistry.of("bottin-admin", permissions, roles, null)` with **no default role**, exposing `ConfiguredAdminAclResolver` as the `AclResolver` bean, and registering `NapServletFilter` and `NapSessionFilter` following `bottin-client-ui/.../config/ClientSecurityConfig.java` including its `ObjectProvider` treatment so `@WebMvcTest` slices are not locked out (depends on T005)
- [X] T007 Add the `nap` block and `bottin.admin.npub` to `bottin-admin-ui/src/main/resources/application.yml` with the values tabulated in `contracts/auth-endpoints.md` — challenge TTL 60, session TTL 3600, clock skew 60, and cookie name **`admin_session`**, which must differ from the client's `client_session` because cookies are not isolated by port and a shared name lets one application's session overwrite the other's on localhost
- [X] T008 Write `RequireAdminSessionFilterTest` in `bottin-admin-ui/src/test/java/xyz/tcheeric/bottin/admin/config/RequireAdminSessionFilterTest.java` asserting that a request accepting HTML with no session gets `302` to `/admin/login`, a request not accepting HTML gets `401`, `/admin/login` itself is never redirected, and a request with a valid session passes through; confirm it FAILS
- [X] T009 Implement `RequireAdminSessionFilter` in `bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/config/RequireAdminSessionFilter.java` per research.md R2 — redirect browsers, `401` for anything else — and register it after `NapSessionFilter`, modelled on `bottin-client-ui/.../config/RequireNapAuthenticationFilter.java` which answers `401` for everything (depends on T008)
- [X] T010 Register `RequireAdminSessionFilter` in `AdminNapConfig` over the whole `/admin/**` prefix, independently of any per-route annotation, so a route added later without `@RequiresPermission` fails closed rather than becoming public — the catch-all rule in `contracts/admin-access-contract.md` (depends on T006, T009)
- [X] T011 Rework `bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/config/AdminSecurityConfig.java`: remove `formLogin`, `logout`, the `InMemoryUserDetailsManager`, and the `bottin.admin.username`/`password` `@Value` fields; keep a filter chain that permits `/admin/login` and `/api/v1/auth/**` and requires authentication elsewhere, relying on the `NapSessionFilter.NapAuthenticationToken` that filter places in the `SecurityContext` (depends on T010)
- [X] T012 Extract the shared browser crypto per research.md R3: move `nostr-crypto.js` and the NAP handshake currently inlined at `bottin-client-ui/src/main/resources/static/js/app.js:19-43` into a module both applications serve as classpath static resources, add the dependency to both `bottin-client-ui/pom.xml` and `bottin-admin-ui/pom.xml`, and confirm the client's Vitest suite still resolves them — the specs import by relative path, so the relocation must keep those imports working or be accompanied by updated ones
- [X] T013 Resolve the dead stub at `bottin-client-ui/src/main/resources/static/js/nap-client.js`, whose three methods all throw `"Not implemented - Phase 2"` while the working handshake lives in `APP.napLogin` — either it becomes the shared implementation from T012 or it is deleted; it must not survive describing itself as the NAP client (depends on T012)
- [X] T014 Run `mvn -q verify` and `npm test` in `bottin-client-ui/`, confirming the relocation broke nothing and that the admin module compiles with NAP wired; the dashboard is expected to be unreachable at this point (depends on T011, T013)

**Checkpoint**: NAP is wired, the role decision exists, and `/admin/**` demands a session nobody can yet obtain. US1 closes that.

---

## Phase 3: User Story 1 - First sign-in with a key (Priority: P1) 🎯 MVP

**Goal**: An administrator supplies their nsec and a passphrase once on a device, and reaches the dashboard.

**Independent Test**: Configure a known administrator npub, sign in on a fresh browser with the matching nsec and a passphrase, and confirm every admin page is reachable and the key never appears in a request.

### Tests for User Story 1

- [X] T015 [US1] Write `AdminAccessControlTest` in `bottin-admin-ui/src/test/java/xyz/tcheeric/bottin/admin/controller/AdminAccessControlTest.java` enumerating every route in the table in `contracts/admin-access-contract.md` and asserting each is reachable with a `super-admin` session and redirects without one — written as an enumeration so a route added later without protection fails the suite; confirm it FAILS
- [X] T016 [P] [US1] Write the first-sign-in browser tests in `bottin-admin-ui/src/test/js/admin-signin.test.js` per `contracts/browser-identity.md` — an encrypted identity is stored, the plaintext nsec is absent from storage, and the passphrase appears in no storage key; confirm they FAIL

### Implementation for User Story 1

- [X] T017 [US1] Annotate the admin controllers in `bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/controller/` with `@RequiresPermission` — `admin:read` on the GET handlers of `AdminDashboardController`, `AdminRecordsController`, `AdminDomainsController`, `AdminSettingsController`, and `admin:write` on their POST handlers — per the route table (depends on T015)
- [X] T018 [US1] Replace the username and password fields in `bottin-admin-ui/src/main/resources/templates/admin/login.html:37-59` with the first-sign-in form: nsec and a new passphrase, no account-creation affordance (FR-016)
- [X] T019 [US1] Implement the first-sign-in path in `bottin-admin-ui/src/main/resources/static/js/admin-signin.js` — `buildEncryptedIdentity(nsec, passphrase)`, store it, run the handshake from `contracts/auth-endpoints.md`, redirect to the dashboard on success (depends on T012, T018)
- [X] T020 [US1] Modify `bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/controller/AdminLoginController.java` to render the sign-in page and pass it the state it needs to choose a form — whether an administrator key is configured — leaving the unconfigured wording to US3
- [X] T021 [US1] Run `mvn -q verify -pl bottin-admin-ui -am` and the admin JS suite, confirm both new suites PASS, and commit as `feat(admin-ui): sign in with a Nostr key instead of a password` (depends on T016–T020)

**Checkpoint**: The dashboard is reachable by key. Password sign-in is already gone, since T011 removed it.

---

## Phase 4: User Story 2 - Any other key is refused (Priority: P2)

**Goal**: A key that is not the configured one is refused, told plainly, recorded — and leaves nothing behind on the device.

**Independent Test**: Attempt sign-in with a different key; confirm no session, a message that does not reveal the authorised key, a security log entry, and empty browser storage afterwards.

### Tests for User Story 2

- [X] T022 [US2] Extend `bottin-admin-ui/src/test/java/xyz/tcheeric/bottin/admin/security/ConfiguredAdminAclResolverTest.java` to assert the refusal reason for a non-matching key is `not_authorised` and is distinct from the unconfigured and unusable reasons (FR-006); confirm the new cases FAIL
- [X] T023 [P] [US2] Write the refused-key browser test in `bottin-admin-ui/src/test/js/admin-signin.test.js` asserting that a key refused at `complete` leaves **no** stored identity, so the next visit does not show an unlock prompt guaranteed to fail; confirm it FAILS

### Implementation for User Story 2

- [X] T024 [US2] Add structured security logging to `bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/security/ConfiguredAdminAclResolver.java` — `admin_signin_succeeded` and `admin_signin_rejected` with `reason`, `client_ip`, and the proven `pubkey`, and never key material — per the logging table in `contracts/admin-access-contract.md` (depends on T022)
- [X] T025 [US2] Discard the stored identity before reporting failure in `bottin-admin-ui/src/main/resources/static/js/admin-signin.js`, so a refused key is not left encrypted on the device (depends on T023)
- [X] T026 [US2] Make the refusal message in `bottin-admin-ui/src/main/resources/templates/admin/login.html` state that the key is not authorised without revealing which key would be (US2.3)
- [X] T027 [US2] Run `mvn -q verify -pl bottin-admin-ui -am` and the admin JS suite, confirm PASS, and commit as `feat(admin-ui): refuse and record any key that is not the configured administrator` (depends on T024–T026)

**Checkpoint**: US1 and US2 both work. The change is now safe, not merely different.

**US2 implementation notes**: most of this story was already satisfied by earlier phases — the structured logging (T024) landed with the resolver, the discard-on-refusal and its test (T023, T025) with the sign-in module, and the non-revealing message (T026) with the sign-in page. Audited rather than reimplemented.

Only T022 needed doing, and it needed a different approach than the task assumed. `AclDecision.denied(String reason)` does not carry the reason back — the record holds only `allowed`, `roles`, `permissions` — so the three refusal reasons cannot be asserted through the return value. FR-006 requires the distinction to live in the log, so that is where it is asserted, with a Logback `ListAppender`.

---

## Phase 5: User Story 3 - Unconfigured deployment admits nobody (Priority: P3)

**Goal**: With no administrator key configured, nobody signs in and an operator is told why.

**Independent Test**: Start with `bottin.admin.npub` unset, attempt sign-in with any key, confirm refusal; then set it to a non-key value and confirm a distinct diagnostic.

### Tests for User Story 3

- [X] T028 [US3] Write `AdminKeyConfigurationTest` in `bottin-admin-ui/src/test/java/xyz/tcheeric/bottin/admin/security/AdminKeyConfigurationTest.java` asserting that an absent key and an unusable value each refuse every sign-in and produce distinct reasons, and that neither falls back to admitting anybody (FR-005, FR-006); confirm it FAILS

### Implementation for User Story 3

- [X] T029 [US3] Add startup validation in `bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/security/ConfiguredAdminAclResolver.java` (or a small component beside it) that logs `admin_key_unreadable` or `no_admin_key_configured` once at startup, so a misconfigured deployment is discoverable from the logs rather than only when somebody fails to sign in — data-model.md, Validation (depends on T028)
- [X] T030 [US3] Render the unconfigured and misconfigured states in `bottin-admin-ui/src/main/resources/templates/admin/login.html` — say that no administrator key is set and how to set one, rather than showing a form that cannot succeed (FR-014, US3.2), and distinguish "not configured" from "configured but unusable"
- [X] T031 [US3] Pass the configuration state to the sign-in page from `bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/controller/AdminLoginController.java`, reading the same source the resolver does so the two cannot disagree (depends on T029, T030)
- [X] T032 [US3] Run `mvn -q verify -pl bottin-admin-ui -am`, confirm PASS, and commit as `feat(admin-ui): admit nobody when no administrator key is configured` (depends on T029–T031)

**Checkpoint**: The safe failure direction is guaranteed and diagnosable.

**US3 implementation notes**: as with US2, most of this story was already satisfied. Startup validation logging (T029) landed with the resolver; the unconfigured and unreadable page states (T030) and the controller reading the resolver's own view of configuration (T031) landed with the sign-in page, tested by `AdminLoginControllerTest`. Audited rather than rebuilt.

T028 was written to be additive rather than a rerun of `ConfiguredAdminAclResolverTest`, which already covers the resolver's decisions one at a time. It asserts the *property* instead: a misconfigured deployment refuses **every** key offered — including the one that would have been the administrator — rather than the single key a happy-path test happens to sample. A deployment does not become permissive because its configuration is broken.

---

## Phase 6: User Story 4 - Returning administrator unlocks with a passphrase (Priority: P4)

**Goal**: A device that already holds the key asks only for the passphrase — on return, and when a session expires mid-work.

**Independent Test**: Sign in, let the session expire, confirm the passphrase alone resumes and the nsec is never requested.

### Tests for User Story 4

- [X] T033 [US4] Write the unlock tests in `bottin-admin-ui/src/test/js/admin-signin.test.js` per `contracts/browser-identity.md` — a wrong passphrase is rejected with the stored identity byte-identical afterwards, the right one decrypts to the original key, and **session expiry does not erase the identity** so the passphrase alone resumes; confirm they FAIL

### Implementation for User Story 4

- [X] T034 [US4] Implement the unlock path in `bottin-admin-ui/src/main/resources/static/js/admin-signin.js` — verify against `passwordHash` before attempting decryption, decrypt, re-run the handshake — and never request the nsec while an identity is stored (FR-019, FR-021) (depends on T033)
- [X] T035 [US4] Render the passphrase-only state in `bottin-admin-ui/src/main/resources/templates/admin/login.html`, chosen by whether the browser holds an identity, per the state table in `contracts/admin-access-contract.md`
- [X] T036 [US4] Add the discard-stored-identity affordance to `bottin-admin-ui/src/main/resources/templates/admin/login.html` and `admin-signin.js` for a forgotten passphrase, returning the browser to first sign-in (FR-023, US4.4) (depends on T034)
- [X] T037 [US4] Run the admin JS suite and `mvn -q verify -pl bottin-admin-ui -am`, confirm PASS, and commit as `feat(admin-ui): unlock a stored key with a passphrase` (depends on T034–T036)

**Checkpoint**: The everyday path works. A raw key is handled once per device, not once per session.

---

## Phase 7: User Story 5 - Signing out removes the key from the device (Priority: P5)

**Goal**: Signing out ends the session and erases the stored key, in one action.

**Independent Test**: Sign in, sign out, confirm admin pages redirect **and** that the next sign-in asks for the nsec rather than a passphrase.

### Tests for User Story 5

- [X] T038 [US5] Write the sign-out tests in `bottin-admin-ui/src/test/js/admin-signin.test.js` — storage is empty afterwards and the logout request was sent; storage is **still** emptied when the logout request fails, with the failure reported; and the pair that distinguishes sign-out from expiry, since collapsing the two is the most likely mistake; confirm they FAIL

### Implementation for User Story 5

- [X] T039 [US5] Implement sign-out in `bottin-admin-ui/src/main/resources/static/js/admin-signin.js` — `POST /api/v1/auth/logout` **and** erase the stored identity as one action, erasing even if the request fails, because a key left on a device is the worse outcome and the session expires on its own (FR-022) (depends on T038)
- [X] T040 [US5] Add the sign-out control to `bottin-admin-ui/src/main/resources/templates/fragments/layout.html`, replacing the existing form POST to `/admin/logout` which belonged to the removed form login, and make it prominent enough to be used on a shared machine
- [X] T041 [US5] Run the admin JS suite and `mvn -q verify`, confirm PASS, and commit as `feat(admin-ui): erase the stored key on sign-out` (depends on T039, T040)

**Checkpoint**: All five stories work. "Signed out" means the device holds nothing.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T042 Add a fixed-window rate limiter for `/api/v1/auth/**` local to `bottin-admin-ui`, keyed on client address per research.md R4, logging `admin_signin_rate_limited`; do **not** reuse `bottin-api`'s `RateLimitService`, which now reads its allowance from the settings row and would drag a service dependency into a presentation module
- [X] T043 Confirm `/api/v1/auth/init` issues a challenge for any well-formed npub, not only the configured one, with a test in `bottin-admin-ui/src/test/java/xyz/tcheeric/bottin/admin/controller/AdminAuthEndpointTest.java` — answering differently would tell an anonymous caller which npub administers the deployment (research.md R4)
- [X] T044 Add `BOTTIN_ADMIN_NPUB` to the `bottin-admin` service in `docker-compose.yml` and to `.env`, and remove `BOTTIN_ADMIN_USER` and `BOTTIN_ADMIN_PASSWORD` **from that service only** — `bottin-api` reads `BOTTIN_ADMIN_PASSWORD` for its own HTTP Basic credentials and will start with a random password if it is deleted wholesale (research.md, Scope boundary)
- [X] T045 [P] Write `docs/how-to/configure-admin-access.md` (Diátaxis how-to: configuring the administrator key, first sign-in, what happens when the key or passphrase is lost) and link it from the How-To section of `docs/README.md`
- [X] T046 [P] Update `docs/reference/docker-compose-configuration.md` — `BOTTIN_ADMIN_NPUB` in, the admin password out of the dashboard's row, with a note that `bottin-api` still uses it
- [X] T047 [P] Update `docs/how-to/docker-deployment.md` with the pre-upgrade step: configure the administrator key **before** deploying, or nobody can sign in
- [X] T048 Sweep for dead references with `grep -rn "admin.username\|admin.password\|formLogin\|/admin/logout" --include=*.java --include=*.yml --include=*.html . | grep -v /target/` and resolve every hit outside `docs/superpowers/` and `specs/`
- [X] T049 Run the full [quickstart.md](./quickstart.md) developer verification — the handshake, all five refusal causes, the route-guard enumeration, and the devtools checks that the nsec and passphrase appear in zero requests (depends on T042–T048)
- [X] T050 Bump the project version in the parent `pom.xml` per semantic versioning, add the `CHANGELOG.md` entry noting the breaking configuration change, and run `graphify update .` (depends on T049)
- [X] T051 Create and update the card on the kan `bottin` board with the commit ids and a note, per the `kan-tracking` skill (depends on T050)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies
- **Foundational (Phase 2)**: depends on Setup — **BLOCKS every user story**
- **US1 (Phase 3)**: depends on Foundational
- **US2 (Phase 4)**: depends on Foundational; shares files with US1, so sequence after it
- **US3 (Phase 5)**: depends on Foundational; touches the same login page and controller as US1
- **US4 (Phase 6)**: depends on **US1** — there is nothing to unlock until first sign-in stores something
- **US5 (Phase 7)**: depends on **US1** for the same reason
- **Polish (Phase 8)**: depends on all five stories

### Why these stories are less independent than usual

Unlike feature 004, the stories here converge on three files — `login.html`, `admin-signin.js`, and
`ConfiguredAdminAclResolver`. They are independently *testable* and independently *demonstrable*,
but not independently *editable*, so they should be built in order rather than in parallel by
different people. The dependency graph reflects the files, not the concepts:

```
Setup ─▶ Foundational ─┬─▶ US1 ─┬─▶ US4 ─┐
                       │        └─▶ US5 ─┤
                       ├─▶ US2 ──────────┼─▶ Polish
                       └─▶ US3 ──────────┘
```

### Within Each Story

- Tests are written first and must FAIL before the implementation they cover
- Configuration and resolver before filters; filters before pages; pages before browser behaviour
- Each story ends with a verification task that closes it

### Parallel Opportunities

Fewer than usual, for the reason above:

- **Phase 2**: T003 is independent of everything else in the phase
- **Phase 3**: T016 (browser tests) is independent of T015 (Java tests)
- **Phase 4**: T023 is independent of T022
- **Phase 8**: T045, T046, T047 are three separate documents

---

## Parallel Example: Phase 8 documentation

```bash
Task: "Write docs/how-to/configure-admin-access.md and link it from docs/README.md"
Task: "Update docs/reference/docker-compose-configuration.md"
Task: "Update docs/how-to/docker-deployment.md"
```

---

## Implementation Strategy

### MVP (US1 only)

1. Phase 1: Setup (T001–T002)
2. Phase 2: Foundational (T003–T014) — **blocks everything, and leaves the dashboard unreachable**
3. Phase 3: US1 (T015–T021)
4. **STOP and VALIDATE**: sign in with the configured key, reach every admin page, confirm the key
   appears in no request
5. At this point password sign-in is gone and key sign-in works — deployable, though US2's logging
   and US3's diagnostics are worth having before anyone else operates it

### Incremental Delivery

1. Setup + Foundational → NAP wired, dashboard closed
2. + US1 → **MVP**: the dashboard is reachable by key
3. + US2 → refusals are safe, recorded, and leave nothing behind
4. + US3 → an unconfigured deployment is diagnosable rather than mysterious
5. + US4 → the everyday path stops handling a raw key every session
6. + US5 → signing out is honest
7. + Polish → rate limiting, configuration, documentation

### A note on sequencing

Phase 2 deliberately removes form login before its replacement exists, because Spring Security's
filter chain owns `/admin/**` and would redirect to the form regardless of any NAP session — the
two cannot coexist. The dashboard is therefore unreachable between T011 and T021. That is safe on a
branch and unsafe to deploy, which is why the checkpoint says so explicitly.

---

## Notes

- `[P]` tasks touch different files and depend on nothing incomplete
- Prefer one commit per task or per tight logical group; avoid grouped commits (repo convention)
- `mvn -q verify` must pass before every commit; browser changes also need the admin JS suite
- Verify each test FAILS before writing the implementation it covers
- The single most important invariant: **the private key and the passphrase never reach the
  server.** SC-004 and SC-010 assert zero occurrences in requests and logs, and T049 checks it in
  devtools against the running stack rather than trusting the code
