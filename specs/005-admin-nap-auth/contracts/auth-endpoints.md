# Contract — Authentication endpoints

**Owner**: nap-spring's `NapAuthController`. bottin does not implement these; it configures them
and depends on their shape. Documented so the admin browser code and its tests have a fixed target.

Verified by inspecting the artifact:

```
NapAuthController(NapServer, NapProperties, ObjectMapper)
  ResponseEntity<?> init(Map<String,String>)
  ResponseEntity<?> complete(HttpServletRequest, HttpServletResponse)
  ResponseEntity<?> checkSession(HttpServletRequest)
  ResponseEntity<?> logout(HttpServletRequest, HttpServletResponse)
```

## The handshake

Taken from the client's working implementation (`app.js:19-43`), which the dashboard reproduces.

### 1. `POST /api/v1/auth/init` — public

```json
{ "npub": "npub1…" }
```

Returns a challenge:

```json
{ "challenge": "…", "challenge_id": "…", "auth_url": "…" }
```

**A challenge is issued for any well-formed npub**, whether or not it is the administrator's. See
research R4: answering differently would tell an anonymous caller which npub administers the
deployment. Rejection happens at step 3, on proof, not here on assertion.

**Rate limited per client address.** This endpoint is unauthenticated and performs work on demand.

### 2. Sign locally — never leaves the browser

```js
NostrCrypto.signNip98Event(challenge, challenge_id, auth_url, 'POST', hexKey)
```

The hex key comes from decrypting the stored identity with the passphrase. This step is the whole
security argument: the deployment learns that the holder controls the key without ever receiving it.

### 3. `POST /api/v1/auth/complete` — public

```
Authorization: Nostr <base64 signed event>
Content-Type: application/json

{ "challenge_id": "…" }
```

On success, sets the session cookie. On failure, no session is created.

nap-core validates the proof — signature, challenge binding, expiry, replay, clock skew — and then
bottin's `ConfiguredAdminAclResolver` decides the role. Both must pass.

| Outcome | Cause | FR |
|---|---|---|
| Session established, role `super-admin` | Proof valid **and** pubkey matches the configured key | FR-003 |
| Refused | Proof valid, pubkey does not match | FR-004 |
| Refused | No administrator key configured | FR-005 |
| Refused | Configured value is not a usable key | FR-006 |
| Refused | Challenge expired, already used, or clock outside tolerance | FR-007, FR-008 |

The refusal shown to the caller must not reveal which key *would* have been accepted (US2.3). The
log entry distinguishes all four causes (FR-006, FR-012).

### 4. `GET /api/v1/auth/session`

Whether the caller still holds a valid session. Used to decide between showing the dashboard, the
unlock prompt, or the first sign-in form.

### 5. `POST /api/v1/auth/logout`

Ends the session server-side. **This is only half of sign-out** — FR-022 also requires erasing the
stored key from the browser, which is bottin's part and is specified in
[`browser-identity.md`](./browser-identity.md). Calling this endpoint alone would leave an
encrypted key on the device while telling the administrator they had signed out.

## Configuration this depends on

| Property | Value | Satisfies |
|---|---|---|
| `nap.enabled` | `true` | — |
| `nap.challenge-ttl-seconds` | `60` | FR-007 (expiry) |
| `nap.session-ttl-seconds` | `3600` | FR-011 |
| `nap.max-clock-skew-seconds` | `60` | FR-008 |
| `nap.protected-path-prefixes` | the admin surface | FR-010 |
| `nap.cookie.name` | `admin_session` | Must differ from the client's `client_session`; cookies are not isolated by port, so a shared name means one application's session overwrites the other's on `localhost` |
| `nap.cookie.secure` | `true`, overridable for local development | — |

## Verification

- The handshake succeeds end to end with the configured key and establishes a session.
- Each of the five refusal causes is exercised and produces no session.
- A challenge issued for a non-administrator npub is still issued (no oracle), and still fails at
  `complete`.
- `logout` invalidates the session such that a subsequent admin request redirects to sign-in.
