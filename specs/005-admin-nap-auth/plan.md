# Implementation Plan: Admin sign-in with a Nostr key

**Branch**: `005-admin-nap-auth` | **Date**: 2026-08-01 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/005-admin-nap-auth/spec.md`

## Summary

Replace the admin dashboard's username-and-password sign-in with proof of control of a Nostr key,
admitted only when the proven public key matches the one an operator configured.

The approach is to adopt nap-spring's existing challenge/response machinery — the same one the
client already uses — rather than write an authentication flow. That library turns out to supply
more than expected: `NapAuthController` already exposes `init`, `complete`, `checkSession` **and
`logout`**, and `nap-server` ships a full role/permission model (`PermissionRegistry`,
`RoleDefinition`, `RegistryAclResolver`, `AclStore`) plus `SessionStore.revokeByPrincipal`. So the
super-administrator role FR-015 requires is a matter of *declaring* roles, not building them.

The work that remains is genuinely bottin's: declaring the permission registry, resolving the
configured key to the super-administrator role, redirecting browsers to a sign-in page rather than
answering 401, building that page, and giving the admin browser the encrypted-key-and-passphrase
handling the client already has.

## Technical Context

**Language/Version**: Java 21; browser JS in the same ES5 style as the client (no build step)
**Primary Dependencies**: Spring Boot 3.4.1 (WebMVC, Thymeleaf, Security), **nap-spring /
nap-server / nap-core** (challenge-response, ACL, session store), nostr-java (NIP-19 decoding),
Web Crypto API in the browser (via the client's existing `nostr-crypto.js`)
**Storage**: **None new.** The administrator public key is deployment configuration; the session
lives in nap-server's `SessionStore`; the encrypted private key lives in the administrator's own
browser. No migration, no schema change.
**Testing**: JUnit 5 + Mockito + AssertJ, `@WebMvcTest` slices for the admin controllers, Vitest +
jsdom for the browser key handling, `mvn -q verify`
**Target Platform**: `bottin-admin` container, reached from an operator's browser
**Project Type**: Server-rendered admin dashboard within a multi-module Maven build
**Performance Goals**: Sign-in is a two-request handshake; a returning administrator unlocks
locally with no additional round trip
**Constraints**: The private key and the passphrase must never reach the server (FR-002, FR-018);
an unconfigured deployment must admit nobody (FR-005); the role decision must be one place (FR-015)
**Scale/Scope**: One administrator; ~6 admin routes to protect; 2 modules touched
(`bottin-admin-ui`, and a shared home for browser crypto)

Four unknowns are resolved in [research.md](./research.md); none remain open.

## Constitution Check

*GATE: evaluated against `.specify/memory/constitution.md` v1.1.0. Re-checked after Phase 1 — still passing.*

| Principle | Verdict | Evidence |
|---|---|---|
| **I. Identity Mapping Integrity** | PASS | No mapping is created, changed, or served differently. The feature changes *who may reach* the admin surface that edits mappings — tightening it, since a shared password becomes a key only one person holds. FR-010 requires every currently protected page to stay protected, and the quickstart verifies it route by route. |
| **II. Protocol Compliance (NIPs)** | PASS | NIP-19 for decoding the configured `npub`, delegated to nostr-java rather than hand-decoded. NIP-98 is the shape of the signed proof and is produced and verified entirely inside nap-core's `Nip98Validator` — bottin neither builds nor parses the event. `.well-known/nostr.json` is untouched. |
| **III. Clean Architecture** | PASS | Everything lands in `bottin-admin-ui`, which the constitution designates presentation-only, plus configuration. The role decision is a single `AclResolver` bean reading a configuration property — no business logic, no persistence, no new service. `bottin-core`, `-service`, `-persistence` are untouched. |
| **IV. Testing Discipline** | PASS | `@WebMvcTest` slices for the redirect-not-401 behaviour and for permission enforcement; unit tests for the resolver's four outcomes (match, mismatch, unconfigured, unusable value); Vitest for encrypt/unlock/erase in the browser. Boundary cases named: wrong passphrase, expired challenge, replayed proof, clock skew, unconfigured deployment. |
| **V. Virtual Threads** | N/A | No I/O fan-out. The handshake is two sequential requests. |
| **VI. Secure Coding & Code Quality** | PASS | **No hand-rolled crypto**: signing and verification are nap-core and nostr-java; key encryption at rest is the browser's Web Crypto through the client's existing `nostr-crypto.js`. The sign-in endpoints are public and therefore rate-limited (see research R4). Failures raise `BottinException` subclasses with error code, retryable flag, and suggestion. Removing the shared password is itself the security win. |
| **VII. Public-by-Design Data & Privacy** | PASS | A public key is not secret — that is exactly what lets it live in a compose file where a password should not. The private key and passphrase never reach the deployment (FR-002, FR-018), so there is nothing new to leak. Security logs record the pubkey, never key material (FR-012). |
| **VIII. Clean Code Craftsmanship** | PASS | One `AclResolver`, one permission registry, one filter. FR-015's "one decision point" is a design requirement, not an aspiration: the follow-up feature adds a role by extending the registry rather than by touching every route. |
| **Security Requirements** | PASS | Admin endpoints keep requiring authentication; every sign-in success, failure, and sign-out is logged with structured fields and no secrets (FR-012); no secret is added to configuration — the value added is public. |
| **Documentation Standards (Diátaxis)** | PASS | A how-to for configuring the administrator key and signing in, linked from `docs/README.md`; the compose reference and deployment guide updated for the variable change. |
| **Development Workflow** | PASS | Conventional Commits per module, `mvn -q verify` before each commit, version bump and CHANGELOG on completion. |

**Gate result: PASS — no violations to justify.**

### One constitutional tension worth naming

Principle VI requires public endpoints to be rate limited. `/api/v1/auth/init` on the admin
dashboard is public by necessity — it must answer before anyone is authenticated. It is also a
free oracle for "is this npub the administrator?" if it behaves differently for the configured key.
Research R4 resolves both: issue a challenge uniformly regardless of the npub offered, and rate
limit issuance per client address. The admin module has no rate limiter today, so this is new work
rather than reuse.

## Project Structure

### Documentation (this feature)

```text
specs/005-admin-nap-auth/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 — four decisions, with what nap-spring already provides
├── data-model.md        # Phase 1 — configuration, roles, session, browser-held identity
├── quickstart.md        # Phase 1 — operator setup and developer verification
├── contracts/
│   ├── README.md
│   ├── auth-endpoints.md        # init / complete / checkSession / logout, as nap-spring exposes them
│   ├── admin-access-contract.md # which routes need which permission, and redirect-not-401
│   └── browser-identity.md      # first sign-in, unlock, sign-out erase
├── checklists/requirements.md   # Spec quality checklist (all items pass)
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
bottin-admin-ui/
├── pom.xml                                        # MODIFY — add nap-spring
├── src/main/java/xyz/tcheeric/bottin/admin/
│   ├── config/
│   │   ├── AdminSecurityConfig.java               # MODIFY — form login and the in-memory user go
│   │   ├── AdminNapConfig.java                    # NEW — permission registry, AclResolver, NAP filters
│   │   ├── AdminPermissions.java                  # NEW — permission and role constants, one place
│   │   └── RequireAdminSessionFilter.java         # NEW — redirect to sign-in, not 401
│   ├── security/
│   │   └── ConfiguredAdminAclResolver.java        # NEW — the single role decision point (FR-015)
│   └── controller/
│       ├── AdminLoginController.java              # MODIFY — render the key sign-in page
│       └── (all admin controllers)                # MODIFY — @RequiresPermission
└── src/main/resources/
    ├── application.yml                            # MODIFY — nap.* block, admin npub property
    ├── templates/admin/login.html                 # MODIFY — key + passphrase, no username/password
    └── static/js/                                 # NEW — admin sign-in behaviour (see research R3)

bottin-client-ui/src/main/resources/static/js/
├── nostr-crypto.js                                # SHARED — see research R3 for how
└── nap-client.js                                  # DELETE or implement — currently a stub that throws

docker-compose.yml                                 # MODIFY — BOTTIN_ADMIN_NPUB in, admin password out
.env                                               # MODIFY — same
docs/
├── how-to/configure-admin-access.md               # NEW
├── how-to/docker-deployment.md                    # MODIFY
├── reference/docker-compose-configuration.md      # MODIFY
└── README.md                                      # MODIFY — link the new how-to
```

**Structure Decision**: The feature is contained in `bottin-admin-ui` plus configuration, which
matches the constitution's designation of that module as presentation-only — the authentication
decision is a configuration concern, not a domain one. The single structural question is where the
browser's key handling lives, since `nostr-crypto.js` sits in `bottin-client-ui` and the dashboard
is a different application that cannot reach it. Research R3 resolves that; it is the one decision
in this plan that touches a module other than `bottin-admin-ui`.

## Implementation Sequence

Ordered so that each step is independently verifiable and the risky browser work comes last.

| Step | Deliverable | Observable change |
|---|---|---|
| 1 | `AdminPermissions`, `ConfiguredAdminAclResolver` + unit tests | None — not yet wired |
| 2 | `AdminNapConfig`: permission registry, NAP filters, session store | Auth endpoints exist; dashboard still on form login |
| 3 | `RequireAdminSessionFilter` + `@RequiresPermission` on controllers | Admin routes demand a NAP session, redirecting to sign-in |
| 4 | Shared browser crypto per R3 | None — relocation only, client tests must stay green |
| 5 | Sign-in page: first sign-in, unlock, sign-out erase | The dashboard is reachable by key |
| 6 | Remove form login, the in-memory user, and the password variables | Password sign-in is gone |
| 7 | Rate limiting and uniform challenge issuance (R4) | The endpoint stops being an oracle |
| 8 | Docs, compose, `.env`, version bump, CHANGELOG | — |

Step 6 is deliberately after step 5: removing the only working sign-in before its replacement is
demonstrable would leave the dashboard unreachable in any intermediate state.

## Complexity Tracking

> No Constitution Check violations. Table intentionally empty.

Two decisions could read as complexity and are recorded in [research.md](./research.md) instead:

- **R3** — sharing `nostr-crypto.js` between two applications rather than copying it. Duplicating
  key-handling code is the alternative, and it is worse: two copies of encryption logic drift, and
  the one that drifts silently is the one nobody is testing.
- **R4** — adding a rate limiter to a module that has none, because the sign-in endpoint is public
  by necessity and would otherwise answer "is this npub the administrator?" to anyone who asks.
