# Contract — Admin route access

**Owner**: `bottin-admin-ui`. Defines which routes require what, and how an unauthenticated browser
is turned away.

## The rule that must not regress

FR-010: **every page and action under the admin dashboard remains reachable only to an
authenticated administrator, exactly as before this change.** The authentication mechanism is being
replaced wholesale, which is precisely when a route quietly loses its guard. The route table below
exists to be checked against reality, not to describe an intention.

| Route | Method | Permission | Note |
|---|---|---|---|
| `/admin/login` | GET | none — public | The sign-in page itself |
| `/api/v1/auth/**` | POST/GET | none — public | The handshake; rate limited |
| `/admin/dashboard` | GET | `admin:read` | |
| `/admin/records`, `/admin/records/{id}` | GET | `admin:read` | |
| `/admin/records/**` | POST | `admin:write` | create, update, toggle, delete |
| `/admin/domains`, `/admin/domains/{id}` | GET | `admin:read` | |
| `/admin/domains/**` | POST | `admin:write` | create, verify, delete |
| `/admin/settings` | GET | `admin:read` | |
| `/admin/settings` | POST | `admin:write` | |
| `/admin/**` | any | `admin:read` at minimum | Anything not listed must still be caught |

`admin:manage-admins` is declared but unused by this feature — it exists so the follow-up adds a
role rather than re-annotating every route (see data-model.md).

**The catch-all matters more than the specific entries.** A route added later that nobody annotates
must fail closed. The filter enforces "a session is required" across the whole `/admin/**` prefix
independently of the per-route permission annotations, so a forgotten annotation degrades to
"authenticated but unrestricted", never to "public".

## Unauthenticated behaviour: redirect, not 401

| Request | Response |
|---|---|
| Browser navigation to an admin page, no session | `302` to `/admin/login` |
| Browser navigation, session expired | `302` to `/admin/login` |
| A request that does not accept HTML, no session | `401` |
| Any request to `/admin/login` | `200` — never redirected, or it would loop |

This differs deliberately from the client, whose `RequireNapAuthenticationFilter` answers `401`
for everything. The client protects fetch calls from JavaScript; the dashboard protects
server-rendered pages an operator navigates to, and a bare `401` in a browser is a blank error page.
US2.4 requires "sent to the sign-in page rather than served the page".

The `Accept`-based distinction exists so that adding a JSON endpoint to the dashboard later does not
produce an HTML redirect where the caller expected an error.

## What the sign-in page must convey

FR-014 — the page tells the administrator what they need, which depends on state it can determine
before anyone signs in:

| State | The page shows |
|---|---|
| Key configured, nothing stored in this browser | Ask for the nsec and a new passphrase (US1) |
| Key configured, identity stored in this browser | Ask for the passphrase only (US4.1) |
| Key configured, wrong passphrase just entered | The same prompt, an error, the stored key untouched (US4.3) |
| **No administrator key configured** | That the deployment has no administrator key and how to set one — **not** a form that cannot succeed (US3.2, FR-014) |
| Configured value unusable | That it is misconfigured, distinctly from "not configured" (FR-006) |

The last two require the page to read the same configuration the resolver does, so a
misconfiguration is visible to an operator without reading logs.

## Logging

FR-012 — every outcome, with no key material:

| Event | Fields |
|---|---|
| `admin_signin_succeeded` | `pubkey`, `role`, `client_ip` |
| `admin_signin_rejected` | `reason` (`not_authorised` \| `no_admin_key_configured` \| `admin_key_unreadable` \| `proof_invalid`), `client_ip`, and `pubkey` where one was proven |
| `admin_signout` | `pubkey` |
| `admin_signin_rate_limited` | `client_ip` |

A pubkey is public and safe to log. A private key or passphrase must never appear — SC-004 and
SC-010 assert zero occurrences.

## Verification

`@WebMvcTest` slices over the admin controllers:

- Unauthenticated browser navigation to each protected route redirects to `/admin/login`
- A request not accepting HTML gets `401`, not a redirect
- `/admin/login` is reachable unauthenticated and does not redirect to itself
- A session holding `super-admin` reaches every route in the table
- A session holding no recognised role reaches none of them
- Each of the five sign-in page states renders its intended content
- Every route in the table is covered — the test enumerates them, so a new unprotected route fails
  the suite rather than passing unnoticed
