# Contract: Administrator management

Server-rendered form endpoints on the admin dashboard. There is no REST
equivalent, for the same reason the settings page has none: a second write path
would need a second authentication story and has no caller.

All three surfaces sit behind the existing admin security chain, so an
unauthenticated browser is redirected to `/admin/login` before any of this
applies.

---

## `GET /admin/settings`

Existing endpoint, extended. Renders the settings page.

**Guard**: `@RequiresPermission(admin:read)` — unchanged, class level.

**Added to the model**:

| Attribute | Meaning |
|---|---|
| `administrators` | The stored administrators, oldest first. Empty list when none — never null. |
| `superAdminPubkey` | The configured master key in canonical hex, or absent when unconfigured or unreadable. |
| `canManageAdministrators` | Whether the viewer holds `admin:manage-admins`. |

**Rendering rules**:

- The administrators section is shown to every administrator, so an added
  administrator can see who else has access. Only a viewer with
  `canManageAdministrators` is offered the add form and the remove controls.
- The super administrator is listed and marked as such, with no remove control
  (FR-009). It is shown even though it is not a row, because a list of who can
  administer the deployment that omits the master key would be misleading.
- When no master key is configured or it is unreadable, the section says so and
  offers no add form — the same three states the sign-in page distinguishes.

---

## `POST /admin/settings/administrators`

Adds an administrator.

**Guard**: `@RequiresPermission(admin:manage-admins)`. An administrator without
it is refused by the interceptor, whether or not the form was rendered for them
(FR-008). This is the contract's load-bearing line: the refusal is not the
template's absence of a button.

**Form fields**:

| Field | Rules |
|---|---|
| `key` | Required. `npub1…` or 64-character hex. Normalised to canonical hex. |
| `label` | Optional, up to 100 characters. Descriptive only. |

**Responses**:

| Outcome | Result |
|---|---|
| Added | `302` to `/admin/settings`, flash success naming the label or the key. Logs `administrator_added`. |
| Not a public key | `302` to `/admin/settings` with an **error** flash naming the offending value, as relay URLs already are. Nothing stored. |
| Already an administrator | `302` to `/admin/settings` with an **informational** flash — not an error — saying the key already administers the deployment. Nothing stored, no duplicate row. Logs `administrator_add_ignored reason=already_administrator`. |
| Is the configured master key | Same: informational flash saying the key is already the super administrator. No entry created, ordinary or otherwise (FR-004, FR-004a, US4 scenario 3). Logs `administrator_add_ignored reason=already_super_admin`. |
| Caller lacks the permission | `403`. Logs `administrator_change_rejected reason=not_super_admin`. |

**Why the two "already administers" cases are informational rather than errors,
and rather than silent.** The request asks for a state that already holds, so
there is nothing to fail — reporting an error would tell the operator to fix
something that is not broken. But answering with a plain success would be worse
than either: an operator who pasted their own key instead of a colleague's would
believe access had been granted, and would discover otherwise only when the
colleague cannot sign in, with nothing on the page to explain it. The
informational flash is the one answer that is both true and useful.

**Redirecting rather than re-rendering, unlike the settings form.** The settings
form re-renders on rejection so the operator keeps what they typed. This
controller does not own the settings page's model, and reconstructing it here to
preserve one field would put that page together in two places — the kind of
duplication that lets two versions of a page drift apart. The error names the
offending value, so nothing needed to correct the mistake is lost. Revised
during implementation; the original contract said re-render.

---

## `POST /admin/settings/administrators/{pubkey}/remove`

Removes an administrator and ends their sessions.

`{pubkey}` is canonical hex. It is the path segment rather than the row id
because it is the value revocation needs (research D4), so the handler cannot
revoke one key while deleting another.

**Guard**: `@RequiresPermission(admin:manage-admins)`.

**Responses**:

| Outcome | Result |
|---|---|
| Removed | `302` to `/admin/settings`, flash success. Every session held by that pubkey is revoked in the same operation. Logs `administrator_removed` with `sessions_revoked`. |
| Not an administrator | Refused as not found, so a mistyped removal is visible rather than silently succeeding. |
| Is the configured master key | Refused (FR-009). The master key is not a row, so this is a guard against a hand-made request, not against the interface. |
| Caller lacks the permission | `403`. Logs `administrator_change_rejected reason=not_super_admin`. |

**The guarantee**: on return, the removed administrator's next request to any
admin page is refused. Not at session expiry, and not after the ACL refresh
interval — see research D5 for why relying on the resolver alone would satisfy
neither.

**The ceiling**: revocation reaches the sessions this instance holds
(research D6). A deployment running several dashboard instances leaves the
removed administrator working on the others until expiry. `sessions_revoked` is
logged so a removal that revoked nothing is visible.

---

## Contract tests

Each row below is a test that must exist, and each fails for a different reason —
none is a restatement of another.

| Test | Proves |
|---|---|
| Super admin adds a key, it appears in the list | FR-001, FR-011 |
| The same key added as npub then hex leaves one entry, and reports no error | FR-002, FR-004, FR-004a |
| A non-key value is refused with the value named, nothing stored | FR-003 |
| Adding the configured master key creates no entry and reports no error | FR-004, FR-004a, US4 |
| Adding the configured master key leaves the administrator list byte-identical | FR-004a — distinct from the above, which a handler that stored then deleted would also pass |
| An added administrator reaches every admin page | FR-005 |
| An added administrator gets `403` from both management endpoints, called directly | FR-008, US3 |
| A removal request for the master key is refused | FR-009 |
| Removal revokes a live session — asserted by a request that worked before and fails after | FR-007, SC-003 |
| Removal of an absent key is refused as not found | State transitions |
| The master key still signs in with the list empty | FR-012 |
| Additions and removals appear in the log with the acting administrator | FR-010 |

The revocation test is the one that must exercise a real session against a real
store. A unit test asserting that the service calls the revoker proves the call,
not the effect, and this feature's whole risk is in the difference.
