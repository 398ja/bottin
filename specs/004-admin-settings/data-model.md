# Phase 1 Data Model — Admin-Maintained Settings

One new entity. No relationships, no foreign keys, no cascade: the settings row is a
singleton describing the deployment, not a record about anything else in the schema.

---

## Table `settings`

`bottin-persistence/src/main/resources/db/migration/V4__settings.sql`

```sql
CREATE TABLE settings (
    id                    BIGINT PRIMARY KEY,
    blossom_url           VARCHAR(512),
    default_relays_json   TEXT      NOT NULL DEFAULT '[]',
    discovery_relays_json TEXT      NOT NULL DEFAULT '[]',
    rate_limit_per_minute INT       NOT NULL DEFAULT 30,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT settings_singleton CHECK (id = 1)
);

INSERT INTO settings (id, updated_at) VALUES (1, CURRENT_TIMESTAMP);
```

| Column | Type | Null | Default | Meaning |
|---|---|---|---|---|
| `id` | `BIGINT` | no | — | Always `1`. Not generated; the `CHECK` makes a second row impossible. |
| `blossom_url` | `VARCHAR(512)` | **yes** | `NULL` | Browser-reachable Blossom media server. `NULL` represents the window between first boot and the admin's first save. |
| `default_relays_json` | `TEXT` | no | `'[]'` | The deployment's **system relays**, as a JSON array of plain URL strings. See the naming note below. |
| `discovery_relays_json` | `TEXT` | no | `'[]'` | Relays searched for an imported key's existing kind-0 profile. Same shape. |
| `rate_limit_per_minute` | `INT` | no | `30` | Requests per minute per client IP on rate-limited public endpoints. |
| `updated_at` | `TIMESTAMP` | no | `CURRENT_TIMESTAMP` | Last save. Answers "did anyone change this?" without opening the database. |

### Why these shapes

- **The migration inserts the row**, so "no settings row" is never a state application code
  handles. *Unconfigured* is `NULL` or `[]` — a value, not an absence.
- **`rate_limit_per_minute` seeds at 30** (matching today's
  `@Value("${bottin.ratelimit.requests-per-minute:30}")` default) because a rate limit with
  no value is not a rate limit. It is `NOT NULL` for the same reason.
- **`blossom_url` is nullable at the database level** to represent first boot honestly. The
  admin form will not save it blank, so the only way to see `NULL` is never having saved.
- **Relay lists are JSON text, not a child table.** They are short, always read and written
  whole, and no query ever selects an individual relay. This matches
  `nip05_records.relays_json` (`V1__initial_schema.sql:25`), which is already `TEXT` and
  parsed with the same `ObjectMapper`.
- **Plain URL strings, not objects with read/write flags.** Every system relay is both
  published to and searched. Per-relay flags were considered and declined in the spec: the
  JSON is already a document, so flags can be added when a write-only archive or read-only
  mirror is an actual requirement rather than a guess.
- **`CHECK (id = 1)`** is portable across PostgreSQL and H2 and does real work — it makes
  "two settings rows" unrepresentable rather than merely unlikely.

### Naming note

`default_relays_json` / `defaultRelays` carry what the UI and documentation call the
**system relays**. The spec fixes both the column and the JSON key; the endpoint is
renamed (`/defaults` → `/system`) because "defaults" describes the opposite of the new
behaviour — these relays are applied on every publish, not copied once as a starting point.
`SettingsData.defaultRelays` carries a Javadoc line stating this, so the mismatch is
discoverable at the field. See [research.md](./research.md) R3.

---

## `SettingsData` — `bottin-core`

`bottin-core/src/main/java/xyz/tcheeric/bottin/core/model/SettingsData.java`

Immutable value object, mirroring `DomainData`: Lombok `@Value` and
`@Builder(toBuilder = true)`, no framework or persistence annotations.

| Field | Type | Notes |
|---|---|---|
| `blossomUrl` | `String` | `null` when unconfigured |
| `defaultRelays` | `List<String>` | never `null`; empty list when unconfigured. The deployment's system relays. |
| `discoveryRelays` | `List<String>` | never `null`; empty list when unconfigured |
| `rateLimitPerMinute` | `int` | always ≥ 1 once validated |
| `updatedAt` | `Instant` | set by the entity on write |

Principle VIII forbids returning `null`, so both list accessors return an empty list rather
than `null`; `SettingsEntity.toSettingsData()` guarantees this at the boundary by mapping
`'[]'`, blank, and unparseable JSON alike to `List.of()`.

---

## `SettingsEntity` — `bottin-persistence`

`bottin-persistence/src/main/java/xyz/tcheeric/bottin/persistence/entity/SettingsEntity.java`

JPA entity for `settings`. Unlike `DomainEntity` it has **no** `@GeneratedValue`: `id` is
the literal `1L`, set by a `SINGLETON_ID` constant.

- `@Column(name = "default_relays_json", columnDefinition = "TEXT")` with
  `@Builder.Default private String defaultRelaysJson = "[]";` — mirrors
  `Nip05RecordEntity.relaysJson` (`:59-61`) including its `@PrePersist` normalisation of
  `null` to `"[]"`.
- A single `onWrite()` annotated `@PrePersist` and `@PreUpdate` normalises null relay JSON to
  `'[]'` and sets `updatedAt = Instant.now()`, mirroring `DomainEntity.onCreate` / `onUpdate`.
- **No** `toSettingsData()` / `fromSettingsData()` conversion methods, unlike `DomainEntity`.
  The entity carries raw JSON strings while `SettingsData` carries `List<String>`, so any
  entity-side conversion would have to deserialise — which would drag Jackson into
  `bottin-persistence`. The mapping therefore lives in `SettingsService`, which already owns
  the `ObjectMapper`, exactly as `Nip05RecordEntity` carries `relaysJson` while
  `Nip05RecordService` serialises it.

  *(Corrected during implementation: an earlier draft of this document asked for both the
  conversion methods and the "no JSON here" rule, which cannot both hold.)*

## `SettingsRepository` — `bottin-persistence`

```java
public interface SettingsRepository extends JpaRepository<SettingsEntity, Long> { }
```

No custom query methods. `findById(1L)` is the only access pattern, and the constant lives
in `SettingsService` where it is used.

---

## Validation rules

Enforced in two places on purpose — bean validation on `SettingsForm` for field-level
feedback, and `SettingsService` so no caller can bypass it. See [research.md](./research.md) R6.

| Field | Rule | Message |
|---|---|---|
| Media server | required, non-blank | "Media server URL is required" |
| Media server | `^https?://\S+$` | "Media server URL must start with http:// or https://" |
| System relays | each line matches `wss?://\S+` | "Relay URL must start with ws:// or wss://: {url}" |
| System relays | duplicates collapsed, order preserved, blank lines dropped | — (normalisation, not rejection) |
| Discovery relays | identical to system relays | identical |
| Rate limit | required, 1–1000 | "Rate limit must be between 1 and 1000 requests per minute" |

**Textarea regex** (whole-field, newline-separated): `^\s*((wss?://\S+)\s*)*$`. `\s` matches
newlines in Java regex without a flag, and an empty textarea passes — an empty relay list is
a valid configured state.

**Normalisation** (`SettingsService.normalizeRelays`): trim each entry, drop blanks,
reject any entry not matching `wss?://\S+` with an `IllegalArgumentException` naming the
offending URL, then de-duplicate through a `LinkedHashSet` so order is preserved. This
mirrors `Nip05RecordService.mergeWithDefaults` (`:193-203`), which already uses a
`LinkedHashSet` for exactly this reason.

---

## State and lifecycle

There are no state transitions — the row is created once by the migration and updated in
place. The only lifecycle question is what *unconfigured* means, and it is answered by
values rather than by absence:

| Condition | Representation | System behaviour |
|---|---|---|
| Fresh install, never saved | `blossom_url IS NULL`, both lists `'[]'`, limit `30` | Uploads disabled with a stated reason; publishing uses only each user's own relays; login profile lookup finds nothing; rate limiting active at 30/min |
| Media server unset | `blossom_url IS NULL` or blank | Upload controls disabled, "Media server not configured"; the rest of onboarding proceeds |
| System relays empty | `default_relays_json = '[]'` | Publish uses the user's own relays; the welcome screen already reports "no write relay configured" when there are none |
| Discovery relays empty | `discovery_relays_json = '[]'` | Login profile lookup returns `null` and proceeds; sign-in never blocks on a relay |
| Row missing | Cannot happen — the migration inserts it | `SettingsService` throws `SettingsNotFoundException` rather than inventing defaults |

## `SettingsNotFoundException` — `bottin-core`

Extends `BottinException` per Principle VI, following `DomainNotFoundException` exactly:

- error code `SETTINGS_NOT_FOUND`
- `retryable = false` — a missing singleton row is a broken schema, not a transient fault
- message: `"Settings row not found"`
- suggestion: `"Verify the V4__settings migration ran; the settings row is seeded by the migration and must always exist."`

It exists so that the impossible case fails loudly instead of silently synthesising
defaults, which would reintroduce exactly the "configuration from two sources" problem this
feature removes.
