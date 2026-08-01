# Contract — `GET /api/v1/settings`

**Server**: `bottin-api` · **Controller**: `xyz.tcheeric.bottin.api.controller.SettingsController`
**Auth**: HTTP Basic, `ROLE_API` · **Consumer**: the `bottin-client-ui` server only

## Request

```http
GET /api/v1/settings HTTP/1.1
Host: bottin-api:8080
Authorization: Basic <api-user credentials>
```

No parameters, no body.

## Response — 200

```json
{
  "blossomUrl": "https://blossom.example.com",
  "defaultRelays": ["ws://relay-a:7777", "wss://relay-b.example"],
  "discoveryRelays": ["wss://relay.damus.io", "wss://nos.lol"]
}
```

| Field | Type | Null | Meaning |
|---|---|---|---|
| `blossomUrl` | `string` | **yes** | Browser-reachable media server. `null` when never configured. |
| `defaultRelays` | `string[]` | no | The deployment's system relays. `[]` when unconfigured, never `null`. |
| `discoveryRelays` | `string[]` | no | Relays searched for an imported key's profile. `[]` when unconfigured, never `null`. |

`rateLimitPerMinute` is **deliberately absent**. The API is its only consumer and the client
has no use for it; shipping it would invite a second consumer for a value that only makes
sense inside the API process.

## Response — other statuses

| Status | Condition |
|---|---|
| `401` | No credentials, or credentials without `ROLE_API` |
| `500` | Settings row missing — `SettingsNotFoundException`, handled by `GlobalExceptionHandler`'s `BottinException` handler. Cannot occur when `V4` has run. |

## Why authenticated rather than public

The payload exposes the deployment's media server and relay topology. That is not secret —
the same relay URLs appear in every kind-10002 this deployment's users publish — but it is
not the public's business either, and the client already holds the credentials. Requiring
`ROLE_API` costs nothing and keeps the surface consistent with `/api/v1/records` and
`/api/v1/domains`.

## Implementation notes

- `SecurityConfig.apiFilterChain` gains
  `.requestMatchers("/api/v1/settings").hasRole("API")` alongside the existing `records`
  and `domains` matchers. Without it the path still requires authentication via
  `.anyRequest().authenticated()`, but the role requirement would be implicit — stating it
  matches the surrounding style and survives a future reordering.
- `SettingsResponse` is a Java record built by a static `from(SettingsData)` factory,
  matching `ProfileReachResponse.from(ProfileReach)`.
- Annotated with springdoc `@Tag` / `@Operation` / `@ApiResponses` like
  `ProfileStatsController`, so it appears in the generated OpenAPI document.

## Verification

`SettingsControllerTest` (`@WebMvcTest`, `SettingsService` mocked):

- payload shape — all three keys present, `rateLimitPerMinute` absent
- `blossomUrl` serialises as JSON `null` when unconfigured
- empty relay lists serialise as `[]`, not `null`
- `401` without the API role
