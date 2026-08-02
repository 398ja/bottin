# Interface Contracts — Admin sign-in with a Nostr key

Three contracts. The first is supplied by nap-spring and documented here only because bottin
depends on its exact shape; the other two are bottin's own.

| Contract | Owner | Audience |
|---|---|---|
| [`auth-endpoints.md`](./auth-endpoints.md) | **nap-spring** (`NapAuthController`) | The admin browser |
| [`admin-access-contract.md`](./admin-access-contract.md) | bottin-admin-ui | Every admin route |
| [`browser-identity.md`](./browser-identity.md) | bottin-admin-ui + shared module | The admin browser |

```
                     ┌─ nap-spring ────────────────────────────┐
 admin browser ──────┤ POST /api/v1/auth/init      (public)    │
   holds the key     │ POST /api/v1/auth/complete  (public)    │
   signs locally     │ GET  /api/v1/auth/session               │
                     │ POST /api/v1/auth/logout                │
                     └────────────┬────────────────────────────┘
                                  │ establishes session, calls
                                  ▼
                     ConfiguredAdminAclResolver  ← the one role decision (FR-015)
                                  │
                                  ▼
                     RequireAdminSessionFilter + @RequiresPermission
                                  │
                                  ▼
                          /admin/**  (server-rendered pages)
```

The private key and the passphrase never cross the boundary into nap-spring. What crosses is a
signed proof, which is worthless to anyone who intercepts it once its challenge has expired or been
used.
