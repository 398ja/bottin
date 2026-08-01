# Phase 0 Research — Admin sign-in with a Nostr key

Four decisions, each resolved against what nap-spring actually provides and how the client already
uses it. The headline finding is that far less needs building than the spec's requirement list
suggests: the library supplies the handshake, sign-out, and a complete role model.

---

## What nap-spring already provides

Established by inspecting the artifacts on the classpath (`nap-spring`, `nap-server`, `nap-core`),
not by assumption. This shapes every decision below.

| Capability | Provided by | Consequence for this feature |
|---|---|---|
| Challenge/response endpoints | `NapAuthController.init` / `.complete` | No endpoint to write |
| **Sign-out** | `NapAuthController.logout` | **US5's session half is free.** Only erasing the browser-held key is ours |
| Session check | `NapAuthController.checkSession` | Drives "am I still signed in?" |
| Session storage, expiry | `SessionStore`, `InMemorySessionStore` | FR-011's expiry is configuration, not code |
| **Revoke by principal** | `SessionStore.revokeByPrincipal` | Not needed here — this is what makes the *follow-up* feature's "kill the session immediately" achievable |
| NIP-98 proof validation, replay defence | `Nip98Validator`, `EventReplayGuard` | FR-007 (replay, expiry) and FR-008 (clock skew) are library behaviour, configured not coded |
| **Roles and permissions** | `PermissionRegistry`, `RoleDefinition`, `PermissionDefinition`, `RegistryAclResolver`, `AclStore`, `AclRecord` | **FR-015's super-administrator role is declarative.** No authorization framework to build |
| Per-route permission checks | `@RequiresPermission`, `NapPermissionInterceptor` | Route protection is an annotation |

The relevant signatures:

```java
AclResolver:      AclDecision resolve(String appId, String pubkey)
AclDecision:      allowed(List<String> roles, List<String> permissions) | denied() | denied(String reason)
RoleDefinition:   (String key, String description, Set<String> permissions)
PermissionRegistry.of(String appId, List<PermissionDefinition>, List<RoleDefinition>, String defaultRole)
AclRecord:        (String appId, String pubkey, String role, boolean suspended)
```

`AclRecord` carries **one role per pubkey**, which matches the two-role design exactly, and a
`suspended` flag the follow-up feature can use to disable an administrator without deleting them.

---

## R1 — How is the configured key resolved to a role?

**Decision**: A single `ConfiguredAdminAclResolver implements AclResolver`, reading the configured
public key from a property. It returns `AclDecision.allowed(["super-admin"], …)` when the pubkey
matches and `AclDecision.denied(reason)` in every other case. It is the only place in the codebase
that decides who an administrator is.

**Rationale**: FR-015 requires the decision to be one point, so the follow-up feature adds a role
rather than retrofitting authorization. `AclResolver` is precisely that seam — nap-spring already
calls it on every session establishment, so bottin implements one interface method and inherits
enforcement everywhere.

The alternative, `RegistryAclResolver.create(registry, aclStore)`, is the right answer *later*: it
resolves roles from an `AclStore`, which the follow-up feature will back with the `admin_users`
table. It is wrong now, because 005 has no store — one key in configuration is not a store, and
introducing `AclStore` with a single hardcoded record would be scaffolding for a feature that has
not been scheduled. The seam is the interface, and both implementations satisfy it.

**Four outcomes, each distinct** (FR-006 requires the last three be distinguishable):

| Configured value | Proven key | Decision | Logged as |
|---|---|---|---|
| valid, matches | any | `allowed(["super-admin"])` | `admin_signin_succeeded` |
| valid, differs | any | `denied` | `admin_signin_rejected reason=not_authorised` |
| absent | any | `denied` | `admin_signin_rejected reason=no_admin_key_configured` |
| present but unusable | any | `denied` | `admin_signin_rejected reason=admin_key_unreadable` |

**Alternatives considered**: putting the comparison in a Spring Security `AuthenticationProvider`
— it would work, but leaves the decision inside the security plumbing rather than at the seam
nap-spring already offers, and the follow-up would then have two places to change.

---

## R2 — Redirect or 401 for an unauthenticated browser?

**Decision**: A `RequireAdminSessionFilter` that **redirects to the sign-in page** for browser
navigation, and answers `401` for anything expecting JSON.

**Rationale**: The client's equivalent, `RequireNapAuthenticationFilter`, returns `401` — correct
there, because everything it protects is a fetch from JavaScript. The dashboard is the opposite:
its protected surface is server-rendered pages an operator navigates to. A bare `401` in a browser
is a blank error, so US2's "sent to the sign-in page rather than served the page" would fail.

The two are distinguished by whether the request accepts HTML. The admin dashboard has no
JSON API of its own today, so in practice everything redirects; the distinction exists so that
adding one later does not produce an HTML redirect where a caller expected an error.

**FR-005 interaction**: when no key is configured, the redirect target must explain that rather
than presenting a sign-in form that cannot succeed (FR-014). The sign-in page therefore reads the
same configuration state the resolver does.

**Alternatives considered**: Spring Security's `LoginUrlAuthenticationEntryPoint`, which does
exactly this redirect — but it belongs to the form-login machinery being removed, and wiring NAP
sessions into Spring Security's `SecurityContext` purely to reuse an entry point is more coupling
than the filter it replaces.

---

## R3 — Where does the browser's key handling live?

**Decision**: Move `nostr-crypto.js` and the NAP handshake into a small shared static module that
both applications serve, rather than copying either file.

**Rationale**: The dashboard needs exactly what the client already has —
`buildEncryptedIdentity(nsec, password)`, `decryptPrivateKey`, `verifyPassword`, `signNip98Event`,
and the `init` → sign → `complete` sequence currently inlined in `APP.napLogin`. Copying is the
obvious move and the wrong one: two copies of key-encryption logic drift, and the copy that drifts
silently is the one without the tests.

**The mechanism needs choosing during implementation.** The candidates, in order of preference:

1. **A shared module packaging the JS as classpath static resources**, which Spring Boot serves
   from `META-INF/resources/` on a jar. Both applications add the dependency; neither owns the file.
   Fits the existing multi-module build with no new tooling.
2. **A build-time copy** from `bottin-client-ui` into `bottin-admin-ui` resources. Simple, but a
   generated file in a source tree invites someone to edit the copy.
3. **Duplicate and accept the drift.** Rejected for crypto.

Option 1 is the plan of record; the tasks phase should confirm the Vitest suite can still reach the
files once relocated, since the specs import them by relative path.

**A related cleanup**: `nap-client.js` in the client is a stub whose three methods all throw
`"Not implemented - Phase 2"`, while the working handshake lives in `APP.napLogin`. It is dead code
that describes itself as the NAP client, which is actively misleading for anyone implementing this
feature. Either it becomes the shared implementation or it is deleted — it must not survive as-is.

---

## R4 — The sign-in endpoint is public. What stops it being abused?

**Decision**: Issue a challenge **uniformly for any npub offered**, and rate limit issuance per
client address.

**Rationale**: `/api/v1/auth/init` must answer before anyone is authenticated. If it answers
differently for the configured key — a challenge for the administrator, an error for anyone else —
it becomes a free oracle: an attacker learns the deployment's administrator npub by trying
candidates. Since the administrator's npub may be publicly known anyway, the leak is modest, but
there is no reason to offer it, and uniform issuance costs nothing: the rejection happens at
`complete`, where a valid signature proves the key and the resolver denies it.

Principle VI requires public endpoints to be rate limited. The admin module has none today, so
this is genuinely new work rather than reuse. `bottin-api`'s `RateLimitService` is not directly
reusable — it now reads its allowance from the settings row via `SettingsService`, and
`bottin-admin-ui` should not acquire a service dependency for a fixed local limit. A small
fixed-window limiter local to the admin module, keyed on client address, is the proportionate
answer.

**Rejected**: refusing to issue a challenge for an unknown npub — turns the endpoint into the
oracle described above. **Also rejected**: no limiting — leaves an unauthenticated endpoint doing
signature verification work on demand.

---

## Scope boundary: `BOTTIN_ADMIN_PASSWORD` is used by two things

Worth stating plainly, because FR-009 ("no administrator password may remain in configuration")
reads more broadly than it should be applied.

`bottin-api`'s `SecurityConfig` provisions an in-memory `admin` user from
`BOTTIN_ADMIN_USER`/`BOTTIN_ADMIN_PASSWORD` holding roles `ADMIN` and `API`, entirely separately
from the dashboard's form login. **This feature removes the dashboard's password only.** The API's
HTTP Basic credentials are a different mechanism, used by machine callers, and changing them is not
in scope.

Two observations for whoever picks that thread up later:

- No route in `bottin-api` requires `ROLE_ADMIN` — `/records/**`, `/domains/**` and `/settings` all
  require `ROLE_API`, and everything else requires only authentication. The admin user's `ADMIN`
  role is currently decorative.
- After this feature, `BOTTIN_ADMIN_PASSWORD` is read only by `bottin-api`, while
  `BOTTIN_ADMIN_USER` becomes unused by the dashboard. The variables should not simply be deleted
  from `docker-compose.yml` without checking the API service block, or the API will start with a
  random password that changes on every restart.

---

## Confirmed facts (verified, not assumed)

| Fact | Where verified |
|---|---|
| `NapAuthController` exposes `init`, `complete`, `checkSession`, `logout` | `javap` on nap-spring |
| `SessionStore` exposes `revokeBySessionId` and `revokeByPrincipal` | `javap` on nap-server |
| `AclRecord` holds one role per pubkey plus a `suspended` flag | `javap` on nap-server |
| The client's handshake is `POST /api/v1/auth/init` → sign → `POST /api/v1/auth/complete` with `Authorization: Nostr <base64>` | `app.js:19-43` |
| `nostr-crypto.js` already provides `buildEncryptedIdentity`, encrypt/decrypt, password hashing | `nostr-crypto.js:63-119` |
| `nap-client.js` is a stub that throws on all three methods | `nap-client.js` |
| The client's NAP config sets challenge TTL 60s, session TTL 3600s, clock skew 60s | `bottin-client-ui/src/main/resources/application.yml` |
| The admin login page posts `username`/`password` to `/admin/login` | `templates/admin/login.html:37-59` |
| `AdminSecurityConfig` provisions one in-memory user and uses form login | `AdminSecurityConfig.java` |
| `bottin-api` provisions its own admin user from the same variables | `bottin-api/.../config/SecurityConfig.java` |
