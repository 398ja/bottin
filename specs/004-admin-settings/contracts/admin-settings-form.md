# Contract — `/admin/settings`

**Server**: `bottin-admin-ui` · **Controller**: `xyz.tcheeric.bottin.admin.controller.AdminSettingsController`
**Auth**: admin form login (existing `AdminSecurityConfig`) · **Consumer**: the operator's browser

Built like the domains page: `GET` renders a bound form, `POST` saves and redirects with a
flash message. There is no REST equivalent — the admin UI shares the database and writes
through `SettingsService` directly, exactly as `AdminDomainsController` uses `DomainService`.

## `GET /admin/settings`

Renders `admin/settings` with:

| Model attribute | Type | Source |
|---|---|---|
| `settingsForm` | `SettingsForm` | Built from `settingsService.find()`; relay lists joined with `\n` |
| `updatedAt` | `Instant` | `settingsService.find().getUpdatedAt()` |

Unauthenticated requests redirect to `/admin/login`, as with every other admin route.

## `POST /admin/settings`

Body: standard form encoding of the four fields below.

| Field | Input | Bean validation on `SettingsForm` |
|---|---|---|
| `blossomUrl` | text | `@NotBlank` · `@Pattern(regexp = "^https?://\\S+$")` |
| `defaultRelays` | textarea, one per line | `@Pattern(regexp = "^\\s*((wss?://\\S+)\\s*)*$")` |
| `discoveryRelays` | textarea, one per line | `@Pattern(regexp = "^\\s*((wss?://\\S+)\\s*)*$")` |
| `rateLimitPerMinute` | number | `@Min(1)` · `@Max(1000)` |

**Success** → `redirect:/admin/settings` with flash `success` = "Settings saved".
**Validation failure** → re-render `admin/settings` (not a redirect) so field-level errors
render next to their inputs; `updatedAt` is re-added to the model.

`SettingsService.normalizeRelays` re-validates the schemes and collapses duplicates, so a
caller that bypasses the form still cannot persist a bad value. An `IllegalArgumentException`
from the service names the offending URL and is surfaced as a flash `error`.

## Page requirements

The page states three things the operator would otherwise have to discover:

- **Empty-state warnings.** "No media server set — image uploads are disabled for all users"
  and "No system relays set — user events publish only to relays each user adds themselves".
  A fresh install reaches these states by design, so they are informational, not errors.
- **"Takes effect within a minute"** beside the save button, because the client caches for
  60 seconds and an instant reload would otherwise look like a failed save.
- **`updatedAt`**, so "did anyone change this?" is answerable without the database.

The media-server field carries a hint that the URL must be reachable **from the browser**,
not over the compose network. The natural mistake is entering the compose service name,
which the server can resolve and the browser cannot — the same class of error as
`bottin-web` vs `bottin-api`.

Field labels use **system relays**, not "default relays", regardless of the underlying
column name. See [research.md](../research.md) R3.

## Navigation

`fragments/layout.html` gains a `Settings` link after `Domains`, matching the existing
anchor styling.

## Verification

`AdminSettingsControllerTest` (`@WebMvcTest(AdminSettingsController.class)`,
`@Import(AdminSecurityConfig.class)`, `SettingsService` mocked, `@WithMockUser(roles = "ADMIN")`),
following `AdminDomainsControllerTest`:

- unauthenticated `GET` redirects to `/admin/login`
- `GET` renders `admin/settings` with the form bound to current values
- `POST` with valid input calls `settingsService.update` and redirects with the success flash
- `POST` with a blank media server fails validation **without reaching the service**
- `POST` with an `http://` relay fails validation **without reaching the service**
- `POST` with `rateLimitPerMinute = 0` fails validation
