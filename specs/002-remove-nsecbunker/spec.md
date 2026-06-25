# Feature Specification: Remove nsecbunker Dependency

**Feature Branch**: `002-remove-nsecbunker`  
**Created**: 2026-06-25  
**Status**: Draft  
**Input**: User description: "I would like to remove the nsecbunker dependency, as it's not used at the moment."

## Context

The project currently declares the `nsecbunker-java` dependency (version 0.1.0, via the
`nsecbunker-account` and `nsecbunker-core` artifacts) and provides an integration layer in
`bottin-spring-boot-starter` that supplies bottin's persistent implementations to nsecbunker-java
through its SPI. This integration is wired (auto-configured beans + SPI providers) but the
nsecbunker key-management capability is not an active, exercised feature of the deployed registry.
The dependency also transitively pulls a deprecated `nostr-java` 1.x onto the classpath, which
conflicts with the `nostr-java` 2.x the profile-reach feature (`001-nostr-profile-reach`) needs.

The goal is to remove the nsecbunker dependency and its integration cleanly, leaving the registry's
core capabilities (NIP-05 record management, domain verification, `.well-known` serving) unchanged.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Build and run without nsecbunker (Priority: P1)

A maintainer builds and runs bottin after the dependency is removed. The application compiles,
starts, and serves all existing endpoints exactly as before, with no reference to nsecbunker
remaining in the dependency graph.

**Why this priority**: This is the whole objective — a clean removal that preserves every existing
capability. If the build or runtime behavior regresses, the removal has failed.

**Independent Test**: Run a full build and start the application; confirm success, confirm the
dependency graph contains no `nsecbunker-*` (and no deprecated `nostr-java` 1.x pulled by it), and
confirm the existing NIP-05/domain/well-known endpoints behave identically.

**Acceptance Scenarios**:

1. **Given** the nsecbunker dependency and its integration are removed, **When** a full build runs, **Then** the build succeeds with no compilation errors and no references to nsecbunker types.
2. **Given** the application starts after removal, **When** the existing NIP-05 record, domain, well-known, and external-verification endpoints are exercised, **Then** they return the same results as before removal.
3. **Given** the build has completed, **When** the resolved dependency tree is inspected, **Then** no `nsecbunker-*` artifact and no `nostr-java` 1.x appear.

---

### User Story 2 - Documentation reflects the removal (Priority: P2)

A reader of the project documentation finds no stale references implying nsecbunker is an active
capability. Any historical mention is either removed or clearly marked as no longer part of the
project.

**Why this priority**: Leaving documentation that advertises a removed integration misleads users
and contributors. It is important but secondary to the code/build change.

**Independent Test**: Search the documentation set for nsecbunker references; confirm each remaining
reference is either removed or explicitly describes the removal (e.g. a changelog entry).

**Acceptance Scenarios**:

1. **Given** the removal is complete, **When** the README, architecture explanation, and how-to guides are reviewed, **Then** they no longer describe nsecbunker as a current capability.
2. **Given** the change is released, **When** the changelog is reviewed, **Then** it records the removal of the nsecbunker integration.

---

### Edge Cases

- **Downstream consumer relies on the SPI**: If any external application embeds the
  `bottin-spring-boot-starter` and depends on the nsecbunker SPI providers, removal is a breaking
  change for that consumer. This must be surfaced and versioned accordingly (see Assumptions).
- **E2E test infrastructure**: The end-to-end test suite starts an `nsecbunkerd` container and
  includes an nsecbunker integration test; both must be removed without breaking the remaining E2E
  tests or their shared configuration.
- **Transitive-only consumers**: Any code relying on a type that arrived only transitively through
  nsecbunker (e.g. the deprecated `nostr-java` 1.x) must be migrated to an explicitly declared
  dependency before removal.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The project MUST no longer declare the `nsecbunker-java` dependency (the
  `nsecbunker-account` / `nsecbunker-core` artifacts and the associated version property) in any
  module.
- **FR-002**: The nsecbunker integration code in `bottin-spring-boot-starter` (the persistent
  manager implementations, the SPI providers, and their auto-configuration wiring) MUST be removed.
- **FR-003**: The end-to-end test assets specific to nsecbunker (the integration test and the
  `nsecbunkerd` container setup) MUST be removed, and the remaining E2E tests MUST continue to pass.
- **FR-004**: Removal MUST NOT change the behavior of the registry's existing capabilities — NIP-05
  record management, domain verification, and `.well-known/nostr.json` serving MUST be unchanged.
- **FR-005**: After removal, the resolved dependency graph MUST contain no `nsecbunker-*` artifact
  and no deprecated `nostr-java` 1.x that was introduced transitively through nsecbunker.
- **FR-006**: Project documentation MUST be updated so no document describes nsecbunker as a current
  capability, and the changelog MUST record the removal.
- **FR-007**: The full build and test suite MUST pass after removal.
- **FR-008**: If removal changes the public surface of the published starter, the project version
  MUST be bumped according to semantic-versioning rules for a breaking change.

### Key Entities

Not applicable — this feature removes code and configuration; it introduces no new data entities.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The full build and test suite passes with zero references to nsecbunker types in the
  codebase.
- **SC-002**: The resolved dependency tree contains zero `nsecbunker-*` artifacts and zero
  `nostr-java` 1.x entries.
- **SC-003**: All existing NIP-05, domain, well-known, and external-verification behaviors produce
  identical results before and after the change (verified by the existing test suite continuing to
  pass unchanged, aside from the removed nsecbunker-specific tests).
- **SC-004**: No documentation page presents nsecbunker as an active capability; the changelog
  contains a removal entry.

## Assumptions

- **No active runtime dependency**: The nsecbunker key-management capability is not an exercised
  feature of the deployed registry; the SPI providers are wired but not relied upon by any current
  production consumer. Removal is therefore safe for the project's own use.
- **Breaking change for external embedders**: Removing the starter's nsecbunker SPI providers is a
  breaking change for any external application that embeds the starter and consumes those providers.
  No such consumer is known within this repository; if one exists externally, the version bump
  (FR-008) and changelog entry (FR-006) communicate the break.
- **Relationship to 001**: This removal eliminates the transitive deprecated `nostr-java` 1.x that
  `001-nostr-profile-reach` would otherwise have to exclude. The two efforts are independent: until
  this removal lands, `001` resolves the conflict by explicitly pinning `nostr-java` 2.x and
  excluding the transitive 1.x; once this removal lands, that exclusion becomes unnecessary.
- **Scope boundary**: This feature only removes the nsecbunker dependency and its integration. It
  does not add a replacement key-management mechanism; if signing/key-management is needed in the
  future, it will be specified separately.
