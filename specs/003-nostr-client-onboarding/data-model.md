# Data Model: Nostr Client Onboarding & Account Management

## Entities

### NostrIdentity

A user's cryptographic identity stored locally in the browser.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `userId` | UUID (v4) | Required, immutable | Unique local identifier for the identity |
| `publicKeyHex` | String (64 hex) | Required, matches `[0-9a-f]{64}` | Nostr public key in hex format |
| `privateKeyEncrypted` | String (base64) | Required | Private key encrypted with AES-256-GCM (PBKDF2-derived key from user password) |
| `privateKeyIv` | String (base64) | Required | AES-GCM initialization vector for decryption |
| `privateKeySalt` | String (base64) | Required | PBKDF2 salt for password-based key derivation |
| `passwordHash` | String (base64) | Required | SHA-256 hash of password (for verification, not encryption) |
| `passwordSalt` | String (base64) | Required | Salt for password hash |
| `npub` | String | Derived | Bech32-encoded public key (`npub1...`) |
| `nsec` | String | Derived, transient | Bech32-encoded private key (only shown during backup step, not stored unencrypted) |
| `createdAt` | ISO 8601 | Required | Timestamp of identity creation |
| `lastUsedAt` | ISO 8601 | Nullable | Timestamp of last login |

**Storage**: `localStorage` under key `imani.identity.{userId}`

**State transitions**:
```
[Created] → [Encrypted with password] → [Stored locally]
                                              ↓
                                      [Loaded for NAP auth]
                                              ↓
                                      [Session established]
                                              ↓
                              [Change passphrase] → [Re-encrypted with new password]
                                              ↓
                                      [Stored locally (updated)]
```

---

### ProfileMetadata

User-visible profile information from kind-0 events.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `name` | String | Max 64 chars, `[a-z0-9_-]` | Username |
| `displayName` | String | Max 100 chars | Display name |
| `about` | String | Max 300 chars | Bio/description |
| `picture` | URL | Valid HTTPS URL | Avatar image URL |
| `banner` | URL | Valid HTTPS URL (Blossom URL) | Banner image URL (uploaded to Blossom server) |
| `nip05` | String | Max 100 chars, auto-derived | NIP-05 identifier (`user@domain`), derived from username |
| `lud16` | String | Max 100 chars | Lightning address |
| `website` | URL | Valid URL | Website URL |

**Derived**: `bestDisplayName()` = `displayName` if non-blank else `name`

**Storage**: Stored as part of the NostrIdentity backup. Also queryable from nostrdb index.

---

### FollowList

The set of pubkeys the user follows (NIP-02 kind-3 contacts).

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `userId` | UUID | Required, immutable | Owning identity |
| `entries` | FollowEntry[] | Max 1000 entries | Ordered list of followed pubkeys |
| `lastPublishedEventId` | String (64 hex) | Nullable | Event ID of the last kind-3 event published |
| `lastPublishedRelay` | URL | Nullable | Relay URL of last publication |
| `updatedAt` | ISO 8601 | Required | Timestamp of last modification |
| `dirty` | boolean | Default false | Whether pending changes need relay publication |

#### FollowEntry

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `pubkey` | String (64 hex) | Required, immutable | Followed user's public key |
| `relay` | URL | Nullable | Preferred relay hint |
| `petname` | String | Max 50 chars | Local nickname (per NIP-02) |
| `addedAt` | ISO 8601 | Required | When the follow was added |

**Storage**: `localStorage` under key `imani.follows.{userId}`

**State transitions**:
```
[Empty list] → [Add pubkey(s)] → [Dirty flag set]
                                      ↓
                              [Publish kind-3 to relays]
                                      ↓
                              [Dirty flag cleared]
                                      ↓
                              [Remove pubkey(s)] → ... (repeat)
```

---

### BlockList

The set of pubkeys the user has blocked.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `userId` | UUID | Required, immutable | Owning identity |
| `entries` | BlockEntry[] | Max 1000 entries | List of blocked pubkeys |
| `updatedAt` | ISO 8601 | Required | Timestamp of last modification |

#### BlockEntry

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `pubkey` | String (64 hex) | Required, immutable | Blocked user's public key |
| `blockedAt` | ISO 8601 | Required | When the block was added |
| `reason` | String | Max 200 chars (optional) | User-specified reason |

**Storage**: `localStorage` under key `imani.blocks.{userId}`

**State transitions**:
```
[Pubkey not blocked] → [Block user] → [In block list, hidden from search]
                                            ↓
                                    [Unblock user] → [Removed from block list]
```

---

### RelayList

The set of Nostr relay URLs the user has configured for reading and publishing.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `pubkey` | String (64 hex) | Required, immutable | Owning identity's public key |
| `relays` | RelayEntry[] | Max 100 entries | Ordered list of configured relays |
| `lastPublishedEventId` | String (64 hex) | Nullable | Event ID of the last NIP-65 kind-10002 event |
| `updatedAt` | ISO 8601 | Required | Timestamp of last modification |

#### RelayEntry

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `url` | URL | Required, immutable, `wss://` scheme | Relay WebSocket URL |
| `read` | boolean | Default true | Whether to read from this relay |
| `write` | boolean | Default true | Whether to write to this relay |
| `addedAt` | ISO 8601 | Required | When the relay was added |

**Storage**: Server-side (associated with authenticated pubkey; in-memory or JDBC)

**State transitions**:
```
[Empty list] → [Add relay(s)] → [Dirty flag set]
                                      ↓
                              [Publish kind-10002 to relays]
                                      ↓
                              [Dirty flag cleared]
                                      ↓
                              [Remove relay(s)] → ... (repeat)
```

---

### NAPSession

Server-side session record (managed entirely by `nap-spring`).

| Field | Type | Description |
|-------|------|-------------|
| `sessionId` | String (base64url) | Opaque session identifier (24 bytes random) |
| `challengeId` | String | Link to originating challenge |
| `principalNpub` | String | npub of authenticated user |
| `principalPubkey` | String (64 hex) | Hex pubkey of authenticated user |
| `issuedAt` | long (epoch sec) | Session creation time |
| `expiresAt` | long (epoch sec) | Sliding expiry (idle window) |
| `absoluteExpiryAt` | long (epoch sec) | Hard cap expiry |

**Storage**: Server-side (InMemorySessionStore or JDBC-backed nap-jdbc)

**State transitions** (NAP-defined):
```
[Issued] → [Active] → [Expired (idle TTL exceeded)]
    ↑           ↓
    |      [Touched (sliding window)]
    |           ↓
    |      [Active (renewed)]
    |
    +--- [Revoked (logout)]
```

---

### NAPChallenge

Server-issued challenge for NAP authentication (managed by `nap-spring`).

| Field | Type | Description |
|-------|------|-------------|
| `challengeId` | String (base64url) | Opaque challenge identifier |
| `challenge` | String (base64url) | CSPRNG 32-byte nonce |
| `npub` | String | Requested user's npub |
| `pubkey` | String (64 hex) | Resolved hex pubkey |
| `authUrl` | URL | URL the NIP-98 proof must authorize |
| `issuedAt` | long (epoch sec) | Challenge creation time |
| `expiresAt` | long (epoch sec) | Challenge TTL (default 60s) |
| `state` | enum | ISSUED → REDEEMED | EXPIRED | FAILED_TERMINAL |

**Storage**: Server-side (InMemoryChallengeStore or JDBC-backed nap-jdbc)

---

### SearchResult

A resolved search result combining nostrdb profile data with app-level state.

| Field | Type | Description |
|-------|------|-------------|
| `pubkey` | String (64 hex) | User's public key |
| `npub` | String | Bech32-encoded pubkey |
| `displayName` | String | Best available display name |
| `name` | String | Username |
| `about` | String | Bio |
| `picture` | URL | Avatar URL |
| `nip05` | String | NIP-05 identifier |
| `isFollowed` | boolean | Whether the current user follows this pubkey |
| `isBlocked` | boolean | Whether the current user has blocked this pubkey |

---

### EncryptedBackup

Export format for identity data recovery.

| Field | Type | Description |
|-------|------|-------------|
| `version` | int (1) | Backup format version |
| `encryptedData` | byte[] | AES-256-GCM encrypted payload (JSON blob) |
| `iv` | byte[] (12 bytes) | AES-GCM initialization vector |
| `salt` | byte[] (16 bytes) | PBKDF2 salt |
| `iterations` | int (100000) | PBKDF2 iteration count |

**Encrypted payload** (decrypted JSON):
```json
{
  "privateKeyHex": "abcdef...",
  "followList": ["pubkey1", "pubkey2", ...],
  "blockList": ["pubkey3", ...],
  "createdAt": "2026-07-22T00:00:00Z"
}
```

---

## Relationships

```
NostrIdentity 1 ──── * ProfileMetadata  (one identity, one profile)
NostrIdentity 1 ──── 1 FollowList       (one identity, one follow list)
NostrIdentity 1 ──── 1 BlockList        (one identity, one block list)
NostrIdentity 1 ──── * NAPSession       (one identity, multiple server sessions over time)
NostrIdentity 1 ──── 1 RelayList       (one identity, one relay list)
ProfileMetadata * ──── 1 Ndb            (many profiles in search index)
NAChallenge *  ──── 1 NapServer        (server-managed)
NAPSession *     ──── 1 NapServer       (server-managed)
```

---

## Validation Rules

| Entity | Field | Rule | Error |
|--------|-------|------|-------|
| NostrIdentity | `publicKeyHex` | Must match `[0-9a-f]{64}` | Invalid public key format |
| NostrIdentity | `privateKeyEncrypted` | Must be non-empty, valid base64 | Corrupted key storage |
| ProfileMetadata | `name` | `[a-z0-9_-]{1,64}` | Username must be 1-64 chars, lowercase alphanumeric, hyphens, underscores |
| ProfileMetadata | `name` | Must be unique (checked against registry) | Username already taken |
| ProfileMetadata | `picture` | Must be HTTPS URL | Avatar must be a secure URL |
| ProfileMetadata | `banner` | Must be HTTPS URL | Banner must be a secure URL |
| ProfileMetadata | `lud16` | Must match `user@domain` | Invalid Lightning address format |
| FollowList | `entries` | Max 1000 | Follow list limit reached |
| BlockList | `entries` | Max 1000 | Block list limit reached |
| FollowEntry | `pubkey` | Must match `[0-9a-f]{64}` | Invalid pubkey format |
| FollowEntry | `pubkey` | Must not be in BlockList (checked before add) | Cannot follow a blocked user |
| RelayList | `relays` | Max 100 entries | Relay list limit reached |
| RelayEntry | `url` | Must match `wss://` scheme | Relay URL must start with wss:// |
| RelayEntry | `url` | Must be unique per pubkey | Duplicate relay URL |
| RelayEntry | `read` or `write` | At least one must be true | Relay must have read and/or write permission |
| EncryptedBackup | `password` | Min 8 chars | Password too weak |
