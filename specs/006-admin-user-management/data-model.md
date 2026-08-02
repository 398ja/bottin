# Phase 1 Data Model: Additional administrators

## Entities

### Administrator (`admin_users`)

A public key permitted to sign in to the dashboard. Holds ordinary
administrators only — the super administrator is deployment configuration and is
never a row here (research D2).

| Field | Type | Rules |
|---|---|---|
| `id` | `BIGSERIAL` | Surrogate key. |
| `pubkey` | `VARCHAR(64) NOT NULL UNIQUE` | Canonical lowercase hex (NIP-01). Accepted as `npub1…` or hex and normalised on the way in (FR-002). Uniqueness is what makes "the same key entered either way is one administrator" true in the store rather than only in the service. |
| `label` | `VARCHAR(100)` | Human-readable, operator-supplied (FR-014). Descriptive only — never consulted at sign-in. Nullable so an existing row is never blocked by it. |
| `role` | `VARCHAR(20) NOT NULL DEFAULT 'ADMIN'` | Only ever `ADMIN`. Retained from V1: it costs nothing, and it is where a future second stored role would land. |
| `enabled` | `BOOLEAN NOT NULL DEFAULT TRUE` | Always true in this feature. The column is kept so suspension can be added without a migration (spec Assumptions). |
| `added_by_pubkey` | `VARCHAR(64)` | Canonical hex of the administrator who added this one — the provenance FR-010 requires. Nullable, since a row could predate a known adder. |
| `created_at` | `TIMESTAMP NOT NULL` | When the administrator was added. |

**Removed by V5**: `username` (`NOT NULL UNIQUE`) and `password_hash`
(`NOT NULL`), both meaningless once sign-in is by key, and the latter actively
obstructive — no administrator could be inserted without inventing a password.

### Role

Not stored as a distinct entity. Two values exist in the domain:

- **Super administrator** — the configured master key. Exactly one per
  deployment. Carries `admin:read`, `admin:write`, and `admin:manage-admins`.
- **Administrator** — a row in `admin_users`. Carries `admin:read` and
  `admin:write`. Never `admin:manage-admins`, which is what makes US3 enforceable
  at the decision point rather than in the template.

### Security log entry

Not a table. Additions, removals, and refused management attempts are structured
log events (Logging Standards), keeping identity-bearing operational data out of
durable storage per Principle VII.

| Event | Fields |
|---|---|
| `administrator_added` | `pubkey`, `added_by`, `label_present` |
| `administrator_removed` | `pubkey`, `removed_by`, `sessions_revoked` |
| `administrator_change_rejected` | `reason`, `pubkey`, `attempted_by` |

`sessions_revoked` is the count returned by the revoker. It is logged because a
removal that revoked zero sessions when one was expected is the visible symptom
of the ceiling in research D6.

## Migration `V5__admin_users_key_based.sql`

Order is not incidental — the unique index must go before the column it covers:

1. `DROP INDEX idx_admin_users_username`
2. `ALTER TABLE admin_users DROP COLUMN username`
3. `ALTER TABLE admin_users DROP COLUMN password_hash`
4. `ALTER TABLE admin_users ADD COLUMN label VARCHAR(100)`
5. `ALTER TABLE admin_users ADD COLUMN added_by_pubkey VARCHAR(64)`
6. `ALTER TABLE admin_users ALTER COLUMN pubkey SET NOT NULL`
7. `CREATE UNIQUE INDEX idx_admin_users_pubkey ON admin_users(pubkey)`

**Why a destructive migration is safe here**: the table has never held a row in
any deployment — nothing outside the four dormant classes references it and no
migration or fixture seeds it (research D1). Step 6 would fail on a table with
existing `NULL` pubkeys; there are none, and that is a fact about every
deployment rather than a hope about this one.

**Both engines must be checked.** H2 backs the test suite and PostgreSQL backs
production. A statement accepted by only one of them passes `mvn verify` and
fails on deploy, which is precisely the failure shape this project has hit
repeatedly. The migration is to be exercised against both before the task is
considered done, not reasoned about.

## Value objects

### `AdminUserData` (`bottin-core`) — modified

Loses `username` and `passwordHash`; gains `label` and `addedByPubkey`. The
`createNew(String username, String passwordHash, AdminRole)` factory is replaced
by one taking the canonical pubkey, label, and adder. `withPasswordHash(...)`
is deleted outright rather than deprecated — there are no passwords, and a method
that sets one would only ever be called by mistake.

`isAdmin()` currently returns `role == ADMIN`, which will be true for every row.
It is removed rather than left to read as though it discriminates.

## Repository (`AdminUserRepository`) — modified

| Method | Purpose |
|---|---|
| `Optional<AdminUserEntity> findByPubkey(String)` | The sign-in question, asked on every ACL resolution. Served by the unique index. |
| `boolean existsByPubkey(String)` | Duplicate detection for FR-004. |
| `List<AdminUserEntity> findAllByOrderByCreatedAtAsc()` | The list for the settings page. |
| `void deleteByPubkey(String)` | Removal. |

Methods carried over from the dormant version that reference `username` are
removed with the column.

## State transitions

An administrator has a short life: **absent → present → absent**.

- **absent → present**: the super administrator adds a key. Refused if the value
  is not a public key (FR-003).
- **present → present** (no transition): adding a key that can already
  administer — one already stored, or the configured master key — leaves the
  store untouched and is reported informationally rather than as a failure
  (FR-004, FR-004a). Adding is therefore **idempotent**: submitting the same key
  any number of times yields exactly one entry, and the master key yields none.
- **present → absent**: the super administrator removes the key. In the same
  operation, every session held by that pubkey is revoked (FR-007, research D4).
  Removal of a key that is not present is refused as not found rather than
  silently succeeding, so a mistyped removal is visible.

There is deliberately no *suspended* state in this feature; the `enabled` column
exists so adding one later is not a migration (spec Assumptions).
