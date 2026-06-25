# Phase 0 Research: Remove nsecbunker Dependency

This feature has no external unknowns — the removal surface was established by reading the codebase.
The decisions below resolve the only non-mechanical choice (how to refactor the auto-configuration
gate) and confirm the removal is behavior-preserving.

## Decision 1: Refactor (not delete) `BottinAutoConfiguration`

**Decision**: Keep `BottinAutoConfiguration` and refactor it. Remove the four nsecbunker `@Bean`
methods (`persistentNip05Manager`, `persistentAccountManager`, `bottinNip05ManagerProvider`,
`bottinAccountManagerProvider`) and the two nsecbunker imports. Retain
`@EnableConfigurationProperties(BottinProperties.class)`, the `@ComponentScan` over the bottin
modules, the `@ConditionalOnProperty(bottin.enabled)` gate, and the `objectMapper()` bean.

**Rationale**: The auto-config class has responsibilities beyond nsecbunker — it is the starter's
single composition point that enables `BottinProperties` and component-scans `persistence`,
`service`, `verification`, and `web`. Deleting it (or its `AutoConfiguration.imports` entry) would
break the starter's core embedding capability, which is unrelated to nsecbunker. Only the
nsecbunker-specific beans are removed.

**Alternatives considered**: Delete the whole class and its
`META-INF/spring/...AutoConfiguration.imports` entry — rejected, it would remove `BottinProperties`
enablement and component scanning, regressing the starter and conflicting with feature 001 (which
extends `BottinProperties`).

## Decision 2: Replace the `@ConditionalOnClass(Nip05Manager.class)` gate

**Decision**: Replace `@ConditionalOnClass(xyz.tcheeric.nsecbunker.account.nip05.Nip05Manager.class)`
with `@ConditionalOnClass(xyz.tcheeric.bottin.persistence.repository.Nip05RecordRepository.class)`
(a stable bottin type always present when the starter is usable).

**Rationale**: The current gate activates the auto-config only when the nsecbunker class is on the
classpath. After removal that class is gone, so the condition must change or the auto-config would
never (or always) activate. Gating on a core bottin persistence type preserves the original intent —
"activate when the bottin modules are present" — without referencing a removed dependency. The
`@ConditionalOnProperty(bottin.enabled)` gate is retained alongside it.

**Alternatives considered**: Drop `@ConditionalOnClass` entirely and rely only on
`@ConditionalOnProperty` — acceptable but loses the "modules present" safety; rejected in favor of
preserving the original gating intent with a bottin-owned type.

## Decision 3: e2e suite — remove the `nsecbunkerd` container, keep the rest

**Decision**: Remove the `nsecbunkerdContainer` `@Bean` from `TestContainersConfig`, remove its
autowired field and `getNsecbunkerdUrl()` from `BaseE2ETest`, and delete
`NsecbunkerIntegrationE2ETest`. Keep the PostgreSQL and strfry containers and all other e2e tests.

**Rationale**: All e2e tests extend `BaseE2ETest`, which currently autowires both the `nsecbunkerd`
and `strfry` containers. Only the deleted nsecbunker test calls `getNsecbunkerdUrl()`; the remaining
tests (`Nip05RegistrationFlowE2ETest`, `RestApiCrudE2ETest`, `SecurityE2ETest`, `ErrorHandlingE2ETest`,
`BasicE2ETest`) do not use nsecbunker, so dropping the container leaves them green while removing a
~120s container startup from every e2e run. The strfry container is retained (general relay fixture,
also useful to feature 001).

**Alternatives considered**: Leave the `nsecbunkerd` container in place but unused — rejected; it
would keep pulling the image and adding startup time for no purpose, and the image reference
(`docker.398ja.xyz/nsecbunkerd`) is exactly the kind of orphaned coupling this feature removes.

## Decision 4: Dependency removal across three POMs

**Decision**: Remove the `nsecbunker-java.version` property and the `nsecbunker-account` /
`nsecbunker-core` `dependencyManagement` entries from the parent `pom.xml`; remove the
`nsecbunker-account` dependency from `bottin-spring-boot-starter/pom.xml`; remove the
`nsecbunker-account` test dependency from `bottin-tests/bottin-e2e/pom.xml`. Verify with
`mvn dependency:tree` that no `nsecbunker-*` artifact and no transitively-introduced `nostr-java`
1.x remain.

**Rationale**: Implements FR-001/FR-005. The transitive `nostr-java` 1.x that nsecbunker pulled in is
removed as a side effect, which is the cross-benefit noted for feature 001.

## Confirmation: removal is behavior-preserving

- `bottin-web` (the deployed application) does not declare `bottin-spring-boot-starter`, so the
  nsecbunker beans are never instantiated at runtime in the registry. The registry serves NIP-05 via
  `Nip05RecordService` + `WellKnownController`, which are untouched. This satisfies FR-004.
- The only non-test references to the deleted classes are the `@Bean` methods inside
  `BottinAutoConfiguration` (being removed) and the deleted e2e test. No `bottin-service`,
  `bottin-web`, or `bottin-persistence` code references them. Verified by repository search.

## Out of scope

No replacement key-management / signing mechanism is introduced. If NIP-46 remote signing or
nsecbunker integration is wanted later, it will be specified as a new feature.
