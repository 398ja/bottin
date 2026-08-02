# Tasks: Additional administrators with super-admin and admin roles

**Input**: Design documents from `/specs/006-admin-user-management/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/admin-administrators.md

**Tests**: Included. The constitution (Principle IV) requires them, and
contracts/admin-administrators.md names the ones that must exist.

**Organization**: Grouped by user story, so each is independently implementable
and testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story the task serves

---

## Phase 1: Setup

**Purpose**: Dependencies the rest of the work needs.

- [ ] T001 Add the nostr-java dependency to `bottin-service/pom.xml`, matching the version already pinned in the parent `pom.xml` rather than declaring a new one (research D8)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, model, and canonicalisation. Every user story depends on
these; none may start until they are done.

- [ ] T002 Write `bottin-persistence/src/main/resources/db/migration/V5__admin_users_key_based.sql` in the order given in data-model.md: drop `idx_admin_users_username`, drop `username`, drop `password_hash`, add `label VARCHAR(100)`, add `added_by_pubkey VARCHAR(64)`, set `pubkey NOT NULL`, create unique index on `pubkey`
- [ ] T003 Verify V5 applies on **both** H2 and PostgreSQL — run the test suite for H2 and apply it against the local PostgreSQL container for production parity. A statement only one engine accepts passes `mvn verify` and fails on deploy (data-model.md)
- [ ] T004 [P] Reshape `bottin-core/src/main/java/xyz/tcheeric/bottin/core/model/AdminUserData.java`: remove `username`, `passwordHash`, `withPasswordHash`, and `isAdmin`; add `label` and `addedByPubkey`; replace `createNew` with one taking canonical pubkey, label, and adder
- [ ] T005 [P] Add `bottin-core/src/main/java/xyz/tcheeric/bottin/core/exception/AdministratorNotFoundException.java` extending `BottinException`, with the `{WHAT}. {WHY}. Suggestion: {ACTIONABLE}.` message shape (Principle VI)
- [ ] T006 Reshape `bottin-persistence/src/main/java/xyz/tcheeric/bottin/persistence/entity/AdminUserEntity.java` to the new columns, dropping the `username` index declaration and the `password_hash` field
- [ ] T007 Rewrite `bottin-persistence/src/main/java/xyz/tcheeric/bottin/persistence/repository/AdminUserRepository.java`: `findByPubkey`, `existsByPubkey`, `findAllByOrderByCreatedAtAsc`, `deleteByPubkey`; delete the `username`-based methods
- [ ] T008 Add `bottin-service/src/main/java/xyz/tcheeric/bottin/service/NostrPublicKeys.java` converting `npub1…` or 64-char hex to canonical lowercase hex using nostr-java `Bech32`, returning empty rather than null for a value that is neither (Principle VIII)
- [ ] T009 [P] Unit-test `NostrPublicKeys` in `bottin-service/src/test/java/.../NostrPublicKeysTest.java`: npub round-trip, uppercase hex, mixed case, an npub with a bad checksum, a 63- and 65-character string, empty, and null
- [ ] T010 Add the port `bottin-service/src/main/java/xyz/tcheeric/bottin/service/port/AdministratorSessionRevoker.java` — one method returning the number of sessions ended for a canonical hex pubkey (research D4)
- [ ] T011 Add `bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/security/NapAdministratorSessionRevoker.java` implementing the port over nap's `SessionStore.revokeByPrincipal`, passing the **hex** pubkey — it matches on `principalPubkey`, so an npub would revoke nothing while reporting success (research D4). Include the `ponytail:` comment naming the single-instance ceiling and the shared-store upgrade path (research D6)

**Checkpoint**: schema, model, and canonicalisation exist and are tested.

---

## Phase 3: User Story 1 — Granting a colleague access (Priority: P1) 🎯 MVP

**Goal**: The super administrator adds a public key; its holder signs in and uses
the dashboard.

**Independent Test**: Add a key on the settings page, sign in with the matching
private key in a clean browser, reach every dashboard page.

- [ ] T012 [US1] Add `bottin-service/src/main/java/xyz/tcheeric/bottin/service/AdminUserService.java` with `add(String keyInput, String label, String addedByPubkey)` and `list()`. `add` canonicalises, rejects a non-key, and treats an already-administering key as a no-op returning an outcome the caller can report — it MUST NOT store then delete (FR-004, FR-004a)
- [ ] T013 [US1] Teach `AdminUserService` the configured master key so `add` can recognise it, injecting the same `bottin.admin.npub` value the resolver reads — one configured value, not two
- [ ] T014 [P] [US1] Unit-test `AdminUserService.add` in `bottin-service/src/test/java/.../AdminUserServiceTest.java`: stores a valid key canonically; npub and hex of one key yield one entry; a non-key is rejected with the value named; an existing key is a no-op that stores nothing; the master key is a no-op that stores nothing
- [ ] T015 [US1] Extend `bottin-admin-ui/.../security/ConfiguredAdminAclResolver.java`: configured key → `SUPER_ADMIN` with read, write, and manage-admins; else an enabled row → `ADMIN` with read and write only; else denied. Keep it the single decision point and keep the existing logging shape (research D7)
- [ ] T016 [P] [US1] Extend `bottin-admin-ui/src/test/java/.../ConfiguredAdminAclResolverTest.java`: a stored administrator is admitted without manage-admins; an unknown key is refused; a stored row matching the configured key still resolves as super administrator (shadowing, research D7)
- [ ] T017 [US1] Add `bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/dto/AddAdministratorForm.java` with the key and optional label, validated for length
- [ ] T018 [US1] Add `bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/controller/AdminAdministratorsController.java` with `POST /admin/settings/administrators` guarded by `@RequiresPermission(MANAGE_ADMINS)`; success redirects with a flash, an already-administering key redirects with an **informational** flash, an invalid value re-renders the settings page with the value named
- [ ] T019 [US1] Put the administrator list, the configured super-admin key, and `canManageAdministrators` on the model in `bottin-admin-ui/.../controller/AdminSettingsController.java`, never null (contracts)
- [ ] T020 [US1] Add the **Administrators** section to `bottin-admin-ui/src/main/resources/templates/admin/settings.html`: the list with the super administrator marked and un-removable, the add form shown only when the viewer may manage, and the unconfigured and unreadable states saying so
- [ ] T021 [P] [US1] Test the add endpoint in `bottin-admin-ui/src/test/java/.../AdminAdministratorsControllerTest.java`: adds and redirects; invalid value re-renders with the value named; an already-administering key returns no error **and leaves the list unchanged** (FR-004a — a store-then-delete implementation passes the weaker assertion)
- [ ] T022 [US1] Log `administrator_added` and `administrator_add_ignored reason=…` with structured fields per data-model.md

**Checkpoint**: a colleague can be granted access and can sign in. This alone is
the MVP — it removes the need to share a private key.

---

## Phase 4: User Story 2 — Revoking access immediately (Priority: P2)

**Goal**: Removal ends access at once, including a session already open.

**Independent Test**: Sign in as an added administrator in one browser, remove
them from another, confirm the first can no longer load an admin page.

- [ ] T023 [US2] Add `remove(String pubkey, String removedByPubkey)` to `AdminUserService`, performing the delete **and** the revocation as one operation so no future caller can do one without the other (research D4). Refuse removal of an absent key with `AdministratorNotFoundException`, and refuse removal of the configured master key
- [ ] T024 [P] [US2] Unit-test `AdminUserService.remove`: deletes and revokes; an absent key raises not-found; the master key is refused; the revoker is called with the canonical **hex**
- [ ] T025 [US2] Add `POST /admin/settings/administrators/{pubkey}/remove` to `AdminAdministratorsController`, guarded by `@RequiresPermission(MANAGE_ADMINS)`, redirecting with a flash
- [ ] T026 [US2] Add the remove control to the administrators section of `settings.html`, absent for the super administrator row and for viewers who may not manage
- [ ] T027 [US2] Log `administrator_removed` with `sessions_revoked`, so a removal that ended nothing is visible (data-model.md, research D6)
- [ ] T028 [US2] Add `bottin-tests/bottin-it/src/test/java/.../AdministratorLifecycleIT.java` proving revocation against a **real session and a real store**: sign in as an added administrator, confirm an admin page loads, remove them, confirm the very next request is refused. Asserting that the service called the revoker proves the call and not the effect, which is where this feature's risk lives (contracts, research D5)
- [ ] T029 [P] [US2] Add a test that removal does not wait for the ACL refresh interval — the removed administrator is refused immediately, not after the cache expires (research D5)

**Checkpoint**: access can be granted and revoked, and revocation is real.

---

## Phase 5: User Story 3 — The role boundary holds (Priority: P3)

**Goal**: An added administrator cannot manage administrators, and the refusal
is enforced where the decision is made rather than by a hidden control.

**Independent Test**: Sign in as an added administrator, confirm the controls are
absent, then issue both management requests directly and confirm 403.

- [ ] T030 [US3] Confirm the added administrator's session never carries `MANAGE_ADMINS` — this falls out of T015 and needs asserting, not implementing
- [ ] T031 [P] [US3] Test in `AdminAdministratorsControllerTest` that an administrator without manage-admins gets 403 from **both** endpoints when calling them directly, not merely that the buttons are hidden (FR-008)
- [ ] T032 [P] [US3] Test that the settings page renders for an added administrator with the list visible but no add form and no remove controls
- [ ] T033 [US3] Extend the route-enumeration test in `bottin-admin-ui/src/test/java/.../AdminAccessControlTest.java` to cover the two new routes, so a future management route added without a guard fails the build
- [ ] T034 [US3] Log `administrator_change_rejected reason=not_super_admin` with the attempting administrator (data-model.md)

**Checkpoint**: the two roles differ in enforcement, not only in appearance.

---

## Phase 6: User Story 4 — The master key cannot be locked out (Priority: P3)

**Goal**: No route through the interface removes, edits, or demotes the master
key, and no sequence of changes leaves nobody able to sign in.

**Independent Test**: Attempt to remove or demote the master key by every route
the interface offers; each is refused.

- [ ] T035 [P] [US4] Test that a direct remove request naming the configured master key is refused (FR-009)
- [ ] T036 [P] [US4] Test that the master key holder still signs in and reaches the dashboard with the administrator list empty (FR-012)
- [ ] T037 [P] [US4] Test that removing every added administrator leaves the master key working — the "locked out" scenario in full (SC-005)
- [ ] T038 [US4] Confirm the settings page offers no remove, edit, or demote control on the super administrator row, by assertion on the rendered markup rather than by inspection

---

## Phase 7: Polish & Documentation

- [ ] T039 Update `docs/how-to/configure-admin-access.md`: adding and removing administrators, the two roles, that removal is immediate, and the single-instance ceiling on revocation (research D6). Correct the existing "One administrator per deployment" statement, which this feature makes false
- [ ] T040 [P] Add the troubleshooting rows: an added administrator cannot sign in, `sessions_revoked=0` when a removal should have ended a session, and the management controls being absent because the viewer is not the super administrator
- [ ] T041 Run `mvn -q verify` from the repository root and confirm it passes before committing (Development Workflow)
- [ ] T042 Walk `specs/006-admin-user-management/quickstart.md` against the running local stack. A green build has hidden a broken admin sign-in three times in this feature's history; the browser check is the acceptance gate, not the suite
- [ ] T043 Bump the project version in the parent `pom.xml` per semantic versioning and record the change in `CHANGELOG.md` (Development Workflow)
- [ ] T044 Run `graphify update .` so the knowledge graph reflects the new and reshaped classes

---

## Dependencies

- **Phase 1 → Phase 2 → everything.** No user story may begin before T011.
- **US1 (Phase 3)** depends only on Foundational. It is the MVP and ships alone.
- **US2 (Phase 4)** depends on US1 — there must be an administrator to remove.
- **US3 (Phase 5)** depends on US1 for the role, and on US2 for the second endpoint it tests.
- **US4 (Phase 6)** depends on US1 and US2; it is mostly assertion over behaviour those two produce.
- **Phase 7** last.

## Parallel Opportunities

- T004 and T005 (different modules, different files).
- T009 alongside T010–T011.
- Within US1: T014 and T016 while T017–T020 proceed.
- Within US2: T024 alongside T025–T027; T028 must follow them.
- US3 and US4 are almost entirely `[P]` tests once US1 and US2 land.

## Implementation Strategy

**MVP is Phase 1–3.** At that point a second person administers the deployment
with their own key, which is the whole reason the feature exists. Phase 4 is
what makes it safe to rely on, and should not be deferred far behind it: granting
access without a working revocation is the worse half to ship alone.

**The two tasks to not let slide**: T028 (revocation against a real session) and
T042 (the browser walk-through). Every defect this feature's predecessor shipped
was invisible to a green build.
