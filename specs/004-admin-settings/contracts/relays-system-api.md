# Contract — `GET /api/v1/relays/system`

**Server**: `bottin-client-ui` · **Controller**: `xyz.tcheeric.bottin.client.controller.RelayController`
**Auth**: NAP session · **Consumer**: the browser (`app.js`)
**Replaces**: `GET /api/v1/relays/defaults`

## Request

```http
GET /api/v1/relays/system HTTP/1.1
Cookie: client_session=<nap session>
```

## Response — 200

```json
{ "relays": ["ws://relay-a:7777", "wss://relay-b.example"] }
```

| Field | Type | Meaning |
|---|---|---|
| `relays` | `string[]` | The deployment's system relays as plain URLs. `[]` when unconfigured. |

**Shape change from `/defaults`**: the old endpoint returned objects
(`{"url": ..., "read": true, "write": true}`) because the values were being copied into the
user's own relay list, where every entry carries flags. System relays are never stored in a
user's list, so the flags described nothing — every system relay is both read from and
published to. Plain strings.

## Response — other statuses

| Status | Condition |
|---|---|
| `401` | No NAP session (enforced by `RequireNapAuthenticationFilter`, order 2) |

## Why this belongs to the client server, not the API

It sits under the existing `/api/v1/relays` prefix, which is already listed in
`nap.protected-path-prefixes` in `bottin-client-ui/src/main/resources/application.yml`, so
it inherits NAP protection with no security configuration change. It is only needed at
publish time, which always follows sign-in. Putting it on `bottin-api` would mean either
exposing it publicly or giving the browser directory credentials.

## Why the rename

`defaults` now describes the opposite of what happens. These relays are applied on **every**
publish, not copied once as a starting point. `APP.ensureRelaysSeeded` — which copied them
into each browser's `localStorage` at first use — is deleted; that seeding is what froze
the relay set per browser and what put system relays into the user's own list where the
Settings page rendered them as theirs to remove.

## Browser-side contract (`app.js`)

`APP.ensureRelaysSeeded(userId)` is **removed**. Three functions replace it:

```js
APP.systemRelays()                  // Promise<string[]>  — fetches this endpoint, [] on failure
APP.effectiveWriteRelays(userId)    // Promise<string[]>  — user's write relays ∪ system relays
APP.effectiveReadRelays(userId)     // Promise<string[]>  — user's read relays  ∪ system relays
```

Union semantics: the user's own URLs first, in their stored order, then any system relay not
already present. De-duplicated by URL. Neither function writes to `localStorage` — that is
the whole point of the change.

`systemRelays()` resolves to `[]` rather than rejecting when the fetch fails, so a publish
falls back to the user's own relays instead of failing outright.

### Call sites

| File | Was | Becomes |
|---|---|---|
| `profile.js:140` | `ensureRelaysSeeded` → filter `write` | `effectiveWriteRelays(userId)` |
| `onboarding-complete.js:69` | `ensureRelaysSeeded` → filter `write` | `effectiveWriteRelays(userId)` |
| `profile-fetch.js:71` | `ensureRelaysSeeded` → filter `read` | `effectiveReadRelays(userId)` |
| `settings-relays.js:121` (`init`) | `ensureRelaysSeeded` | `APP.loadRelays(userId)` — renders the user's own list only |
| `settings-relays.js:97` (`publishRelays`) | user's relays only | user's relays ∪ system relays, as read+write entries, for both the kind-10002 tags and the publish targets |

`settings-relays.js` renders only the user's own list, so system relays cannot appear
there — they were never in it. They **are** included in the published kind-10002 and in the
relay array sent with the NIP-05 registration, because events land there and excluding them
would make those events unfindable. "Not viewable" means "not yours to edit", not "secret".

## Verification

`RelayControllerTest` (`@WebMvcTest`, `DirectorySettingsClient` mocked):

- returns the configured system relays as plain strings
- returns `{"relays": []}` when none are configured

Vitest (`src/test/js/app-session.test.js` and callers):

- `effectiveWriteRelays` unions and de-duplicates, user's relays first
- `effectiveWriteRelays` returns the user's relays alone when the endpoint fails
- neither `effectiveWriteRelays` nor `effectiveReadRelays` writes to `localStorage`
- the settings page renders only the user's own relays
