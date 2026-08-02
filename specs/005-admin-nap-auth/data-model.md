# Phase 1 Data Model — Admin sign-in with a Nostr key

**No database change.** No table, no migration, no entity. That is the notable property of this
feature: the administrator's identity is configuration, the session is nap-server's, and the key
itself never leaves the browser.

The four things that hold state:

| What | Where it lives | Lifetime | Who can read it |
|---|---|---|---|
| Administrator public key | Deployment configuration | Until restart | The deployment; it is public anyway |
| Roles and permissions | Code, as a declared registry | Build time | The deployment |
| Session | nap-server `SessionStore`, in memory | Configured TTL, or until sign-out | The deployment |
| Encrypted private key | Administrator's browser | Until sign-out | Only the administrator, and only with the passphrase |

---

## Administrator public key — configuration

| Property | Value |
|---|---|
| Environment variable | `BOTTIN_ADMIN_NPUB` |
| Spring property | `bottin.admin.npub` |
| Accepted forms | `npub1…` (NIP-19) — see the note below on hex |
| Default | **none** |

There is deliberately no default. FR-005 requires an unconfigured deployment to admit nobody, and
a default value is how software accidentally ships with a known administrator.

**Why configuration and not the database**: it is what admits an operator when the database is
empty, wrong, or freshly restored. This is the same bootstrap-versus-operational split feature 004
drew for settings, applied to the one value that must never depend on the thing it is used to
administer.

**On accepting hex as well as npub**: the follow-up feature (board card `367swx4lb8gb`) accepts
both and stores canonically. This feature should decode `npub` via NIP-19 and compare on the
64-character hex form internally, so that accepting hex input later is a validation change rather
than a comparison change. Whether hex is *accepted* here is a small choice for the tasks phase;
comparing canonically is not optional.

### Validation

| Rule | Failure is reported as |
|---|---|
| Present | `no_admin_key_configured` — distinct from a wrong key (FR-006) |
| Decodes as a Nostr public key | `admin_key_unreadable` — distinct from both |
| Resolves to 64-character lowercase hex | as above |

Validation happens once at startup where possible, so a misconfigured deployment is discoverable
from the logs rather than only when somebody fails to sign in.

---

## Roles and permissions — declared, not stored

nap-server's `PermissionRegistry` is built in code and registered as a bean. Nothing about it is
persisted, which is why adding the second role later is a code change plus a store, not a migration
of existing data.

```
PermissionRegistry.of(
    appId       = "bottin-admin",
    permissions = [ admin:read, admin:write, admin:manage-admins ],
    roles       = [ super-admin, admin ],
    defaultRole = none)
```

| Permission | Meaning | Held by |
|---|---|---|
| `admin:read` | View dashboard, records, domains, settings | super-admin, admin |
| `admin:write` | Create, edit, delete records, domains, settings | super-admin, admin |
| `admin:manage-admins` | Add or remove administrators | **super-admin only** |

| Role | Description | Permissions |
|---|---|---|
| `super-admin` | The configured master key. Exactly one. | all three |
| `admin` | Added by the super admin. | `admin:read`, `admin:write` |

**Why declare `admin` and `admin:manage-admins` now, when neither can be exercised?** FR-015
requires the role decision to be one point so the follow-up adds a role rather than introducing
authorization. Declaring the target shape costs three constants and makes the follow-up a change to
one registry and one resolver. Omitting them would mean revisiting every `@RequiresPermission` when
the second role arrives.

**No default role.** An unrecognised key resolves to `AclDecision.denied()`, not to a limited role.
`PermissionRegistry.of` takes a `defaultRole` argument; it must be null or a role with no
permissions, never `admin`.

---

## Session — nap-server's, not bottin's

| Property | Source | Notes |
|---|---|---|
| Store | `SessionStore` (`InMemorySessionStore` by default) | Sessions do not survive a restart, which is acceptable and arguably desirable for an admin surface |
| Cookie | `nap.cookie.*` | `http-only`, `secure` outside local development, `SameSite=Lax`, matching the client |
| Session TTL | `nap.session-ttl-seconds` | Adopt the client's 3600 (Assumption: follow the existing convention) |
| Challenge TTL | `nap.challenge-ttl-seconds` | Adopt the client's 60 — satisfies FR-007's "expired" half |
| Clock skew | `nap.max-clock-skew-seconds` | Adopt the client's 60 — satisfies FR-008 |
| Replay defence | `EventReplayGuard` | Satisfies FR-007's "already used" half |

FR-007, FR-008, and FR-011 are therefore configuration entries rather than code, and the tests for
them assert the configured values take effect, not that the mechanism works — that is nap-core's
own responsibility.

The cookie name must differ from the client's `client_session`. Both may be served from `localhost`
on different ports during development, and cookies are not isolated by port, so a shared name means
one application's session silently overwrites the other's. `admin_session`.

---

## Browser-held identity — the administrator's device only

Never transmitted, never stored server-side. This mirrors the client's existing structure so that
the shared module (research R3) serves both.

| Field | Purpose |
|---|---|
| `privateKeyEncrypted` | The nsec, encrypted under a key derived from the passphrase |
| `privateKeyIv` | Initialisation vector for that encryption |
| `privateKeySalt` | Salt for deriving the encryption key from the passphrase |
| `passwordHash` | Lets a wrong passphrase be rejected without attempting decryption |
| `npub` | Which key this is, so the sign-in page can address the administrator |

Produced by `NostrCrypto.buildEncryptedIdentity(nsec, password)`, which already exists.

**The passphrase itself is stored nowhere** — not in the browser, not on the server (FR-018). Only
`passwordHash` and the salt persist, which is what makes FR-023 true: a forgotten passphrase is
unrecoverable, and the only route back is discarding the stored identity.

### State transitions

```
        no stored identity
                │
                │  first sign-in: nsec + new passphrase   (US1)
                ▼
        stored, locked  ──── wrong passphrase ───▶ stored, locked   (US4.3: key intact, unusable)
                │
                │  correct passphrase                     (US4)
                ▼
        stored, unlocked ── session expires ──▶ stored, locked      (US4.2: passphrase resumes)
                │
                │  sign out                                (US5)
                ▼
        no stored identity  ── and the session is revoked server-side
```

The distinction the diagram exists to make: **session expiry and sign-out end in different
states.** Expiry returns to *stored, locked*, so the passphrase alone resumes. Sign-out returns to
*no stored identity*, so the key is required again. FR-022 makes ending the session and erasing the
key one action — a sign-out that ended the session but left the key would be a false reassurance.

**One device, one identity.** The client already enforces this (`saveIdentity` evicts any other
stored identity, because "the" identity is resolved by enumeration and a leftover would make it
arbitrary). The dashboard inherits the rule with the shared module. There is one administrator key,
so a second stored identity has no meaning.

---

## What deliberately has no model

- **No admin user table.** `admin_users` exists, unused, since V1 — with a `pubkey` column that
  anticipates exactly this feature. It stays unused here: one key in configuration is not a store,
  and populating a table to hold a single value that must work when the database is unavailable
  would defeat the point. The follow-up feature is where that table earns its place.
- **No password anywhere.** Not in configuration, not hashed in a table, not as a fallback (FR-009).
- **No account records, no registration, no recovery tokens.** There is no sign-up (FR-016) and no
  passphrase recovery (FR-023).
