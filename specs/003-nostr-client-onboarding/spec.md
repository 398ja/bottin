# Feature Specification: Nostr Client Onboarding & Account Management

**Feature Branch**: `003-nostr-client-onboarding`  
**Created**: 2026-07-22  
**Status**: Draft  
**Input**: User description: "I want to build a new web application, as a submodule, a client for nostr registration (onboarding), and account management (follow and block list management). It should also have search capabilities, based on our nostrdb implementation. The onboarding should be similar to the one in the imani-apps. I want to integrate it with our nap implementation"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Onboard a New Nostr Identity (Priority: P1)

A new user visits the application and wants to create a Nostr identity. They are guided through a multi-step flow: choose to create a new key or import an existing one, set up their profile (display name, bio, avatar, banner via Blossom upload, Lightning address), set a local encryption password, and land on the welcome screen with their new identity ready. The NIP-05 identifier is automatically derived from their chosen username (e.g., `alice` → `alice@bottin.example.com`). The nsec is generated or imported entirely within the browser and never leaves it. After onboarding, they can log in via NAP on subsequent visits by signing challenges locally.

**Why this priority**: Onboarding is the entry point — no user can use the application without first having an identity.

**Independent Test**: Can be fully tested by starting from the landing page, selecting "Create New Account", completing all steps, and verifying the new identity is stored locally.

**Acceptance Scenarios**:

1. **Given** a first-time visitor, **When** they select "Create new account", generate a keypair in-browser, set their profile and password, and confirm, **Then** the identity is stored in browser localStorage and they land on the welcome screen.
2. **Given** a user on the profile step, **When** they go back to the method selection step, **Then** their previous selections are preserved and they can continue forward without data loss.
3. **Given** a user who selected "Import existing key", **When** they paste a valid nsec into the browser, **Then** the public key is derived locally, the nsec is discarded from memory after encryption, and the profile step pre-fills any available metadata from nostrdb.
4. **Given** a user on the profile step, **When** they enter a username that is already taken on the registry, **Then** they see an inline error and cannot proceed until choosing a different username.

---

### User Story 2 - Log In via NAP (Priority: P1)

A returning user wants to log in to the application. They paste their nsec into the browser (or connect a NIP-46 signer) — the nsec never leaves the browser. The browser signs a NAP challenge locally and sends only the NIP-98 proof to the server. The server verifies the proof and issues a session. All subsequent authenticated actions (follow, block) are authorized through this session.

**Why this priority**: Login gates all authenticated operations. NAP provides secure, key-based authentication without passwords. The nsec stays in the browser at all times.

**Independent Test**: Can be fully tested by pasting an nsec into the browser, completing the NAP challenge-response locally, and verifying the session cookie is set while confirming no network request contained the raw nsec.

**Acceptance Scenarios**:

1. **Given** a returning user on the login screen, **When** they paste their nsec into the browser and the browser signs a NAP challenge locally, **Then** only the NIP-98 proof is sent to the server; the raw nsec is never transmitted.
2. **Given** a user who provided an invalid or incorrect key, **When** they attempt NAP completion, **Then** the server rejects the proof and the user sees a clear error message.
3. **Given** a user with an active NAP session, **When** they revisit the application, **Then** the session is automatically validated and they are signed in without re-authentication.
4. **Given** a user whose NAP session has expired, **When** they attempt an authenticated action, **Then** they are redirected to the login screen.

---

### User Story 3 - Search for Profiles (Priority: P1)

An authenticated or anonymous user wants to find Nostr users. They type a search query, and the application queries the local nostrdb index for matching profiles. Results appear with avatar, display name, and NIP-05 identifier.

**Why this priority**: Search enables discovery, which is the prerequisite for building follow and block lists.

**Independent Test**: Can be fully tested by entering search terms and verifying matching profiles appear with correct metadata within 500ms.

**Acceptance Scenarios**:

1. **Given** the nostrdb has indexed profiles, **When** a user types a search term matching a profile name or display name, **Then** that profile appears in results within 500ms.
2. **Given** no profiles match the query, **When** the user searches, **Then** a clear "no results" message is shown.
3. **Given** a user has typed a partial query, **When** they continue typing to refine it, **Then** results update accordingly.
4. **Given** a user submits an empty or whitespace-only search, **When** they trigger search, **Then** no database query is fired and an empty state is shown.

---

### User Story 4 - Manage Follow List (Priority: P2)

A signed-in user wants to follow and unfollow other Nostr users. They can view their current follow list, add follows from search results or profile pages, and remove follows. Follow list changes are published as NIP-02 kind-3 events. All actions are authenticated through the NAP session.

**Why this priority**: Follow list management is the core account management feature. It depends on search (Story 3) but delivers the primary social graph functionality.

**Independent Test**: Can be fully tested by following a user, verifying they appear in the follow list, then unfollowing and verifying removal — all while the NAP session is active.

**Acceptance Scenarios**:

1. **Given** an authenticated user viewing search results, **When** they click "Follow" on a profile, **Then** the profile is added to their follow list and a kind-3 event is published.
2. **Given** an authenticated user, **When** they view their follow list, **Then** all followed profiles are shown with avatar, name, and an unfollow button.
3. **Given** a user viewing their follow list, **When** they click "Unfollow", **Then** the profile is removed and an updated kind-3 event is published.
4. **Given** a user's session has expired, **When** they attempt to follow or unfollow, **Then** the action is denied and they are prompted to re-authenticate.
5. **Given** a user follows the maximum number of accounts (1000), **When** they try to follow another, **Then** they are notified the list is full.

---

### User Story 5 - Manage Block List (Priority: P3)

A signed-in user wants to block or unblock other Nostr users. Blocked profiles are hidden from search results and cannot be followed. Blocks are recorded per NIP-02 conventions.

**Why this priority**: Blocking is important for user safety but builds on top of search and follow features.

**Independent Test**: Can be fully tested by blocking a user, verifying they are hidden from search and unfollowed, then unblocking and verifying they reappear.

**Acceptance Scenarios**:

1. **Given** an authenticated user viewing a profile, **When** they select "Block user", **Then** the pubkey is added to the block list, the user is unfollowed if previously followed, and a kind-3 event is published.
2. **Given** a user has blocked an account, **When** they search, **Then** the blocked profile is excluded from results.
3. **Given** a user views their block list, **When** they click "Unblock", **Then** the profile is removed from the block list and a kind-3 event is published.
4. **Given** a user navigates directly to a blocked profile's URL, **When** the page loads, **Then** a "Blocked" indicator is shown with an option to unblock.

---

### User Story 6 - Restore Identity from Backup (Priority: P3)

An existing user who lost their device wants to restore their Nostr identity from an encrypted backup file. After restoration, they are redirected to the login screen where they can log in via NAP using the restored key.

**Why this priority**: Restoration is a safety net for existing users — less frequent but critical for retention. After restore, NAP provides the secure login path.

**Independent Test**: Can be fully tested by exporting a backup, clearing state, restoring from the file with the correct passphrase, then logging in via NAP and verifying the identity and follow list are intact.

**Acceptance Scenarios**:

1. **Given** a user on the login screen, **When** they select "Restore from backup", choose a valid backup file, and enter the correct passphrase, **Then** their key and lists are restored and they are taken to the NAP login flow.
2. **Given** a user attempts to restore, **When** they provide an incorrect passphrase, **Then** they see an error message and can retry.
3. **Given** a user attempts to restore, **When** the backup file is corrupted or invalid, **Then** they see a descriptive error.

### User Story 7 - Manage Relay List (Priority: P1)

A signed-in user wants to manage which Nostr relays their client uses. They can view their relay list (split into read relays and write relays), add new relays with read/write permissions, and remove relays. Relay list changes are published as NIP-65 kind-10002 relay list metadata events. The relay list controls which servers the application uses for publishing NIP-02 kind-3 follow/block events.

**Why this priority**: Relay configuration gates all Nostr network interactions. Without the ability to manage relays, users cannot control where their data goes.

**Independent Test**: Can be fully tested by adding a relay URL, verifying it appears in the read and/or write list, removing it, and confirming the list updates accordingly.

**Acceptance Scenarios**:

1. **Given** an authenticated user on the settings page, **When** they view the relays section, **Then** they see separate lists for read relays and write relays with current URLs.
2. **Given** a user with the relay management UI open, **When** they enter a valid `wss://` URL and select read/write permissions and click Add, **Then** the relay appears in the corresponding list(s).
3. **Given** a user with a relay in their list, **When** they click Remove on that relay, **Then** the relay is removed from both read and write lists.
4. **Given** a user with unsaved relay changes, **When** they click "Save & Publish", **Then** the relay list is persisted server-side and a NIP-65 kind-10002 event is published to all configured relays.
5. **Given** a user with no relays configured, **When** they view the relay management section, **Then** a "No servers configured" placeholder is shown in each list.
6. **Given** a user enters an invalid URL (not `wss://`), **When** they attempt to add it, **Then** an inline validation error is shown and the relay is not added.

---

### User Story 8 - Change Local Passphrase (Priority: P2)

A signed-in user who set a password during onboarding wants to change their local encryption passphrase. They enter their current passphrase, choose a new one, and the private key is re-encrypted with the new passphrase. The nsec never leaves the browser during this process.

**Why this priority**: Passphrase management is important for account security but is a maintenance operation, not a critical path feature.

**Independent Test**: Can be fully tested by setting a passphrase during onboarding, then changing it and verifying the new passphrase correctly decrypts the private key while the old one no longer works.

**Acceptance Scenarios**:

1. **Given** an authenticated user on the settings page, **When** they select "Change Passphrase", enter the correct current passphrase, enter a new valid passphrase and confirmation, **Then** the private key is re-encrypted with the new passphrase and old passphrase no longer decrypts it.
2. **Given** a user in the change passphrase flow, **When** they enter an incorrect current passphrase, **Then** they see an error and cannot proceed.
3. **Given** a user in the change passphrase flow, **When** the new passphrase and confirmation do not match, **Then** they see a mismatch error.
4. **Given** a user in the change passphrase flow, **When** the new passphrase is fewer than 8 characters, **Then** they see a strength error and cannot proceed.

---

### Edge Cases

- What happens when the NAP server is unavailable during login?
- How does the system handle a NAP challenge that expires mid-flow?
- What happens when nostrdb is empty or uninitialized?
- How does the system handle a user following someone who is already blocked?
- What happens when an already-authenticated user visits the onboarding flow?
- How does the system handle very long search queries?
- What happens when local storage is full or unavailable?
- How does the system handle concurrent sessions from the same user?
- What happens when a user adds a duplicate relay URL?
- How does the system handle removing the last write relay when there are pending follow list changes?
- What happens if a relay URL is valid but unreachable?
- How does the system handle a passphrase change that fails mid-way (e.g., browser crash during re-encryption)?
- What happens when the user attempts to change passphrase but localStorage key is corrupted?
- What should happen when the user has no relays configured and tries to publish a kind-3 event?

## Requirements *(mandatory)*

### Functional Requirements

**Onboarding & Identity**

- **FR-001**: Users MUST be able to create a new Nostr keypair (nsec/npub) locally in the browser using Web Crypto API
- **FR-002**: Users MUST be able to import an existing Nostr identity by pasting an nsec into the browser; the nsec MUST be processed entirely client-side and MUST NOT be transmitted to any server
- **FR-003**: The onboarding flow MUST follow a multi-step wizard: method selection, profile setup, security (password), confirmation, and welcome
- **FR-004**: Users MUST be able to set profile metadata (display name, bio, avatar, banner, Lightning address, website) during onboarding; the NIP-05 identifier is automatically derived from the chosen username

**Security & NAP Authentication**

- **FR-005**: The nsec MUST never be transmitted to any server. All key operations (generation, import, signing) MUST happen client-side in the browser.
- **FR-006**: The application MUST use NAP (Nostr Authentication Protocol v2) for login authentication, including challenge issuance, NIP-98 proof verification, and session management
- **FR-007**: When a user logs in, the browser MUST sign a NIP-98 event (kind 27235) locally using the nsec; only the resulting proof (base64-encoded signed event) MUST be sent to the server, never the raw nsec
- **FR-008**: Authenticated API requests MUST be authorized via the NAP session cookie, never via the nsec
- **FR-009**: The NAP session MUST have a configurable idle timeout and absolute expiry; expired sessions MUST redirect the user to login
- **FR-010**: Users MUST be able to log out, which revokes the NAP session server-side

**Search**

- **FR-011**: Users MUST be able to search for Nostr profiles using the local nostrdb index
- **FR-012**: Search results MUST display avatar, display name, NIP-05 identifier, and pubkey for each match

**Follow & Block Management**

- **FR-013**: Authenticated users MUST be able to follow other Nostr users from search results or profile views
- **FR-014**: Authenticated users MUST be able to view their follow list with profile details
- **FR-015**: Authenticated users MUST be able to unfollow any followed account
- **FR-016**: Follow list changes MUST publish NIP-02 kind-3 contact list events to configured relays
- **FR-017**: Authenticated users MUST be able to block other Nostr users
- **FR-018**: Authenticated users MUST be able to view their block list
- **FR-019**: Authenticated users MUST be able to unblock any blocked account
- **FR-020**: Blocked users MUST be excluded from search results
- **FR-021**: Blocked users MUST NOT be followable (block takes precedence)

**Persistence & Recovery**

- **FR-022**: Follow and block list state MUST survive browser restart (persistent local storage)
- **FR-023**: Users MUST be able to export their identity, follow list, and block list as an encrypted backup file
- **FR-024**: Users MUST be able to restore identity and account data from an encrypted backup file; the uploaded backup file MUST be decrypted client-side and MUST NOT be transmitted to any server
- **FR-025**: Avatar and banner images MUST be uploadable via a Blossom file server (NIP-96); the Blossom server URL is configured via environment variable
- **FR-026**: The NIP-05 identifier MUST be automatically derived from the username during onboarding (e.g., `{username}@{bottin-domain}`); the user does not input it manually
- **FR-027**: The application MUST support a single identity per device; switching identities requires logging out
- **FR-028**: The maximum supported follow list size is 1000; the maximum block list size is 1000

**Relay Management**

- **FR-029**: Authenticated users MUST be able to view their configured relay list, with relays split into read relays and write relays
- **FR-030**: Authenticated users MUST be able to add a new relay URL with read and/or write permission flags
- **FR-031**: Authenticated users MUST be able to remove a relay from their list
- **FR-032**: Authenticated users MUST be able to toggle read/write permissions on an existing relay
- **FR-033**: Relay list changes MUST be persisted server-side, associated with the authenticated pubkey
- **FR-034**: On "Save & Publish", the application MUST publish a NIP-65 kind-10002 relay list metadata event to all configured relays
- **FR-035**: The relay list MUST have sensible defaults (configurable in `application.yml`) when the user has not yet configured any relays

**Passphrase & Key Management**

- **FR-036**: Users MUST be able to change their local encryption passphrase from the settings page
- **FR-037**: Passphrase change MUST verify the current passphrase by attempting to decrypt the stored private key before accepting a new one
- **FR-038**: After passphrase change, the encrypted private key (`privateKeyEncrypted`), IV, salt, password hash, and password salt in localStorage MUST be updated atomically
- **FR-039**: The nsec MUST NOT leave the browser during passphrase change (re-encryption is entirely client-side using Web Crypto API)
- **FR-040**: Authenticated users MUST be able to reveal their nsec private key from the settings page, gated by passphrase verification

### Key Entities

- **Nostr Identity**: A user's cryptographic keypair (nsec/npub). Generated locally or imported. Used to sign NAP challenges.
- **NAP Challenge**: A high-entropy server-issued nonce bound to a specific pubkey and auth URL. Has a short TTL. Issued via `POST /api/v1/auth/init`.
- **NAP Session**: A server-side session record created after successful NIP-98 proof verification. Tied to a pubkey with configurable idle and absolute TTLs. Conveyed via HTTP-only secure cookie.
- **Profile Metadata**: User-visible profile information from kind-0 event content (display name, bio, avatar, banner, Lightning address, website). NIP-05 is automatically derived from the username.
- **Follow List**: A collection of pubkeys the user follows. Corresponds to NIP-02 kind-3 event content (positive "p" tags).
- **Block List**: A collection of pubkeys the user has blocked. Represented as "p" tags with "-" prefix per NIP-02 conventions.
- **Relay List**: A collection of relay URLs the user has configured, each with read and/or write permission flags. Published as NIP-65 kind-10002 relay list metadata events. Stored server-side associated with the authenticated pubkey.
- **Search Index**: The nostrdb-backed index of profiles for querying, populated from ingested Nostr events.
- **Encrypted Backup**: An export of the identity key, follow list, and block list data, encrypted with a user-supplied passphrase.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A new user can complete onboarding (key creation, profile setup, password) in under 3 minutes
- **SC-002**: 90% of users who start onboarding successfully complete it on their first attempt
- **SC-003**: NAP challenge issuance completes in under 500ms; challenge response verification completes in under 1 second
- **SC-004**: Search results appear within 500ms for queries matching existing profiles
- **SC-005**: Users can follow or unfollow with a single action; change is reflected within 2 seconds
- **SC-006**: Blocked users are excluded from search results with zero false negatives
- **SC-007**: Session expiry redirects to login; logout revokes the session server-side within 1 second
- **SC-008**: Backup export and restore succeeds for 99% of attempts with a valid passphrase
- **SC-009**: A restored identity has the same follow list and block list as before the backup
- **SC-010**: Relay list changes are reflected in the UI within 1 second and, on "Save & Publish", a NIP-65 kind-10002 event is published to all configured relays within 5 seconds
- **SC-011**: Passphrase change completes in under 1 second; the old passphrase immediately stops working and the new passphrase correctly decrypts the private key

## Assumptions

- The nostrdb instance is pre-populated with Nostr events; the ingestion pipeline is handled by a separate component
- The nap-java modules (nap-core, nap-server, nap-client, nap-spring) are available as dependencies and provide the NAP v2 server and Spring Boot auto-configuration
- NAP endpoints are served under `/api/v1/auth/init`, `/api/v1/auth/complete`, `/api/v1/auth/session`, and `/api/v1/auth/logout`
- The application runs alongside the existing bottin NIP-05 registry as a new Maven submodule
- Users have a modern browser with Web Crypto API support for key generation and signing
- Relays for publishing kind-3 events are configured through the existing relay infrastructure
- Private keys are generated/imported in the browser, encrypted in localStorage, and never transmitted to any server; login uses local signing of NAP challenge-response with the nsec, sending only the NIP-98 proof to the server
- Search supports name and display name fields, with results ordered by relevance
- The onboarding flow follows the imani-apps multi-step wizard pattern exactly: method selection, profile setup, password, confirmation, and welcome
- A single identity per device is supported; switching identities requires logging out
- The nsec is NEVER transmitted to the server; only NIP-98 signed events (base64) are sent as proof of key ownership
- Default relays are configurable in `application.yml` (e.g., `bottin.client.default-relays`); users can override them via the settings page
- Relay list is stored server-side and published as NIP-65 kind-10002 events; the server uses the stored relay list to publish kind-3 follow/block events
- Passphrase change is entirely client-side; no server endpoint is required
