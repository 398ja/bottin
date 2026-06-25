# Implementation Plan: Nostr Profile Reach Stats

**Branch**: `001-nostr-profile-reach` | **Date**: 2026-06-25 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-nostr-profile-reach/spec.md`

## Summary

Add a "reach" statistic for registered Nostr profiles. Reach is the count of distinct users whose latest NIP-02 (kind-3) contact list includes the target pubkey. A scheduled service (default every 6 hours) iterates every registered profile, gathers follower events from the default application relays plus each profile's advertised NIP-65 (kind-10002) relays, de-duplicates globally by author, and stores the result. A public, rate-limited REST endpoint serves the most recently stored figure (with its calculation timestamp and a complete/partial indicator) for any given `npub`.

**Technical approach**: A new self-contained feature module `bottin-reach` (mirroring the existing `bottin-verification` module) holds the scheduled job, the calculation service, and the relay-gathering infrastructure. Relay interaction uses **nostr-java 2.0.7** (`xyz.tcheeric`, latest release; 1.x is deprecated and not used): `NostrRelayClient` (Spring-WebSocket based) with `EventFilter`/`Filters`/`ReqMessage` for `#p` follower queries (kind-3) and author/kind-10002 NIP-65 resolution, completing each subscription on the EOSE callback. nostr-java's `Bech32` utilities handle NIP-19 npub decoding. Note: `nsecbunker` pulls nostr-java 1.x transitively, so `bottin-reach` must pin 2.0.7 explicitly and exclude/align the transitive 1.x coordinate (feature `002-remove-nsecbunker` removes nsecbunker outright, after which the exclusion is no longer needed). Persistence (entity, repository, Flyway migration) lives in `bottin-persistence`; the read-only REST controller and DTOs live in `bottin-web`; domain abstractions and the new exception live in `bottin-core`; configuration binds under the existing `bottin.*` prefix via the starter. Reach is computed by directly crawling the default relays plus each profile's NIP-65 relays (per FR-010/FR-011); precomputed third-party aggregators are deliberately not used because they would bypass the spec's required computation method.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 3.4.1 (WebMVC, Data JPA, Scheduling), Hibernate, Flyway 10.10.0, Jackson 2.17.0, Lombok 1.18.32, springdoc-openapi 2.4.0, **nostr-java 2.0.7** (`xyz.tcheeric`, latest; Spring-WebSocket relay client for kind-3 `#p` and kind-10002 queries; pinned explicitly to override the deprecated 1.x pulled transitively by nsecbunker)
**Storage**: PostgreSQL 42.7.3 (production), H2 2.2.224 (dev/test); schema via Flyway migrations
**Testing**: JUnit 5.10.2, Mockito 5.11.0, AssertJ; Testcontainers (PostgreSQL) for integration tests tagged/suffixed `IT`
**Target Platform**: Linux server (Spring Boot application)
**Project Type**: Multi-module Maven web service
**Performance Goals**: Reach lookup served from storage, perceived-instant (<1s under normal load, SC-001); a single scheduled run processes up to ~10,000 profiles within the configured interval (default 6h, SC-002/SC-008)
**Constraints**: Relay gathering is best-effort with per-relay connect/read timeouts and EOSE-bounded reads; partial gathers are flagged, never silently authoritative (FR-019); lookup endpoint is public and per-client rate-limited (FR-017); follower counts are a lower bound bounded by relay coverage
**Scale/Scope**: ~10,000 tracked profiles (registered NIP-05 record pubkeys); individual profiles may have very large follower sets requiring paginated relay reads

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

The project constitution (`.specify/memory/constitution.md`) is an **unfilled template** with no ratified principles, so there are no formal constitutional gates to evaluate. In their place, this plan is held to the project's documented engineering standards in `AGENTS.md` / `CLAUDE.md`:

- **Clean Architecture / layering**: New code respects the existing module boundaries (core → persistence → feature module → web); the controller depends on an abstraction, not on relay infrastructure (DIP). ✅
- **SRP / cohesive modules**: A dedicated `bottin-reach` module isolates the reach feature (matching the precedent set by `bottin-verification`). ✅
- **Exception hierarchy**: New failures extend `BottinException` with error code + suggestion (e.g. `ReachNotAvailableException`, `InvalidPubkeyException`). ✅
- **Testing**: Unit tests (Mockito) for services; integration tests (Testcontainers) for persistence and the endpoint; tests named `should…When…` with AAA structure and plain-English comments. ✅
- **Conventional commits + per-task commits + roadmap status update** on completion. ✅
- **Diátaxis docs**: A how-to/reference doc added under `docs/` and linked from `docs/README.md`. ✅

**Gate result: PASS** (no ratified constitution; project standards applied). Re-evaluated post-design — still PASS (no new violations; no extra projects, no speculative complexity).

## Project Structure

### Documentation (this feature)

```text
specs/001-nostr-profile-reach/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── reach-api.yaml   # OpenAPI contract for the reach endpoint
├── checklists/
│   └── requirements.md  # From /speckit.specify
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (repository root)

The feature spans existing modules plus one new module (`bottin-reach`), following the `bottin-verification` precedent.

```text
bottin-core/src/main/java/xyz/tcheeric/bottin/core/
├── reach/
│   ├── ProfileReach.java                 # domain value (pubkey, reachCount, calculatedAt, complete)
│   └── ReachQueryService.java            # abstraction the web layer depends on (DIP)
└── exception/
    ├── ReachNotAvailableException.java   # extends BottinException → 404
    └── InvalidPubkeyException.java        # extends BottinException → 400

bottin-persistence/src/main/
├── java/xyz/tcheeric/bottin/persistence/
│   ├── entity/
│   │   ├── ProfileReachEntity.java       # table: profile_reach
│   │   └── ReachCalculationRunEntity.java# table: reach_calculation_runs
│   └── repository/
│       ├── ProfileReachRepository.java
│       └── ReachCalculationRunRepository.java
└── resources/db/migration/
    └── V3__profile_reach.sql             # new tables + indexes

bottin-reach/                              # NEW MODULE (mirrors bottin-verification)
└── src/main/java/xyz/tcheeric/bottin/reach/
    ├── ScheduledReachJob.java            # @Scheduled, cron from config
    ├── ReachCalculationService.java      # orchestrates per-profile calculation + persistence
    ├── ReachQueryServiceImpl.java        # implements core ReachQueryService (read path)
    ├── relay/
    │   ├── FollowerGatherer.java         # kind-3 #p REQ, pagination, EOSE, global dedupe
    │   ├── Nip65RelayResolver.java       # kind-10002 lookup → read/write relay URLs
    │   └── RelayQueryClient.java         # nostr-java wrapper (connect, subscribe, timeout)
    └── PubkeyCodec.java                  # npub(bech32) ↔ hex, validation

bottin-web/src/main/java/xyz/tcheeric/bottin/web/
├── controller/ProfileStatsController.java# GET /api/v1/profiles/{identifier}/reach
└── dto/ProfileReachResponse.java

bottin-spring-boot-starter/.../BottinProperties.java   # add nested ReachProperties

bottin-tests/bottin-it/.../reach/                       # integration tests (Testcontainers)
```

**Structure Decision**: Add a new cohesive feature module **`bottin-reach`** for the scheduled job, calculation orchestration, and relay-gathering infrastructure — directly mirroring `bottin-verification`, which already pairs a `@Scheduled` job with external-IO logic. Shared, stable concerns reuse existing modules: persistence in `bottin-persistence`, the read abstraction + exceptions in `bottin-core`, the REST surface in `bottin-web`, and configuration in the starter's `BottinProperties`. `bottin-web` depends on the `bottin-core` `ReachQueryService` abstraction (implemented in `bottin-reach`), keeping the controller free of relay/infrastructure concerns. Parent `pom.xml` gains the `bottin-reach` module and the `nostr-java` version property.

## Complexity Tracking

No constitutional violations to justify. The single structural addition (a new module) follows an established in-repo precedent (`bottin-verification`) rather than introducing a novel pattern, so no complexity exception is required.
