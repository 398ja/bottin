# Feature Specification: Admin sign-in with a Nostr key

**Feature Branch**: `005-admin-nap-auth`
**Created**: 2026-08-01
**Status**: Draft
**Input**: User description: "I want to implement the nap on the bottin admin. Instead of username/password, we will use the admin nsec. The nsec is validated against the npub in the config (docker compose) and if matching, the admin is let in. Also, in a separate effort, I want to write a detailed technical documentation describing how to implement the nap in an application"

## Overview

The bottin admin dashboard is reached today with a username and a password held in the
deployment's configuration. That password is a shared secret: it sits in `docker-compose.yml`
and in every operator's notes, it is the same for everyone who administers the deployment, and
anyone who reads it can act as the administrator from anywhere.

This feature replaces it. An administrator signs in by proving they control a Nostr private key,
and the deployment admits them only when the corresponding public key matches the one an operator
configured. The private key never leaves the administrator's device — proving control is enough,
so there is no longer a secret the deployment could leak.

This is the same authentication approach the bottin client already uses for its users, applied to
the administrator.

### Out of scope

Writing the technical documentation on implementing this authentication approach in an
application is **a separate feature** and is not specified here, as requested. It should get its
own specification so it can be scheduled, reviewed, and delivered independently of this change.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - An administrator signs in with their key (Priority: P1) 🎯 MVP

An administrator opens the admin dashboard and is asked to prove control of their Nostr key
rather than to type a username and password. They authorise the request with their key, and the
dashboard opens.

**Why this priority**: This is the feature. Nothing else here has value without it, and it is the
only story that delivers a usable dashboard on its own.

**Independent Test**: Configure a deployment with a known administrator public key, sign in with
the matching private key, and confirm the dashboard and every admin page are reachable.

**Acceptance Scenarios**:

1. **Given** a deployment configured with an administrator public key, **When** the administrator
   proves control of the matching private key, **Then** they reach the dashboard and can use every
   admin page.
2. **Given** an administrator who has signed in, **When** they navigate between admin pages,
   **Then** they are not asked to authorise again.
3. **Given** an administrator on the sign-in page, **When** the page is displayed, **Then** it
   offers no username or password field.
4. **Given** an administrator signing in, **When** the exchange completes, **Then** their private
   key has not been transmitted to the deployment at any point.

---

### User Story 2 - Any other key is refused (Priority: P2)

Somebody who controls a Nostr key that is not the configured administrator key attempts to sign
in. They are refused, told plainly that the key is not authorised, and the attempt is recorded.

**Why this priority**: An authentication change that admits the wrong person is worse than the
password it replaced. This is what makes the feature safe rather than merely different.

**Independent Test**: Attempt to sign in with a key other than the configured one; confirm access
is refused and the attempt appears in the security log.

**Acceptance Scenarios**:

1. **Given** a deployment configured with an administrator public key, **When** somebody proves
   control of a different key, **Then** they are refused and no session is created.
2. **Given** a refused attempt, **When** an operator reviews the security log, **Then** the
   attempt is recorded with enough context to investigate and with no key material.
3. **Given** a refused attempt, **When** the person reads the message, **Then** it tells them the
   key is not authorised without revealing which key would be.
4. **Given** somebody with no valid session, **When** they request any admin page directly,
   **Then** they are sent to the sign-in page rather than served the page.

---

### User Story 3 - A deployment with no configured key admits nobody (Priority: P3)

An operator deploys without configuring an administrator public key. Nobody can sign in, and the
operator is told why rather than left guessing.

**Why this priority**: The dangerous failure is the opposite one — an unconfigured deployment that
lets anyone in. This story makes the safe direction the guaranteed one, and it matters most on
first deploy.

**Independent Test**: Start a deployment with no administrator public key configured, attempt to
sign in with any key, and confirm refusal plus a diagnostic an operator can act on.

**Acceptance Scenarios**:

1. **Given** a deployment with no administrator public key configured, **When** anybody attempts
   to sign in with any key, **Then** they are refused.
2. **Given** that same deployment, **When** an operator inspects the sign-in page or the logs,
   **Then** they are told that no administrator key is configured and how to set one.
3. **Given** a deployment where the configured value is not a usable public key, **When** anybody
   attempts to sign in, **Then** they are refused and the misconfiguration is reported distinctly
   from "wrong key".

---

### User Story 4 - Sessions end (Priority: P4)

An administrator signs out when finished, and a session left unattended stops working on its own.

**Why this priority**: Valuable but not what makes the feature work. A session that never ends
undermines the security gain, so it belongs in scope — just after the paths above.

**Independent Test**: Sign in, sign out, and confirm admin pages require authorising again; then
sign in, leave the session beyond its lifetime, and confirm the same.

**Acceptance Scenarios**:

1. **Given** a signed-in administrator, **When** they sign out, **Then** their session ends
   immediately and admin pages require authorising again.
2. **Given** a session older than the configured lifetime, **When** the administrator requests an
   admin page, **Then** they are sent to the sign-in page.

---

### Edge Cases

- **The administrator loses the key.** There is no password to fall back on and no reset flow.
  Recovery means an operator configuring a different public key and restarting the deployment.
  This is a deliberate consequence of removing the shared secret, and it must be documented where
  an operator will read it *before* they are locked out.
- **A proof is replayed.** A proof already used, or captured in transit, must not grant a second
  session.
- **A proof is stale.** A proof produced too long ago must be refused, so a captured one has a
  short useful life.
- **The administrator's clock is wrong.** A modest difference between their device and the
  deployment must not prevent sign-in; a large one must not be exploitable.
- **The configured key is written in an unexpected form.** Operators may paste a public key in
  either of the two forms commonly published. Whichever forms are accepted, a value that is not a
  key at all must be reported as misconfiguration rather than silently refusing everyone.
- **The administrator has no key on the device they are using.** They must be told what they need
  rather than shown a form that cannot succeed.
- **Two browser sessions.** Signing out in one place should not silently leave another usable
  beyond its own lifetime.
- **Plain HTTP in local development.** Sign-in must stay usable on a local stack without weakening
  the protections that matter in production.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST authenticate an administrator by having them prove control of a
  Nostr private key.
- **FR-002**: The administrator's private key MUST NOT be transmitted to, stored by, or logged by
  the deployment at any point.
- **FR-003**: The system MUST grant an administrator session only when the proven public key
  matches the administrator public key configured for the deployment.
- **FR-004**: The system MUST refuse, and record, any sign-in attempt proving a public key that
  does not match the configured one.
- **FR-005**: The system MUST refuse every sign-in attempt when no administrator public key is
  configured, and MUST NOT fall back to admitting anybody.
- **FR-006**: The system MUST distinguish "no administrator key configured" and "the configured
  value is not a usable public key" from "this key is not the administrator", both in what it
  reports to an operator and in what it logs.
- **FR-007**: The system MUST refuse a proof that has expired or that has already been used.
- **FR-008**: The system MUST tolerate a small difference between the administrator's clock and
  its own, and MUST refuse proofs outside that tolerance.
- **FR-009**: Username and password sign-in MUST be removed. No administrator password may remain
  in configuration, in the database, or as a fallback path.
- **FR-010**: Every page and action under the admin dashboard MUST remain reachable only to an
  authenticated administrator, exactly as before this change.
- **FR-011**: Administrators MUST be able to end their session deliberately, and a session MUST
  expire after a configurable period.
- **FR-012**: The system MUST record administrator sign-in successes, failures, and sign-outs in
  the security log, with enough context to investigate and with no key material.
- **FR-013**: An operator MUST be able to set the administrator public key through the same
  deployment configuration mechanism used for other bootstrap values, and changing it MUST take
  effect on restart.
- **FR-015**: The system MUST support [NEEDS CLARIFICATION: one administrator key, or several? The
  description names "the npub", singular. With one, a deployment run by two operators has to share
  a private key, which reintroduces the shared secret this feature removes.]
- **FR-016**: An administrator MUST make their key available to the dashboard by [NEEDS
  CLARIFICATION: storing an encrypted key in the dashboard and unlocking it with a passphrase, as
  the client does, or providing the key afresh for each sign-in? The dashboard is a separate
  application from the client and inherits nothing it holds. This changes both the administrator's
  experience and how much key material rests on the machine.]
- **FR-014**: The sign-in page MUST tell an administrator what they need in order to sign in,
  including when the deployment is unconfigured and when their device holds no usable key.

### Key Entities

- **Administrator identity**: The public key a deployment recognises as its administrator, set by
  an operator in deployment configuration. It is not a secret — it is safe to publish — which is
  exactly what lets it live in a compose file where a password should not.
- **Sign-in challenge**: A short-lived, single-use value the deployment issues and the
  administrator signs to prove control of their key. Expires quickly and cannot be reused.
- **Administrator session**: The record of a completed sign-in, held by the administrator's
  browser, with a bounded lifetime, ended by signing out or by expiry.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An administrator who has their key available can sign in and reach the dashboard in
  under 30 seconds.
- **SC-002**: 100% of sign-in attempts proving a key other than the configured one are refused.
- **SC-003**: No administrator password exists anywhere in deployment configuration, in the
  database, or in the running system after this change.
- **SC-004**: The administrator's private key appears in zero requests reaching the deployment and
  in zero log entries.
- **SC-005**: A deployment with no administrator key configured admits zero administrators, and an
  operator can tell why within one minute of looking.
- **SC-006**: Every admin page that required authentication before this change still requires it
  afterwards, with none newly reachable while signed out.
- **SC-007**: 100% of administrator sign-in attempts, successful or not, appear in the security
  log.

## Assumptions

- **The key is created elsewhere.** Administrators already have a Nostr key. This feature does not
  generate one, back one up, or teach an administrator what a key is.
- **No recovery flow.** Losing the key means an operator changes the configured public key and
  restarts. This follows from removing the shared secret and is accepted rather than solved.
- **Session lifetime follows the existing convention.** The deployment already runs bounded
  sessions elsewhere; this feature adopts the same default rather than inventing one.
- **The admin dashboard is a separate application from the client.** Signing in to the dashboard
  is a separate act from signing in to the client, even when the same person holds both keys.
- **Removing password sign-in is a breaking change for existing deployments.** Operators must
  configure an administrator public key before upgrading, or be locked out.
- **The technical documentation is a separate feature** and is not delivered by this one.
