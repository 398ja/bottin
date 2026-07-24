# Recognized login & root router

Routes the root path by stored state and turns `/login` into a "welcome back"
card for a returning user, who only needs to enter their passphrase.

## Root router (`/`)

`/` renders a minimal standalone page whose script:

1. Probes the session (`GET /api/v1/auth/session`): `200` → `/search`.
2. Else scans `localStorage` for an `imani.identity.*` key: found → `/login`;
   none → `/onboarding`.

## Stored identity profile metadata

The encrypted identity record gains optional plain-text display fields:
`nip05`, `displayName`, `picture` (avatar), `banner`. Key material stays
encrypted; these are only for the recognition card.

- **Create flow**: persists `displayName`, `picture`, `banner`, and
  `nip05 = <username>@<domain>` from the profile step (the domain is captured
  from the server-rendered `bottinDomain`).
- **Import flow**: fetches the kind-0 profile from relays (see below).

## Import profile fetch

After the nsec is validated, `step-import` queries a default relay set
(`relay.damus.io`, `nos.lol`, `relay.primal.net`, `relay.nostr.band`) with
`NostrTools.SimplePool.querySync({kinds:[0], authors:[pubkey]})` and a ~4s
timeout, parses the latest event's `display_name`/`picture`/`banner`/`nip05`,
and stores them on the identity. Timeout or not-found → proceed with no
metadata; import never blocks on relays. Relay lookups are best-effort — no
relay settings are wired yet, so the list is hardcoded.

## Recognized login card (`/login` with a stored identity)

- Banner as the header background, avatar, display name, and NIP-05 (never the
  npub), then the passphrase prompt + "Unlock & Sign In", with "Use a different
  key" underneath.
- No local profile (older import / failed fetch) → neutral placeholder avatar,
  no banner, npub as a small secondary label.
- Fresh browser (no identity) → the existing paste-nsec form.

## Testing

Browser JS + a thin router route, verified live. The one Java test asserting
`/` redirects is updated to expect the router view. Full reactor stays green.
