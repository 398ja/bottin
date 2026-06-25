# Phase 0 Research: Nostr Profile Reach Stats

This document resolves the technical unknowns the specification deferred to planning — chiefly *how* followers are discovered across relays and *which* Java capability performs the relay queries.

## Decision 1: Relay client library

**Decision**: Add **nostr-java 2.0.7** (`xyz.tcheeric`) — the latest release — as an explicit dependency in the new `bottin-reach` module. The 1.x line is treated as deprecated and is not used. The 2.0.7 API surface (verified against the published jars) is:

- `nostr.client.springwebsocket.NostrRelayClient` — a Spring-WebSocket (`TextWebSocketHandler`) relay client. Connect via `NostrRelayClient.connectAsync(url[, timeoutMs])` (or the constructor with `(url, timeoutMs, …)`). Issue a subscription with `subscribe(reqMessage, onEvent, onError, onComplete)` returning an `AutoCloseable`; the `onComplete` `Runnable` fires on **EOSE**. Incoming frames arrive as raw JSON strings and are decoded via `nostr.event.BaseMessage` → `EventMessage` / `EoseMessage`.
- `nostr.event.filter.EventFilter.builder()` — build the filter with `kinds`, `authors`, and tag filters (`getTagFilters()` exposes a `Map<String, List<String>>`, i.e. `#p`, `#e`, …). Wrap one or more `EventFilter` in `nostr.event.filter.Filters`, then in `nostr.event.message.ReqMessage(subscriptionId, filters)`.
- `nostr.crypto.bech32.Bech32` + `Bech32Prefix` for NIP-19 `npub` encode/decode; `nostr.base.PublicKey` for key handling.

The two required queries map directly: followers = `EventFilter(kinds=[3], tagFilter "p"=[hex])`; NIP-65 = `EventFilter(kinds=[10002], authors=[hex])`.

**Rationale**:
- Latest maintained release (2.0.7, May 2026). The 1.x facade was removed in 2.x; building new code on 1.x would start on a deprecated, dead-end branch.
- Built on Spring WebSocket, matching bottin's Spring Boot stack — async connect, configurable timeouts, transport-error recovery hooks, and an EOSE-completed `subscribe` callback are provided out of the box.
- Modular artifacts (`nostr-java-core` / `-event` / `-client`) let `bottin-reach` depend only on what it needs.
- A strfry Testcontainer fixture already exists in the repo, enabling realistic integration tests of the gathering path against a real relay.

**Classpath caveat (must handle during implementation)**: `nsecbunker` pulls nostr-java **1.x** transitively. `bottin-reach` MUST pin **2.0.7** explicitly and exclude/align the transitive 1.x coordinate so the build does not end up with two incompatible versions of the `nostr.*` packages on the classpath. Verify with `mvn dependency:tree` that only 2.0.7 resolves. (Feature `002-remove-nsecbunker` removes nsecbunker entirely, which eliminates the transitive 1.x and makes this exclusion unnecessary; the two efforts are independent, so until that removal lands the exclusion keeps `001` self-contained.)

**Alternatives considered**:
- **nostr-java 1.2.x (already transitive via nsecbunker)**: would avoid a new explicit coordinate, but 1.x is deprecated and its API facade no longer exists in the maintained line — a maintenance dead-end. Rejected per the directive to use the latest library.
- **Raw `java.net.http.WebSocket` + Jackson**: full control but re-implements framing, subscription lifecycle, and EOSE handling that nostr-java already provides. Rejected as needless reinvention.
- **`org.rust-nostr:nostr-sdk-jvm`**: Broadest protocol coverage (typed NIP-65, NIP-45 COUNT) but requires per-platform native libraries. Rejected — packaging cost outweighs benefit for a server feature.

## Decision 2: Follower discovery strategy

**Decision**: Compute reach by **crawling relays directly**: for each tracked profile, open subscriptions on the default application relays plus the profile's NIP-65 relays with filter `{"kinds":[3],"#p":["<hex>"]}`, read until EOSE (paginating with `until` when a relay returns a capped page), merge results across all relays, and de-duplicate globally.

**Rationale**:
- This is exactly what FR-010/FR-011 require: the figure must be derived from the default relays *and* the profile's own NIP-65 relays.
- Direct crawling lets us honor the NIP-65 contribution to the count (SC-003), which a precomputed aggregate cannot.

**Alternatives considered**:
- **Third-party aggregators** (nostr.band kind-33333 count event; NIP-45 `COUNT` at relay.nostr.band): ~2 orders of magnitude cheaper at scale, but they return a single precomputed number that **bypasses the spec's required computation method** and cannot reflect a specific profile's NIP-65 relay set. Rejected for v1; noted below as a possible future optimization if the spec's method is relaxed.
- **NIP-45 `COUNT` generally**: Contentious and not universally supported (only some relays implement it). Rejected as a primary mechanism; unsafe to assume on arbitrary relays.

## Decision 3: Distinct-follower counting algorithm

**Decision**: After gathering kind-3 events from all relays:
1. De-duplicate raw events by event id.
2. Group surviving events by author pubkey; keep only the **newest** per author (highest `created_at`, tie-break by event id per NIP-01 replaceable-event rules).
3. **Re-verify** the target pubkey still appears in the `#p` tags of that newest event (drops users who have since unfollowed but whose older event matched the filter).
4. Count the surviving distinct authors. Optionally exclude self-follow (target authoring its own contact list) per policy.

**Rationale**: Satisfies FR-012 (count each follower once) and FR-013 (count only if present in the *current* contact list). Global de-dup across relays is required because the same follower appears on multiple relays.

**Alternatives considered**: Per-relay counting then summing — rejected, double-counts followers seen on multiple relays. Trusting the `#p` filter match without re-verifying the latest event — rejected, would over-count stale unfollows.

## Decision 4: Partial-gather handling

**Decision**: Track, per profile run, whether every targeted relay responded (reached EOSE within timeout). If one or more relays failed/timed out but at least one returned data, store the figure with `complete = false`. If **no** relay returned data, skip the profile and retain its prior stored figure.

**Rationale**: Implements FR-014 (retain prior on total failure) and FR-019 (flag partial figures). A per-relay connect/read timeout (configurable, default ~10–15s) bounds run time; relays exceeding it are treated as non-responding for that profile.

## Decision 5: Tracked-profile source

**Decision**: The set of tracked profiles is the distinct, enabled pubkeys from the existing `nip05_records` table. The scheduled job loads this set each run.

**Rationale**: Matches the spec assumption "tracked profiles = registered identities". No new registration mechanism is introduced.

## Decision 6: Module placement & scheduling

**Decision**: A new `bottin-reach` module holds the `@Scheduled` job (cron from `bottin.reach.calculation-cron`, default every 6 hours), the calculation orchestration, and the relay infrastructure — mirroring `bottin-verification`. Scheduling is already active (Spring Boot auto-enables it with `@SpringBootApplication`; `bottin-verification` relies on the same).

**Rationale**: SRP / component cohesion (CCP): the reach feature changes for its own reasons and is cohesively releasable, exactly like the verification feature. Reuses the established in-repo precedent rather than inventing a new layout.

## Decision 7: Identifier handling (npub ↔ hex)

**Decision**: The endpoint accepts either an `npub` (NIP-19 bech32) or a 64-char hex pubkey. A `PubkeyCodec` decodes/validates the input to canonical lowercase hex (the storage key). Invalid input raises `InvalidPubkeyException` → HTTP 400 before any lookup.

**Rationale**: Implements FR-001/FR-005 and the spec's identifier assumption. nostr-java 2.0.7 provides bech32/NIP-19 utilities (`nostr.crypto.bech32.Bech32`, `Bech32Prefix`) and `nostr.base.PublicKey`, so `PubkeyCodec` is a thin wrapper over them rather than a hand-rolled bech32 implementation (also honors the constitution's "no hand-rolled bech32" rule).

## Open items deferred to implementation (non-blocking)

- Exact default-relay list values (configuration data, not a design decision) — seeded in `application.yml` with sensible defaults (e.g. `wss://relay.damus.io`, `wss://nos.lol`, plus an indexing relay for coverage).
- Concurrency/parallelism of the per-profile crawl within a run (thread pool / connection-pool sizing) — a tuning concern validated against the ~10k/6h target during implementation; start conservative (small bounded pool, few connections per relay) and measure.
