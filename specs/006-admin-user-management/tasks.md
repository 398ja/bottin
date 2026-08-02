# Tasks: Additional administrators with super-admin and admin roles

**Input**: Design documents from `/specs/006-admin-user-management/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/admin-administrators.md

**Tests**: Included. Principle IV of the constitution requires them, and
`contracts/admin-administrators.md` names the ones that must exist. Per the
project's TDD convention, each story's tests are written **first** and must fail
before its implementation begins.

**Organization**: Grouped by user story so each is independently implementable,
testable, and shippable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel — different files, no dependency on incomplete work
- **[Story]**: The user story the task serves

## Path Conventions

Multi-module Maven, per plan.md: `bottin-core/`, `bottin-persistence/`,
`bottin-service/`, `bottin-admin-ui/`, `bottin-tests/bottin-it/` at the
repository root.

---

## Phase 1: Setup

**Purpose**: The one dependency the rest of the work needs.

- [X] T001 Add the nostr-java dependency to `bottin-service/pom.xml`, using the version already managed in the parent `pom.xml` rather than declaring a new one (research D8)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, domain model, and key canonicalisation.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 Write `bottin-persistence/src/main/resources/db/migration/V5__admin_users_key_based.sql` in the order data-model.md gives: drop index `idx_admin_users_username`, drop column `username`, drop column `password_hash`, add `label VARCHAR(100)`, add `added_by_pubkey VARCHAR(64)`, set `pubkey NOT NULL`, create unique index on `pubkey`
- [X] T003 Verify V5 applies on **both** engines — H2 via the test suite, PostgreSQL against the local container. A statement only one accepts passes `mvn verify` and fails on deploy (data-model.md)
- [X] T004 [P] Reshape `bottin-core/src/main/java/xyz/tcheeric/bottin/core/model/AdminUserData.java`: drop `username`, `passwordHash`, `withPasswordHash()` and `isAdmin()`; add `label` and `addedByPubkey`; replace `createNew` with one taking canonical pubkey, label, and adder
- [X] T005 [P] Add `bottin-core/src/main/java/xyz/tcheeric/bottin/core/exception/AdministratorNotFoundException.java` extending `BottinException`, with an error code, `retryable=false`, and the `{WHAT}. {WHY}. Suggestion: {ACTIONABLE}.` message shape (Principle VI)
- [X] T006 Reshape `bottin-persistence/src/main/java/xyz/tcheeric/bottin/persistence/entity/AdminUserEntity.java` to the new columns, removing the `username` index declaration and the `passwordHash` field
- [X] T007 Rewrite `bottin-persistence/src/main/java/xyz/tcheeric/bottin/persistence/repository/AdminUserRepository.java` with `findByPubkey`, `existsByPubkey`, `findAllByOrderByCreatedAtAsc`, `deleteByPubkey`; delete the `username`-based methods
- [X] T008 [P] Write `bottin-service/src/test/java/xyz/tcheeric/bottin/service/NostrPublicKeysTest.java` first and confirm it fails: npub round-trip, uppercase hex, an npub with a broken checksum, 63- and 65-character strings, empty, and null
- [X] T009 Add `bottin-service/src/main/java/xyz/tcheeric/bottin/service/NostrPublicKeys.java` converting `npub1…` or 64-character hex to canonical lowercase hex via nostr-java `Bech32`, returning `Optional.empty()` rather than null for anything else (Principle VIII)

**Checkpoint**: schema, model, and canonicalisation exist and are tested.

---

## Phase 3: User Story 1 — Granting a colleague access (Priority: P1) 🎯 MVP

**Goal**: The super administrator adds a public key from the settings page, and
its holder signs in and uses the dashboard.

**Independent Test**: Add a key on the settings page, sign in with the matching
private key in a clean browser, and reach every dashboard page.

### Tests for User Story 1 ⚠️ Write first, confirm they fail

- [X] T010 [P] [US1] `bottin-service/src/test/java/xyz/tcheeric/bottin/service/AdminUserServiceTest.java` for `add`: stores a valid key canonically; the npub and hex of one key yield exactly one entry; a non-key is rejected with the offending value named; an already-stored key stores nothing and raises no error; the configured master key stores nothing and raises no error
- [X] T011 [P] [US1] Extend `bottin-admin-ui/src/test/java/xyz/tcheeric/bottin/admin/security/ConfiguredAdminAclResolverTest.java`: a stored administrator is admitted with read and write but **without** manage-admins; an unknown key is refused; a stored row matching the configured key still resolves as super administrator (shadowing, research D7)
- [X] T012 [P] [US1] `bottin-admin-ui/src/test/java/xyz/tcheeric/bottin/admin/controller/AdminAdministratorsControllerTest.java` for the add endpoint: adds and redirects; an invalid value re-renders with the value named; an already-administering key returns no error **and leaves the list byte-identical** (FR-004a — a store-then-delete implementation passes the weaker assertion)

### Implementation for User Story 1

- [X] T013 [US1] Add `bottin-service/src/main/java/xyz/tcheeric/bottin/service/AdminUserService.java` with `list()` and `add(String keyInput, String label, String addedByPubkey)`; `add` canonicalises, rejects a non-key, and returns an outcome distinguishing *added* from *already administers* so the caller can report it (FR-001, FR-002, FR-003, FR-004)
- [X] T014 [US1] In `bottin-service/src/main/java/xyz/tcheeric/bottin/service/AdminUserService.java`, take the configured master key by injecting the same `bottin.admin.npub` property the resolver reads, so `add` can recognise it — one configured value, not two (FR-004a)
- [X] T015 [US1] Extend `bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/security/ConfiguredAdminAclResolver.java`: configured key → `SUPER_ADMIN` with read, write and manage-admins; else an enabled row → `ADMIN` with read and write only; else denied — keeping it the single decision point and preserving the existing log shape (FR-005, FR-013, research D7)
- [X] T016 [P] [US1] Add `bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/dto/AddAdministratorForm.java` with the key and an optional label, length-validated
- [X] T017 [US1] Add `bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/controller/AdminAdministratorsController.java` with `POST /admin/settings/administrators` guarded by `@RequiresPermission(MANAGE_ADMINS)`: success redirects with a success flash, an already-administering key redirects with an **informational** flash, an invalid value re-renders the settings page (contracts)
- [X] T018 [US1] Put `administrators`, `superAdminPubkey` and `canManageAdministrators` on the model in `bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/controller/AdminSettingsController.java`, never null (FR-011, contracts)
- [X] T019 [US1] Add the **Administrators** section to `bottin-admin-ui/src/main/resources/templates/admin/settings.html`: the list with the super administrator marked and offered no remove control, the add form rendered only when the viewer may manage, and the unconfigured and unreadable states saying so (FR-011, FR-014)
- [X] T020 [US1] Log `administrator_added` and `administrator_add_ignored reason=…` with the structured fields data-model.md specifies (FR-010)

**Checkpoint**: a colleague can be granted access and signs in with their own
key. This alone removes the need to share a private key — the MVP.

---

## Phase 4: User Story 2 — Revoking access immediately (Priority: P2)

**Goal**: Removal ends access at once, including a session already open.

**Independent Test**: Sign in as an added administrator in one browser, remove
them from another, and confirm the first can no longer load an admin page.

### Tests for User Story 2 ⚠️ Write first, confirm they fail

- [X] T021 [P] [US2] Extend `bottin-service/src/test/java/xyz/tcheeric/bottin/service/AdminUserServiceTest.java` for `remove`: deletes and revokes as one operation; an absent key raises `AdministratorNotFoundException`; the configured master key is refused; the revoker receives the canonical **hex**, never the npub (research D4)
- [X] T022 [P] [US2] `bottin-admin-ui/src/test/java/xyz/tcheeric/bottin/admin/security/NapAdministratorSessionRevokerTest.java`: passes the hex pubkey to `revokeByPrincipal` and returns the count it reports
- [X] T023 [US2] `bottin-admin-ui/src/test/java/xyz/tcheeric/bottin/admin/AdministratorLifecycleTest.java` proving revocation against a **real session and a real store**: sign in as an added administrator, confirm an admin page loads, remove them, confirm the very next request is refused. Asserting that the service called the revoker proves the call and not the effect, which is where this feature's risk lives (contracts, research D5). *Located in bottin-admin-ui, not bottin-it: that module has no bottin-admin-ui dependency. Named `*Test` rather than `*IT` because this module binds no failsafe execution, so an `*IT` would be silently skipped.*
- [X] T024 [P] [US2] In `AdministratorLifecycleTest`, pin `nap.acl-refresh-interval-seconds=3600` so the cached authorization decision stays stale for the whole test: the second request can then only be refused because the session was ended, never because the ACL was re-resolved (research D5). Mutation-checked — with revocation stubbed to a no-op the removed administrator still loads the dashboard (200) and the test fails.

### Implementation for User Story 2

- [X] T025 [US2] Add the port `bottin-service/src/main/java/xyz/tcheeric/bottin/service/port/AdministratorSessionRevoker.java` — one method returning the number of sessions ended for a canonical hex pubkey (research D4)
- [X] T026 [US2] Add `bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/security/NapAdministratorSessionRevoker.java` implementing the port over nap's `SessionStore.revokeByPrincipal`, passing the **hex** pubkey because the store matches on `principalPubkey` — an npub would revoke nothing while reporting success. Include the `ponytail:` comment naming the single-instance ceiling and the shared-store upgrade path (research D4, D6)
- [X] T027 [US2] Add `remove(String pubkey, String removedByPubkey)` to `bottin-service/src/main/java/xyz/tcheeric/bottin/service/AdminUserService.java`, performing the delete **and** the revocation as one operation so no future caller can do one without the other; refuse an absent key and refuse the configured master key (FR-006, FR-007, FR-009)
- [X] T028 [US2] Add `POST /admin/settings/administrators/{pubkey}/remove` to `bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/controller/AdminAdministratorsController.java`, guarded by `@RequiresPermission(MANAGE_ADMINS)`, redirecting with a flash (contracts)
- [X] T029 [US2] Add the remove control to the administrators section of `bottin-admin-ui/src/main/resources/templates/admin/settings.html`, absent on the super administrator row and for viewers who may not manage
- [X] T030 [US2] In `bottin-service/src/main/java/xyz/tcheeric/bottin/service/AdminUserService.java`, log `administrator_removed` with `sessions_revoked`, so a removal that ended nothing is visible rather than silent (FR-010, research D6)

**Checkpoint**: access can be granted and revoked, and revocation is real rather
than eventual.

---

## Phase 5: User Story 3 — The role boundary holds (Priority: P3)

**Goal**: An added administrator cannot manage administrators, and the refusal is
enforced where the decision is made rather than by a hidden control.

**Independent Test**: Sign in as an added administrator, confirm the controls are
absent, then issue both management requests directly and confirm each is refused.

### Tests for User Story 3 ⚠️ Write first, confirm they fail

- [ ] T031 [P] [US3] In `bottin-admin-ui/src/test/java/xyz/tcheeric/bottin/admin/controller/AdminAdministratorsControllerTest.java`, assert an administrator without manage-admins receives 403 from **both** endpoints when calling them directly — not merely that the buttons are hidden (FR-008)
- [ ] T032 [P] [US3] In `bottin-admin-ui/src/test/java/xyz/tcheeric/bottin/admin/controller/AdminSettingsControllerTest.java`, assert the settings page renders for an added administrator with the list visible but no add form and no remove controls (US3 scenario 1)
- [ ] T033 [P] [US3] In `bottin-admin-ui/src/test/java/xyz/tcheeric/bottin/admin/controller/AdminAdministratorsControllerTest.java`, assert an added administrator cannot promote themselves (US3 scenario 3)
- [ ] T034 [US3] Extend the route-enumeration test in `bottin-admin-ui/src/test/java/xyz/tcheeric/bottin/admin/controller/AdminAccessControlTest.java` to cover the two new routes, so a future management route added without a guard fails the build

### Implementation for User Story 3

- [ ] T035 [US3] Confirm in `bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/security/ConfiguredAdminAclResolver.java` that the added administrator's session never carries `MANAGE_ADMINS` — this falls out of T015 and needs asserting rather than implementing; if a change is required, it belongs in the resolver and nowhere else
- [ ] T036 [US3] In `bottin-admin-ui/src/main/java/xyz/tcheeric/bottin/admin/controller/AdminAdministratorsController.java`, log `administrator_change_rejected reason=not_super_admin` with the attempting administrator (FR-010, data-model.md)

**Checkpoint**: the two roles differ in enforcement, not only in appearance.

---

## Phase 6: User Story 4 — The master key cannot be locked out (Priority: P3)

**Goal**: No route through the interface removes, edits, or demotes the master
key, and no sequence of changes leaves nobody able to sign in.

**Independent Test**: Attempt to remove or demote the master key by every route
the interface offers; each is refused.

### Tests for User Story 4 ⚠️ Write first, confirm they fail

- [ ] T037 [P] [US4] In `bottin-admin-ui/src/test/java/xyz/tcheeric/bottin/admin/controller/AdminAdministratorsControllerTest.java`, assert a direct remove request naming the configured master key is refused (FR-009, US4 scenario 2)
- [ ] T038 [P] [US4] In `bottin-admin-ui/src/test/java/xyz/tcheeric/bottin/admin/security/ConfiguredAdminAclResolverTest.java`, assert the master key holder signs in and reaches the dashboard with the administrator list empty (FR-012)
- [ ] T039 [P] [US4] In `bottin-tests/bottin-it/src/test/java/.../AdministratorLifecycleIT.java`, assert that removing every added administrator leaves the master key working — the lock-out scenario in full (SC-005)
- [ ] T040 [P] [US4] In `bottin-admin-ui/src/test/java/xyz/tcheeric/bottin/admin/controller/AdminSettingsControllerTest.java`, assert the rendered settings markup offers no remove, edit, or demote control on the super administrator row, by assertion rather than by eye (FR-009, US4 scenario 1)

### Implementation for User Story 4

- [ ] T041 [US4] Ensure the master-key guard is in `bottin-service/src/main/java/xyz/tcheeric/bottin/service/AdminUserService.java` rather than only the controller, so a direct request cannot bypass it; most of this story is assertion over behaviour T013–T027 already produce (FR-009, FR-013)

**Checkpoint**: all four stories are independently functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T042 Update `docs/how-to/configure-admin-access.md` for adding and removing administrators, the two roles, that removal is immediate, and the single-instance ceiling on revocation. Correct the existing "One administrator per deployment" statement, which this feature makes false (research D6)
- [ ] T043 [P] Add troubleshooting rows to `docs/how-to/configure-admin-access.md`: an added administrator cannot sign in; `sessions_revoked=0` when a removal should have ended a session; management controls absent because the viewer is not the super administrator
- [ ] T044 Run `mvn -q verify` from the repository root and confirm it passes before committing (Development Workflow)
- [ ] T045 Walk `specs/006-admin-user-management/quickstart.md` against the running local stack in a browser. A green build has hidden a broken admin sign-in three times in this feature's history; this walk-through is the acceptance gate, not the suite
- [ ] T046 Bump the version in the parent `pom.xml` per semantic versioning and record the change in `CHANGELOG.md` (Development Workflow)
- [ ] T047 Run `graphify update .` so the knowledge graph reflects the new and reshaped classes

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies.
- **Foundational (Phase 2)**: depends on Setup — **blocks every user story**.
- **User Story 1 (Phase 3)**: depends on Foundational only. Ships alone.
- **User Story 2 (Phase 4)**: depends on US1 — there must be an administrator to remove.
- **User Story 3 (Phase 5)**: depends on US1 for the role and US2 for the second endpoint it tests.
- **User Story 4 (Phase 6)**: depends on US1 and US2; largely assertion over behaviour they produce.
- **Polish (Phase 7)**: last.

### Within Each User Story

- Tests are written first and must fail before implementation.
- Migration and model before repository; repository before service; service before controller; controller before template.
- The story is complete and independently testable before the next begins.

### Parallel Opportunities

- **Phase 2**: T004 and T005 together (different modules); T008 while T006–T007 proceed.
- **Phase 3**: T010, T011 and T012 together — three test files, no shared state. T016 alongside T013–T015.
- **Phase 4**: T021, T022 and T024 together. T023 must follow T025–T029, since it exercises the finished path.
- **Phase 5 and 6**: almost entirely `[P]` once US1 and US2 land — T031/T032/T033 together, and T037–T040 together.

### Parallel Example: User Story 1 tests

```bash
# Write these three together, then confirm all three fail:
Task: "AdminUserServiceTest for add in bottin-service/src/test/java/.../AdminUserServiceTest.java"
Task: "ConfiguredAdminAclResolverTest additions in bottin-admin-ui/src/test/java/.../ConfiguredAdminAclResolverTest.java"
Task: "AdminAdministratorsControllerTest for the add endpoint in bottin-admin-ui/src/test/java/.../AdminAdministratorsControllerTest.java"
```

---

## Requirements Coverage

| Requirement | Tasks |
|---|---|
| FR-001 add by key | T013, T017, T019 |
| FR-002 npub or hex, one administrator | T009, T010, T013 |
| FR-003 refuse a non-key, naming it | T010, T013, T017 |
| FR-004 / FR-004a already administers → no-op, no entry | T010, T012, T013, T014 |
| FR-005 added administrator reaches the dashboard | T011, T015 |
| FR-006 remove | T027, T028, T029 |
| FR-007 removal ends the session at once | T021, T023, T024, T025, T026, T027 |
| FR-008 refuse non-super-admin, called directly | T031, T034, T035 |
| FR-009 master key not removable, editable, demotable | T037, T040, T041 |
| FR-010 security log | T020, T030, T036 |
| FR-011 list, master distinguished | T018, T019 |
| FR-012 master admitted regardless of the list | T038 |
| FR-013 master authority stays in configuration | T015, T041 |
| FR-014 readable label | T016, T019 |
| SC-003 no window after removal | T023, T024 |
| SC-005 nobody can be locked out | T039 |

---

## Implementation Strategy

### MVP first (User Story 1 only)

1. Phase 1: Setup.
2. Phase 2: Foundational — blocks everything.
3. Phase 3: User Story 1.
4. **Stop and validate**: a colleague signs in with their own key.
5. That is the whole reason the feature exists; it is demonstrable here.

### Incremental delivery

US1 → US2 → US3 → US4, each independently testable. **Do not leave US2 far
behind US1**: granting access without working revocation is the worse half to
ship alone, and it is the half that matters under pressure.

### The two tasks not to let slide

**T023** — revocation proven against a real session rather than a mock that
records the call. **T045** — the browser walk-through. Every defect this
feature's predecessor shipped was invisible to a green build.

---

## Notes

- `[P]` means different files with no dependency on incomplete work.
- `[Story]` maps each task to a user story for traceability.
- Commit after each task or logical group; prefer several small commits.
- Confirm tests fail before implementing them away.
- Avoid: cross-story dependencies that break independence, and tasks that touch the same file in parallel.
