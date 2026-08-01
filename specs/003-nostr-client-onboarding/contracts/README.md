# Contracts: Nostr Client Onboarding & Account Management

## 1. NAP Auth Endpoints

Contract provided by `nap-spring` module. These endpoints are auto-configured when `nap.enabled=true`.

### POST `/api/v1/auth/init`

Initiate a NAP challenge for a given public key.

**Request**:
```json
{
  "npub": "npub1..."  
}
```
(Also accepts raw `pubkey` hex string.)

**Success (200)**:
```json
{
  "challenge_id": "chlg_abc123",
  "challenge": "base64url-32-byte-nonce",
  "auth_url": "https://example.com/api/v1/auth/complete",
  "auth_method": "POST",
  "issued_at": 1750000000,
  "expires_at": 1750000060
}
```

**Failure (400)**:
```json
{
  "status": "error",
  "code": "NAP_INIT_INVALID_NPUB"
}
```

### POST `/api/v1/auth/complete`

Complete NAP auth by presenting a NIP-98 signed event.

**Request**:
- **Header**: `Authorization: Nostr <base64-encoded-kind-27235-event>`
- **Body**: `{"challenge_id": "chlg_abc123"}`

**Success (200)**:
```json
{
  "status": "authenticated",
  "access_token": "...",
  "token_type": "Bearer",
  "expires_at": 1750003600,
  "absolute_expiry_at": 1750043200,
  "principal": {
    "npub": "npub1...",
    "pubkey": "abcdef..."
  },
  "roles": [],
  "permissions": []
}
```

**Response sets cookie**: `client_session` (HttpOnly, Secure, SameSite=Lax)

**Failure (401)**:
```json
{
  "status": "error",
  "message": "Authentication failed"
}
```

### GET `/api/v1/auth/session`

Check and refresh the current session.

**Request**: Session cookie `client_session`

**Success (200)**:
```json
{
  "pubkey": "abcdef...",
  "expires_at": 1750003600,
  "absolute_expiry_at": 1750043200
}
```

**Failure (401)**:
```json
{
  "error": "session_ended",
  "reason": "expired"
}
```

### POST `/api/v1/auth/logout`

Revoke the current session.

**Request**: Session cookie `client_session`
**Response**: `204 No Content`

---

## 2. NIP-98 Event Contract (Client-Side)

The NIP-98 event that the client must sign and submit to `/auth/complete`.

```json
{
  "id": "<serialized-and-hashed-event-id>",
  "kind": 27235,
  "pubkey": "<user-hex-pubkey>",
  "created_at": 1750000010,
  "content": "",
  "tags": [
    ["u", "https://example.com/api/v1/auth/complete"],
    ["method", "POST"],
    ["payload", "<sha256-of-request-body-as-hex>"],
    ["challenge", "<challenge-nonce-from-init>"],
    ["challenge_id", "<challenge-id-from-init>"]
  ],
  "sig": "<schnorr-signature>"
}
```

**Serialization**: Event JSON is base64-encoded and placed in `Authorization: Nostr <base64>` header.

---

## 3. Onboarding Wizard Pages (Thymeleaf Views)

The wizard is served as server-rendered HTML pages with HTMX for dynamic updates.

### Page Flow

```
/onboarding → step-method  (GET)
                   ↓
            /onboarding/step-profile (POST, HTMX)
                   ↓
            /onboarding/step-security (POST, HTMX)
                   ↓
            /onboarding/step-confirm (POST, HTMX)
                   ↓
            /onboarding/complete (POST)
                   ↓
            /onboarding/welcome (GET)
```

### Step Method (`GET /onboarding`)

Renders the method selection page with two options: "Create New Key" and "Import Key".

**HTMX interactions**:
- `POST /onboarding/step-method` with body `{method: "create" | "import", nsec?: "..."}`
- Returns the profile setup form fragment

### Step Profile (`POST /onboarding/step-profile`)

**Fields**: `username`, `displayName`, `about`, `picture` (Blossom upload), `banner` (Blossom upload), `lud16`, `website`

**HTMX interactions**:
- `GET /api/v1/resolve/{username}` — availability check (debounced 500ms)
- NIP-05 is NOT a form field — it is auto-derived from the username server-side as `{username}@{bottin-domain}`
- Avatar and banner are uploaded to the configured Blossom server (NIP-96); the returned URLs are stored in the profile
- Returns security step fragment on success

### Step Security (`POST /onboarding/step-security`)

**Fields**: `password`, `passwordConfirm`

Returns confirm step fragment on success.

### Step Confirm (`POST /onboarding/step-confirm`)

Final submission. On success, returns welcome page redirect.

---

## 4. Search API (Server-Side)

### GET `/api/v1/search?q={query}&limit={limit}`

Search for profiles via nostrdb.

**Query params**:
- `q` — Search query string (max 1000 chars)
- `limit` — Max results (default 20, max 1000)

**Request**: Optional NAP session cookie (authenticated users see block-aware results)

**Success (200)**:
```json
{
  "query": "will",
  "results": [
    {
      "pubkey": "abcdef1234...",
      "npub": "npub1...",
      "display_name": "William",
      "name": "will",
      "about": "Nostr enthusiast",
      "picture": "https://example.com/avatar.jpg",
      "nip05": "will@example.com",
      "is_followed": true,
      "is_blocked": false
    }
  ],
  "total": 1
}
```

---

## 5. Follow/Block API (Server-Side)

All endpoints require NAP session cookie. The session filter validates the cookie before these endpoints are reached.

### POST `/api/v1/follow`

**Request**:
```json
{
  "pubkey": "hex-pubkey-to-follow",
  "relay": "wss://relay.example.com"
}
```

**Success (200)**: `{"status": "followed", "pubkey": "..."}`

**Failure (409)**: `{"error": "already_followed"}` (if already following)
**Failure (400)**: `{"error": "user_blocked"}` (if user is in block list)

### POST `/api/v1/unfollow`

**Request**:
```json
{
  "pubkey": "hex-pubkey-to-unfollow"
}
```

**Success (200)**: `{"status": "unfollowed", "pubkey": "..."}`

### GET `/api/v1/follows`

**Success (200)**:
```json
{
  "follows": [
    {"pubkey": "hex...", "relay": "wss://...", "petname": "...", "added_at": 1750000000}
  ]
}
```

### POST `/api/v1/block`

**Request**:
```json
{
  "pubkey": "hex-pubkey-to-block"
}
```

**Success (200)**: `{"status": "blocked", "pubkey": "..."}`

### POST `/api/v1/unblock`

**Request**:
```json
{
  "pubkey": "hex-pubkey-to-unblock"
}
```

**Success (200)**: `{"status": "unblocked", "pubkey": "..."}`

### GET `/api/v1/blocks`

**Success (200)**:
```json
{
  "blocks": [
    {"pubkey": "hex...", "blocked_at": 1750000000}
  ]
}
```

---

## 6. Relay Management API (Server-Side)

All endpoints require NAP session cookie. The relay list is stored server-side and associated with the authenticated pubkey.

### GET `/api/v1/relays`

List all configured relays for the authenticated user.

**Success (200)**:
```json
{
  "relays": [
    {"url": "wss://relay.example.com", "read": true, "write": true, "added_at": 1750000000},
    {"url": "wss://relay2.example.com", "read": true, "write": false, "added_at": 1750000001}
  ]
}
```

### POST `/api/v1/relays`

Add a new relay or update an existing one.

**Request**:
```json
{
  "url": "wss://relay.example.com",
  "read": true,
  "write": true
}
```

**Success (200)**: `{"status": "added", "url": "wss://relay.example.com"}`

**Failure (400)**: `{"error": "invalid_url"}` (if URL is not valid `wss://`)
**Failure (409)**: `{"error": "duplicate_relay"}` (if relay already exists; use PUT to update)

### PUT `/api/v1/relays`

Update read/write permissions for an existing relay.

**Request**:
```json
{
  "url": "wss://relay.example.com",
  "read": false,
  "write": true
}
```

**Success (200)**: `{"status": "updated", "url": "wss://relay.example.com"}`

**Failure (404)**: `{"error": "relay_not_found"}`

### DELETE `/api/v1/relays`

Remove a relay from the list.

**Request**:
```json
{
  "url": "wss://relay.example.com"
}
```

**Success (200)**: `{"status": "removed", "url": "wss://relay.example.com"}`

**Failure (404)**: `{"error": "relay_not_found"}`

### POST `/api/v1/relays/publish`

Publish the current relay list as a NIP-65 kind-10002 event to all configured write relays.

**Success (200)**:
```json
{
  "status": "published",
  "event_id": "hex-event-id",
  "published_to": ["wss://relay1.example.com", "wss://relay2.example.com"]
}
```

**Failure (503)**: `{"error": "publish_failed", "details": "Could not reach relay at wss://..."}`
