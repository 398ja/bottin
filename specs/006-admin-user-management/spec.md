# Feature Specification: Additional administrators with super-admin and admin roles

**Feature Branch**: `006-admin-user-management`
**Created**: 2026-08-02
**Status**: Draft
**Input**: User description: "In the admin settings, let the master administrator add further admin npubs that can sign in to the admin dashboard exactly as the master key does. Two roles: super admin (the master key from deployment configuration, exactly one, the only role that may add or remove administrators, and it cannot be removed, edited or demoted from the UI) and admin (added by the super admin, full access to every admin page but cannot manage the administrator list). Both npub and hex accepted on input and stored in one canonical form. Removing an administrator ends any session they currently hold immediately. A non-super-admin must be refused if they call the management endpoints directly. Additions and removals are recorded in the security log. Storage uses the existing unused admin_users table."

## Why this feature exists

A deployment today recognises exactly one administrator key, set in deployment
configuration. Two people who both administer it have no choice but to share one
private key — precisely the shared secret that key-based sign-in existed to
remove. Sharing a private key is worse than sharing a password: it cannot be
rotated for one holder alone, and every action is attributable to the same key,
so the security log can no longer say who did anything.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The super administrator grants a colleague access (Priority: P1)

The person holding the master key opens the settings page, enters a colleague's
public key, and from then on the colleague signs in with their own key and uses
the dashboard exactly as the master key holder does.

**Why this priority**: This is the feature. Without it nothing else here has a
purpose, and it alone removes the need to share a private key.

**Independent Test**: Add a key on the settings page, sign in with the matching
private key in a clean browser, and reach every dashboard page.

**Acceptance Scenarios**:

1. **Given** a deployment with only the master key configured, **When** the super
   administrator adds a public key and the holder of the matching private key
   signs in, **Then** they reach the dashboard and can view and change records,
   domains, and settings.
2. **Given** a key was added in `npub1…` form, **When** the same key is later
   added in hex form, **Then** nothing changes and the page says the key can
   already administer the deployment — no second entry is created.
3. **Given** a value that is not a public key, **When** it is submitted, **Then**
   it is refused with the offending value named, and nothing is added.
4. **Given** an administrator has just been added, **When** the security log is
   read, **Then** it records the addition, the key added, and which
   administrator performed it.

---

### User Story 2 - The super administrator revokes access (Priority: P2)

Someone leaves, or a key is suspected compromised. The super administrator
removes them and their access ends at once — not whenever their session would
have expired.

**Why this priority**: Revocation that is not immediate is not revocation. It is
the half of access management that matters under pressure, and the half that is
easy to implement in a way that looks correct and is not.

**Independent Test**: Sign in as an added administrator in one browser, remove
them from another, and confirm the first browser can no longer load an admin
page.

**Acceptance Scenarios**:

1. **Given** an added administrator with a session open, **When** the super
   administrator removes them, **Then** their next request to any admin page is
   refused, without waiting for the session to expire.
2. **Given** an administrator has been removed, **When** they attempt to sign in
   again with the same key, **Then** they are refused exactly as any unknown key
   is.
3. **Given** every added administrator has been removed, **When** the master key
   holder signs in, **Then** they still reach the dashboard.
4. **Given** an administrator has just been removed, **When** the security log is
   read, **Then** it records the removal and which administrator performed it.

---

### User Story 3 - An added administrator cannot grant access to others (Priority: P3)

An added administrator uses the dashboard fully, but the administrator list is
not theirs to change — and the refusal holds whether they use the interface or
address the deployment directly.

**Why this priority**: The distinction between the two roles is only real if it
is enforced where the decision is made. A control that is merely hidden is a
permission that does not exist.

**Independent Test**: Sign in as an added administrator, confirm the management
controls are absent, then issue the add and remove requests directly and confirm
both are refused.

**Acceptance Scenarios**:

1. **Given** an added administrator signed in, **When** they open the settings
   page, **Then** they can read and change the deployment settings but are
   offered no control to add or remove administrators.
2. **Given** an added administrator signed in, **When** they issue an add or
   remove request directly rather than through the interface, **Then** it is
   refused, and the refusal is recorded.
3. **Given** an added administrator signed in, **When** they attempt to promote
   themselves, **Then** it is refused.

---

### User Story 4 - The master key cannot be locked out (Priority: P3)

The master key holder cannot remove, edit, or demote themselves through the
interface, and no sequence of changes in the interface can leave the deployment
with nobody able to sign in.

**Why this priority**: The master key is what admits an operator when the stored
data is empty, wrong, or freshly restored. If it were editable, one save could
lock out everybody including the person making it — a state nothing in the
product could undo.

**Independent Test**: Attempt to remove or demote the master key by every route
the interface offers, and confirm each is refused.

**Acceptance Scenarios**:

1. **Given** the master key holder signed in, **When** they view the
   administrator list, **Then** the master key is shown as the super
   administrator and is offered no remove, edit, or demote control.
2. **Given** the master key holder signed in, **When** they issue a request to
   remove or demote the master key directly, **Then** it is refused.
3. **Given** the master key is already the super administrator, **When** the
   super administrator adds that same key as an ordinary administrator, **Then**
   nothing happens: no entry is created, no error is raised, and the page says
   the key already administers the deployment as the super administrator.
4. **Given** the master key was added as an ordinary administrator by some other
   route, **When** its holder signs in, **Then** they are the super
   administrator; configuration decides the role, and the stored entry cannot
   demote them.

---

### Edge Cases

- **The configured master key changes between restarts.** The previous holder
  loses the super administrator role; added administrators are unaffected and
  keep their access. A configuration change rewrites nothing in the stored list.
- **The configured master key is unset or unreadable.** Added administrators can
  still sign in and use the dashboard, but nobody can manage the administrator
  list until a readable master key is configured. The page says so rather than
  offering a control that cannot work.
- **An administrator is removed while performing a change.** The in-flight
  request is refused like any other; no partial change is left behind.
- **The same key is added twice at once.** One addition succeeds, the other
  changes nothing. The list never holds the same key twice, and neither
  submission reports a failure.
- **The configured master key is also present as a stored administrator.** It
  cannot arrive that way through the interface, but it can if the configured key
  is later changed to one already added. Configuration decides: the holder is the
  super administrator, and the stored entry neither demotes them nor grants them
  anything they do not already have. Removing that stored entry is permitted and
  leaves them the super administrator.
- **The last added administrator is removed.** Permitted: the master key holder
  can always still sign in.
- **A key is added that nobody holds the private half of.** Accepted — the
  deployment cannot tell, and a public key that never signs in is harmless. It
  is removed like any other.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST let the super administrator add an administrator by
  public key from the settings page.
- **FR-002**: The system MUST accept a public key in either `npub1…` or
  64-character hex form, and MUST treat the same key entered either way as one
  administrator.
- **FR-003**: The system MUST refuse a value that is not a public key, naming the
  offending value as the settings page already does for relay URLs, and MUST
  record nothing.
- **FR-004**: Adding a key that can already administer the deployment — whether
  it is already an administrator or is the configured master key — MUST change
  nothing and MUST NOT be treated as an error. The system MUST say that the key
  already administers the deployment, so that an operator who pasted the wrong
  key learns it now rather than when their colleague cannot sign in.
- **FR-004a**: The system MUST NOT create a second entry for a key that can
  already administer the deployment, and MUST NOT record an ordinary
  administrator entry for the configured master key.
- **FR-005**: An added administrator MUST be able to sign in with their own key
  and reach every part of the dashboard the master key holder reaches, except
  the management of administrators.
- **FR-006**: The system MUST let the super administrator remove an added
  administrator.
- **FR-007**: Removing an administrator MUST end any session that administrator
  currently holds, taking effect on their next request rather than at session
  expiry.
- **FR-008**: The system MUST refuse an add, remove, or role change requested by
  anyone other than the super administrator, whether or not the interface
  offered the control.
- **FR-009**: The system MUST offer the master key holder no control to remove,
  edit, or demote the master key, and MUST refuse such a request if made
  directly.
- **FR-010**: The system MUST record every addition, removal, and refused
  management attempt in the security log, identifying the key acted on and the
  administrator who acted.
- **FR-011**: The system MUST show the super administrator the current list of
  administrators, distinguishing the configured master key from added ones.
- **FR-012**: The system MUST continue to admit the master key holder regardless
  of the contents of the administrator list.
- **FR-013**: The system MUST keep the master key's authority in deployment
  configuration, not in stored data the interface can change.
- **FR-014**: An added administrator MUST be identifiable in the list by
  something a person can read, so the list can be maintained without comparing
  64-character keys by eye.

### Key Entities

- **Administrator**: A public key permitted to sign in to the dashboard. Carries
  a role, whether it is currently in effect, a human-readable label, when it was
  added, and which administrator added it.
- **Role**: Either *super administrator* — the configured master key, exactly one
  per deployment, the only role that may manage administrators — or
  *administrator*, which may use the whole dashboard but not manage the list.
- **Security log entry**: A record that an administrator was added or removed, or
  that a management attempt was refused, naming the key acted on and the
  administrator who acted.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Two people can administer one deployment without either learning
  the other's private key, and without any private key being shared.
- **SC-002**: A super administrator can grant a colleague access in under one
  minute, knowing only that colleague's public key.
- **SC-003**: A removed administrator is refused on their very next request, with
  no window in which their existing session still works.
- **SC-004**: An added administrator is refused every management action, through
  the interface and when addressing the deployment directly — 100% of attempts,
  not merely hidden controls.
- **SC-005**: No sequence of actions available in the interface can leave a
  deployment that nobody can sign in to.
- **SC-006**: Every change to who may administer the deployment is attributable
  from the security log to the administrator who made it.

## Assumptions

- **Administrators are added or removed, not suspended.** A temporary disable was
  not asked for; removing and re-adding achieves it. The stored shape should not
  preclude adding suspension later.
- **An added administrator holds one role.** There is no per-page or
  per-resource permission; the two roles differ only in whether they may manage
  the administrator list. Anything finer is a separate feature.
- **A human-readable label is supplied when adding.** A list of bare keys cannot
  be maintained in practice. The label is descriptive only and carries no
  authority — it is never used to identify anyone at sign-in.
- **No invitation or notification.** The super administrator obtains the
  colleague's public key out of band and tells them out of band that access has
  been granted. The deployment sends nothing.
- **No self-service.** Nobody can request access; the super administrator is the
  only route in.
- **Storage adopts the existing dormant administrator table** rather than a list
  on the settings row, at the requester's direction. Administrator keys are
  looked up individually on every sign-in, and each carries attributes a list of
  strings has nowhere to put. That table's username and password columns predate
  key-based sign-in and no longer have a meaning; what becomes of them is a
  planning decision, but this feature MUST NOT require a password to exist for
  an administrator.
- **The number of administrators is small** — single digits for a typical
  deployment. Nothing here needs to scale to thousands.

## Dependencies

- Builds directly on admin sign-in by Nostr key (feature 005), which established
  the master key as an explicit super-administrator role rather than merely
  "authenticated". This feature adds a second role and a stored list; it does not
  introduce authorization.
- Requires the session store to be reachable wherever the administrator list is
  written, so removal can end a session immediately (FR-007).

## Out of Scope

- Per-page or per-resource permissions beyond the two roles.
- Suspending an administrator without removing them.
- Self-service access requests, invitations, or notifications.
- Transferring the super administrator role between keys through the interface —
  it moves by changing deployment configuration.
- Administrator management for anything other than the admin dashboard.
