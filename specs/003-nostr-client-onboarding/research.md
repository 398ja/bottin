# Research: Nostr Client Onboarding & Account Management

## Overview

Consolidated findings from investigating nostrdb search API, imani-apps onboarding patterns, and NAP v2 Spring integration. All technical unknowns resolved.

---

## 1. Nostrdb Profile Search

**Decision**: Use `Ndb.searchProfiles(txn, query, limit)` for name-based profile search, then resolve full profiles via `getProfileByPubkey()`.

**Rationale**: nostrdb natively indexes `name` and `display_name` fields from kind-0 profile events. The search returns pubkeys directly (not full events), making lookups extremely fast (<100µs). Resulting pubkeys are then resolved to full `Profile` objects. This avoids building an additional search index.

**API surface**:
- `searchProfiles(Transaction, String query, int limit)` → `List<byte[]>` (pubkeys)
- `getProfileByPubkey(Transaction, String pubkeyHex)` → `Optional<Profile>`
- `Profile` fields: `name`, `displayName`, `about`, `picture`, `banner`, `nip05`, `lud16`, `lud06`, `website`
- `Profile.bestDisplayName()` returns `displayName` if set, else `name`

**Usage pattern**:
```java
try (Transaction txn = ndb.beginTransaction()) {
    List<byte[]> pubkeys = ndb.searchProfiles(txn, query, limit);
    List<Profile> results = pubkeys.stream()
        .map(pk -> ndb.getProfileByPubkey(txn, pk))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
}
```

**Security limits**: Max query length 1000 chars, max limit 100M results. Both enforced by `Filter` constants.

**Alternatives considered**: Using `Filter.search()` on kind-0 events via `queryNotes()` was considered but rejected because `searchProfiles` is purpose-built for profile lookups and avoids unnecessary event deserialization.

---

## 2. Imani-Apps Onboarding Flow

**Decision**: Follow the imani-apps multi-step wizard pattern exactly: method selection → profile setup → security (password) → confirm → welcome.

**Rationale**: The user explicitly specified "similar to the one in the imani-apps." The existing pattern is proven, user-tested, and covers all required flows.

**Flow details** (from imani-apps `auth/register.html` + `auth/js/register.js`):

| Step | Screen | Key Actions |
|------|--------|-------------|
| 1 | Method Choice | "Create New Key" (secp256k1) or "Import Key" (nsec paste) |
| 2 | Profile Setup | Username (availability check), display name, bio, avatar/banner (Blossom upload), Lightning address, NIP-05 |
| 3 | Security | Password (min 8 chars, strength indicator), confirm. Used for AES-GCM local encryption. |
| 4 | Confirm | Review card of all entered data |
| 5 | Processing | Loading spinner with dynamic status text |
| 6 | Backup Key | Display nsec (masked, copy to clipboard, checkbox acknowledgment) |
| 7 | Success | "You're All Set!" with NIP-05 display |
| 8 | Welcome | Carousel walkthrough (slides: identity, payments, security, ready) |

**Key technical patterns**:
- Web Crypto API for key generation (`crypto.getRandomValues` for entropy)
- PBKDF2 (100k iterations, SHA-256) for password-based key derivation
- AES-256-GCM for local key encryption
- Debounced username availability check (500ms) via backend API
- Profile validation via regex (`/^[a-z0-9_-]{1,64}$/` for usernames)
- Step data preserved across back-navigation (no reload required)

**Differences from imani-apps for this feature**:
- No LNbits/Lightning wallet creation (out of scope)
- No encrypted vault events or NIP-78 app-specific data
- After onboarding, user goes directly to the app home (not a sync page)
- Login uses NAP (not local password unlock + Nostr key session)

---

## 3. NAP v2 Authentication

**Decision**: Use `nap-spring` for all server-side authentication. The client signs NIP-98 events in JavaScript. nap-spring provides auto-configuration, auth controller, session filter, and permission interceptor.

**Rationale**: NAP v2 is the existing authentication protocol in the project ecosystem. `nap-spring` provides turnkey Spring Boot integration (auto-configuration, servlet filter, auth controller). The client-side proof construction via `nap-client` is integration-test focused; the actual browser signing uses Web Crypto API directly, matching the nostr-java library patterns.

**Protocol flow**:

```
Client                              Server (nap-spring)
  │                                       │
  │  POST /api/v1/auth/init {npub}        │
  │──────────────────────────────────────>│
  │  {challenge_id, challenge, auth_url}  │
  │<──────────────────────────────────────│
  │                                       │
  │  [User signs NIP-98 kind:27235        │
  │   with {u, method, payload,           │
  │    challenge, challenge_id} tags]      │
  │                                       │
  │  POST /api/v1/auth/complete           │
  │  Authorization: Nostr <base64 event>  │
  │  Body: {challenge_id}                 │
  │──────────────────────────────────────>│
  │  200 + Set-Cookie: merchant_session   │
  │<──────────────────────────────────────│
  │                                       │
  │  GET /api/v1/auth/session (cookie)    │
  │──────────────────────────────────────>│
  │  {pubkey, expires_at, abs_expiry_at}  │
  │<──────────────────────────────────────│
```

**Configuration** (in `application.yml`):
```yaml
nap:
  enabled: true
  external-base-url: ${BOTTIN_EXTERNAL_URL:http://localhost:8080}
  challenge-ttl-seconds: 60
  session-idle-ttl-seconds: 1800
  session-absolute-ttl-seconds: 43200
  protected-path-prefixes: /api/follow,/api/block,/api/backup
  cookie:
    name: client_session
    http-only: true
    secure: true
    same-site: Lax
```

**Auto-wired beans**: `NapAuthController` (4 endpoints), `NapServer`, `NapSessionFilter` (protected paths), `NapPermissionInterceptor` (`@RequiresPermission`), `ChallengeStore`, `SessionStore`.

**Client-side signing** (JavaScript):
```javascript
// After receiving challenge from /auth/init
const event = {
    kind: 27235,
    created_at: Math.floor(Date.now() / 1000),
    tags: [
        ['u', authUrl],
        ['method', 'POST'],
        ['payload', sha256(JSON.stringify({challenge_id}))],
        ['challenge', challenge],
        ['challenge_id', challengeId]
    ],
    content: ''
};
event.id = serializeAndHash(event);
event.sig = schnorrSign(event.id, privateKeyHex);
const header = 'Nostr ' + btoa(JSON.stringify(event));
```

**Alternatives considered**:
- Local password-only auth (no NAP): simpler but doesn't provide server-verified sessions for follow/block operations
- NIP-46 bunker auth: more complex, requires external signer; saved for future enhancement
- Static API tokens: insecure, no identity binding

---

## 4. Follow/Block List Architecture

**Decision**: Store follow and block lists locally (IndexedDB) for offline read access. Publish changes as NIP-02 kind-3 events to configured relays. The kind-3 event content contains the complete list of followed pubkeys with relay hints; blocked pubkeys are tagged with a `"-"` prefix per NIP-02 convention.

**NIP-02 kind-3 event structure**:
```json
{
  "kind": 3,
  "tags": [
    ["p", "<followed-pubkey-hex>", "<relay-url>", "<petname>"],
    ["p", "<another-followed-pubkey>"],
    ["p", "-<blocked-pubkey-hex>"]
  ],
  "content": "{\"<pubkey>\": \"<relay-url>\", ...}"
}
```

**Sync approach**: On each follow/unfollow/block/unblock action, the local list is updated immediately and a new kind-3 event is published to relays asynchronously. If relays are unreachable, the change is queued for retry.

**Alternatives considered**:
- Server-side storage of follow lists: rejected per constitution Principle VII (data minimization) — follow lists are user-owned data
- Relay-only with no local cache: rejected because offline access to own follow list is a hard requirement (FR-022)

---

## 5. Browser Crypto & Key Storage

**Decision**: Use Web Crypto API for key generation and signing. Store encrypted private key in `localStorage` using AES-256-GCM with a PBKDF2-derived key from the user's password (100k iterations, SHA-256). The password never leaves the browser.

**Key generation**: secp256k1 (via Web Crypto `SubtleCrypto.generateKey` or pure JS library like `@noble/secp256k1`).

**Key export format**: Encrypted backup file contains the private key, follow list, and block list, encrypted with the user-supplied passphrase.

**Security properties**:
- Private key: never sent to any server, never leaves the browser
- NAP authentication uses the key to sign a challenge, proving ownership without exposing the key
- Password protects local key storage, but the key itself authenticates via NAP

---

## Summary of Key Decisions

| Area | Decision | Rationale |
|------|----------|-----------|
| Search API | `ndb.searchProfiles()` | Native name/displayName index, fastest path |
| Onboarding Flow | imani-apps multi-step wizard | User-specified, proven pattern |
| Auth Protocol | NAP v2 (nap-spring) | Existing project component, Spring auto-config |
| Client Signing | Web Crypto (JS) | no server key exposure |
| Session Store | HTTP-only secure cookie | nap-spring default |
| Follow/Block Storage | IndexedDB (local) + kind-3 events (relay) | Offline-capable, NIP-02 compliant |
| Key Storage | localStorage, AES-256-GCM encrypted | Password-protected, never server-side |
| Wallet/Lightning | Out of scope | Not requested, removes complexity |
