# Implementation Plan: Remove nsecbunker Dependency

**Branch**: `002-remove-nsecbunker` | **Date**: 2026-06-25 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/002-remove-nsecbunker/spec.md`

## Summary

Remove the `nsecbunker-java` dependency and its SPI integration from bottin without changing any
existing registry behavior. The integration lives entirely in `bottin-spring-boot-starter` (a set of
persistent-manager classes + SPI providers wired by `BottinAutoConfiguration`) and in the
end-to-end test module (`bottin-tests/bottin-e2e`, which starts an `nsecbunkerd` container and has a
dedicated integration test). The deployed registry (`bottin-web`) does **not** depend on the starter,
so the integration is dormant at runtime — confirming removal is safe for the project's own use.

**Technical approach**: A surgical removal across three areas — (1) delete the four nsecbunker
integration classes and refactor `BottinAutoConfiguration` to drop the nsecbunker beans and its
`@ConditionalOnClass(Nip05Manager.class)` gate while preserving `BottinProperties`, the
`@ComponentScan`, and the `ObjectMapper` bean; (2) remove the nsecbunker pieces from the e2e module
(the `nsecbunkerd` container bean, its `BaseE2ETest` wiring, and the `NsecbunkerIntegrationE2ETest`)
while keeping every other e2e test green; (3) remove the dependency declarations from the parent,
starter, and e2e POMs, and update documentation + changelog. The full `mvn -q verify` gate and a
`mvn dependency:tree` check (no `nsecbunker-*`, no transitive `nostr-java` 1.x) confirm success.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 3.4.1 (auto-configuration, Data JPA), Maven multi-module build;
removing `nsecbunker-java` 0.1.0 (`nsecbunker-account`, `nsecbunker-core`)
**Storage**: PostgreSQL / H2 (unchanged; no schema change in this feature)
**Testing**: JUnit 5, Mockito, Testcontainers (e2e); existing suites must pass unchanged aside from
the removed nsecbunker-specific test
**Target Platform**: Linux server (Spring Boot application)
**Project Type**: Multi-module Maven web service
**Performance Goals**: N/A (removal; no runtime behavior change)
**Constraints**: Zero behavioral change to NIP-05 record management, domain verification, and
`.well-known/nostr.json` serving (FR-004); removal of the starter's public SPI beans is a breaking
change to the published starter artifact and requires a version bump (FR-008)
**Scale/Scope**: ~4 production classes + 1 auto-config refactor in the starter; ~3 e2e test files
touched + 1 deleted; 3 POMs; 4 documentation files

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against the ratified bottin Constitution v1.0.0:

- **I. Identity Mapping Integrity (NON-NEGOTIABLE)**: The classes being removed (`PersistentNip05Manager`,
  `PersistentAccountManager`) are an *outbound SPI bridge* exposing bottin's data to nsecbunker-java;
  they are **not** the registry's own serving path (which is `Nip05RecordService` + `WellKnownController`
  in `bottin-service`/`bottin-web`). Removal does not touch mapping creation, verification, or
  `.well-known` serving. Guarded by FR-004/SC-003 (existing behavior unchanged, verified by the
  unchanged test suite). ✅
- **II. Protocol Compliance (Nostr NIPs)**: The `.well-known/nostr.json` (NIP-05) contract is unchanged.
  ✅
- **III. Clean Architecture**: Removal *improves* cohesion by deleting an unused outbound integration;
  the refactored `BottinAutoConfiguration` remains the starter's single composition point. ✅
- **IV. Testing Discipline**: `mvn -q verify` gate enforced; all non-nsecbunker unit/integration/e2e
  tests must remain green; only the nsecbunker-specific test is removed. ✅
- **V / VI / VII**: Not materially affected (no concurrency, security-surface, or PII change). ✅
- **Development Workflow**: Feature branch off `develop`; conventional commits, multiple small commits;
  breaking change to the published starter → version bump per SemVer (0.x breaking → MINOR, i.e.
  `0.2.1 → 0.3.0`). ✅

**Gate result: PASS.** Re-evaluated post-design — still PASS (no new violations).

## Project Structure

### Documentation (this feature)

```text
specs/002-remove-nsecbunker/
├── plan.md              # This file
├── research.md          # Phase 0 output (the design decisions for the removal)
├── quickstart.md        # Phase 1 output (verification guide)
├── checklists/
│   └── requirements.md  # From /speckit.specify
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

`data-model.md` and `contracts/` are intentionally **not** generated: this feature introduces no
data entities (spec Key Entities = N/A) and changes no externally-consumed interface. The only
contract affected is the *removal* of the starter's nsecbunker SPI beans, documented under
"Removed surface" below.

### Source Code (files removed / modified)

```text
bottin-spring-boot-starter/src/main/java/xyz/tcheeric/bottin/starter/
├── PersistentNip05Manager.java          # DELETE (implements nsecbunker Nip05Manager)
├── PersistentAccountManager.java        # DELETE (implements nsecbunker AccountManager)
├── BottinNip05ManagerProvider.java      # DELETE (nsecbunker SPI provider)
├── BottinAccountManagerProvider.java    # DELETE (nsecbunker SPI provider)
├── BottinAutoConfiguration.java         # MODIFY: drop nsecbunker imports, the 4 nsecbunker
│                                        #   beans, and @ConditionalOnClass(Nip05Manager.class);
│                                        #   keep @EnableConfigurationProperties(BottinProperties),
│                                        #   @ComponentScan, and the objectMapper() bean
└── BottinProperties.java                # KEEP (unrelated; extended by feature 001)

bottin-spring-boot-starter/pom.xml       # MODIFY: remove nsecbunker-account dependency
pom.xml (parent)                         # MODIFY: remove nsecbunker-java.version property +
                                         #   the nsecbunker-account/-core dependencyManagement entries

bottin-tests/bottin-e2e/
├── src/test/java/.../NsecbunkerIntegrationE2ETest.java  # DELETE
├── src/test/java/.../TestContainersConfig.java          # MODIFY: remove nsecbunkerdContainer bean
├── src/test/java/.../BaseE2ETest.java                   # MODIFY: remove nsecbunkerdContainer
│                                                        #   autowire + getNsecbunkerdUrl(); keep strfry
└── pom.xml                                              # MODIFY: remove nsecbunker-account test dep

docs/                                    # MODIFY: README.md, CHANGELOG.md,
                                         #   docs/explanation/architecture.md,
                                         #   docs/how-to/running-e2e-tests.md
```

**Structure Decision**: No module is added or removed. The starter module survives — only its
nsecbunker integration is excised, and its `BottinAutoConfiguration` is refactored to keep the
non-nsecbunker responsibilities (`BottinProperties` enablement, component scanning, `ObjectMapper`).
The e2e module survives with its `nsecbunkerd` container and nsecbunker test removed and its other
containers (PostgreSQL, strfry) and tests intact.

## Removed surface (the only externally-visible change)

The published `bottin-spring-boot-starter` artifact currently exposes four auto-configured beans
implementing nsecbunker-java SPI contracts (`Nip05Manager`, `AccountManager`,
`Nip05ManagerProvider`, `AccountManagerProvider`). After this feature, those beans no longer exist.
Any external application that embeds the starter solely to provide bottin's persistence to
nsecbunker-java would lose that integration — hence the version bump (FR-008) and changelog entry
(FR-006). No such consumer exists within this repository.

## Complexity Tracking

No constitutional violations to justify. The change reduces complexity (removes an unused
integration and a Docker container from the e2e suite). No new abstractions are introduced.
