# Phase 0 Research: Additional administrators

Eight decisions. Each records what was chosen, why, and what was rejected.
Findings marked **verified** were read out of the code or the dependency rather
than recalled.

---

## D1 — Adopt the dormant `admin_users` table

**Decision**: Reshape and use the existing `admin_users` table rather than adding
a list to the settings row or creating a new table.

**Rationale**: **Verified** — the table exists in `V1__initial_schema.sql` with a
`pubkey VARCHAR(64)` column, and `AdminRole`, `AdminUserData`, `AdminUserEntity`
and `AdminUserRepository` all exist. **They reference only each other**: no
controller, service, configuration or migration outside that set mentions any of
them, and nothing seeds the table. Key-based administration was the original
intent; this feature completes it.

A table also fits the access pattern in a way a JSON list does not. Administrator
keys are looked up *individually* on every ACL resolution, and each carries a
role, an enabled flag, a label, and provenance — attributes an array of strings
has nowhere to put. This is the opposite of the relay lists in feature 004, which
are only ever read whole, which is what justified JSON for them.

**Alternatives rejected**:
- *A list on the settings row* — makes every sign-in read and parse the whole
  settings document to answer a single-key question, and has nowhere to record
  who added whom.
- *A new table alongside the dormant one* — leaves dead schema in place
  permanently and invites a future reader to wonder which is authoritative.

---

## D2 — The super administrator is never a stored row

**Decision**: `admin_users` holds ordinary administrators only. The super
administrator is the configured key, and no `SUPER_ADMIN` value is added to
`AdminRole`.

**Rationale**: The master key's authority comes from deployment configuration
(FR-013). If it were also a row, the two could disagree, and there is no correct
answer to which wins. A `SUPER_ADMIN` enum value that the table can never
legitimately hold is a value that can only ever be wrong — it would exist purely
to be validated against.

This also makes FR-009 (the master cannot be removed, edited, or demoted) true by
construction rather than by a guard: there is nothing to remove. A guard can be
forgotten; an absent row cannot.

**Alternatives rejected**:
- *Seed the master key as a `SUPER_ADMIN` row on startup* — creates the
  disagreement above, and a deployment whose configured key changes would leave a
  stale super administrator in the database with nothing to reconcile it.
- *Remove the unused `READONLY` enum value while here* — nothing in this feature
  needs it, and widening scope to delete an unrelated dead value is how a focused
  change becomes an unreviewable one. Recorded as a follow-up.

---

## D3 — Destructive V5 migration, safe because the table is empty

**Decision**: `V5` drops `password_hash` and `username`, adds `label` and
`added_by_pubkey`, and makes `pubkey` `NOT NULL UNIQUE`.

**Rationale**: `password_hash` is `NOT NULL` today, so an administrator could not
be inserted without inventing a password — for a feature whose entire point is
that there are no passwords. `username` is `NOT NULL UNIQUE` and means nothing
once sign-in is by key.

Dropping columns is normally the risky kind of migration. Here it is not, and the
reason is worth stating rather than assuming: **nothing has ever written to this
table** (D1), so there are no rows to lose in any deployment. The migration is
destructive in form and inert in effect.

Ordering matters: the unique index `idx_admin_users_username` must be dropped
before its column. Both PostgreSQL and H2 are targets, so the migration must be
written in syntax both accept and verified against both — H2 is what the test
suite runs on and PostgreSQL is what production runs on, so a statement that
works on only one passes tests and fails on deploy.

**Alternatives rejected**:
- *Leave the columns and write filler values* — a `password_hash` containing
  something meaningless is worse than no column: it looks like a credential.
- *Make them nullable instead of dropping* — keeps two misleading names in the
  schema for no benefit.

---

## D4 — Revocation is bound to removal in the service, behind a port

**Decision**: `AdminUserService.remove(...)` performs both the deletion and the
session revocation. nap's `SessionStore` is reached through a port
(`AdministratorSessionRevoker`) declared in `bottin-service` and implemented in
`bottin-admin-ui`.

**Rationale**: FR-007 says removal ends the session. If the controller called
`remove()` and then a revoker separately, the guarantee would live in the
presentation layer, and the next caller of `remove()` — a REST endpoint, a
cleanup job, a test helper — would silently not revoke. Binding them in the
service makes "removed" and "session ended" one operation that cannot be half
performed.

The port exists because `bottin-service` must not depend on nap-server types
(Principle III). It is not speculative abstraction: there is one implementation
because there is one session store, and the constitution mandates the boundary.

**Verified**: `SessionStore.revokeByPrincipal(String, long)` exists and returns
the number of sessions revoked. Reading `InMemorySessionStore`'s bytecode, it
matches on **`SessionRecord.principalPubkey()`** — the hex form, not the npub.
Removal must therefore pass the canonical hex, and passing an npub would revoke
nothing while appearing to succeed.

**Alternatives rejected**:
- *Orchestrate in the controller* — above.
- *Rely on the ACL resolver refusing the removed key on its next refresh* — see D5.

---

## D5 — Revoke the session; do not rely on the ACL refusing later

**Decision**: Removal revokes sessions explicitly. It does not rely on
`ConfiguredAdminAclResolver` returning denied on the next resolution.

**Rationale**: **Verified** — `NapSessionFilter` caches the ACL decision per
session and only re-resolves after `nap.acl-refresh-interval-seconds`. A removed
administrator whose decision is cached keeps full access until that interval
elapses. The deployment currently configures this, so "the resolver will say no
next time" means "next time, up to an interval from now" — which fails FR-007 and
SC-003 while appearing correct in any test that does not watch the clock.

Revoking the session record removes it from the store, and the filter's session
lookup precedes the ACL cache entirely, so the next request is refused with no
window.

This is exactly the class of defect this project keeps finding at runtime and not
in tests, so the integration test for it must observe a *live* session dying, not
a resolver returning denied.

---

## D6 — Revocation reaches one instance; say so

**Decision**: Implement against the session store the deployment has, and record
the ceiling honestly in code and documentation.

**Rationale**: **Verified** — the session store in use is `InMemorySessionStore`.
Revocation therefore reaches sessions held by the instance that processed the
removal. A deployment running several dashboard instances behind a load balancer
would leave a removed administrator working on the other instances until their
sessions expire.

The bottin deployment runs a single admin instance, so FR-007 holds as specified
today. The honest treatment is a `ponytail:` comment naming the ceiling and the
upgrade path (a shared session store), plus a line in the how-to — not silence,
and not a distributed session store nobody asked for.

SC-003 should be read as scoped to the deployment topology the product actually
ships; the how-to says so, so an operator adding a second instance learns it from
documentation rather than from an incident.

**Alternatives rejected**:
- *Introduce a shared session store now* — solves a problem no deployment has,
  and adds an operational dependency to a dashboard that one person uses.
- *Say nothing* — the requirement would read as universally satisfied.

---

## D7 — The resolver stays the single decision point

**Decision**: `ConfiguredAdminAclResolver` answers, in order: configured master
key → super administrator with all permissions including `MANAGE_ADMINS`; else an
enabled row in `admin_users` → administrator with read and write but **not**
`MANAGE_ADMINS`; else denied.

**Rationale**: Feature 005 deliberately made this the one place that decides who
administers the deployment, and made the master key hold an explicit
`SUPER_ADMIN` role rather than merely being authenticated. That is what lets this
feature add a role instead of retrofitting authorization across every route.
Keeping both sources in one resolver means there is still exactly one answer to
"who is this?", and one place to read when it is wrong.

The permission difference is what makes US3 real: the added administrator's
session simply never carries `MANAGE_ADMINS`, so the interceptor refuses the
management endpoints whether or not the interface offered them.

Checking configuration **first** also settles what happens if a stored row ever
matches the configured key — which the interface will not produce (FR-004a) but
a later change of `BOTTIN_ADMIN_NPUB` to an already-added key would. The
configured key wins, the stored row is shadowed, and the holder is the super
administrator. Ordering gives this for free; no reconciliation step is needed,
and none should be added, because a job that deleted the shadowed row would be
editing the administrator list in response to a configuration change.

**Verified**: nap-spring's auto-configuration declares both
`napPermissionInterceptor` and a `napPermissionWebMvcConfigurer` that registers
it, so `@RequiresPermission` is genuinely enforced rather than decorative. This
was worth confirming — an annotation that nothing installs would make FR-008 a
comment.

---

## D8 — Canonicalisation lives in `bottin-service`

**Decision**: A `NostrPublicKeys` helper in `bottin-service` converts `npub1…` or
64-character hex to canonical lowercase hex, using nostr-java's `Bech32`.
`bottin-service` gains the nostr-java dependency.

**Rationale**: **Verified** — `bottin-core` depends only on Jackson, Lombok and
SLF4J, and the constitution requires it to stay free of infrastructure; adding a
codec dependency to it for one feature is the wrong module to widen. Validation
and normalisation already live in the service layer — `SettingsService` normalises
relay URLs there — so this is the established home for exactly this kind of rule.

Principle VI forbids hand-rolling bech32, and Principle II requires NIP-19
decoding to follow the library, so nostr-java does the decoding in both the
service and the resolver.

**Known duplication, deliberately deferred**: `PubkeyCodec` in `bottin-reach`
does the same conversion. Consolidating them means either `bottin-reach`
depending on `bottin-service` or a shared module, and `bottin-admin-ui` cannot
simply depend on `bottin-reach` — an unrelated feature module — to get it. That
refactor is worth doing and is not worth doing inside this feature; it is
recorded here so the second copy is a known cost rather than an oversight.

**Alternatives rejected**:
- *Add nostr-java to `bottin-core`* — widens the strictest module for one helper.
- *Depend on `bottin-reach` from `bottin-admin-ui`* — couples the dashboard to an
  unrelated feature module and points a dependency the wrong way.
- *Keep the private copy in the resolver and a second in the service* — the same
  rule in two places, which is how the two drift apart.
