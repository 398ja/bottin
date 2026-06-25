# Quickstart: Nostr Profile Reach Stats

A short guide to building, exercising, and verifying the reach feature locally.

## Prerequisites

- Java 21, Maven
- The repo builds: `mvn -q verify` from the root
- (Integration tests) Docker available for Testcontainers (PostgreSQL + the strfry relay fixture)

## What this feature adds

- A scheduled job that, by default every 6 hours, computes each registered profile's follower count from the default relays + the profile's NIP-65 relays and stores it.
- A public, rate-limited endpoint to read the stored figure for any profile.

## Configuration

Defaults live in `bottin-web/src/main/resources/application.yml` under the `bottin.reach.*` keys (bound via `BottinProperties`):

```yaml
bottin:
  reach:
    enabled: true
    calculation-cron: "0 0 */6 * * ?"     # every 6 hours (FR-007)
    relay-timeout-seconds: 12              # per-relay connect/read budget
    max-profiles-per-run: 10000            # SC-008 target
    default-relays:
      - wss://relay.damus.io
      - wss://nos.lol
      - wss://relay.nostr.band             # indexing relay for coverage
```

## Try the endpoint

After the application starts and at least one figure has been calculated:

```bash
# By npub
curl -s http://localhost:8080/api/v1/profiles/npub1.../reach | jq

# By hex pubkey
curl -s http://localhost:8080/api/v1/profiles/82341f88...e6a2/reach | jq
```

Expected `200` body:

```json
{
  "pubkey": "82341f88...e6a2",
  "npub": "npub1...",
  "reachCount": 1542,
  "complete": true,
  "calculatedAt": "2026-06-25T06:00:12Z"
}
```

Other outcomes:
- `404` with `errorCode: REACH_NOT_AVAILABLE` — never calculated / not a tracked profile.
- `400` with `errorCode: INVALID_PUBKEY` — malformed identifier.
- `429` — rate limit exceeded.

## Trigger a calculation without waiting for the cron

For local verification, either temporarily set `calculation-cron` to a near-future expression, or (recommended) expose the calculation entry point so a test can invoke `ReachCalculationService.calculateAll()` directly. Integration tests drive it against the strfry Testcontainer fixture with seeded kind-3 / kind-10002 events.

## Verifying acceptance scenarios

| Scenario (spec) | How to verify |
|-----------------|---------------|
| US1 — lookup returns count + timestamp | Seed a `profile_reach` row; GET the endpoint; assert body fields. |
| US1 — never-calculated → not available | GET an unknown npub; assert `404` `REACH_NOT_AVAILABLE`. |
| US1 — invalid identifier | GET `/profiles/not-an-npub/reach`; assert `400`. |
| US2 — scheduled refresh updates figures | Run `calculateAll()` against strfry fixture; assert row `calculatedAt`/`reachCount` updated. |
| US2 — lookup during run returns prior figure | Read endpoint while a run is in progress; assert previous value served. |
| US2 — total gather failure retains prior | Point a profile at an unreachable relay only; assert prior row unchanged + run record shows a skip. |
| US3 — NIP-65 relays add followers | Seed extra followers only on a NIP-65 relay; assert count includes them, deduped. |
| US3 — duplicate follower across relays counted once | Seed same follower's kind-3 on two relays; assert count increments by one. |
| Partial gather flagged | Make one of several relays time out; assert stored `complete = false` and response reflects it. |

## Run the tests

```bash
# Unit tests (fast, mocked relays)
mvn -q -pl bottin-reach test

# Integration tests (Testcontainers: PostgreSQL + strfry)
mvn -q -pl bottin-tests/bottin-it verify

# Full build + all tests (run before committing — AGENTS.md)
mvn -q verify
```
