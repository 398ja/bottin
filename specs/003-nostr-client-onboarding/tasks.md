# Task Breakdown: Nostr Client Onboarding & Account Management

**Branch**: `003-nostr-client-onboarding` | **Date**: 2026-07-22
**Inputs**: [spec.md](spec.md), [plan.md](plan.md), [data-model.md](data-model.md), [contracts/README.md](contracts/README.md), [ui-flow.md](ui-flow.md)

## Task Estimate Summary

| Phase | Tasks | Est. Effort | Dependencies |
|-------|-------|-------------|--------------|
| Phase 0 | 11 | 5h | None | ✓
| Phase 1 | 14 | 20h | Phase 0 |
| Phase 2 | 9 | 14h | Phase 1 |
| Phase 3 | 5 | 6h | Phase 2 |
| Phase 4 | 13 | 18h | Phase 3 |
| Phase 5 | 10 | 14h | Phase 2 |
| Phase 6 | 7 | 10h | Phase 2 |
| Phase 7 | 7 | 8h | Phase 1 |
| Phase 8 | 8 | 10h | All above |
| **Total** | **84** | **~105h** | |

---

## Phase 0 — Foundation & Scaffolding

> Epics: 2 | Tasks: 11 | Est: 5h
> FRs: none (infrastructure)
> Dependencies: none

### Epic 0.1 — Maven Module Setup

**0.1.1** [X] Create `bottin-client-ui/pom.xml`
- Parent: `bottin-parent` (Java 21, Spring Boot 3.4)
- Dependencies: `spring-boot-starter-web`, `spring-boot-starter-thymeleaf`, `spring-boot-starter-actuator`, `nap-spring`, `nap-client`, `nostr-java-core`, `nostr-java-event`, `nostr-java-client`, `nostrdb-jni`
- Plugins: `spring-boot-maven-plugin` (Jib for Docker)

**0.1.2** [X] Create `BottinClientApplication.java`
- `src/main/java/xyz/tcheeric/bottin/client/BottinClientApplication.java`
- `@SpringBootApplication` with `scanBasePackages` including nap-spring auto-config

**0.1.3** [X] Create `application.yml`
- Server port, app name `bottin-client-ui`
- `nap.enabled=true`, session TTLs (idle: 30min, absolute: 24h)
- Default relays list (`bottin.client.default-relays`)
- Blossom server URL (`bottin.client.blossom-url`)
- Bottin domain for auto NIP-05 (`bottin.client.domain`)
- nostrdb path config

**0.1.4** [X] Create `ClientWebConfig.java`
- `src/main/java/xyz/tcheeric/bottin/client/config/ClientWebConfig.java`
- `@EnableWebMvc`, Thymeleaf view resolver, HTMX configuration
- Resource handlers for `/js/**`, `/css/**`

**0.1.5** [X] Create `ClientSecurityConfig.java`
- `src/main/java/xyz/tcheeric/bottin/client/config/ClientSecurityConfig.java`
- NAP session filter (`NapServletFilter`) registration
- Public path whitelist: `/`, `/onboarding/**`, `/login`, `/restore`, `/api/v1/auth/**`, `/api/v1/resolve/**`, `/api/v1/search`, `/api/v1/backup/restore`, `/css/**`, `/js/**`
- Authenticated path guard for all other routes

### Epic 0.2 — Shared Layout & Static Assets

**0.2.1** [X] Create shared layout template `templates/layout.html`
- Thymeleaf layout dialect: DOCTYPE, head (meta viewport, title, CSS, HTMX script), body (nav fragment + content + JS scripts)
- Authentication-aware nav bar (unauth: logo only; auth: logo, search, settings, profile, logout)

**0.2.2** [X] Create nav fragment `templates/fragments/nav.html`
- Logo linking to `/` (redirect logic) or `/search` when authenticated
- Search icon → `/search`
- Settings icon → `/settings`
- User avatar → `/profile`
- Logout button → `POST /api/v1/auth/logout` with HTMX (confirm dialog via `hx-confirm`)
- Conditional rendering via Thymeleaf: `sec:authorize="isAuthenticated()"`

**0.2.3** [X] Create base CSS `static/css/styles.css`
- CSS custom properties for colors, spacing, typography
- Responsive grid (desktop + mobile 375px)
- Form styles, button styles, card styles, badge styles
- Wizard step indicator styles
- Skeleton loading placeholder styles
- Password strength meter styles
- Upload drag & drop area styles
- Modal overlay styles

**0.2.4** [X] Create `static/js/app.js`
- Shared initialization: HTMX event listeners, form validation helpers
- Debounce utility (for search input, username availability)
- Flash message display handler
- Session check on page load (`GET /api/v1/auth/session`)

**0.2.5** [X] Vendor HTMX: `static/js/htmx.js`
- Downloaded from htmx.org v2.x
- Served from `/js/htmx.js`

**0.2.6** [X] Create `TestClientConfiguration.java`
- `src/test/java/xyz/tcheeric/bottin/client/TestClientConfiguration.java`
- Test slices configuration, mock beans for external deps

---

## Phase 1 — Onboarding Wizard

> Story: US1 (Onboard) | FRs: FR-001–FR-004, FR-025, FR-026 | Tasks: 14 | Est: 20h
> Dependencies: Phase 0
> Milestone: User can complete full onboarding flow end-to-end

### Epic 1.1 — Client-Side Key Operations

**1.1.1** Create `static/js/nostr-crypto.js` — key generation
- `generateKeypair()`: Web Crypto API `SubtleCrypto.generateKey` (P-256 → secp256k1 via nostr-java WASM or pure JS)
- `nsecToNpub(nsec)`: Decode Bech32 nsec, derive pubkey, re-encode as npub
- `nsecToHex(nsec)`: Extract hex private key from Bech32 nsec
- `hexToNsec(hex)`: Encode hex private key as Bech32 nsec
- FR-001: Key generation MUST use Web Crypto API, entirely client-side
- FR-002: nsec import MUST be processed client-side, NEVER transmitted

**1.1.2** Extend `static/js/nostr-crypto.js` — encryption
- `encryptPrivateKey(privateKeyHex, password)`: PBKDF2 derive key, AES-256-GCM encrypt, return `{encrypted, iv, salt, passwordHash, passwordSalt}`
- `decryptPrivateKey(encrypted, iv, salt, password)`: Reverse of encrypt
- `verifyPassword(passwordHash, passwordSalt, password)`: SHA-256 check
- FR-005: nsec NEVER leaves browser
- Use PBKDF2 with 100k iterations, SHA-256

**1.1.3** Extend `static/js/nostr-crypto.js` — NIP-98 signing
- `signNip98Event(challenge, challengeId, authUrl, method, nsecHex)`: Build kind-27235 event, sign with Schnorr, return base64-encoded event JSON
- Inputs: `challenge` (nonce string), `challengeId`, `authUrl`, `method` (POST), `payload` optional
- Tags: `["u", authUrl]`, `["method", method]`, `["challenge", challenge]`, `["challenge_id", challengeId]`
- Uses nostr-java signing primitives or pure JS Schnorr

**1.1.4** Extend `static/js/nostr-crypto.js` — passphrase operations
- `reEncryptPrivateKey(oldPassword, newPassword, storedIdentity)`: Decrypt with old, re-encrypt with new
- Returns updated identity fields (privateKeyEncrypted, iv, salt, passwordHash, passwordSalt)
- FR-037, FR-038, FR-039

### Epic 1.2 — Root Redirect Logic

**1.2.1** Implement `OnboardingController` — root redirect
- `GET /`: Check localStorage for identity → if none, redirect `/onboarding`; if identity exists but no session → redirect `/login`; if authenticated → redirect `/search`
- This is a server-side redirect but the identity check happens via JS on page load
- Render a minimal landing page that fires `GET /api/v1/auth/session` and redirects based on result
- FR-027: Single identity per device — root redirect enforces one identity; switching requires logout

### Epic 1.3 — Wizard Step Templates

**1.3.1** Create `templates/onboarding/step-method.html`
- Two option cards: "Create New Key" (radio) and "Import Key" (radio + nsec textarea)
- HTMX: `POST /onboarding/step-method` with `{method, nsec?}` → returns step-profile fragment
- Back-button-not-applicable (first step)
- FR-003: Multi-step wizard

**1.3.2** Create `templates/onboarding/step-profile.html`
- Fields: username (with auto NIP-05 badge `{username}@bottin.example.com`), displayName, about, picture (Blossom file upload), banner (Blossom file upload), lud16, website
- Username debounce 500ms → `GET /api/v1/resolve/{username}` availability check
- Avatar/banner: drag & drop upload to Blossom server (NIP-96), store returned URL
- Save form data in sessionStorage for back-navigation
- FR-004, FR-025, FR-026

**1.3.3** Create `templates/onboarding/step-security.html`
- Password field (min 8 chars, strength meter), confirm field
- Client-side validation: min length, match confirmation
- HTMX: `POST /onboarding/step-security` → returns step-confirm fragment
- Back button preserves sessionStorage data
- FR-003

**1.3.4** Create `templates/onboarding/step-confirm.html`
- Read-only review card: method (create/import), username, displayName, about, avatar URL, banner URL, lud16, website, auto NIP-05
- "Back" button, "Confirm" button
- HTMX: `POST /onboarding/complete` → triggers key generation/encryption/storage, returns redirect to `/onboarding/welcome`
- FR-003

**1.3.5** Create `templates/onboarding/step-welcome.html`
- Success banner: "Your Nostr identity is ready!"
- nsec display: masked by default, reveal button, copy button, download as `.txt`
- Checkbox: "I have saved my backup key" (enables Continue button)
- Continue button → `/search`
- FR-005: nsec only shown here, never transmitted

### Epic 1.4 — Onboarding Controller Backend

**1.4.1** Implement `OnboardingController` — wizard steps
- `GET /onboarding` → `step-method.html`
- `POST /onboarding/step-method` → validate method choice, store in session, return `step-profile.html` fragment
- `POST /onboarding/step-profile` → validate profile fields, check username availability server-side, return `step-security.html` fragment
- `POST /onboarding/step-security` → validate password strength, return `step-confirm.html` fragment
- `POST /onboarding/complete` → finalize (note: actual key gen is client-side, server just marks onboarding complete), return redirect
- `GET /onboarding/welcome` → `step-welcome.html`

**1.4.2** Implement username availability endpoint
- `GET /api/v1/resolve/{username}` → check bottin registry for username, return `{available: true/false}`
- Debounced 500ms client-side
- Validation: `[a-z0-9_-]{1,64}`

### Epic 1.5 — Blossom File Upload Client

**1.5.1** Add Blossom upload to `static/js/app.js`
- `uploadFile(file, blossomUrl)`: Upload to `POST {blossomUrl}/upload` per NIP-96
- Returns uploaded file URL
- Handle upload progress, error states, file type validation (image/*)
- Drag & drop handler for avatar/banner upload areas
- FR-025

---

## Phase 2 — NAP Authentication

> Story: US2 (Login) | FRs: FR-005–FR-010 | Tasks: 9 | Est: 14h
> Dependencies: Phase 0, Phase 1 (nostr-crypto.js)
> Milestone: User can log in via NAP and maintain session

### Epic 2.1 — NAP Client Library

**2.1.1** Create `static/js/nap-client.js` — NAP flow
- `napInit(npub)`: `POST /api/v1/auth/init {npub}` → receive challenge
- `napComplete(challengeId, signedEventBase64)`: `POST /api/v1/auth/complete` with `Authorization: Nostr <base64>` header
- Uses `nostr-crypto.js` `signNip98Event()` from Epic 1.1.3
- FR-006, FR-007: nsec NEVER sent to server, only NIP-98 proof
- Error handling for expired challenges, invalid keys, network failures

### Epic 2.2 — Login Page

**2.2.1** Create `templates/login.html`
- Single page: nsec input (password field with paste button), "Sign In" button, "Restore from backup" link → `/restore`
- Flow: paste nsec → derive npub (client-side) → `POST /api/v1/auth/init` → sign challenge → `POST /api/v1/auth/complete` → receive session cookie → redirect to `/search`
- Loading state during NAP flow
- Error state: "Invalid key" / "Authentication failed" / "Challenge expired"

**2.2.2** Implement `LoginController`
- `GET /login` → `login.html`
- No other endpoints needed (NAP auth handled by nap-spring auto-config at `/api/v1/auth/*`)
- FR-008, FR-009, FR-010

### Epic 2.3 — Session Management

**2.3.1** Add session check on page load (`static/js/app.js`)
- On authenticated page load: `GET /api/v1/auth/session`
- If 401: redirect to `/login` with return URL
- If 200: extend session (nap-spring sliding window)
- FR-009: Configurable idle timeout + absolute expiry

**2.3.2** Implement logout flow
- Logout button in nav bar: `POST /api/v1/auth/logout` via HTMX
- Confirm dialog via `hx-confirm`: "This will clear your session."
- On success: clear local session state, redirect to `/login`
- FR-010: Server-side session revocation

**2.3.3** Add authenticated route guard (`ClientSecurityConfig.java`)
- `NapServletFilter` on `/api/v1/follow*`, `/api/v1/block*`, `/api/v1/relays*`, `/api/v1/backup/export`
- Session check on all page routes: `/search`, `/profile/**`, `/settings/**`
- Redirect to `/login` with `?redirect=` parameter on session expiry
- FR-008

### Epic 2.4 — NAP Integration Verification

**2.4.1** Wire nap-spring auto-configuration
- Confirm `nap.enabled=true` registers `NapServletFilter`, `NapChallengeEndpoint`, `NapSessionEndpoint`, `NapCompleteEndpoint`
- Ensure nap endpoints use `NapServletFilter` path matcher for public access

**2.4.2** NAP session cookie configuration
- Cookie name: `client_session` (matching `nap.cookie-name`)
- HttpOnly, Secure (in production), SameSite=Lax
- Path: `/`

---

## Phase 3 — Profile Search

> Story: US3 (Search) | FRs: FR-011, FR-012 | Tasks: 5 | Est: 6h
> Dependencies: Phase 2 (session auth for block-aware results)
> Milestone: Users can search nostrdb for profiles

### Epic 3.1 — Search Service & Controller

**3.1.1** Implement `SearchService.java`
- `search(query, limit, currentUserPubkey?)`: Query nostrdb via `Ndb.searchProfiles(txn, query, limit)`
- Filter results against block list (if authenticated) — FR-020
- Map to `SearchResult` DTO with `isFollowed`/`isBlocked` flags
- Empty query guard (return empty results)
- Max query length: 1000 chars
- FR-011

**3.1.2** Implement `SearchController.java`
- `GET /api/v1/search?q=&limit=` → JSON response with `{query, results[], total}`
- Optional session cookie for authenticated block-aware results (FR-020)
- `GET /search` → `search.html` (Thymeleaf page)
- FR-012: results include avatar, display name, NIP-05, pubkey

### Epic 3.2 — Search Page

**3.2.1** Create `templates/search.html`
- Search input with 300ms debounce → `GET /api/v1/search?q=...&limit=20` via HTMX
- Results container: each result shows avatar (32px), display name, name, NIP-05 badge, pubkey (truncated)
- Action buttons per result: Follow/Unfollow, Block/Unblock (authenticated only)
- States: empty (no query yet), loading (skeleton placeholders), results, no results, error
- Search input auto-focus on page load

**3.2.2** Create `templates/fragments/search-result.html`
- HTMX fragment for a single result row
- Used for dynamically updating follow/block state without full re-render
- Conditional action buttons based on auth state

**3.2.3** Create `SearchResult.java` DTO
- Fields: pubkey, npub, displayName, name, about, picture, nip05, isFollowed, isBlocked

---

## Phase 4 — Follow & Block Management

> Stories: US4 (Follow), US5 (Block) | FRs: FR-013–FR-021, FR-028 | Tasks: 13 | Est: 18h
> Dependencies: Phase 2 (auth), Phase 3 (search)
> Milestone: Users can follow/block profiles, lists persist, kind-3 events published

### Epic 4.1 — Follow Service & Controller

**4.1.1** Implement `FollowListService.java`
- Layered service: `NostrIdentityService` → `FollowListService` → relay client
- `follow(pubkey, relayHint, currentUserPubkey)`: Add to follow list, set dirty flag, auto-unblock if blocked
- `unfollow(pubkey, currentUserPubkey)`: Remove from follow list, set dirty flag
- `getFollows(currentUserPubkey)`: Return `List<FollowEntry>`
- `publishFollowList(pubkey)`: Build kind-3 event with positive "p" tags, sign with stored key, publish to write relays
- Max 1000 entries check (FR-028)
- Cross-check block list before follow (FR-021)
- FR-013, FR-014, FR-015, FR-016

**4.1.2** Implement `FollowController.java`
- `POST /api/v1/follow` → `{"pubkey": "hex", "relay": "wss://..."}` → 200/409/400
- `POST /api/v1/unfollow` → `{"pubkey": "hex"}` → 200
- `GET /api/v1/follows` → `{"follows": [...]}`
- All require NAP session

**4.1.3** Create follow list page `templates/settings/follows.html`
- List of followed pubkeys with avatar, display name, unfollow button
- Pagination or infinite scroll for >100 entries
- Empty state: "You are not following anyone yet"
- FR-014

**4.1.4** Add follow/unfollow actions to search results (HTMX fragments)
- `templates/fragments/follow-button.html`: Toggle button Follow/Unfollow
- On click: `POST /api/v1/follow` or `/api/v1/unfollow`, swap button state
- Optimistic UI: toggle immediately, revert on error

### Epic 4.2 — Block Service & Controller

**4.2.1** Implement `BlockListService.java`
- `block(pubkey, currentUserPubkey)`: Add to block list, auto-unfollow
- `unblock(pubkey, currentUserPubkey)`: Remove from block list
- `getBlocks(currentUserPubkey)`: Return `List<BlockEntry>`
- `isBlocked(pubkey, currentUserPubkey)`: Fast check for search filtering
- `publishBlockList(pubkey)`: Build kind-3 event with negative "p" tags ("-" prefix per NIP-02), merge follows+blocks into single kind-3 event
- Max 1000 entries (FR-028)
- FR-017, FR-018, FR-019

**4.2.2** Implement `BlockController.java`
- `POST /api/v1/block` → `{"pubkey": "hex"}` → 200
- `POST /api/v1/unblock` → `{"pubkey": "hex"}` → 200
- `GET /api/v1/blocks` → `{"blocks": [...]}`
- All require NAP session

**4.2.3** Create block list page `templates/settings/blocks.html`
- List of blocked pubkeys with avatar, display name, unblock button
- Empty state: "No blocked users"
- FR-018

**4.2.4** Add block/unblock actions to search results and profile pages
- `templates/fragments/block-button.html`: Toggle button Block/Unblock
- On click: `POST /api/v1/block` or `/api/v1/unblock`, swap button state
- Block icon/color distinct from follow

### Epic 4.3 — Kind-3 Event Publication

**4.3.1** Implement relay publishing in `FollowListService.java`
- `publishContactList(pubkey)`: Merge follows (positive "p") and blocks (negative "p" with "-" prefix) into single kind-3 event
- Sign with user's private key (decrypted via passphrase — needs client-side signing or server-side signing with session key)
- **Decision**: Since nsec never leaves browser, kind-3 publication must be initiated client-side. Server provides the event construction guidance; client signs and sends signed event to server for relay publication.
- Alternative: Server holds a separate signing key for relay operations (but violates Principle VII). Prefer: client signs, server relays.
- FR-016: Follow list changes publish kind-3 events

**4.3.2** Create relay publication endpoint
- `POST /api/v1/publish-contact-list` → accepts pre-signed kind-3 event, publishes to configured write relays
- Server-side: `nostr-java-client` to publish event to each write relay
- Returns `{event_id, published_to[], failed[]}`

### Epic 4.4 — Profile Page

**4.4.1** Implement `ProfileController.java`
- `GET /profile` → own profile view (from localStorage metadata + nostrdb)
- `GET /profile/{pubkey}` → other user's profile (from nostrdb)
- `POST /profile/update` → update own profile (kind-0 event update)

**4.4.2** Create `templates/profile.html`
- Own profile: display name, username, avatar, banner, about, NIP-05, Lightning, website, npub (copy button)
- Edit mode: inline form fields, save button
- Other user profile: same display, follow/unfollow button, block/unblock button
- FR-012 (search linking to profile)

---

## Phase 5 — Relay Management

> Story: US7 (Relay Management) | FRs: FR-029–FR-035 | Tasks: 10 | Est: 14h
> Dependencies: Phase 2 (auth)
> Milestone: Users can manage relays and publish NIP-65 events

### Epic 5.1 — Relay Service & Controller

**5.1.1** Implement `RelayService.java`
- `getRelays(pubkey)`: List configured relays from server-side store
- `addRelay(pubkey, url, read, write)`: Add relay with validation
- `updateRelay(pubkey, url, read, write)`: Update permissions
- `removeRelay(pubkey, url)`: Remove relay
- `publishRelayList(pubkey)`: Build NIP-65 kind-10002 event, publish to all write relays
- `getDefaultRelays()`: Fallback to `application.yml` defaults if none configured
- Validation: `wss://` scheme, max 100 entries, unique URL, at least one read/write flag
- FR-029–FR-035
- FR-033: Server-side persistence of relay list associated with authenticated pubkey — use JDBC or in-memory store keyed by pubkey

**5.1.2** Implement `RelayController.java`
- `GET /api/v1/relays` → `{"relays": [...]}`
- `POST /api/v1/relays` → `{"url", "read", "write"}` → add/update
- `PUT /api/v1/relays` → `{"url", "read", "write"}` → update
- `DELETE /api/v1/relays` → `{"url"}` → remove
- `POST /api/v1/relays/publish` → publish kind-10002
- All require NAP session cookie
- Contract per Section 6 of contracts/README.md

### Epic 5.2 — Relay Settings UI

**5.2.1** Create `templates/settings/relays.html`
- Read Relays list: show URLs with read badge, remove button
- Write Relays list: show URLs with write badge, remove button
- Add Relay form: URL input, Read checkbox (default checked), Write checkbox (default checked), Add button
- "Save & Publish" button (shows when unsaved changes exist)
- "Unsaved Changes" indicator badge
- Default relays shown as placeholder when list is empty
- FR-029, FR-030, FR-031, FR-032, FR-035

**5.2.2** Create `static/js/settings-relays.js`
- Relay list CRUD UI logic (no page reloads)
- Add relay: validate `wss://`, POST to `/api/v1/relays`, update UI
- Remove relay: DELETE `/api/v1/relays`, update UI
- Toggle permissions: PUT `/api/v1/relays`, update UI
- Save & Publish: persist all changes, POST `/api/v1/relays/publish`, show success/error toast
- Track dirty state for "Unsaved Changes" indicator

**5.2.3** Create relay list defaults config
- Add `bottin.client.default-relays` list to `application.yml`
- Example: `wss://relay.damus.io`, `wss://nos.lol`
- FR-035

### Epic 5.3 — NIP-65 Kind-10002 Publication

**5.3.1** Implement kind-10002 event construction in `RelayService.java`
- Build event with tags: `["r", relayUrl, "read"]`, `["r", relayUrl, "write"]`, or `["r", relayUrl]` (both)
- Return unsigned event for client signing
- Client signs and sends back to server for relay publication
- FR-034

**5.3.2** Implement relay publication client
- `nostr-java-client` to publish events to each configured write relay
- Handle connection failures per relay (partial success)
- Return `{event_id, published_to[], failed[]}`

---

## Phase 6 — Security Settings

> Story: US8 (Passphrase Change, nsec reveal) | FRs: FR-036–FR-040 | Tasks: 7 | Est: 10h
> Dependencies: Phase 2 (auth), Phase 1 (nostr-crypto.js)
> Milestone: Users can change passphrase and reveal nsec

### Epic 6.1 — Settings Hub

**6.1.1** Create `templates/settings/index.html`
- Settings hub overview: cards/links to each section
- Profile, Relays, Security, Follows, Blocks, Backup
- Large card layout with icons

**6.1.2** Implement `SettingsController.java`
- `GET /settings` → `settings/index.html`
- `GET /settings/relays` → `settings/relays.html`
- `GET /settings/security` → `settings/security.html`

### Epic 6.2 — Passphrase Change

**6.2.1** Create `templates/settings/security.html`
- Section 1 — Change Passphrase:
  - Current password field, new password (strength meter), confirm new password
  - Client-side validation: current decrypts nsec, new matches confirm, new ≥ 8 chars
  - Update button triggers `reEncryptPrivateKey()` from nostr-crypto.js
  - Success toast, error display
- Section 2 — Reveal nsec:
  - "Reveal nsec" button → password modal
  - Password modal: enter passphrase → verify against stored `passwordHash`
  - On success: show nsec in warning card, copy button, download button
- Section 3 — Logout:
  - Logout button with confirm dialog
- FR-036–FR-040

### Epic 6.3 — Passphrase Change Execution

**6.3.1** Wire passphrase change in `static/js/app.js`
- Load identity from localStorage
- Verify current passphrase (decrypt attempt)
- Call `reEncryptPrivateKey()`
- Update localStorage atomically: `privateKeyEncrypted`, `privateKeyIv`, `privateKeySalt`, `passwordHash`, `passwordSalt`
- Verify new passphrase works immediately (decrypt test)
- FR-037, FR-038, FR-039

### Epic 6.4 — nsec Reveal

**6.4.1** Wire nsec reveal in `static/js/app.js`
- Load identity from localStorage
- Show password modal (client-side rendered, not a server page)
- On correct passphrase: decrypt nsec, display in warning card
- Copy to clipboard button (`navigator.clipboard.writeText`)
- Download as `.txt` file button
- FR-040

---

## Phase 7 — Backup & Restore

> Story: US6 (Restore) | FRs: FR-022–FR-024 | Tasks: 7 | Est: 8h
> Dependencies: Phase 1 (nostr-crypto.js)
> Milestone: Users can export/restore encrypted backup

### Epic 7.1 — Backup Export

**7.1.1** Implement `BackupService.java`
- `exportBackup(pubkey)`: Gather identity, follow list, block list, build encrypted backup blob
- Uses `EncryptedBackup` data model (version 1, PBKDF2 100k, AES-256-GCM)
- Returns encrypted file for download

**7.1.2** Create backup page `templates/settings/backup.html`
- Export section: "Download Encrypted Backup" button
- Triggers `GET /api/v1/backup/export` → downloads `.bottin-backup` file
- Info text: "Includes your private key, follow list, and block list"
- FR-023

**7.1.3** Implement `BackupController.java` — export
- `GET /api/v1/backup/export` → download encrypted backup file
- Requires NAP session
- Content-Type: `application/octet-stream`
- FR-023

### Epic 7.2 — Backup Restore

**7.2.1** Create `templates/backup.html`
- File picker for `.bottin-backup` file
- Passphrase input (min 8 chars)
- "Restore" button → client-side decryption
- On success: store identity in localStorage, redirect to `/login`
- Error states: wrong passphrase, corrupted file, incompatible version
- FR-024

**7.2.2** Implement restore logic in `BackupService.java`
- `restoreBackup(encryptedBackup, passphrase)`: Decrypt, parse JSON, validate, return identity data
- Server-side: accept encrypted blob, return decrypted payload (nsec still not persisted server-side — decrypted in transit, passed back to client for local storage)
- OR: all decryption happens client-side (preferred for Principle VII). Server only stores/retrieves the encrypted blob.
- Decision: Client-side decryption. Server stores/returns encrypted blob only. FR-024: "MUST NOT be transmitted to any server" — the encrypted backup may be uploaded to server for restoration on a new device, but the server never has the passphrase.

**7.2.3** Implement `BackupController.java` — restore
- `GET /restore` → `backup.html`
- `POST /api/v1/backup/restore` → accept encrypted backup file, return `{status: "uploaded"}` (server stores blob temporarily, client downloads, decrypts client-side, discards server copy)
- FR-024

### Epic 7.3 — Local Storage Persistence

**7.3.1** Implement local storage operations in `static/js/app.js`
- `saveIdentity(identity)`: `localStorage.setItem('imani.identity.{userId}', JSON.stringify(identity))`
- `loadIdentity(userId)`: Retrieve and parse
- `saveFollowList(userId, follows)`: `localStorage.setItem('imani.follows.{userId}', ...)`
- `saveBlockList(userId, blocks)`: `localStorage.setItem('imani.blocks.{userId}', ...)`
- `clearAll(userId)`: Remove all `imani.*` keys for user
- FR-022: State survives browser restart

---

## Phase 8 — Integration, Testing & Polish

> FRs: All | Tasks: 8 | Est: 10h
> Dependencies: All phases above
> Milestone: All 8 stories pass acceptance criteria

### Epic 8.1 — Unit Tests

**8.1.1** Controller unit tests
- `OnboardingControllerTest`, `LoginControllerTest`, `SearchControllerTest`
- `FollowControllerTest`, `BlockControllerTest`, `RelayControllerTest`
- `ProfileControllerTest`, `SettingsControllerTest`, `BackupControllerTest`
- Mock services, test request/response, error scenarios
- FR coverage per controller

**8.1.2** Service unit tests
- `FollowListServiceTest`, `BlockListServiceTest`, `RelayServiceTest`
- `SearchServiceTest`, `BackupServiceTest`
- Edge cases: max lists, duplicate follows/blocks, empty states
- FR-028: Max 1000 enforcements

### Epic 8.2 — Integration Tests

**8.2.1** NAP auth integration test
- Testcontainers with strfry relay
- Full flow: init challenge, sign NIP-98, complete auth, verify session cookie
- Test: expired challenge, invalid key, session refresh, logout
- SC-003: NAP within performance targets

**8.2.2** Search integration test
- nostrdb with seeded profile data
- Test: exact match, partial match, no match, blocked user exclusion
- SC-004: Search within 500ms

### Epic 8.3 — Edge Case & Error Handling

**8.3.1** Edge case scenarios
- NAP server unavailable during login → retry with backoff, user-facing error
- Session expires mid-operation → redirect to login with return URL
- nostrdb uninitialized → graceful "search unavailable" message
- Follow blocked user → 409 error message
- Authenticated user visits onboarding → redirect to settings
- Very long search query → truncate to 1000 chars
- localStorage full → user-facing error with instructions
- Duplicate relay URL → 409 error
- Remove last write relay with pending follows → warning
- Passphrase change mid-failure → atomic localStorage update guards
- FR-009: Session expiry

**8.3.2** Error UI polish
- Consistent error flash messages across all pages
- Network error handling with retry options
- Form validation: inline errors, disabled submit until valid
- Loading states: skeleton placeholders, spinner buttons

### Epic 8.4 — Acceptance Scenario Verification

**8.4.1** User Story Acceptance Checklist
- US1 (Onboard): All 4 scenarios verified
- US2 (Login): All 4 scenarios verified
- US3 (Search): All 4 scenarios verified
- US4 (Follow): All 5 scenarios verified
- US5 (Block): All 4 scenarios verified
- US6 (Restore): All 3 scenarios verified
- US7 (Relays): All 6 scenarios verified
- US8 (Passphrase): All 4 scenarios verified

**8.4.2** Performance verification
- SC-001: Onboarding < 3min
- SC-003: NAP < 1s round trip
- SC-004: Search < 500ms
- SC-005: Follow/unfollow < 2s
- SC-010: Relay publish < 5s
- SC-011: Passphrase change < 1s

---

## Implementation Order

```
Phase 0 ──────────────────────────────────────────────────────────┐
                                                                    │
Phase 1 (Onboarding) ──────────────────────────────────────────────┤
    │                                                                │
    └──► Phase 2 (Login) ──────────────────────────────────────────┤
           │                                                         │
           ├──► Phase 3 (Search) ────► Phase 4 (Follow/Block) ─────┤
           │                                                         │
           ├──► Phase 5 (Relay Mgmt) ──────────────────────────────┤
           │                                                         │
           ├──► Phase 6 (Settings) ────────────────────────────────┤
           │                                                         │
           └──► Phase 7 (Backup/Restore) ──────────────────────────┤
                                                                     │
Phase 8 (Integration, Testing & Polish) ◄───────────────────────────┘
```

## Key Decisions

1. **Kind-3 event signing**: Client-side signing (nsec never leaves browser). Client sends signed event to server for relay publication. This is the only approach compatible with FR-005.
2. **Relay list storage**: Server-side (JDBC/MapDB), associated with pubkey. Needed for server to publish events to correct relays on behalf of the user.
3. **Passphrase change**: Entirely client-side. No server endpoint. The encrypted blob in localStorage is re-encrypted atomically.
4. **Backup decryption**: Client-side. Encrypted backup may transit through server for cross-device restore, but decryption key never reaches server.
