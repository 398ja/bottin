# Quickstart: Verifying the nsecbunker Removal

A guide to confirming the removal is complete and behavior-preserving.

## Prerequisites

- Java 21, Maven
- Docker (for the e2e Testcontainers suite)

## 1. No nsecbunker references remain in code

```bash
# Should return NOTHING:
grep -rinE "nsecbunker" --include=*.java bottin-spring-boot-starter bottin-service bottin-web \
  bottin-persistence bottin-verification bottin-core

# Only the deleted test should be gone; remaining e2e tests must not reference nsecbunker:
grep -rinE "nsecbunker|getNsecbunkerdUrl" --include=*.java bottin-tests
```

## 2. No nsecbunker (or transitive nostr-java 1.x) on the classpath

```bash
# Should return NOTHING:
mvn -q dependency:tree | grep -iE "nsecbunker|nostr-java.*:1\."
```

## 3. The full build and test suite passes

```bash
mvn -q verify
```

Expected: success, with the same tests passing as before — minus the removed
`NsecbunkerIntegrationE2ETest`.

## 4. The registry behaves identically

Start the application and exercise the unchanged endpoints:

```bash
# NIP-05 serving (the core contract — must be unchanged)
curl -s "http://localhost:8080/.well-known/nostr.json?name=alice" | jq

# Record / domain / external-verification APIs
curl -s "http://localhost:8080/api/v1/records?username=alice" | jq
curl -s "http://localhost:8080/api/v1/verify?nip05=alice@example.com" | jq
```

All responses must match pre-removal behavior (FR-004 / SC-003).

## 5. The starter still auto-configures (without nsecbunker)

The refactored `BottinAutoConfiguration` must still:
- enable `BottinProperties`,
- component-scan `persistence` / `service` / `verification` / `web`,
- provide the `ObjectMapper` bean,
- activate under `bottin.enabled=true` (now gated on a bottin persistence type, not an nsecbunker
  type).

Covered by the existing starter/auto-configuration tests; confirm they pass in step 3.

## 6. Documentation and changelog updated

```bash
# README, architecture explanation, and e2e how-to should no longer present nsecbunker as current:
grep -rin "nsecbunker" README.md docs/

# CHANGELOG should contain a removal entry:
grep -in "nsecbunker" CHANGELOG.md
```

## 7. Version bump

Removing the starter's public SPI beans is a breaking change to the published starter. Bump the
project version in the parent `pom.xml` per SemVer (0.x breaking change → minor: `0.2.1 → 0.3.0`) and
record it in the changelog.
