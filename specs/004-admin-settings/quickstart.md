# Quickstart — Admin-Maintained Settings

Two audiences: an operator bringing up a deployment after this feature lands, and a
developer verifying the change end to end.

---

## For the operator

After this change the stack **comes up unconfigured by design**. There is no environment
fallback for the media server or the relays, so a fresh deployment publishes only to relays
each user adds themselves and has image uploads disabled until you configure it. This is
one deliberate post-deploy step, not a failure.

### 1. Remove the retired variables

`BOTTIN_BLOSSOM_URL` and `BOTTIN_DEFAULT_RELAYS` no longer do anything. Delete them from
`.env` and from `docker-compose.yml`; leaving them in place is harmless but misleading.

Everything else in your environment stays. These are still environment-only, and
deliberately so:

| Variable | Why it cannot move |
|---|---|
| `BOTTIN_DATABASE_*` | Needed to reach the database that would store them |
| `BOTTIN_DIRECTORY_URL`, `BOTTIN_API_USER`, `BOTTIN_API_PASSWORD` | The client needs these to fetch settings at all |
| `BOTTIN_ADMIN_PASSWORD` | A secret; the database would hold a hash, the client needs plaintext |
| `BOTTIN_TRUSTED_PROXIES` | Tomcat binds it at startup, and a wrong value re-opens the rate-limit bypass closed in `a62d311` |
| `BOTTIN_API_DOCS_ENABLED`, `BOTTIN_SWAGGER_ENABLED` | springdoc reads them at startup, so a UI toggle would have to say "restart required" |

### 2. Deploy

```bash
docker compose up -d
```

Flyway runs `V4__settings.sql`, which creates the table and seeds row 1. The rate limit
starts at 30 requests per minute; the other three settings start empty.

### 3. Configure

Open `/admin/settings` and fill in:

| Field | What to enter |
|---|---|
| **Media server (Blossom)** | The Blossom URL **as the browser reaches it** — e.g. `https://blossom.example.com`, or `http://localhost:8888` for the bundled service on a local stack. Not the compose service name: the server can resolve `blossom`, the browser cannot. |
| **System relays** | One `ws://` or `wss://` URL per line. For the bundled strfry service: `ws://localhost:8086`. Every relay listed here is both published to and searched, for every user. |
| **Profile discovery relays** | One per line. Searched when someone signs in with a key imported from elsewhere. The previously hardcoded set is a reasonable start: `wss://relay.damus.io`, `wss://nos.lol`, `wss://relay.primal.net`, `wss://relay.nostr.band`. |
| **Rate limit per minute** | Requests per client IP on public endpoints. 30 is the seeded value. |

Save. The page shows when it was last changed.

### 4. Confirm

Changes reach users **within a minute** — the client caches settings for 60 seconds. An
immediate reload may still show the old values; that is the cache, not a failed save.

Then check the two things a fresh install gets wrong most often:

- Open the client's profile editor and pick an image. If the upload controls are disabled
  with "Media server not configured", the settings row still has no media server. If the
  upload fails with a network error, the URL is probably reachable from the server but not
  from the browser — the compose-service-name mistake.
- Save a profile. The toast reports how many relays accepted the event. Zero accepted with
  system relays configured means the relay URLs are wrong or unreachable from the browser.

### Known limitations

- **Leftover seeded relays.** Users who onboarded before this change still have the old
  default relays in their personal list, where Settings now shows them as theirs to remove.
  They are the same URLs the system applies anyway, so the union de-duplicates them. They
  are not cleaned up: deleting something from a user's storage that they cannot distinguish
  from a relay they added themselves is worse than leaving it.
- **Empty relay page for new users.** A new user sees "no relays added yet" while publishing
  works, because system relays are invisible to them by design.
- **Single deployment-wide settings.** Per-domain media servers or relays are not supported.
  The table can grow a nullable `domain_id` if that changes.

---

## For the developer

### Build and test

```bash
mvn -q verify                    # from the repository root — required before every commit
cd bottin-client-ui && npm test  # Vitest, for the relay-union and upload-guard changes
```

### Verify the migration

```bash
mvn -q -pl bottin-tests/bottin-it verify
```

The settings row must exist immediately after migration with `rate_limit_per_minute = 30`,
both relay lists `'[]'`, and `blossom_url` null. Attempting to insert a second row must fail
on `settings_singleton`.

### Verify the API endpoint

```bash
curl -u api:changeme-api http://localhost:8080/api/v1/settings
# {"blossomUrl":null,"defaultRelays":[],"discoveryRelays":[]}

curl -i http://localhost:8080/api/v1/settings
# HTTP/1.1 401
```

`rateLimitPerMinute` must **not** appear in the payload.

### Verify the rate limit is live

Change the rate limit in `/admin/settings` to `1`, then hit a rate-limited public endpoint
twice without restarting anything:

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/v1/profiles/<npub>/reach
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/v1/profiles/<npub>/reach
# 200 then 429
```

### Verify the client endpoint rename

`/api/v1/relays/defaults` must be gone. Confirm nothing still references it:

```bash
grep -rn "relays/defaults\|ensureRelaysSeeded" bottin-client-ui/src docs/
```

Expect no hits outside `docs/superpowers/` (historical plans and specs are records of what
was, and are not updated).

### Verify degradation

Stop `bottin-api` and reload the client's onboarding profile step. Expected: the page still
renders, upload controls are disabled with "Media server not configured", and no stack trace
reaches the browser. Restart `bottin-api`, wait out the 60-second cache, and the controls
come back.

### Manual walkthrough

1. Configure settings in `/admin/settings`.
2. Onboard a new user in the client. The kind-0 must publish to the system relays even
   though the user's own relay list is empty.
3. Open Settings → Relays as that user. The list must be empty — system relays are applied,
   not owned.
4. Add one personal relay and publish the relay list. The published kind-10002 must contain
   both the personal relay and the system relays.
5. Sign out, then sign in with an nsec that has a profile published on a public relay. The
   discovery relays configured in step 1 must be the ones queried.

---

## Related documents

- [spec.md](./spec.md) — the feature specification
- [plan.md](./plan.md) — implementation plan and constitution check
- [research.md](./research.md) — the eight decisions behind the implementation
- [data-model.md](./data-model.md) — schema, value object, validation rules
- [contracts/](./contracts/) — the three interface contracts
- `docs/how-to/configure-deployment-settings.md` — the operator-facing how-to this feature adds
