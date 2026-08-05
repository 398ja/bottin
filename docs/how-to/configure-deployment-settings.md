# Configure Deployment Settings

This guide shows you how to set the media server, relays, and rate limit for a
Bottin deployment from the admin UI, and what each one affects.

These four values used to live in `docker-compose.yml`, where changing one meant
editing the file and recreating containers. They are now stored in the database
and edited at `/admin/settings`, so changing them needs no restart and no deploy.

## Before you start

A fresh deployment comes up **unconfigured by design**. There is no environment
fallback for these values: until you set them, image uploads are disabled and
user events publish only to relays each user adds themselves. This is one
post-deploy step, not a failure.

If you are upgrading, `BOTTIN_BLOSSOM_URL` and `BOTTIN_DEFAULT_RELAYS` no longer
do anything. Delete them from `.env` and `docker-compose.yml`; leaving them is
harmless but misleading.

## Configure the settings

1. Sign in to the admin UI and open **Settings** (`/admin/settings`).
2. Fill in the four fields described below.
3. Press **Save**. The page records when the settings last changed.

### Media server (Blossom)

The server the browser uploads profile avatars and banners to.

Enter the URL **as the browser reaches it** — for the bundled service on a local
stack, `http://localhost:8888`. Not the compose service name: the Bottin server
can resolve `blossom`, and every user's browser cannot. This is the most common
mistake, and it produces uploads that fail only for real users.

Leave it unset and the file pickers are disabled with "Media server not
configured". The rest of onboarding still works.

### System relays

One `ws://` or `wss://` URL per line. For the bundled relay on a local stack,
`ws://localhost:8086`.

Every user's events are published to all of these and read back from them. They
apply automatically and never appear in any user's own relay list, so a user
sees an empty relay page while publishing works. Changing this list reaches
every user at once, including people who signed up long ago.

Leave it empty and user events publish only to relays each user adds themselves.

### Profile discovery relays

One URL per line. Searched for an existing profile when someone signs in with a
key created elsewhere, so their name and picture carry over. The system relays
above are searched as well, since a key that registered here published there.

A reasonable starting set:

```text
wss://relay.damus.io
wss://nos.lol
wss://relay.primal.net
wss://relay.nostr.band
```

Leave it empty and sign-in still succeeds, just without importing a profile.

### Rate limit per minute

Requests allowed per client address on the public endpoints (external
verification and profile reach). Defaults to 30. Applies to the next request
after you save — no restart.

## Confirm the change reached users

Client servers cache these settings for **60 seconds**, so an immediate reload may
still show the old values. That is the cache, not a failed save.

After a minute:

- Open the client's profile editor and choose an image. If the control is
  disabled, no media server is set. If the upload fails with a network error, the
  URL is probably reachable from the server but not from the browser.
- Save a profile. The toast reports how many relays accepted the event. Zero
  accepted, with system relays configured, means the relay URLs are wrong or
  unreachable from the browser.

## What stays in the environment

These are deliberately **not** editable here, because they are how a process
reaches the things that would otherwise hold them, or because they are read once
at startup:

| Variable | Why it cannot move |
|---|---|
| `BOTTIN_DATABASE_*` | Needed to reach the database that would store them |
| `BOTTIN_DIRECTORY_URL`, `BOTTIN_API_USER`, `BOTTIN_API_PASSWORD` | The client needs these to fetch settings at all |
| `BOTTIN_ADMIN_PASSWORD` | A secret; the database would hold a hash, the client needs plaintext |
| `BOTTIN_TRUSTED_PROXIES` | Tomcat binds it at startup, and a wrong value re-opens a rate-limit bypass |
| `BOTTIN_API_DOCS_ENABLED`, `BOTTIN_SWAGGER_ENABLED` | springdoc reads them at startup, so a UI toggle would have to say "restart required" |

## Known limitations

- **Users who signed up earlier** still have the old default relays in their own
  list, where Settings now shows them as theirs to remove. They are the same URLs
  the system applies anyway, so publishing de-duplicates them. They are not
  cleaned up automatically: deleting something from a user's storage that they
  cannot distinguish from a relay they added themselves would be worse.
- **Settings are deployment-wide.** Per-domain media servers or relays are not
  supported.

## Related

- [Deploy with Docker](docker-deployment.md)
- [Upload a Profile Avatar and Banner](upload-profile-images.md)
- [Docker Compose Configuration](../reference/docker-compose-configuration.md)
- [REST API](../reference/rest-api.md)
