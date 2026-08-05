# Verify Profile and Relay Publishing

This guide shows you how to verify, in a real browser, that the Bottin client can edit
and publish a Nostr profile (kind-0) and relay list (kind-10002) to real relays. It
covers the interactive behaviour that server-side tests cannot assert: how the
deployment's system relays apply without appearing in a user's own list, the shared
unlock-once-per-session modal, idle re-lock, and the per-relay publish result.

The server-side guards for this feature (the `/settings/relays` route renders, the
system relay endpoint returns the configured list, the profile page renders its
editable fields) live in `RelayControllerTest`, `SettingsControllerTest`, and
`ProfileControllerTest`, and run with the normal build. Use this guide for the
end-to-end flow on top of a running client and real relays.

## Prerequisites

- The `bottin-client-ui` module running locally.
- A Chromium-based browser.
- An `nsec` you can sign in with (a test key is fine — profile/relay-list events are
  small and harmless to publish to public relays).

## Start the Stack and Configure System Relays

The relays a deployment publishes to are maintained by an administrator, and the
client reads them through the directory API, so the whole stack must be running —
the client alone has nowhere to read them from.

```bash
docker compose up -d
```

Then open the admin UI at `http://localhost:8081/admin/settings`, enter one relay
per line under **System relays**, and save. For a check against public relays:

```text
wss://relay.damus.io
wss://nos.lol
```

Changes reach the client within a minute; the page says so beside the save button.

## Verify How System Relays Apply

1. **Sign in.** Open `http://localhost:8082/login`, paste a valid `nsec`, and submit.
   You are redirected to `/apps`.
2. **Open `/settings/relays`.** For a new identity the page is **empty** — "No read
   relays configured" and "No write relays configured". This is correct: the system
   relays are applied to every publish but are not yours to edit, and they were never
   copied into your list. Publishing still works, as the next section shows.
3. **Edit the list.** Add a relay by pasting a `wss://` URL, choosing read and/or write,
   and clicking **Add**. Remove one with the `×` button next to its row. Both actions
   persist immediately to the browser's local storage — no publish required to keep the
   edit. Only relays you add appear here.
4. **Change the system relays.** Edit them in the admin UI and wait a minute. The next
   publish uses the new set, for every user at once — no per-browser reset, and nothing
   in `/settings/relays` changes.

## Verify Profile Publish (kind-0)

1. Open `/profile`. The form is pre-filled from the stored identity (display name,
   about, picture, banner, lightning address, website). **NIP-05** is read-only here —
   it is set during onboarding, not from this page.
2. Change a field, e.g. **Display name**, then click **Save & Publish**.
3. **Unlock modal appears.** The session is locked (first publish this session), so a
   passphrase prompt opens. Enter the identity's passphrase and confirm.
4. **Per-relay toast.** After signing, the client publishes the kind-0 event to every
   relay marked **write** in `/settings/relays` and shows a toast such as
   `Published to 2 of 2 relays`. A relay that rejects or times out lowers the accepted
   count but does not block the others.
5. Reload `/profile` — the edited field persists (it was saved locally before publish
   was attempted, so a publish failure never loses the edit).

## Verify Relay List Publish (kind-10002)

1. Back on `/settings/relays`, add or remove a relay, then click **Publish**.
2. Because the session is still unlocked from the profile publish above, **no unlock
   prompt appears this time** — this confirms the unlock-once-per-session behavior.
3. A per-relay toast confirms the kind-10002 relay list event was published to each
   write relay, the same way as the profile publish.

## Verify Idle Re-Lock

The unlocked session key lives in `sessionStorage` with a 15-minute idle expiry that
resets on each use, not a fixed session length.

1. After unlocking (either publish above), wait more than 15 minutes without triggering
   another publish or page reload.
2. Click **Save & Publish** on `/profile` again. The unlock modal reappears, because the
   idle window elapsed and the in-memory key was cleared.
3. To check the re-lock without waiting, open the browser devtools console and clear the
   entry manually: `sessionStorage.clear()`, then click **Save & Publish** — the unlock
   modal must appear immediately.

## Confirm the Events Landed on a Relay

The success toast is a real per-relay acknowledgement (`Promise.allSettled` over each
relay's `OK` response), not a fire-and-forget send. To double-check independently of the
UI, query the relay directly for the event kind and your pubkey using any NIP-01 client,
for example [`nak`](https://github.com/fiatjaf/nak):

```bash
nak req -k 0 -a <your-hex-pubkey> --limit 1 wss://relay.damus.io
nak req -k 10002 -a <your-hex-pubkey> --limit 1 wss://relay.damus.io
```

Each command should return the event you just published, with a `created_at` matching
the time of the publish.

## Confirm Logout Erases the Key From the Browser

Open the avatar dropdown and click **Logout**, then accept the confirmation warning it
shows. After the redirect to `/login`, check that no key material is left behind:

```bash
# In the browser devtools console, before logging back in:
sessionStorage.getItem('imani.session.<your-user-id>')   // -> null
localStorage.getItem('imani.identity.<your-user-id>')    // -> null
```

Logout forgets the device: the encrypted identity is removed along with the session, so
`/login` offers the nsec paste form rather than the passphrase unlock. Signing in again
requires the nsec — from a password manager or the backup file taken on the
**Settings → Security** page.
