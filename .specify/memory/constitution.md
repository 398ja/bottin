<!--
  Sync Impact Report
  ==================
  Version change: 0.0.0 (template) -> 1.0.0 -> 1.1.0 -> 1.2.0 -> 1.3.0
  1.3.0 amendment (sourced from the 007-follow-block-lists feature):
    - Principle II: added NIP-51 (Lists) and NIP-44 (Encrypted Payloads,
      Versioned) to the feature-driven NIPs. 007 publishes a client user's
      blocks as a NIP-51 mute list whose entries are NIP-44 sealed to their
      own author, so both are relied upon and, per this principle, both now
      imply a passing test against the linked spec.
    - No principle was added, removed or redefined; this is a material
      expansion of an existing list, hence MINOR.
  1.2.0 amendment (sourced from the 004-006 feature cycle):
    - Principle III: corrected `bottin-web` to `bottin-api` — the named
      module did not exist. Added `bottin-client-ui` and
      `bottin-web-assets`, and the rule that the starter MUST NOT scan
      delivery layers (an auto-configuration contributing controllers and
      a security chain broke every consuming application).
    - Principle IV: added "a green build is not evidence that the
      deployment works" — runtime verification, assert-the-effect,
      mutation-check security tests, both database engines, and the
      warning that an unrunnable test is worse than a failing one. Each
      clause traces to a defect that passed the full suite.
    - Security Requirements: admin authentication is key-based (NAP), and
      authorization decisions live at a single point where a refusal holds
      against a direct request rather than a hidden control.
    - Development Workflow: commit scopes corrected to real modules.
  1.1.0 amendment (sourced from AGENTS.md):
    - Expanded Principle III (Clean Architecture) with the Component
      Principles (REP/CCP/CRP cohesion; ADP/SDP/SAP coupling) and the
      sanctioned design-pattern catalogue.
    - Expanded Principle IV (Testing Discipline) with the AAA pattern,
      F.I.R.S.T., one-concept-per-test, the should[Behavior]When[State]
      naming convention, test data builders, and jqwik property tests.
    - Expanded Principle VI with the standard error-message template
      ("{WHAT}. {WHY}. Suggestion: {ACTIONABLE}.").
    - Added Principle VIII (Clean Code Craftsmanship): meaningful names,
      small single-purpose functions, Command-Query Separation, no flag
      arguments, exceptions over error codes, never return null.
    - Added two sections: "Documentation Standards" (Diataxis) and
      "Logging Standards" (structured, subject-first, secrets masked).
    - Development Workflow: added the "bump version on task completion"
      rule and the Diataxis documentation-linking requirement.
    - Deliberately NOT imported from AGENTS.md: its residual Cashu-NUT
      compliance clause (bottin uses Nostr NIPs — Principle II) and its
      WalletOperationException references (bottin uses BottinException —
      Principle VI), both copy-paste artifacts from an upstream template.
  Ratification: First concrete constitution for bottin. All template
    placeholders replaced with bottin-specific content.
  Adapted from cashu-mint constitution (1.1.0) at the user's request,
    retargeted from Cashu protocol to the Nostr protocol:
    - Principle I: "Token Integrity" -> "Identity Mapping Integrity"
      (a NIP-05 registry's existential concern is the correctness and
      authorization of username@domain -> pubkey mappings, not token
      supply).
    - Principle II: "Protocol Compliance (Cashu NUTs)" ->
      "Protocol Compliance (Nostr NIPs)" with the authoritative source
      changed to github.com/nostr-protocol/nips and per-NIP URLs
      (NIP-01, NIP-05, NIP-19 mandatory; NIP-02, NIP-65, NIP-11
      feature-driven).
    - Principle III (Clean Architecture): retargeted to bottin modules
      (bottin-core / -persistence / -service / -web / -verification /
      -reach / -admin-ui / -spring-boot-starter).
    - Principle IV (Testing Discipline): retargeted to bottin test
      layout (Testcontainers PostgreSQL + strfry relay fixture).
    - Principle V (Virtual Threads): retargeted to relay/DNS/HTTP I/O.
    - Principle VI (Secure Coding): retargeted to BottinException, no
      hand-rolled bech32/crypto, public-endpoint rate limiting.
    - Principle VII: "Data Minimisation and Customer-Identity Custody"
      -> "Public-by-Design Data & Privacy Boundaries" (NIP-05 data is
      public by protocol design; operational PII is minimised).
    - Removed all Cashu/mint-specific clauses (blinded outputs, proofs,
      BDHKE, melt/swap, webhook amount-binding) — not applicable.
  Added sections (1.0.0):
    - 7 Core Principles (Identity Mapping Integrity, Protocol Compliance,
      Clean Architecture, Testing Discipline, Virtual Threads, Secure
      Coding & Code Quality, Public-by-Design Data & Privacy Boundaries)
    - Security Requirements
    - Development Workflow
    - Governance
  Removed sections: None (template placeholders replaced in 1.0.0)
  Templates requiring updates:
    - .specify/templates/plan-template.md — ✅ compatible (Constitution
      Check reads gates from this file generically)
    - .specify/templates/spec-template.md — ✅ compatible
    - .specify/templates/tasks-template.md — ✅ compatible
  Follow-up TODOs: None.
-->

# bottin Constitution

## Core Principles

### I. Identity Mapping Integrity (NON-NEGOTIABLE)

bottin is the authority on the NIP-05 identity mappings it serves. Every code
path that creates, updates, verifies, or serves a `username@domain -> pubkey`
mapping MUST preserve the following invariants:

- **Authorized mappings only**: a record MUST only be created or modified under
  a domain whose ownership/control has been verified (DNS-TXT or
  `.well-known`); operations on unverified domains MUST be rejected or held,
  never silently served as authoritative
- **Uniqueness**: the `(username, domain)` pair MUST be unique; the registry
  MUST NOT serve two pubkeys for the same identifier
- **Faithful serving**: the `.well-known/nostr.json` response MUST reflect
  exactly the enabled, stored records for the queried name(s) — no fabricated,
  stale, disabled, or unverified entries
- **Valid keys**: every stored and served pubkey MUST be a 64-character
  lowercase hex key (NIP-01); `npub` inputs MUST be decoded and validated
  (NIP-19) before persistence
- **Durable state**: records, domains, and verification status MUST live in a
  durable store with atomic transitions; in-process maps and caches MUST NOT
  be the source of truth for write decisions
- **No silent reassignment**: changing the pubkey bound to an existing
  identifier MUST be an authorized, audited operation; verification revocation
  or expiry MUST disable serving rather than continue serving an unverified
  mapping
- **Verification honesty**: a domain's `verified` state MUST reflect a real,
  re-checkable proof; expired or failed verification MUST transition the domain
  out of verified serving

Identity-mapping violations are blocking defects. Performance, ergonomics, and
refactor cleanliness MUST yield to mapping integrity.

### II. Protocol Compliance (Nostr NIPs)

bottin MUST implement Nostr NIP (Nostr Implementation Possibilities)
specifications faithfully and visibly. The authoritative source is the upstream
Nostr protocol repository:

- **Specification index**: [github.com/nostr-protocol/nips](https://github.com/nostr-protocol/nips)
- **Mandatory NIPs** (the registry MUST implement):
  - [NIP-01 — Basic protocol flow, events, and filters](https://github.com/nostr-protocol/nips/blob/master/01.md)
  - [NIP-05 — Mapping Nostr keys to DNS-based internet identifiers](https://github.com/nostr-protocol/nips/blob/master/05.md)
  - [NIP-19 — bech32-encoded entities (npub, nprofile)](https://github.com/nostr-protocol/nips/blob/master/19.md)
- **Feature-driven NIPs** (implemented where a feature requires them; advertised
  or relied-upon support implies a passing test against the linked spec):
  - [NIP-02 — Follow List (contact lists)](https://github.com/nostr-protocol/nips/blob/master/02.md)
  - [NIP-51 — Lists](https://github.com/nostr-protocol/nips/blob/master/51.md) — the mute list
    a client user's blocks are published as
  - [NIP-44 — Encrypted Payloads (Versioned)](https://github.com/nostr-protocol/nips/blob/master/44.md) —
    seals the mute list's entries to their own author, so a block is not a public
    statement about the person blocked
  - [NIP-65 — Relay List Metadata](https://github.com/nostr-protocol/nips/blob/master/65.md)
  - [NIP-11 — Relay Information Document](https://github.com/nostr-protocol/nips/blob/master/11.md)
  - [NIP-98 — HTTP Auth](https://github.com/nostr-protocol/nips/blob/master/98.md) — the signed
    proof that admin and client sign-in rest on (NAP)

Compliance rules:

- The `.well-known/nostr.json` endpoint MUST conform to NIP-05: the `names`
  map (identifier -> hex pubkey) and, when present, the `relays` map
  (pubkey -> relay URL list) MUST match the spec's shape exactly
- Pubkeys MUST be handled as 64-character hex per NIP-01; `npub`/bech32
  encoding and decoding MUST follow NIP-19
- Relay interaction (REQ subscriptions, tag filters such as `#p`, EOSE
  handling) MUST follow NIP-01; follower data MUST be read as NIP-02 kind-3
  events and relay discovery as NIP-65 kind-10002 events
- Each component implementing a NIP MUST carry a Javadoc reference to the exact
  spec URL above; when a NIP is revised upstream, the linked URL SHOULD be
  pinned to a specific commit hash
- Non-standard extensions (admin endpoints, internal stats, mock fixtures)
  MUST be clearly separated from NIP-compliant public paths and MUST NOT be
  reachable on public endpoints without explicit gating
- Breaking changes to public NIP request/response shapes (notably the
  `.well-known/nostr.json` contract) MUST bump bottin's MAJOR version
- Upstream NIP amendments MUST be tracked: when
  [nostr-protocol/nips](https://github.com/nostr-protocol/nips) changes a spec
  bottin relies on, a follow-up issue MUST be filed within one release cycle to
  re-test against the new revision or drop the reliance

### III. Clean Architecture

Every module MUST follow Clean Architecture with inward-pointing dependencies,
matching bottin's established module layout:

- **bottin-core**: Pure domain models, ports/abstractions, and the
  `BottinException` hierarchy, with zero framework or infrastructure
  dependencies
- **bottin-service** and feature modules (**bottin-verification**,
  **bottin-reach**): Use cases depending only on core abstractions and
  repository/gateway ports
- **bottin-api**: REST controllers and DTOs (the delivery layer); controllers
  MUST delegate to services and MUST NOT contain business or protocol logic
- **bottin-persistence**: JPA entities, Spring Data repositories, and Flyway
  migrations — adapters behind repository ports
- **bottin-admin-ui** and **bottin-client-ui**: Presentation only
  (Thymeleaf/HTMX); no business logic. A rule that decides who may do what
  belongs in a service or a single security decision point, never in a template
  or a controller
- **bottin-web-assets**: Browser code shared by both user interfaces (key
  handling, the authentication handshake). A second copy of security-relevant
  browser code drifts, and the copy that drifts silently is the one without tests
- **bottin-spring-boot-starter**: Composition and auto-configuration. It MUST NOT
  scan delivery layers: an auto-configuration that contributes controllers or a
  security filter chain imposes them on every application that merely has the
  starter on its classpath

The Ports & Adapters, Repository, and Factory patterns are mandatory.
Infrastructure (JPA, relay clients, DNS/HTTP clients) MUST NOT leak into the
domain or use-case layers. Relay, DNS, and external `.well-known` access MUST
go through ports owned by the relevant feature module.

Module boundaries MUST respect the component principles:

- **Cohesion** — REP (a component is the unit of release), CCP (classes that
  change together live together), CRP (do not force consumers to depend on what
  they do not use)
- **Coupling** — ADP (no cycles in the module dependency graph; break cycles
  with Dependency Inversion), SDP (depend in the direction of stability), SAP
  (stable components are abstract)

Apply established design patterns (Strategy, Adapter, Decorator, Template
Method, Builder, State, Chain of Responsibility, etc.) where they simplify the
design; never force a pattern where a simpler solution suffices.

### IV. Testing Discipline

All code MUST meet the following testing standards:

- **Unit tests** (`*Test.java`): Run via `mvn -q test`; use Mockito; every test
  method MUST have a plain-English comment describing the scenario and follow
  the Arrange-Act-Assert structure with behaviour-revealing names
- **Integration tests** (`*IT.java`, `bottin-tests/bottin-it`): Use
  Testcontainers for PostgreSQL; relay-dependent paths MUST be exercised
  against the strfry relay test fixture; external NIP-05 verification MUST be
  exercised against an HTTP test harness
- Tests MUST cover realistic registry scenarios for every NIP that serves or
  derives identity — registration, duplicate username, unverified-domain
  rejection, verification expiry/revocation, `.well-known` serving, external
  verification success/failure, and (for relay features) cross-relay
  de-duplication plus partial/failed relay gathering
- The `mvn -q verify` gate MUST pass before commit; identity-serving and
  verification paths MUST be covered by both unit and integration tests
- Mocked approximations are forbidden for verification-state and serving
  transitions where a real container or fixture is feasible

**A green build is not evidence that the deployment works.** Changes that alter
wiring, security filters, schema, or served assets MUST be exercised against a
running deployment before they are called done. This is not belt-and-braces: it
is the only thing that has caught a repeated class of defect here — an ACL check
that refused every key, a principal discarded by the security chain, a required
bean that stopped a sibling application from starting, and a migration that only
one database engine accepted. Every one passed the full suite.

- **Assert the effect, not the call.** A test proving a collaborator was invoked
  does not prove what it was invoked for. Where a requirement is about an outcome
  — a session ending, a row not being written — the test MUST observe the outcome
- **A test that has never failed has not been shown to work.** For any test
  guarding a security property or a hard-won bug fix, break the code deliberately
  once and confirm the test fails. Record that in the commit message
- **Tests MUST NOT encode the same assumption as the code they guard.** Where
  behaviour depends on an external contract, derive the test from the contract —
  the library's own signatures and documentation — not from the implementation's
  reading of it
- **Both database engines MUST be exercised** for schema changes: H2 backs the
  test suite and PostgreSQL backs production, and a statement only one accepts
  passes the build and fails on deploy
- **A test that cannot run is worse than one that fails.** Placing a test where
  no execution is bound, or naming it so no runner selects it, produces a green
  build that asserts nothing

All tests MUST follow Clean Code (Chapter 9) discipline:

- **AAA structure**: each test is split into Arrange / Act / Assert (or
  Given / When / Then) sections
- **One concept per test**: assert a single logical concept; split unrelated
  assertions into separate test methods
- **F.I.R.S.T.**: Fast, Independent, Repeatable, Self-validating, Timely
- **Descriptive names**: follow `should[ExpectedBehavior]When[StateUnderTest]`;
  no `testMethod1`-style names
- **Boundary coverage**: null inputs, empty collections, boundary values
  (0, -1, MAX), invalid inputs, and concurrency where applicable
- **Test data builders / factory methods** for complex fixtures; avoid
  duplicated setup
- **Property-based tests** (jqwik) where a property holds across many inputs

### V. Virtual Threads for Concurrency

Java 21 Virtual Threads (Project Loom) are the standard concurrency model for
I/O-bound work in bottin:

- I/O-bound fan-out (relay subscriptions, DNS lookups, external `.well-known`
  fetches, multi-relay follower gathering) SHOULD use
  `Executors.newVirtualThreadPerTaskExecutor()` with `CompletableFuture`
- Use `ReentrantLock` instead of `synchronized` to avoid virtual-thread pinning
- Spring Boot virtual-thread support SHOULD be enabled via
  `spring.threads.virtual.enabled=true`
- Concurrency against external relays MUST be bounded (per-relay connection and
  subscription limits) to respect relay capacity and avoid being throttled
- CPU-bound work (bech32/cryptographic encoding) MUST NOT block virtual-thread
  workers on I/O

### VI. Secure Coding & Code Quality

- Input validation at every system boundary (REST controllers, `.well-known`
  query parameters, `npub` inputs, and relay/DNS/HTTP responses)
- Output encoding to prevent injection on any human-facing surface (admin UI,
  logs)
- No hand-rolled cryptography or bech32; use vetted libraries (e.g. nostr-java)
  for NIP-19 and event handling
- Secrets MUST live in environment variables or a secret manager, never in
  code, configs, or commits
- Public endpoints MUST be rate-limited; admin endpoints MUST require
  authentication and MUST NOT be exposed on public networks
- OWASP Top 10 vulnerabilities are blocking defects
- Follow SOLID principles; use Java records or Lombok to reduce boilerplate
- Prefer exceptions over error codes; exceptions exposed at the REST boundary
  MUST extend `BottinException` with an error code, a retryable flag, a user
  message, and an actionable suggestion; preserve the original cause when
  wrapping. User-facing messages MUST follow the template
  `{WHAT_HAPPENED}. {WHY_IT_HAPPENED}. Suggestion: {ACTIONABLE_STEP}.`
- `retryable=true` is reserved for genuinely transient failures (timeouts,
  temporary unavailability); never swallow a caught exception — log or rethrow
- YAGNI: no speculative abstractions; prefer a few clear lines over a premature
  helper

### VII. Public-by-Design Data & Privacy Boundaries

Nostr identity data is public by protocol design. Usernames, domains, and
pubkeys served via NIP-05 are intended to be world-readable, and bottin MUST
treat them as such — it MUST NOT promise secrecy it cannot keep for data the
protocol publishes. That public nature does NOT extend to operational data:

- **Disclose what is recorded**: a customer-facing note MUST describe what the
  registry persists (public mappings) versus what is operational
- **Operational PII minimisation**: request-source identifiers used for rate
  limiting (e.g. client IPs) MUST be ephemeral — held in an in-memory window,
  not in durable storage; admin identities and credentials follow the
  secure-coding rules (Principle VI)
- **Bounded audit retention**: verification and security audit logs MUST be
  retained only for a configurable window sufficient for forensics, then
  pruned; they MUST NOT accumulate identity-bearing operational data
  indefinitely
- **No over-collection**: bottin MUST NOT persist fields beyond what serving,
  verification, and operations require
- **Right to removal**: deleting a NIP-05 record MUST stop serving it and
  remove its durable mapping

Identity Mapping Integrity (Principle I) takes precedence where the two
interact: audit data genuinely required to protect mapping integrity is
retained within the stated policy rather than discarded.

### VIII. Clean Code Craftsmanship

Code MUST be written to be read. The following Clean Code rules are enforced in
review:

- **Meaningful names**: intention-revealing, pronounceable, searchable; no
  disinformation; one word per concept (pick one of fetch/get/retrieve and keep
  it); classes are nouns, methods are verbs
- **Small functions**: ideally under 20 lines; do one thing at one level of
  abstraction; zero/one/two arguments preferred, three needs justification
- **No flag arguments**: split a boolean-driven function into two
- **Command-Query Separation**: a function either does something or answers
  something, never both
- **Comments are a last resort**: prefer self-documenting code; good comments
  explain *why* (intent, consequences, legal, TODO, public-API Javadoc), never
  restate *what*; no commented-out code, no journal/noise comments
- **No dead or duplicate code**; replace magic numbers with named constants;
  encapsulate complex conditionals behind well-named methods; avoid negative
  conditionals
- **Never return null** — throw an exception or return a Special Case object;
  do not pass null unless an API explicitly expects it
- Use Lombok / Java records to remove boilerplate; rely on imports rather than
  fully-qualified class names

## Security Requirements

- **Authentication on admin paths**: all admin endpoints (`bottin-admin-ui` and
  any admin REST surface) MUST require authentication and MUST NOT be exposed on
  public networks. Administrators authenticate by proving control of a Nostr key
  (NAP); the deployment MUST NOT hold a credential that could sign on an
  administrator's behalf
- **Authorization at a single decision point**: who may do what MUST be decided
  in one place. A control hidden from a page is not a permission — a refusal MUST
  hold when the endpoint is addressed directly, and MUST be logged when it does
- **Rate limiting on public paths**: public endpoints (external verification,
  profile stats) MUST enforce per-client rate limiting
- **No secrets in commits**: `.env`, credentials, and private keys MUST be
  excluded by `.gitignore` and verified before commit
- **Security event logging**: every rejected request, verification failure,
  rate-limit rejection, and serving anomaly MUST be logged with structured
  fields suitable for SIEM ingestion, with secrets and unnecessary PII masked
- **Dependency vulnerabilities**: critical CVEs in transitive dependencies MUST
  be addressed within one release cycle and triaged within the same week
- **Verification audit trail**: domain and external NIP-05 verification outcomes
  MUST be persisted as audit entries to support re-checks and incident analysis

## Documentation Standards

Documentation MUST follow the [Diataxis](https://diataxis.fr/) framework:

- Every document is classified as exactly one of: **tutorial**, **how-to
  guide**, **reference**, or **explanation**, and lives under
  `docs/<section>` matching that category
- Each document starts with a top-level `#` heading and a short introduction
  stating its purpose
- New documents MUST be linked from `docs/README.md` in the corresponding
  section; cross-references use relative links and code snippets are kept
  minimal and tested
- New REST endpoints MUST be documented in the API documentation; new features
  MUST update the relevant docs in the same change

## Logging Standards

- Each log entry states, in one plain-language sentence, what happened, why,
  and the resulting impact — leading with the subject (component/entity), then
  the action and outcome
- Use structured key-value pairs (e.g. `domain_id=1 success=false`) with stable
  field names; messages stay grep-friendly and machine-parseable (no multi-line
  blobs)
- State the exact state transition or decision (e.g. `payment_quote
  marked_pending`) and whether it succeeded, failed, or was skipped
- **Levels**: ERROR for unexpected failures needing investigation; WARN for
  expected-but-problematic conditions (retryable failure, circuit open);
  DEBUG/TRACE for diagnostic variables and branch choices, not payload dumps
- Secrets, personal data, and cryptographic material MUST be omitted or masked;
  identifiers and correlation/trace IDs are included where they aid diagnosis
- Use neutral, professional language; avoid duplicating the same event across
  levels or components

## Development Workflow

- **Commits**: Conventional Commits format (`feat(scope):`, `fix(scope):`,
  `docs(scope):`, etc.); `scope` SHOULD identify the affected module
  (`api`, `service`, `persistence`, `verification`, `reach`, `core`,
  `admin-ui`, `client-ui`, `web-assets`, `starter`), or the feature number for
  spec-driven work (`006`). Prefer multiple small commits; avoid grouped commits
- **Builds**: `mvn -q verify` MUST pass before committing
- **Integration tests**: PRs that touch verification, relay interaction, or
  `.well-known` serving MUST run the integration tests locally before merge
- **Versions**: Managed in the parent `pom.xml`; use coordinated bumps
  following semantic versioning, deriving the bump from the accumulated
  Conventional Commits. Bump the project version on task/feature completion
  before publishing, and record the change in `CHANGELOG.md`
- **Documentation**: features that add or change behavior MUST update the
  corresponding Diataxis document (see Documentation Standards) in the same
  change
- **Branching**: Feature branches off `develop`; PRs target `develop`; `main`
  tracks released versions
- **Code review**: All PRs require review; changes to identity-serving or
  verification paths MUST be reviewed with explicit attention to the Identity
  Mapping Integrity principle

## Governance

This constitution is the authoritative source of project standards for bottin.
It supersedes ad-hoc practices and informal conventions.

- **Amendments**: Any change to this constitution MUST be documented with
  rationale, reviewed by a maintainer, and reflected in the version below. A
  Sync Impact Report at the top of the file MUST summarise the change.
- **Versioning**: MAJOR for principle removals or redefinitions, MINOR for new
  principles or material expansions, PATCH for clarifications and wording fixes.
- **Compliance**: All PRs and code reviews MUST verify adherence to these
  principles. Violations of Principle I (Identity Mapping Integrity) are
  blocking and require maintainer sign-off to merge under any exception clause.
- **Runtime guidance**: See `CLAUDE.md` and `AGENTS.md` for build commands,
  module structure, coding standards, and operational patterns.

**Version**: 1.3.0 | **Ratified**: 2026-06-25 | **Last Amended**: 2026-08-05
