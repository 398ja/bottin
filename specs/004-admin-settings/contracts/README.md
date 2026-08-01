# Interface Contracts — Admin-Maintained Settings

Three interfaces change or appear. They live on two different servers, which is the point
worth holding onto while reading them: `BOTTIN_DIRECTORY_URL` is an internal compose
hostname the browser cannot resolve, so the browser never calls the directory API.

| Contract | Server | Auth | Caller |
|---|---|---|---|
| [`settings-api.md`](./settings-api.md) — `GET /api/v1/settings` | `bottin-api` | HTTP Basic, `ROLE_API` | `bottin-client-ui` server, over the compose network |
| [`relays-system-api.md`](./relays-system-api.md) — `GET /api/v1/relays/system` | `bottin-client-ui` | NAP session | the browser |
| [`admin-settings-form.md`](./admin-settings-form.md) — `/admin/settings` | `bottin-admin-ui` | admin form login | the operator's browser |

```
operator browser ──form──▶ bottin-admin-ui ──SettingsService──▶ settings table
                                                                      ▲
                                                                      │ SettingsService
user browser ──NAP──▶ bottin-client-ui ──Basic──▶ bottin-api ─────────┘
                            │                          │
                    60 s cache                  RateLimitService
```

There is deliberately **no write endpoint**. The admin UI shares the database and writes
through `SettingsService` directly, exactly as `AdminDomainsController` uses `DomainService`;
a `PUT` would be a second write path with a second auth story and no caller.
