# Phase 1 Data Model: Nostr Profile Reach Stats

Derived from the spec's Key Entities and Functional Requirements. Two new persisted entities are introduced in `bottin-persistence`; one domain value object lives in `bottin-core`. Conventions mirror existing entities (`DomainEntity`, `Nip05RecordEntity`): Lombok builders, `Instant` timestamps via `@PrePersist`/`@PreUpdate`, identity-generated `Long` ids, explicit indexes.

## Entity: ProfileReach (`profile_reach` table)

The latest stored reach figure for one tracked profile. Upserted on each successful run (one row per pubkey).

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `Long` | PK, identity | Surrogate key |
| `pubkey` | `String` | NOT NULL, UNIQUE, length 64 | Canonical lowercase hex; the lookup key. FR-002 |
| `reachCount` | `long` | NOT NULL, ≥ 0 | Distinct current followers. FR-002, FR-012 |
| `complete` | `boolean` | NOT NULL | `true` if every targeted relay responded; `false` if gathered from a partial source set. FR-019 |
| `calculatedAt` | `Instant` | NOT NULL | When this figure was computed. FR-003 |
| `createdAt` | `Instant` | NOT NULL, immutable | Row creation (`@PrePersist`) |
| `updatedAt` | `Instant` | NOT NULL | Last update (`@PreUpdate`) |

**Indexes**: unique on `pubkey` (`idx_profile_reach_pubkey`).

**Validation rules**:
- `pubkey` must be 64 hex chars (validated upstream by `PubkeyCodec` before persistence).
- `reachCount` never negative; a genuine zero is stored as `0` (distinct from "no row" → not available). FR-004.

**Lifecycle**:
- *Successful gather* (≥1 relay responded): upsert row by `pubkey`, set `reachCount`, `complete`, `calculatedAt`.
- *Total gather failure* (no relay responded): no write — prior row retained unchanged. FR-014.
- Only the latest figure is kept; no history retained (spec assumption "single result per profile").

## Entity: ReachCalculationRun (`reach_calculation_runs` table)

A summary record of one scheduled execution, for operational observability. FR-015.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `Long` | PK, identity | |
| `startedAt` | `Instant` | NOT NULL | Run start |
| `finishedAt` | `Instant` | nullable | Run end (null while in progress) |
| `profilesTotal` | `int` | NOT NULL | Tracked profiles at run start |
| `profilesProcessed` | `int` | NOT NULL | Figures successfully updated |
| `profilesSkipped` | `int` | NOT NULL | Skipped due to total gather failure |
| `gatherFailures` | `int` | NOT NULL | Count of relay-gather failures encountered |
| `status` | `String` | NOT NULL | `RUNNING` / `COMPLETED` / `FAILED` |
| `createdAt` | `Instant` | NOT NULL, immutable | |

**Indexes**: index on `startedAt` (`idx_reach_runs_started_at`) for "latest run" queries.

## Domain value: ProfileReach (`bottin-core`)

Immutable value object the `ReachQueryService` returns to the web layer, decoupling the controller from the persistence entity (DIP).

```text
ProfileReach {
  String  pubkey        // canonical hex
  long    reachCount
  boolean complete
  Instant calculatedAt
}
```

## Transient: Follower set (in-memory during a run)

Not persisted. During gathering, the calculation holds a working set keyed by author pubkey → newest kind-3 event, reduced to a distinct count per the algorithm in research.md (Decision 3).

## Tracked-profile source (existing data, no new table)

The set of profiles to process each run = `SELECT DISTINCT pubkey FROM nip05_records WHERE enabled = true`. A repository query on the existing `Nip05RecordRepository` (or a dedicated projection query) supplies it; no schema change to `nip05_records`.

## Relationships

- `profile_reach.pubkey` logically references the pubkey(s) in `nip05_records`. **No DB foreign key** is added: pubkeys are not unique in `nip05_records` (a pubkey may back multiple username@domain records) and reach is keyed by pubkey alone. The relationship is by value, resolved at query time.
- `reach_calculation_runs` is standalone (no FK to `profile_reach`).

## Flyway migration

New file `bottin-persistence/src/main/resources/db/migration/V3__profile_reach.sql` (next sequential version after `V2`), creating both tables and their indexes, following the DDL style of `V1__initial_schema.sql` (BIGSERIAL PK, `VARCHAR(64)` pubkey, `TIMESTAMP` columns with defaults).
