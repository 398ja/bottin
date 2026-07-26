# Profile and Settings Pages — Design

**Date:** 2026-07-26
**Status:** Approved (pending spec review)
**Module:** `bottin-client-ui`

## Purpose

Implement the Profile page and complete the Settings section so a logged-in user
can edit their Nostr profile and relay list in the browser and publish both to the
Nostr network. Profile edits become a signed **kind-0** metadata event; the relay
list becomes a signed **kind-10002** relay-list event (NIP-65). All signing and
relay I/O happen client-side, consistent with the module's existing "dumb server"
architecture.

## Scope

**In scope**

- Profile page (`/profile`): display, edit, and publish kind-0 profile metadata.
- Relays page (`/settings/relays`): editable read/write relay list, publish kind-10002.
- Shared unlock-once-per-session signing flow and a client-side relay publish path.
- Extend the stored identity to persist `about`, `lud16`, `website` (currently dropped).
- Fix onboarding so new signups persist all profile fields.
- Verify Security and Backup still work end-to-end.
- JS unit tests (Vitest) wired into `mvn verify`; server-side route/render tests; an
  end-to-end verification how-to.

**Out of scope**

- Viewing other users' profiles (`/profile/{pubkey}` stays a placeholder).
- Follows (kind-3) and Blocks (kind-10000) — remain current placeholders.
- Editing NIP-05 (tied to bottin registration; a separate re-registration flow).
- Server-side event building or relay broadcasting (`nostr-java-client` stays unused).

## Architecture

The server remains stateless about identity. It serves Thymeleaf shells and the NAP
auth endpoints; it never sees the private key or any event. The only server change is
adding one missing route mapping. All data, cryptography, event construction, and
relay WebSocket traffic run in the browser, reusing the existing client-side patterns
(`NostrCrypto` for crypto, `nostr-tools` for signing, the Security page's modal
patterns for passphrase entry).

### Browser state

| State | Storage | Shape | Notes |
| --- | --- | --- | --- |
| Identity | `localStorage: imani.identity.<npub>` | crypto fields + `displayName`, `picture`, `banner`, `nip05`, `about`, `lud16`, `website` | Gains `about`, `lud16`, `website`, which onboarding currently collects then drops. |
| Relay list | `localStorage: imani.relays.<npub>` | `[{ url, read, write }]` | New. NIP-65-shaped. Seeded on first visit from the server-configured defaults (see below) if absent. Single source of truth for both pages. |
| Session key | `sessionStorage: imani.session.<npub>` | `{ key: <hexPrivateKey>, expiresAt: <epochMs> }` | Written on unlock, read by publish actions, cleared on logout, on tab close, and on idle expiry. |

### Default relay set

The default relays come from server configuration, not a client-side constant. They are
provided by the `BOTTIN_DEFAULT_RELAYS` environment variable — a comma-separated list of
`wss://` URLs — and every configured relay is seeded as both **read and write**.

- **Binding:** `application.yml` maps `bottin.client.default-relays: ${BOTTIN_DEFAULT_RELAYS:}`,
  bound to a `List<String>` via a new `ClientProperties` (`@ConfigurationProperties(prefix =
  "bottin.client")`) class. The public relays currently hardcoded in the yml are removed;
  real deployments set the env var. If unset, the default list is empty.
- **Surfacing to the client:** a new authed endpoint `GET /api/v1/relays/defaults` returns
  the configured relays as `{ relays: [{ url, read: true, write: true }, ...] }`, backed by
  `ClientProperties`.
- **Seeding:** a shared `APP.ensureRelaysSeeded()` helper runs on the Profile and Relays
  pages; if `imani.relays.<npub>` is absent, it fetches `/api/v1/relays/defaults` and saves
  the result as the user's initial relay list. Thereafter the user's edited list is the
  source of truth.
- **Empty config:** if `BOTTIN_DEFAULT_RELAYS` is unset (no defaults) and the user has no
  saved relays, publish is blocked with "Add at least one write relay in Settings → Relays."

## Components

### 1. Profile page (kind-0)

- **Route/controller:** unchanged. `GET /profile` already renders the shell.
- **Template (`profile.html`, rewritten):** a live **preview header** (avatar, display
  name, NIP-05) above an **edit form** pre-filled from the stored identity.
  - Editable fields: `display_name`, `about` (textarea), `picture` (URL), `banner`
    (URL), `lud16` (lightning address), `website`.
  - **NIP-05 is read-only** (display only) — tied to the bottin registration.
  - **npub** displayed with a working copy button (today's `data-npub` is never set).
- **Driver (`profile.js`, new):** populates the form, renders the live preview, runs
  validation, and handles Save & Publish.
- **Validation (client-side):** `picture`/`banner`/`website` must be safe `http(s)`
  URLs (reuse the `safeImageUrl` guard); `lud16` must match a lightning-address shape
  (`user@domain`); `display_name` bounded length. Avatar preview uses the
  `onerror`→default-svg fallback.
- **Save & Publish flow:**
  1. Validate; on success, write the fields into the stored identity locally (so the
     nav avatar and `/login` recognition card update immediately).
  2. `ensureUnlocked()` → obtain the session key (prompt for passphrase if needed).
  3. `buildProfileEvent(fields)` → kind-0 template; `signEvent(...)`.
  4. `publish(writeRelayUrls, signedEvent)`; render per-relay results in a toast.
  - Local save and publish are decoupled: valid edits persist locally even if the
    broadcast partially or fully fails, so edits are never lost and publish is
    retryable.
- **kind-0 content:** JSON of the non-empty metadata fields —
  `name` (NIP-05 local part), `display_name`, `about`, `picture`, `banner`, `nip05`,
  `lud16`, `website`. Empty fields are omitted.

### 2. Relays page (kind-10002)

- **Route:** add `relays()` to `SettingsController` mapping
  `GET /settings/relays` → `content="settings/relays"` (mirrors `index`/`security`).
- **Defaults endpoint:** add `GET /api/v1/relays/defaults` (backed by `ClientProperties`)
  so the client can seed the initial list from `BOTTIN_DEFAULT_RELAYS`.
- **Template (`settings/relays.html`, existing):** already renders read/write relay
  lists, an add-relay form (`wss://` URL + read/write checkboxes), and a publish
  button. Kept; only its JS is rewired.
- **Driver (`settings-relays.js`, rewired):** today it calls the stub `/api/v1/relays/*`
  endpoints; point it at `APP.loadRelays()/saveRelays()` instead. The stub REST
  endpoints go unused (left in place).
- **Behavior:**
  - On load, render relays from the stored list; `ensureRelaysSeeded()` seeds from the
    server-configured defaults if none exist.
  - **Add relay:** validate `wss://` URL, add with read/write flags, persist immediately.
  - **Remove relay:** drop it, persist immediately.
  - **Publish:** `buildRelayListEvent(relays)` → kind-10002; `ensureUnlocked()` →
    `signEvent` → `publish(writeRelayUrls, ...)`; per-relay result toast.
- **kind-10002 tags (NIP-65):** one `r` tag per relay — `["r", url]` when a relay is
  both read and write, `["r", url, "read"]` or `["r", url, "write"]` otherwise.
- Relay-list edits persist to localStorage immediately; Publish is a separate
  best-effort broadcast.

### 3. Shared unlock and signing

Because both pages publish, the unlock-sign-publish machinery is shared, single-purpose
modules rather than duplicated per page.

- **Unlock (`app.js`):** one reusable passphrase modal (same pattern as the Security
  page's reveal-nsec modal) plus `APP.ensureUnlocked()` → `Promise<hexKey>`:
  - If `getSessionKey()` returns a live key, resolve immediately.
  - Otherwise show the modal; `unlockSession(passphrase)` verifies it
    (`NostrCrypto.verifyPassword`), decrypts the key (`NostrCrypto.decryptPrivateKey`),
    stashes `{ key, expiresAt }` in `sessionStorage`, and resolves. Wrong passphrase →
    inline modal error, re-prompt, no key stashed.
  - **Idle timeout:** `getSessionKey()` returns null once `expiresAt` passes (default
    15 min); each successful use refreshes it. `APP.logout()` calls `lockSession()`.
- **Signing (`NostrCrypto`):** add a generic `signEvent(unsignedEvent, hexKey)` built on
  the same `NT.finalizeEvent` that `signNip98Event` already uses, not hardcoded to a kind.
- **Event building + publishing (`nostr-publish.js`, new):**
  - `buildProfileEvent(fields)` → kind-0 template.
  - `buildRelayListEvent(relays)` → kind-10002 template.
  - `publish(relayUrls, signedEvent)` uses nostr-tools' `SimplePool` to broadcast and
    returns per-relay results (accepted / rejected + reason).

Separation of concerns: `app.js` = session/storage/modal; `NostrCrypto` = crypto;
`nostr-publish.js` = event shapes + relay I/O. Each page: `ensureUnlocked()` → build →
`signEvent` → `publish` → render results.

### 4. Onboarding fix

`onboarding/step-confirm.html` (`generateAndSaveKey()`) currently maps only
`displayName`, `picture`, `banner`, `nip05` into the stored identity. Extend it to also
persist `about`, `lud16`, `website` from the collected onboarding data so new signups
start with a complete profile.

## Data flow

**Edit and publish profile**

```
Profile form (edit) → validate → save fields to imani.identity.<npub>
  → ensureUnlocked() → getSessionKey() or passphrase modal → decrypt key (sessionStorage)
  → buildProfileEvent() → signEvent() → publish(writeRelays) → per-relay result toast
```

**Edit and publish relays**

```
Relays form (add/remove) → validate wss:// → save to imani.relays.<npub>
Publish button → ensureUnlocked() → buildRelayListEvent() → signEvent()
  → publish(writeRelays) → per-relay result toast
```

## Error handling

Actionable, client-side messages (what / why / suggestion):

- **Not logged in / no identity:** authed pages already redirect to `/login` via
  `checkSession()` on a missing session.
- **Wrong passphrase:** inline modal error, re-prompt, key never stashed.
- **No write relays configured:** publish blocked with "Add at least one write relay in
  Settings → Relays."
- **Invalid field input:** inline `form-error` messages, save blocked.
- **Partial/total publish failure:** toast "Published to N of M relays"; zero accepted →
  error toast; local state retained so the user can retry.
- **Local vs. publish decoupled:** valid edits always persist locally first; broadcast is
  best-effort and retryable.

## Security considerations

- The private key never leaves the browser and is never sent to the server (unchanged).
- **Unlock-once-per-session tradeoff:** the decrypted key sits in `sessionStorage` for the
  session so publishes don't re-prompt. This exposes it to XSS for the session's duration.
  Mitigations: session-only lifetime (cleared on tab close), cleared on logout, and an
  idle-timeout re-lock. Documented as an explicit convenience/security tradeoff chosen for
  this iteration.
- Profile URL fields (`picture`, `banner`, `website`) pass the `http(s)`-only
  `safeImageUrl`/URL guard; relay URLs are validated as `wss://`.

## Testing

The module has no JS toolchain today; this iteration adds one so the client logic is
durably CI-enforced.

- **JS unit tests (Vitest + jsdom), run in the Maven `test` phase via
  `frontend-maven-plugin`** (Node installed once and cached). The pure logic is extracted
  into importable modules and tested:
  - kind-0 content JSON: empty-field omission, `name` from NIP-05 local part.
  - kind-10002 NIP-65 `r`-tag markers: read / write / both.
  - Field validation: safe `http(s)` URLs, `lud16` format, display-name length.
  - Session-key logic: expiry, refresh, lock/clear.
  - Relay-list load/save shape.
  - Publish-result handling with `SimplePool`/`WebSocket` mocked.
  - `ensureRelaysSeeded()`: seeds from the fetched defaults when absent, no-op when a
    stored list already exists.
- **Server-side `@WebMvcTest`:** a `SettingsControllerTest` case for the new
  `GET /settings/relays` route; a `RelayControllerTest` case asserting
  `GET /api/v1/relays/defaults` returns the configured `BOTTIN_DEFAULT_RELAYS` as
  read+write entries; a `ProfileControllerTest` asserting the profile form's field IDs
  render.
- **Diátaxis how-to** documenting the end-to-end verification (unlock, edit profile,
  publish, confirm the kind-0/kind-10002 landed on a relay), linked from `docs/README.md`.
- **Live Playwright verification** of the full unlock → edit → publish flow during
  implementation.

## File-by-file changes

**Server (Java)**

- `controller/SettingsController.java` — add `relays()` → `GET /settings/relays`.
- `config/ClientProperties.java` — new `@ConfigurationProperties(prefix = "bottin.client")`
  binding `domain`, `blossomUrl`, and `defaultRelays` (`List<String>`).
- `controller/RelayController.java` — add `GET /api/v1/relays/defaults` returning the
  configured defaults as `{ relays: [{ url, read: true, write: true }] }`.

**Templates**

- `templates/profile.html` — rewrite as preview header + edit form.
- `templates/onboarding/step-confirm.html` — persist `about`, `lud16`, `website`.

**Client JS**

- `static/js/app.js` — relay storage helpers (`loadRelays`/`saveRelays`),
  `ensureRelaysSeeded()` (fetches `/api/v1/relays/defaults` when the list is absent),
  session helpers (`unlockSession`/`getSessionKey`/`lockSession`), `ensureUnlocked()` +
  shared passphrase modal; `logout()` clears the session key.
- `static/js/nostr-crypto.js` — add generic `signEvent(unsignedEvent, hexKey)`.
- `static/js/nostr-publish.js` — new: `buildProfileEvent`, `buildRelayListEvent`, `publish`.
- `static/js/profile.js` — new: drives the Profile page.
- `static/js/settings-relays.js` — rewire from stub endpoints to localStorage.

**Build / tests**

- `bottin-client-ui/package.json`, Vitest config — new.
- `pom.xml` (`bottin-client-ui`) — add `frontend-maven-plugin` bound to the `test` phase.
- `src/main/resources/application.yml` — replace the structured public-relay
  `default-relays` list with `default-relays: ${BOTTIN_DEFAULT_RELAYS:}` (comma-separated).
- `src/test/.../SettingsControllerTest.java` — add `/settings/relays` case.
- `src/test/.../RelayControllerTest.java` — assert `/api/v1/relays/defaults`.
- `src/test/.../ProfileControllerTest.java` — assert profile form field IDs.
- JS unit test files under the module.

**Docs**

- `docs/how-to/verify-profile-and-relay-publishing.md` — new; linked from `docs/README.md`.

## Open questions

None outstanding. The default-relay source is resolved: server-configured via
`BOTTIN_DEFAULT_RELAYS` (comma-separated `wss://` URLs, all seeded read+write).
