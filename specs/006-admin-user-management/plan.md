# Implementation Plan: Additional administrators with super-admin and admin roles

**Branch**: `006-admin-user-management` | **Date**: 2026-08-02 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/006-admin-user-management/spec.md`

## Summary

Let the configured master key holder add and remove further administrator public
keys from the settings page. Added administrators sign in with their own key and
use the whole dashboard, but cannot manage the administrator list — a refusal
enforced at the decision point, not by hiding a control. Removal ends any session
the removed administrator currently holds, at once.

The approach adopts the `admin_users` table that has been dormant since V1 and
reshapes it for key-based administrators, keeps the master key in configuration
where no interface can edit it, and extends the single ACL decision point
established by feature 005 to consult the stored list after the configured key.

## Technical Context

**Language/Version**: Java 21 (parent `pom.xml` `java.version`)
**Primary Dependencies**: Spring Boot 3.4.1 (WebMVC, Data JPA, Validation, Thymeleaf, Security), nap-spring / nap-server / nap-core 0.23.0, nostr-java (NIP-19 bech32), Flyway 10.10.0, Lombok
**Storage**: PostgreSQL 42.7.3 (production) / H2 2.2.224 (dev + test); new Flyway migration `V5`
**Testing**: JUnit 5, Mockito, AssertJ, MockMvc slices (`bottin-admin-ui`), Testcontainers (`bottin-tests/bottin-it`)
**Target Platform**: Linux server, Docker Compose deployment
**Project Type**: Web application — multi-module Maven, server-rendered admin UI
**Performance Goals**: Administrator lookup on ACL resolution, which nap caches per session for `nap.acl-refresh-interval-seconds`; single-digit administrator counts
**Constraints**: Session revocation is bounded by the session store's reach — see the single-instance ceiling in research.md D6; no new browser JavaScript (server-rendered forms, as the rest of the dashboard)
**Scale/Scope**: Single-digit administrators per deployment; one new table shape, one service, one controller, one settings-page section

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Verdict | Reasoning |
|---|---|---|
| **I. Identity Mapping Integrity** | ✅ Pass | No NIP-05 record path is touched. The principle's key rule still binds: every stored pubkey is 64-character lowercase hex, and `npub` input is decoded and validated before persistence (FR-002, D8). |
| **II. Protocol Compliance (NIPs)** | ✅ Pass | NIP-19 decoding is done by nostr-java, never hand-rolled (D8). The canonical form stored is NIP-01 hex. |
| **III. Clean Architecture** | ✅ Pass | Model in `bottin-core`, adapter in `bottin-persistence`, use case in `bottin-service`, presentation in `bottin-admin-ui`. nap's `SessionStore` is kept out of the service behind a port (D4), so no infrastructure leaks into the use-case layer. |
| **IV. Testing Discipline** | ✅ Pass | Unit tests per layer plus an integration test that proves a live session stops working on removal — the one requirement most able to pass a unit test and fail in the deployment (D5, D6). |
| **V. Virtual Threads** | ➖ N/A | No new I/O fan-out; administrator lookup is a single indexed read. |
| **VI. Secure Coding & Code Quality** | ✅ Pass | Management endpoints require `MANAGE_ADMINS` and are refused when called directly (FR-008). Rejections use `BottinException` with the `{WHAT}. {WHY}. Suggestion: {ACTIONABLE}.` template. Every addition, removal, and refusal is logged (FR-010). |
| **VII. Public-by-Design Data & Privacy** | ✅ Pass | Public keys are public by protocol design. The label is operator-supplied and minimal; no new PII is collected. Audit lives in the security log under existing log retention, not a new durable identity-bearing table. |
| **VIII. Clean Code Craftsmanship** | ✅ Pass | No flag arguments; command/query separation between the list query and the add/remove commands; no dead code introduced — the feature instead retires dead columns. |

**Security Requirements**: admin paths authenticated (existing chain, extended with a role); security events logged with structured fields; no secrets introduced.

**Result**: no violations. Complexity Tracking is therefore omitted.

## Project Structure

### Documentation (this feature)

```text
specs/006-admin-user-management/
├── plan.md              # This file
├── research.md          # Phase 0 output — the eight decisions
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── admin-administrators.md
├── checklists/
│   └── requirements.md  # From /speckit.specify
└── tasks.md             # /speckit.tasks — not created here
```

### Source Code (repository root)

```text
bottin-core/
└── src/main/java/xyz/tcheeric/bottin/core/
    ├── model/AdminUserData.java              # MODIFY — drop username/passwordHash, add label/addedByPubkey
    └── exception/AdministratorNotFoundException.java   # NEW

bottin-persistence/
└── src/main/
    ├── java/xyz/tcheeric/bottin/persistence/
    │   ├── entity/AdminUserEntity.java       # MODIFY — reshape to the key-based columns
    │   └── repository/AdminUserRepository.java  # MODIFY — findByPubkey, existsByPubkey
    └── resources/db/migration/
        └── V5__admin_users_key_based.sql     # NEW

bottin-service/
├── pom.xml                                   # MODIFY — add nostr-java for NIP-19 decoding
└── src/main/java/xyz/tcheeric/bottin/service/
    ├── AdminUserService.java                 # NEW — add / remove / list, canonicalisation, revocation
    ├── NostrPublicKeys.java                  # NEW — npub-or-hex → canonical hex
    └── port/AdministratorSessionRevoker.java # NEW — port; keeps nap out of the service

bottin-admin-ui/
└── src/main/
    ├── java/xyz/tcheeric/bottin/admin/
    │   ├── security/ConfiguredAdminAclResolver.java      # MODIFY — configured key, then stored list
    │   ├── security/NapAdministratorSessionRevoker.java  # NEW — port adapter over nap SessionStore
    │   ├── controller/AdminAdministratorsController.java # NEW — add / remove
    │   ├── controller/AdminSettingsController.java       # MODIFY — put the list on the model
    │   └── dto/AddAdministratorForm.java                 # NEW
    └── resources/templates/admin/settings.html           # MODIFY — administrators section

bottin-tests/bottin-it/
└── src/test/java/.../AdministratorLifecycleIT.java       # NEW — add, sign in, remove, session dies

docs/how-to/configure-admin-access.md                     # MODIFY — managing administrators
```

**Structure Decision**: The existing module layout is used unchanged, with each
concern landing in the module the constitution assigns it. The only structural
addition is the `port` package in `bottin-service`, required so that immediate
session revocation can be a service-layer guarantee without nap-server types
reaching the use-case layer (research D4).

## Phase 0 — Research

See [research.md](./research.md). Eight decisions, of which four carry real risk:

- **D4** — where revocation is bound, so it cannot be forgotten by a future caller.
- **D5** — why the ACL cache makes "the resolver will refuse next time" insufficient.
- **D6** — the single-instance ceiling on revocation, named rather than glossed.
- **D8** — where canonicalisation lives, given `bottin-core` has no nostr-java.

## Phase 1 — Design & Contracts

- [data-model.md](./data-model.md) — the reshaped `admin_users`, the V5 migration, and why a destructive migration is safe here.
- [contracts/admin-administrators.md](./contracts/admin-administrators.md) — the two form endpoints, their guards, and every refusal.
- [quickstart.md](./quickstart.md) — how to exercise the feature end to end, including the checks that only fail in a running deployment.

## Post-Design Constitution Re-check

Re-evaluated after the design above: **no new violations**.

Two points were re-examined because they are where this design could have drifted:

- **Is the `port` package a speculative abstraction (Principle VIII, YAGNI)?** No.
  Principle III requires infrastructure to stay behind ports owned by the module
  that needs them, and the alternative — orchestrating removal in the controller
  — puts a security guarantee in the presentation layer where the next caller can
  omit it. The port has one implementation because there is one session store,
  not because a second is anticipated.
- **Does reshaping a V1 table violate Identity Mapping Integrity's durable-state
  rule?** No. `admin_users` has never held a row and no identity mapping refers
  to it; the migration touches no NIP-05 record. See data-model.md for the
  evidence that the table is empty by construction.
