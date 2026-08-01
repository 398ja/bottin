# Implementation Plan: Nostr Client Onboarding & Account Management

**Branch**: `003-nostr-client-onboarding` | **Date**: 2026-07-22 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/003-nostr-client-onboarding/spec.md`

## Summary

Build a new Maven submodule (`bottin-client-ui`) within the existing bottin multi-module project that provides a Nostr client web application. The application supports onboarding (local key generation/import, profile setup), NAP-based login, nostrdb-backed profile search, NIP-02 follow/block list management, NIP-65 relay list management, and security settings (passphrase change, identity export). All authenticated actions are gated by NAP session cookies.

## Technical Context

**Language/Version**: Java 21 (matching parent POM)  
**Primary Dependencies**: Spring Boot 3.4, Thymeleaf, HTMX, nostrdb-jni (search), nap-spring (NAP auth), nap-client, nostr-java (event signing, NIP-98), nostr-java-client (relay publishing)  
**Storage**: nostrdb LMDB (search index), browser IndexedDB (follow/block lists per identity), no new server-side DB required beyond bottin's existing PostgreSQL  
**Testing**: JUnit 5, Mockito, AssertJ (unit); Testcontainers PostgreSQL + strfry relay (integration); Playwright (E2E)  
**Target Platform**: Linux server (Docker/Jib), modern browser (Chrome/Firefox/Safari with Web Crypto API)  
**Project Type**: Web application — Spring Boot MVC backend + browser UI (HTMX/Thymeleaf)  
**Performance Goals**: NAP auth <1s round trip, search <500ms, follow/unfold <2s reflected, relay publish <5s, passphrase change <1s, onboarding <3 min  
**Constraints**: Must follow Clean Architecture with inward-pointing deps per Principle III; no business logic in controllers; session auth via NAP cookie; search reads only from local nostrdb  
**Scale/Scope**: Single identity per device, max 1000 follows/blocks each; concurrent user count is low (admin/internal tool scale)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Gate I — Identity Mapping Integrity (Principle I)
- **Status**: PASS (not applicable — this submodule does not create, modify, or serve NIP-05 identity mappings; it is a client UI that reads from the existing registry and nostrdb)
- **Why**: The feature is a Nostr client application for onboarding and account management, not a registry. NIP-05 mapping creation/verification stays in the existing bottin modules.

### Gate II — Protocol Compliance (Principle II)
- **Status**: PASS with tracking
- **NIPs used**: NIP-02 (contact lists), NIP-98 (NAP auth), NIP-01 (event format, filters). None of these conflict with or duplicate the existing NIP-05 registry's NIP-05/NIP-19 implementation.
- **Tracking**: Each NIP implementation section must carry a Javadoc reference to the exact spec URL per Principle II.

### Gate III — Clean Architecture (Principle III)
- **Status**: PASS
- The new submodule follows the same layered pattern as `bottin-admin-ui`: controller → service → port/adapter. Domain types live in the module itself; no business logic leaks into controllers.
- The module depends on `bottin-core` (for shared types), `nap-spring` (for NAP auth), `nostrdb-jni` (for search), and `nostr-java` (for event construction/signing).

### Gate IV — Testing Discipline (Principle IV)
- **Status**: PASS
- Unit tests for all services and controllers. Integration tests using Testcontainers for nostrdb and relay fixtures. Each NIP interaction tested against spec.

### Gate V — Secure Coding (Principle VI)
- **Status**: PASS
- NAP session cookie is HTTP-only, Secure, SameSite. Private keys never leave the browser unencrypted. All authenticated endpoints gated by `NapServletFilter`. Rate limiting on public paths.

### Gate VI — Public-by-Design Data (Principle VII)
- **Status**: PASS
- Follow/block lists are user-owned data stored locally. NAP sessions contain only the pubkey principal. Relay lists are stored server-side only as operational data required for the server to publish kind-3/kind-10002 events to the correct relays. No operational PII is durably stored server-side beyond what bottin already persists.

### Gate VII — Clean Code (Principle VIII)
- **Status**: PASS (standard requirement, tracked in review)

**Overall Gate Verdict**: PASS — no violations requiring Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/003-nostr-client-onboarding/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
bottin-client-ui/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/xyz/tcheeric/bottin/client/
    │   │   ├── BottinClientApplication.java     # @SpringBootApplication
    │   │   ├── config/
    │   │   │   ├── ClientWebConfig.java         # MVC + HTMX config
    │   │   │   └── ClientSecurityConfig.java    # NAP filter + auth wiring
    │   │   ├── controller/
    │   │   │   ├── OnboardingController.java    # Registration wizard pages
    │   │   │   ├── LoginController.java         # NAP login flow
    │   │   │   ├── SearchController.java        # nostrdb search
    │   │   │   ├── FollowController.java        # Follow/unfollow
    │   │   │   ├── BlockController.java         # Block/unblock
    │   │   │   ├── ProfileController.java       # Profile view/edit
    │   │   │   ├── BackupController.java        # Export/restore
    │   │   │   ├── SettingsController.java      # Settings hub, relay mgmt, security (passphrase + nsec reveal)
    │   │   │   └── RelayController.java         # Relay list CRUD API
    │   │   ├── service/
    │   │   │   ├── NostrIdentityService.java    # Key gen, import, store, passphrase change
    │   │   │   ├── ProfileService.java          # Profile CRUD
    │   │   │   ├── FollowListService.java       # Follow/block management
    │   │   │   ├── SearchService.java           # nostrdb query facade
    │   │   │   ├── BackupService.java           # Export/restore logic
    │   │   │   └── RelayService.java            # Relay list CRUD + NIP-65 publication
    │   │   └── dto/
    │   │       ├── RegistrationForm.java
    │   │       ├── SearchResult.java
    │   │       └── ProfileForm.java
    │   └── resources/
    │       ├── templates/
    │       │   ├── onboarding/
    │       │   │   ├── step-method.html
    │       │   │   ├── step-profile.html
    │       │   │   ├── step-security.html
    │       │   │   ├── step-confirm.html
    │       │   │   └── step-welcome.html
    │       │   ├── login.html
    │       │   ├── search.html
    │       │   ├── profile.html
    │       │   ├── settings/
    │       │   │   ├── index.html
    │       │   │   ├── relays.html
    │       │   │   ├── passphrase.html
    │       │   │   └── security.html
    │       │   ├── settings.html
    │       │   └── backup.html
    │       ├── static/
    │       │   ├── js/
    │       │   │   ├── nostr-crypto.js        # Web Crypto key gen/signing, passphrase change
    │       │   │   ├── nap-client.js           # NAP init/complete flow
    │       │   │   ├── settings-relays.js      # Relay list add/remove/save UI
    │       │   │   └── app.js
    │       │   └── css/
    │       │       └── styles.css
    │       └── application.yml
    └── test/
        └── java/xyz/tcheeric/bottin/client/
            ├── controller/
            ├── service/
            └── TestClientConfiguration.java
```

**Structure Decision**: Web application with Spring Boot MVC backend and Thymeleaf/HTMX browser UI — matching the established `bottin-admin-ui` pattern exactly. Backend serves HTML via Thymeleaf templates with HTMX for dynamic interactions. Client-side JavaScript handles Nostr key operations (Web Crypto) and NAP challenge signing. No separate frontend build pipeline.

## Complexity Tracking

> (Empty — no Constitution Check violations to justify)
