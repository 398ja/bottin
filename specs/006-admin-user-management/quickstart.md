# Quickstart: Additional administrators

How to exercise this feature end to end. The checks in the last section are the
ones that only fail in a running deployment — this project has now been bitten
three times by a green build hiding a broken sign-in, so they are not optional.

## Prerequisites

- A deployment with `BOTTIN_ADMIN_NPUB` set to a key you hold, and sign-in
  working (feature 005).
- Over plain HTTP, `COOKIE_SECURE=false` — otherwise the browser stores no
  session and everything below fails for an unrelated reason.
- A second Nostr keypair to act as the colleague. Generate one with the project's
  own crypto rather than any hand-rolled bech32.

## Add an administrator

1. Sign in as the master key holder and open `/admin/settings`.
2. In **Administrators**, enter the second public key — `npub1…` or hex, either
   works — and a label such as `Ops laptop`.
3. Save. The key appears in the list, labelled, below the master key which is
   marked as the super administrator and has no remove control.

Expected refusals, each of which should leave the list unchanged:

| Enter | Expect |
|---|---|
| `not-a-key` | Refused, with `not-a-key` named in the message. |
| The same key again, in the other encoding | "Already present" — not a second row. |
| Your own master key | "Already the super administrator". |

## Sign in as the added administrator

1. In a different browser (or a private window — the key is stored per browser),
   open `/admin/login`.
2. First sign-in with the second nsec and a passphrase, entered twice.
3. You reach the dashboard. Records, domains, and settings all load and can be
   changed.
4. On `/admin/settings`, the administrators section is visible but offers no add
   form and no remove controls.

## Verify the role boundary is real, not cosmetic

A hidden button is not a permission. With the added administrator's session,
issue the management requests directly:

```bash
# Both must answer 403.
curl -i -b "admin_session=<their session cookie>" \
  -X POST http://localhost:8091/admin/settings/administrators \
  -d "key=npub1someotherkey&label=smuggled"

curl -i -b "admin_session=<their session cookie>" \
  -X POST http://localhost:8091/admin/settings/administrators/<some-hex>/remove
```

Then confirm the deployment recorded each attempt:

```bash
docker logs bottin004-admin 2>&1 | grep administrator_change_rejected
```

## Remove, and confirm access ends at once

This is the check most likely to pass in a test and fail in reality.

1. Keep the added administrator's browser open on `/admin/dashboard`, signed in
   and working.
2. As the master key holder, remove them from `/admin/settings`.
3. **Immediately** reload the added administrator's browser — do not wait.

They must be returned to `/admin/login` on that very next request. If they can
still load the page, the session was not revoked: either removal did not revoke,
or it revoked by the wrong identifier. Revocation matches on the hex pubkey, so
passing an npub revokes nothing while reporting success.

Confirm what the deployment thinks happened:

```bash
docker logs bottin004-admin 2>&1 | grep administrator_removed
# sessions_revoked=1 for a signed-in administrator; 0 means nothing was ended.
```

`sessions_revoked=0` when you expected 1 is the symptom to chase, not a detail.

## Confirm nobody can be locked out

1. Remove every added administrator.
2. Sign in as the master key holder. You still reach the dashboard.
3. There is no control anywhere that removes, edits, or demotes the master key.

## Known ceiling

Revocation reaches the sessions held by the instance that processed the removal.
The shipped deployment runs one admin instance, so removal is immediate as
specified. Behind a load balancer with several instances, a removed
administrator would keep working on the instances that did not see the removal,
until their session expires. Read `sessions_revoked` in the logs if this ever
looks wrong.
