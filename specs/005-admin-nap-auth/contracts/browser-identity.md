# Contract — Browser-held identity

**Owner**: `bottin-admin-ui`, using the shared crypto module (research R3). Defines what the admin
browser holds, and the three transitions it supports.

Nothing here crosses the network. The contract exists because FR-002, FR-017, FR-018 and FR-022 are
all statements about browser behaviour, and browser behaviour that is not specified is browser
behaviour that is not tested.

## Stored shape

One identity per browser, produced by `NostrCrypto.buildEncryptedIdentity(nsec, password)`:

| Field | Purpose |
|---|---|
| `npub` | Which key this is |
| `privateKeyEncrypted` | The key, encrypted under a key derived from the passphrase |
| `privateKeyIv` | Initialisation vector |
| `privateKeySalt` | Salt for deriving the encryption key |
| `passwordHash` | Rejects a wrong passphrase without attempting decryption |

**The passphrase is not among them.** It exists only in memory while being used (FR-018). The
plaintext key likewise exists only for the moment it signs, held the way the client holds it — in
session storage with an idle timeout, not in local storage.

## Transition 1 — first sign-in (US1)

```
input:  nsec, new passphrase
        │
        ├─▶ derive, encrypt, store the identity          (FR-017)
        ├─▶ run the handshake with the decrypted key     (auth-endpoints.md)
        └─▶ on success: dashboard.  on refusal: see below
```

**A refused key must not leave an identity behind.** If the proven key is not the configured
administrator, the stored identity is discarded before reporting the failure — otherwise the
browser holds an encrypted key that can never sign in, and the next visit shows an unlock prompt
that is guaranteed to fail. This is the edge case named in the spec as "the private key is supplied
but does not match".

## Transition 2 — unlock (US4)

```
input:  passphrase
        │
        ├─▶ verify against passwordHash                  (no decryption attempt on a wrong one)
        ├─▶ wrong  ─▶ error, identity untouched, retry allowed   (FR-021, US4.3)
        └─▶ right  ─▶ decrypt, run the handshake, dashboard
```

Used both when returning to the dashboard and when a session expires mid-work (FR-019, FR-020).
**The nsec is never requested again while the identity is stored** — that is the point of storing it.

The administrator must also be able to discard the stored identity deliberately, for a forgotten
passphrase (FR-023, US4.4). That path leads back to transition 1.

## Transition 3 — sign out (US5)

```
        ├─▶ POST /api/v1/auth/logout        (server session ends)
        └─▶ erase the stored identity       (device holds nothing)
```

**Both, atomically, in one action** (FR-022). Either half alone is a bug worth naming:

- Ending the session without erasing leaves an encrypted key on the device after the administrator
  was told they signed out.
- Erasing without ending the session leaves a live session cookie the browser will keep presenting.

If the logout request fails, the local erase still happens and the failure is reported. A key left
on a device is the worse outcome, and the session expires on its own.

## Verification

Vitest, mirroring the client's suite:

| Test | Asserts |
|---|---|
| First sign-in stores an encrypted identity | `privateKeyEncrypted` present, plaintext nsec absent from storage |
| The passphrase is never stored | It appears in no storage key after sign-in |
| A wrong passphrase is rejected | Error raised, stored identity byte-identical afterwards |
| The right passphrase yields the key | Decrypts to the original hex |
| A refused key leaves nothing stored | Storage empty after a mismatch |
| Sign-out erases | Storage empty, and the logout request was sent |
| Sign-out erases even if logout fails | Storage empty, failure reported |
| Session expiry does **not** erase | Identity still stored, so the passphrase alone resumes |

The last two are the pair that distinguishes expiry from sign-out — the distinction the whole
design rests on, and the one most likely to be collapsed by accident.
