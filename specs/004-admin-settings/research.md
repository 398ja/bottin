# Phase 0 Research — Admin-Maintained Settings

The specification is unusually decided: scope, storage shape, precedence, and the list of
values that deliberately stay in the environment are all settled there. This document
records only the questions the spec left implicit, each resolved against the code as it
stands on `feat/client-ui-redesign`. Every decision below is closed — no NEEDS
CLARIFICATION items remain.

---

## R1 — How does `RateLimitService` read a database-backed limit?

**Decision**: Read `SettingsService.find().getRateLimitPerMinute()` on each rate-limited
request, with no memoisation, and mark the shortcut with a `ponytail:` comment naming the
ceiling and the upgrade path.

**Rationale**: `RateLimitService` today reads `@Value("${bottin.ratelimit.requests-per-minute:30}")`
into a field — free. Moving it to the database makes each `isAllowed` call a primary-key
`SELECT` on a single-row table. Two endpoints are rate limited
(`bottin-api/.../ExternalVerificationController.java:61` and
`bottin-api/.../ProfileStatsController.java:61`), both low-traffic public paths on a NIP-05
registry. A primary-key read on a one-row table is cheaper than the DNS and HTTP work the
verification endpoint performs immediately afterwards. Reading through also makes the
spec's own test — "a changed value applies without a restart" — a two-line test rather than
one that has to control a clock.

**Alternatives considered**:
- *60-second memo inside `RateLimitService`*. Matches the client's TTL, but the test for
  "applies without a restart" then needs an injected clock or a 60-second sleep, and the
  memo is a second cache to reason about. Rejected as premature; the `ponytail:` comment
  names it as the upgrade if profiling ever shows the read.
- *Cache inside `SettingsService`*. Would help every reader, but `SettingsService` is
  written by `bottin-admin-ui` and read by `bottin-api` in *different processes*, so a
  service-level cache would need invalidation it cannot receive. Rejected.

**Consequence to accept**: if the database is unreachable, `SettingsService.find()` throws
and rate-limited endpoints return 500 rather than serving unlimited. That is the correct
failure direction — an API that cannot reach its database cannot serve records either —
and it never silently disables the limiter.

---

## R2 — The spec names `effectiveWriteRelays`. What happens to the read path?

**Decision**: Add `APP.effectiveReadRelays(userId)` alongside `APP.effectiveWriteRelays(userId)`,
both built on one shared helper and one `APP.systemRelays()` fetch.

**Rationale**: `APP.ensureRelaysSeeded` is used on both paths today — write in
`profile.js:140`, `settings-relays.js:121`, `onboarding-complete.js:69`, and **read** in
`profile-fetch.js:71`. Deleting it and replacing only the write path would leave
`ProfileFetch.refresh()` querying `APP.loadRelays(userId)` filtered by `read`, which for a
newly onboarded user is the empty list — and `ProfileFetch.fetch` returns `null` on an
empty relay list. The user's kind-0 was just published to the system relays, so their own
profile would stop resolving on refresh. The spec's rationale for including system relays
in discovery ("a user who registered on this deployment published their profile to those")
applies verbatim to this path.

**Alternatives considered**:
- *Write-only union, leave `profile-fetch.js` on the user's own read relays*. Literal
  reading of the spec; produces a silent regression for every new user. Rejected.
- *Union everything unconditionally, ignoring the read/write flags*. Would publish to
  relays the user marked read-only. Rejected — it discards a user setting.

---

## R3 — Column and field naming: `defaultRelays` vs "system relays"

**Decision**: Follow the spec verbatim — the column is `default_relays_json`, the Java
field and JSON key are `defaultRelays` — while all operator-facing copy (admin form label,
documentation, endpoint path) says **system relays**.

**Rationale**: The spec fixes both the schema block and the API payload explicitly, and a
plan should not quietly re-decide a written schema. The spec also explains why the
*endpoint* is renamed (`defaults` now describes the opposite of what happens: these relays
are applied on every publish, not copied once). Renaming the endpoint but not the column is
the spec's deliberate split, so the storage name stays stable while user-visible language
is corrected.

**Consequence to accept**: one layer of translation between `defaultRelays` in the payload
and "system relays" in the UI. `SettingsData.defaultRelays` carries a Javadoc line saying
these are the deployment's system relays, so the mismatch is discoverable at the field.

**Alternatives considered**: renaming the column to `system_relays_json` — cleaner, but it
contradicts an explicit schema in the approved spec. Deferred to a follow-up if the naming
proves confusing in review.

---

## R4 — How does `bottin-client-ui` deserialise the settings payload?

**Decision**: A Java record `DirectorySettings(String blossomUrl, List<String> defaultRelays,
List<String> discoveryRelays)` in `xyz.tcheeric.bottin.client.dto`, deserialised by Jackson
with no annotations.

**Rationale**: The parent `pom.xml` sets `<parameters>true</parameters>` on
`maven-compiler-plugin` (line 450), and Spring Boot auto-registers
`jackson-module-parameter-names`, so record components deserialise by name without
`@JsonProperty`. `RegistrationRequest` in the same package is already a plain record, so
this matches the established pattern. Reusing `SettingsData` from `bottin-core` was
rejected: it is a Lombok `@Value` with a builder and no Jackson creator, and adding
deserialisation concerns to a core domain model would push a framework dependency inward,
against Principle III.

**Alternatives considered**: parsing into `Map<String, Object>` — untyped, and every caller
would repeat the casting. Rejected.

---

## R5 — Where does the settings cache live?

**Decision**: One cache, 60 seconds, inside `DirectorySettingsClient` in
`bottin-client-ui`. `SettingsService` reads through with no cache.

**Rationale**: The client is the only reader whose access is remote and repeated per page
render, and it is the reader for whom staleness is bounded and advertised ("takes effect
within a minute"). `SettingsService` is called in-process by `bottin-admin-ui` (which
writes) and `bottin-api` (see R1); caching there would need cross-process invalidation.
Keeping exactly one cache means exactly one staleness window to explain to an operator.

**Degradation rules**, per the spec's error-handling table:
1. Cache warm and within TTL → serve cached.
2. Cache expired, fetch succeeds → replace and serve.
3. Cache expired, fetch fails → serve the stale cached value and log a warning.
4. No cache at all, fetch fails → serve `DirectorySettings.unconfigured()`
   (`null`, `List.of()`, `List.of()`). Never an environment fallback, never a guess.

---

## R6 — How is the relay-scheme rule enforced without duplicating it badly?

**Decision**: Two enforcement points with different jobs. `SettingsForm` carries a bean
validation `@Pattern` so a bad submission is rejected with field-level errors before
reaching the service; `SettingsService.normalizeRelays` re-validates so **no** caller can
persist a bad value.

**Rationale**: The spec states both explicitly ("Bean validation lives on `SettingsForm`;
the relay-scheme rule lives in `SettingsService` so no path can store a bad value") and its
`AdminSettingsControllerTest` requires the form to fail "without reaching the service".
This is defence at a trust boundary, which Principle VI requires and which the "no
duplicate code" heuristic explicitly does not override.

**Regex**: `^\s*((wss?://\S+)\s*)*$` on the newline-separated textarea. `\s` matches
newlines in Java regex without any flag, so no `Pattern.Flag` is needed, and an empty
textarea passes — an empty relay list is a valid configured state.

**Alternatives considered**: a custom `@ValidRelayList` constraint annotation — an
abstraction with one use site. Rejected under YAGNI (Principle VI).

---

## R7 — Where does the "media server not configured" guard belong?

**Decision**: One guard at the top of `ProfileImage.bind(config)` in
`bottin-client-ui/src/main/resources/static/js/profile-image.js`: when `config.blossomUrl`
is blank, disable `config.fileInputId` and write "Media server not configured" into
`config.errorId`, then return before registering the `change` listener.

**Rationale**: There are exactly two upload call sites — `profile.js:56` and `profile.js:65`
for the profile editor, and the inline loop at
`templates/onboarding/step-profile.html:181` for onboarding — and both already route
through `ProfileImage.bind` passing `blossomUrl` and `errorId`. One guard in the shared
function covers both and cannot be forgotten by a third call site added later. Guarding at
each call site would be a larger diff that leaves the next caller unprotected.

**Behaviour**: uploads are disabled with a stated reason; the rest of the page, including
onboarding's Continue button, proceeds. This is the spec's "degrades, not blocks" rule — a
visitor cannot fix the deployment's configuration, so blocking them turns an operator's
omission into a dead end.

---

## R8 — What happens to the hardcoded public discovery relays at login?

**Decision**: Delete the `DISCOVERY_RELAYS` constant from
`templates/onboarding/step-import.html:55-58` outright. `OnboardingController` injects
`discoveryRelays` — the admin's discovery relays unioned with the system relays — into the
template, and the span is renamed from `configured-relays` to `discovery-relays`.

**Rationale**: The spec requires that the list stop being hardcoded, and the four public
relays currently baked into the template (`relay.damus.io`, `nos.lol`, `relay.primal.net`,
`relay.nostr.band`) are precisely what an admin should now be able to change without
rebuilding the client image. They become the *suggested* seed values in the new how-to
rather than a compiled-in default — otherwise deleting the environment fallback for relays
while keeping a compiled-in relay list would be inconsistent.

The injection must be server-side at render time because `/onboarding/step/import` is
reached **before** any NAP session exists, so the values cannot come from the NAP-protected
`/api/v1/relays/system`. The same ordering constraint applies to the media server URL, which
is why both are model attributes rather than browser fetches.

**Empty-state behaviour**: with no discovery and no system relays configured, the login
profile lookup queries nothing and returns `null` — `ProfileFetch.fetch` already
short-circuits on an empty relay list (`profile-fetch.js:38`). Sign-in never blocks on a
relay.

---

## Confirmed facts (verified in code, not assumptions)

| Fact | Where verified |
|---|---|
| `V3__profile_reach.sql` is the current migration head, so `V4` is free | `bottin-persistence/src/main/resources/db/migration/` |
| `TEXT` columns are already used for relay lists in both H2 and PostgreSQL | `V1__initial_schema.sql:25` (`relays_json TEXT DEFAULT '[]'`) |
| `bottin-client-ui` depends on `bottin-core` only — no persistence or service | `bottin-client-ui/pom.xml` |
| `bottin-admin-ui` depends on `bottin-service` and writes through it already | `bottin-admin-ui/pom.xml`; `AdminDomainsController:39` |
| `bottin-api` already depends on `bottin-service` | `Nip05RecordController` → `Nip05RecordService` |
| `/api/v1/relays` is already a NAP-protected prefix | `bottin-client-ui/src/main/resources/application.yml`, `nap.protected-path-prefixes` |
| `/api/v1/relays/defaults` has exactly one browser caller | `app.js:126`; asserted in `src/test/js/app-session.test.js:36` |
| `ObjectMapper` is injected into services already | `Nip05RecordService:34`, with `serializeRelays` at `:205` as the pattern to mirror |
| Records deserialise without annotations | parent `pom.xml:450` `<parameters>true</parameters>` |
| `ApiEndpointIT` asserts on `/api/v1/relays`, not `/defaults` | `bottin-client-ui/src/test/java/.../integration/ApiEndpointIT.java:60` |

**Test files that must change when `/defaults` is renamed** (found by grep, listed so the
rename is not left half-done): `src/test/js/app-session.test.js`,
`src/test/js/onboarding-complete.test.js`, `src/test/js/profile-fetch.test.js`,
`src/test/java/.../controller/RelayControllerTest.java`,
`src/test/java/.../controller/ProfileControllerTest.java`.
