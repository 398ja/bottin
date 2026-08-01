# Admin-Maintained Settings — Design

Move deployment configuration that is really *data* out of environment variables and
into the database, edited in the bottin admin UI and read by the client through the
API. Covers the media server (Blossom) URL, the system relays, the profile discovery
relays, and the API rate limit.

## Problem

Three kinds of configuration are tangled together in `docker-compose.yml` today:

1. **Bootstrap** — database credentials, the API URL and credentials the client uses.
   These must be in the environment; they are how a process reaches the things that
   would otherwise hold them.
2. **Infrastructure** — ports, images, volumes, `SPRING_PROFILES_ACTIVE`,
   `BOTTIN_TRUSTED_PROXIES`. Bound at container start; a UI toggle would silently do
   nothing until a restart.
3. **Operational data** — the media server, which relays the deployment publishes to,
   which relays it searches for existing profiles, how many requests a caller gets per
   minute. Changing these is an ordinary operator decision that today requires editing
   compose and recreating containers.

Only the third kind moves. Two of those values are worse than inconvenient: the
default relays are copied into each browser's `localStorage` at first use, so
changing them never reaches anyone who already onboarded, and the discovery relays are
hardcoded in `bottin-client-ui/src/main/resources/templates/onboarding/step-import.html`,
where changing them means rebuilding the client image.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Scope | One global settings record | Mirrors the current env model; a nullable `domain_id` can be added later without breaking readers |
| Client access | Client server fetches from the API and serves the browser | `BOTTIN_DIRECTORY_URL` is an internal compose hostname the browser cannot resolve; keeps CORS out of it |
| System relays | Applied implicitly at use time, never stored per user | Cannot appear in Settings because they were never in the user's list; admin changes reach everyone at once |
| Outward lists | System relays **are** included in kind-10002 and the NIP-05 record | Events land there; excluding them would make those events unfindable. "Not viewable" means "not yours to edit", not "secret" |
| Precedence | Database only; the env vars are deleted | One source of truth from the first line of code |
| Storage shape | Singleton row, typed columns | Constraints do real work; relay lists as JSON text matches `nip05_records.relays_json` |
| Media server | Required by the admin form | An unconfigured media server is a broken deployment, not a mode |

Settings in scope: media server URL, system relays, discovery relays, rate limit. A
deployment may run several system relays; they are stored as a plain list of URLs and
every one of them is both published to and searched. Per-relay read/write flags were
considered and declined: the JSON is already a document, so flags can be added later
when a write-only archive or read-only mirror is an actual requirement rather than a
guess.

Explicitly **not** moved, with reasons, so this is not revisited by accident:

- `BOTTIN_DATABASE_*` — needed to reach the database that would store them.
- `BOTTIN_DIRECTORY_URL`, `BOTTIN_API_USER`, `BOTTIN_API_PASSWORD` — the client needs
  these to fetch settings at all.
- `BOTTIN_ADMIN_PASSWORD`, `BOTTIN_API_PASSWORD` — secrets; the client needs the API
  password in plaintext, a database would hold a hash.
- `BOTTIN_TRUSTED_PROXIES` — Tomcat binds it at startup, and a wrong value re-opens the
  rate-limit bypass closed in `a62d311`.
- `BOTTIN_DEFAULT_DOMAIN` / `BOTTIN_CLIENT_DOMAIN` — considered and declined for now.
- `BOTTIN_API_DOCS_ENABLED` / `BOTTIN_SWAGGER_ENABLED` — springdoc reads them at
  startup, so the UI would have to say "restart required".

## Schema

`bottin-persistence/src/main/resources/db/migration/V4__settings.sql`:

```sql
CREATE TABLE settings (
    id                    BIGINT PRIMARY KEY,
    blossom_url           VARCHAR(512),
    default_relays_json   TEXT      NOT NULL DEFAULT '[]',
    discovery_relays_json TEXT      NOT NULL DEFAULT '[]',
    rate_limit_per_minute INT       NOT NULL DEFAULT 30,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT settings_singleton CHECK (id = 1)
);

INSERT INTO settings (id, updated_at) VALUES (1, CURRENT_TIMESTAMP);
```

The migration inserts the row, so "no settings row" is never a state any code handles:
*unconfigured* is `NULL` or `[]`, which is a value. `rate_limit_per_minute` seeds at 30
because a rate limit with no value is not a rate limit. `blossom_url` is nullable at the
database level to represent the window between first boot and the admin's first save;
the form will not save it blank.

Relay lists are JSON text rather than a child table: they are short, always read and
written whole, and no query ever selects an individual relay. Both lists hold plain URL
strings — `["ws://relay-a:7777", "wss://relay-b.example"]` — not objects with read/write
flags. Every system relay is published to and searched.

## Components

| Module | Addition |
|---|---|
| `bottin-core` | `SettingsData` — immutable record, mirrors `DomainData` |
| `bottin-persistence` | `SettingsEntity`, `SettingsRepository`, `V4__settings.sql` |
| `bottin-service` | `SettingsService` — read, update, relay-list (de)serialisation, relay-scheme validation |
| `bottin-api` | `SettingsController` — `GET /api/v1/settings`; `RateLimitService` reads its limit from `SettingsService` |
| `bottin-admin-ui` | `AdminSettingsController`, `SettingsForm`, `admin/settings.html`, nav entry |
| `bottin-client-ui` | `DirectorySettingsClient` (cached), `/api/v1/relays/system`, `APP.effectiveWriteRelays` |

## API

```
GET /api/v1/settings          auth: API role (Basic)
    → { "blossomUrl": "...", "defaultRelays": [...], "discoveryRelays": [...] }
```

No write endpoint. The admin UI shares the database and writes through
`SettingsService` directly, exactly as `AdminDomainsController` uses `DomainService`; a
`PUT` would be a second write path with a second auth story and no caller.

`rateLimitPerMinute` is deliberately absent from the payload — the API is its only
consumer, and the client has no use for it.

The endpoint requires the API role rather than being public: it exposes the
deployment's media server and relay topology, which is not secret but is not the
public's business either. The client already holds those credentials.

## Client consumption

`DirectorySettingsClient` uses the same `RestClient` and credentials as
`DirectoryRegistrationService`, behind a 60-second in-memory cache. Admin changes reach
users within a minute. On a cold start with the API unreachable the client serves
*unconfigured* rather than a guess; there is no environment fallback any more.

### System relays

`APP.ensureRelaysSeeded()` is removed. It copied the default relays into each browser at
first use, which froze them per browser and put them in the user's own list, where
Settings would render them.

```
GET /api/v1/relays/system      served by bottin-client-ui, NAP session required
                               (replaces /api/v1/relays/defaults)

APP.effectiveWriteRelays(userId) = user's write relays ∪ system relays
```

This endpoint belongs to the **client server**, not the API: it sits under the existing
`/api/v1/relays` prefix in `ClientSecurityConfig.PROTECTED_URL_PATTERNS`, so it inherits
NAP protection, and it is only needed at publish time, which always follows sign-in.

Every publish path uses the union: profile save, the onboarding kind-0, the kind-10002
relay list, and the relay array sent with the NIP-05 registration.
`settings-relays.js` renders only the user's own list, so system relays cannot appear
there — they were never in it.

The endpoint is renamed because `defaults` now describes the opposite of what happens:
these relays are applied on every publish, not copied once as a starting point.

### Media server

The hidden `#blossom-url` span is populated from settings instead of `ClientProperties`,
server-side at render time. This matters for ordering: uploads happen during onboarding,
before any NAP session exists, so the value cannot come from a protected endpoint. The
same applies to the discovery relays below — both are injected into the template by the
controller, which reads them from the cached settings.
When it is empty — first boot before the admin's first save, or the API unreachable —
the upload controls are disabled with "Media server not configured" rather than posting
to an empty URL, which today produces an opaque network error.

### Discovery relays

`step-import.html` stops hardcoding the public relay list. Profile lookup at login
queries the admin's discovery relays **plus** the system relays, because a user who
registered on this deployment published their profile to those.

## Admin UI

`/admin/settings`, built like the domains page: GET renders a bound form, POST saves and
redirects with a flash message.

| Field | Input | Validation |
|---|---|---|
| Media server (Blossom) | text | required, `http(s)://` only |
| System relays | textarea, one per line | `ws://` or `wss://`, duplicates collapsed |
| Profile discovery relays | textarea, one per line | `ws://` or `wss://`, duplicates collapsed |
| Rate limit per minute | number | required, 1–1000 |

Bean validation lives on `SettingsForm`; the relay-scheme rule lives in
`SettingsService` so no path can store a bad value.

The page states three things the operator would otherwise have to discover:

- **Empty-state warnings.** "No media server set — image uploads are disabled for all
  users" and "No system relays set — user events publish only to relays each user adds
  themselves". A fresh install reaches these states by design.
- **"Takes effect within a minute"** beside the save button, because the client caches
  for 60 seconds and an instant reload would otherwise look like a failed save.
- **`updated_at`**, so "did anyone change this?" is answerable without the database.

The media-server field carries a hint that the URL must be reachable **from the
browser**, not over the compose network. The natural mistake is entering the compose
service name, which the server can resolve and the browser cannot — the same class of
error as `bottin-web` vs `bottin-api`.

## Error handling

| Condition | Behaviour |
|---|---|
| Settings row missing | Cannot happen; the migration inserts it. `SettingsService` throws rather than inventing defaults if it ever does |
| API unreachable from the client | Serve last cached values; if none, serve unconfigured. Never fall back to a guess |
| `blossom_url` empty | Upload controls disabled with a stated reason; the rest of onboarding proceeds |
| System relays empty | Publish uses the user's own relays; the welcome screen already reports "no write relay configured" when there are none |
| Discovery relays empty | Login profile lookup finds nothing and proceeds; sign-in never blocks on relays |
| Invalid relay scheme submitted | Rejected by form validation with the offending line named |

An unconfigured media server **degrades**: uploads are disabled with a stated reason and
the rest of onboarding proceeds normally. Refusing onboarding outright was considered
and declined — a visitor cannot fix the deployment's configuration, so blocking them
turns an operator's omission into a dead end for someone who came to register.

## Testing

- `SettingsServiceTest` — JSON round-trip for both relay lists, relay-scheme rejection,
  singleton update, `updated_at` advancing.
- `AdminSettingsControllerTest` — form renders current values; save redirects with the
  flash; blank media server and `http://` relay fail validation without reaching the
  service.
- `SettingsControllerTest` — payload shape; 401 without the API role.
- `RateLimitServiceTest` — limit read from settings; a changed value applies without a
  restart.
- `DirectorySettingsClientTest` — cache serves within the TTL, refetches after it, and
  an unreachable API yields unconfigured rather than an exception or a stale guess.
- JS — `effectiveWriteRelays` unions and dedupes; the settings page renders only the
  user's relays; an empty media server disables the upload controls.

## Rollout

1. Migration, `SettingsData`, `SettingsEntity`, `SettingsRepository`, `SettingsService`.
2. `GET /api/v1/settings`; `RateLimitService` reads from settings.
3. Admin settings page. Settings are now editable but nothing consumes them.
4. Client switch-over: `DirectorySettingsClient`, `/api/v1/relays/system`,
   `effectiveWriteRelays`, media server and discovery relays from settings.
5. Delete `BOTTIN_BLOSSOM_URL` and `BOTTIN_DEFAULT_RELAYS` from `docker-compose.yml` and
   `ClientProperties`, and the hardcoded discovery list from `step-import.html`. Update
   `docs/reference/docker-compose-configuration.md`, `docs/how-to/docker-deployment.md`
   and `docs/reference/rest-api.md`.
6. Deploy, then configure the values in the admin UI. The stack comes up unconfigured by
   design.

Steps 1–3 are independently deployable and change no behaviour, which keeps the risky
step (4) small.

## Accepted limitations

- **Leftover seeded relays.** Users who onboarded before this change still have the old
  default relays in their personal list, where Settings will now show them as theirs to
  remove. They are the same URLs the system applies anyway, so the union dedupes them.
  Cleaning it would mean deleting something from a user's storage that they cannot
  distinguish from a relay they added themselves.
- **Empty relay page for new users.** A new user sees "no relays added yet" while
  publishing works, because the system relays are invisible. This follows from the "not
  viewable" requirement; the locked-row alternative was considered and declined.
- **60-second propagation.** An admin change is not instant in the client. The UI says
  so.
- **Single deployment-wide settings.** Per-domain media servers or relays are not
  supported. The table can grow a nullable `domain_id` if that changes.
