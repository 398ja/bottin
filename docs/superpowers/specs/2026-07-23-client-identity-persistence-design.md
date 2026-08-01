# Client identity persistence & backup

This design closes the two real findings from the `003-nostr-client-onboarding`
review that were originally skipped: onboarding never persists the encrypted
identity to `localStorage` (#0), and backup export returns an empty body (#4).
The third skipped finding (#6, `checkSession` hitting a nonexistent endpoint) is
a **false positive** — `GET /api/v1/auth/session` is provided by nap-spring's
`NapAuthController` and returns `401` when there is no session, which is exactly
what `app.js` expects. No change is made for #6.

## Decisions

- **Login stays session-only.** The login page establishes the NAP session
  cookie; it does not store a local identity. A local identity is created only
  during onboarding or by restoring from a backup file. This avoids adding a
  device-passphrase prompt to login. Consequence: on a fresh device, settings
  (reveal nsec / change passphrase) and backup are unavailable until the user
  restores from a backup file.

## Canonical local identity schema

Stored under `localStorage` key `imani.identity.<npub>` — the field names match
what `settings/security.html` and `nostr-crypto.js` already consume:

```json
{
  "userId": "<npub>",
  "npub": "<npub>",
  "pubkeyHex": "<hex>",
  "privateKeyEncrypted": "<base64>",
  "privateKeyIv": "<base64>",
  "privateKeySalt": "<base64>",
  "passwordHash": "<base64>",
  "passwordSalt": "<base64>",
  "createdAt": <epoch-millis>
}
```

## Components

### `NostrCrypto.buildEncryptedIdentity(nsec, password)`

New helper, the single owner of the schema mapping. Encrypts the key with the
existing `encryptPrivateKey`, maps its `{encrypted, iv, salt, passwordHash,
passwordSalt}` output onto the schema field names, derives `npub`/`pubkeyHex`
from the nsec, and returns the identity object above. Async (PBKDF2).

### Onboarding persistence (#0) — `step-confirm.html`

`generateAndSaveKey` becomes async:

1. `event.preventDefault()`.
2. Obtain the nsec (existing create/import branch, unchanged).
3. Read `password` from `sessionStorage['onboarding-data']`.
4. `identity = await NostrCrypto.buildEncryptedIdentity(nsec, password)`.
5. `APP.saveIdentity(identity)`.
6. Store the nsec in `sessionStorage['onboarding-nsec']` for the welcome page to
   display once; clear `onboarding-data` (removes the plaintext password).
7. Submit the form (`/onboarding/complete` → `/onboarding/welcome`).

`step-welcome.html` reads `onboarding-nsec` to display the backup key (already
does) and clears it afterwards. The "generated and encrypted locally" copy is
now accurate.

### Backup, client-side (#4) — `backup.html`, `app.js`

The server cannot read `localStorage`, so backup is built and consumed entirely
in the browser.

- `app.js`: add `loadFollowList(userId)` / `loadBlockList(userId)`.
- Export: build `{version, createdAt, npub, identity, follows, blocks}` from
  `localStorage` and download as `<npub>.bottin-backup` (JSON). The `identity`
  key material is already AES-GCM encrypted. If no identity is present, show a
  toast instead.
- Restore: parse the uploaded file client-side, validate structure, optionally
  verify the passphrase (`NostrCrypto.verifyPassword`) when one is entered,
  write `identity`/`follows`/`blocks` to `localStorage`, then redirect to
  `/search`.
- Remove the now-dead server endpoints `POST /api/v1/backup/export` and
  `POST /api/v1/backup/restore` from `BackupController` (keep the page-serving
  GET mappings). Drop `/api/v1/backup/export` from the NAP `protected-path-prefixes`
  (`application.yml`) and from `ClientSecurityConfig.PROTECTED_URL_PATTERNS`.
- Update `BackupControllerTest`: keep the page tests, drop the endpoint tests.

## Security note

The `.bottin-backup` file contains the passphrase-encrypted key plus a PBKDF2
`passwordHash`/`passwordSalt` verifier. This is a standard encrypted-wallet
backup; the verifier makes a weak passphrase offline-brute-forceable, which is
industry-normal and accepted here.

## Testing

The client identity/crypto/backup paths are browser JavaScript, and the repo has
no JS test harness (all existing client JS is untested). Those paths are verified
live in the running app. Java-side, `BackupControllerTest` is updated for the
removed endpoints, and the full `mvn verify` reactor must stay green.

## Out of scope

- Persisting an identity from the login flow (decided against).
- Making follow/block writes persist to `localStorage` (separate concern; backup
  includes whatever follow/block lists already exist locally).
