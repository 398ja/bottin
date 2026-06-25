# Tasks: Remove nsecbunker Dependency

**Input**: Design documents from `/specs/002-remove-nsecbunker/`
**Prerequisites**: plan.md, spec.md, research.md, quickstart.md

**Tests**: No new tests are requested. This is a removal feature; correctness is verified by the
existing test suite continuing to pass (minus the deleted nsecbunker-specific test) plus the
`mvn dependency:tree` and endpoint-parity checks from quickstart.md.

**Organization**: Tasks are grouped by user story. US1 delivers the actual removal (build/run clean);
US2 delivers documentation alignment.

**Commit discipline**: Per project convention, commit in small conventional-commit units (one per
task or tightly-related pair) — `refactor(starter):`, `chore(deps):`, `test(e2e):`, `docs:`,
`chore(release):`. Avoid grouped commits.

## Path Conventions

Multi-module Maven web service. Absolute paths shown from repo root
`/home/eric/IdeaProjects/bottin/`.

---

## Phase 1: Setup

- [X] T001 Establish a green baseline: run `mvn -q verify` on branch `002-remove-nsecbunker` and record the result so post-removal behavior can be compared.
- [X] T002 [P] Capture the current nsecbunker removal surface for reference: run the grep/`dependency:tree` inventory commands from `specs/002-remove-nsecbunker/quickstart.md` (§1, §2) and confirm they match the files listed in `plan.md`.

---

## Phase 2: Foundational

No foundational/blocking tasks. The removal is self-contained within User Story 1, and User Story 2
(documentation) is independent. Proceed to Phase 3.

---

## Phase 3: User Story 1 - Build and run without nsecbunker (Priority: P1) 🎯 MVP

**Goal**: Remove all nsecbunker code, test assets, and dependency declarations so the project builds,
runs, and resolves with no `nsecbunker-*` (and no transitive `nostr-java` 1.x), while every existing
registry behavior is unchanged.

**Independent Test**: `mvn -q verify` passes; `mvn dependency:tree | grep -iE "nsecbunker|nostr-java.*:1\."`
returns nothing; the `.well-known/nostr.json`, `/api/v1/records`, and `/api/v1/verify` endpoints
return identical results to the baseline.

### Source refactor (order matters — keep the module compilable at each step)

- [X] T003 [US1] Refactor `bottin-spring-boot-starter/src/main/java/xyz/tcheeric/bottin/starter/BottinAutoConfiguration.java`: remove the four nsecbunker `@Bean` methods (`persistentNip05Manager`, `persistentAccountManager`, `bottinNip05ManagerProvider`, `bottinAccountManagerProvider`); remove the imports of `xyz.tcheeric.nsecbunker.account.nip05.Nip05Manager` and `xyz.tcheeric.nsecbunker.account.registration.AccountManager`; replace `@ConditionalOnClass(Nip05Manager.class)` with `@ConditionalOnClass(xyz.tcheeric.bottin.persistence.repository.Nip05RecordRepository.class)`; keep `@EnableConfigurationProperties(BottinProperties.class)`, `@ComponentScan`, `@ConditionalOnProperty(bottin.enabled)`, and the `objectMapper()` bean; update the class Javadoc to drop the "Integration with nsecbunker-java via SPI providers" bullet. (After this, the class no longer references the Persistent*/Provider types.)
- [X] T004 [P] [US1] Delete `bottin-spring-boot-starter/src/main/java/xyz/tcheeric/bottin/starter/PersistentNip05Manager.java`.
- [X] T005 [P] [US1] Delete `bottin-spring-boot-starter/src/main/java/xyz/tcheeric/bottin/starter/PersistentAccountManager.java`.
- [X] T006 [P] [US1] Delete `bottin-spring-boot-starter/src/main/java/xyz/tcheeric/bottin/starter/BottinNip05ManagerProvider.java`.
- [X] T007 [P] [US1] Delete `bottin-spring-boot-starter/src/main/java/xyz/tcheeric/bottin/starter/BottinAccountManagerProvider.java`.

### E2E test cleanup

- [X] T008 [US1] Delete `bottin-tests/bottin-e2e/src/test/java/xyz/tcheeric/bottin/e2e/NsecbunkerIntegrationE2ETest.java`.
- [X] T009 [US1] Edit `bottin-tests/bottin-e2e/src/test/java/xyz/tcheeric/bottin/e2e/BaseE2ETest.java`: remove the `@Autowired protected GenericContainer<?> nsecbunkerdContainer;` field and the `getNsecbunkerdUrl()` method; keep the `strfryContainer` field, `getRelayUrl()`, and all other setup. Update the class Javadoc ("PostgreSQL, nsecbunkerd, and strfry containers" → drop nsecbunkerd).
- [X] T010 [US1] Edit `bottin-tests/bottin-e2e/src/test/java/xyz/tcheeric/bottin/e2e/TestContainersConfig.java`: remove the `nsecbunkerdContainer()` `@Bean` method (and its `docker.398ja.xyz/nsecbunkerd` image reference); keep `postgresContainer()`, `strfryContainer()`, and `testNetwork()`. Update the class Javadoc accordingly.

### Dependency declarations (remove child usages before the parent property)

- [X] T011 [US1] Remove the `nsecbunker-account` `<dependency>` block from `bottin-spring-boot-starter/pom.xml` (the block at the "nsecbunker-java integration" comment).
- [X] T012 [P] [US1] Remove the `nsecbunker-account` test `<dependency>` block from `bottin-tests/bottin-e2e/pom.xml` (the block at the "nsecbunker-java for integration testing" comment).
- [X] T013 [US1] Remove the `<nsecbunker-java.version>0.1.0</nsecbunker-java.version>` property and the `nsecbunker-account` + `nsecbunker-core` `<dependencyManagement>` entries from the parent `pom.xml` (after T011 and T012, so no reference to `${nsecbunker-java.version}` remains).

### Verification (US1 acceptance)

- [X] T014 [US1] Confirm the dependency graph is clean: `mvn dependency:tree | grep -iE "nsecbunker|nostr-java.*:1\."` returns nothing.
- [X] T015 [US1] Run `mvn -q verify` from repo root: full build and all remaining tests pass (the only removed test is `NsecbunkerIntegrationE2ETest`). Capture output for the PR.
- [X] T016 [US1] Endpoint-parity check per `quickstart.md` §4: start the app and confirm `.well-known/nostr.json`, `/api/v1/records`, and `/api/v1/verify` behave identically to the T001 baseline (FR-004 / SC-003).

**Checkpoint**: At this point the project is fully free of nsecbunker in code, tests, and the
dependency graph, with unchanged registry behavior — US1 is independently complete and shippable.

---

## Phase 4: User Story 2 - Documentation reflects the removal (Priority: P2)

**Goal**: No project document presents nsecbunker as a current capability, and the changelog records
the removal.

**Independent Test**: `grep -rin "nsecbunker" README.md docs/` surfaces no text describing nsecbunker
as a current capability; `CHANGELOG.md` contains a removal entry.

- [X] T017 [P] [US2] Update `README.md`: remove or correct any text presenting nsecbunker-java integration as a current capability of bottin.
- [X] T018 [P] [US2] Update `docs/explanation/architecture.md`: remove the nsecbunker integration / SPI-provider description from the architecture explanation.
- [X] T019 [P] [US2] Update `docs/how-to/running-e2e-tests.md`: remove instructions/prerequisites referencing the `nsecbunkerd` container.
- [X] T020 [US2] Add a `CHANGELOG.md` entry recording the removal of the nsecbunker-java integration, explicitly noting it is a breaking change for any external embedder of `bottin-spring-boot-starter` that consumed the nsecbunker SPI beans (FR-006 / FR-008).
- [X] T021 [US2] Verify documentation per `quickstart.md` §6: `grep -rin "nsecbunker" README.md docs/` shows only removal-context references (or none), and the `CHANGELOG.md` entry is present.

**Checkpoint**: Documentation and changelog are aligned with the removal.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T022 Bump the project version from `0.2.1` to `0.3.0` in the parent `pom.xml` (breaking change to the published starter under SemVer 0.x → MINOR), per `quickstart.md` §7 and the constitution's versioning rule.
- [X] T023 Final confirmation: re-run `mvn -q verify` and `mvn dependency:tree` after the version bump; update task status + commit id(s) in the project tracking per repo convention.

---

## Dependencies & Execution Order

- **Setup (Phase 1)** → before everything. T001 baseline first; T002 [P] alongside.
- **US1 (Phase 3)** is the MVP and the only hard prerequisite for shipping.
  - T003 (auto-config refactor) MUST precede T004–T007 (class deletions) so the starter keeps compiling.
  - T008 → T009 → T010 (delete the test before stripping the base-class helper it used, then the container bean).
  - T011 and T012 [P] (child pom edits) MUST precede T013 (parent property/dependencyManagement removal).
  - T014–T016 (verification) run after all edits.
- **US2 (Phase 4)** depends only on the *decision* to remove (it can technically start any time) but should follow US1 so docs match reality. T017–T019 are [P] (different files); T020 then T021.
- **Polish (Phase 5)** runs last; T022 before T023.

## Parallel Execution Examples

- After T003: run T004, T005, T006, T007 together (four independent file deletions).
- T011 and T012 can run together (different POMs).
- In US2: run T017, T018, T019 together (three independent docs).

## Implementation Strategy

- **MVP = User Story 1 only.** Completing Phases 1 + 3 yields a project with nsecbunker fully removed
  and behavior preserved — independently shippable.
- **Incremental delivery**: ship US1, then layer US2 (docs) and the Phase 5 version bump.
- This removal also unblocks feature `001-nostr-profile-reach` by eliminating the transitive
  deprecated `nostr-java` 1.x, after which 001 no longer needs its exclusion workaround.

## Verification Notes (implementation run, 2026-06-25)

- **`mvn -q verify` (default gate, unit tests): PASS** before (T001) and after (T015, T023) the change.
- **Dependency tree (T014): clean** — no `nsecbunker-*` and no `nostr-java` at all (the transitive 1.x is gone).
- **No `nsecbunker` references** remain in source, POMs, or docs (only the CHANGELOG removal entry).
- **T016 caveat**: automated endpoint parity via the `it`/`e2e` profiles could not serve as the gate —
  `mvn -P it verify` fails on a **pre-existing** `BeanDefinitionOverrideException` (duplicate
  `@EnableJpaRepositories` between `TestApplication` and `BottinWebApplication`). This was confirmed by
  stashing all changes and reproducing the identical failure on the pristine tree, so it is **not a
  regression from this removal** and is **out of scope** for it. Parity confidence rests on: the default
  unit suite passing, the clean dependency tree, and the fact that the deployed `bottin-web` runtime path
  (NIP-05 serving via `Nip05RecordService`/`WellKnownController`) is untouched. **Recommend a separate
  ticket** to fix the IT/E2E duplicate-repository wiring.
- **Commits**: not yet made — awaiting maintainer approval (see project commit-discipline convention).
