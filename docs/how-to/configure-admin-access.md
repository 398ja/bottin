# Configure Admin Access

This guide shows you how to set who may administer a Bottin deployment, and how
an administrator signs in.

The admin dashboard no longer has a username and password. You configure the
**public** key of the person who administers the deployment, and they sign in by
proving they hold the matching private key.

## Why the change

A password in `docker-compose.yml` is a shared secret. It sits in the file, in
every operator's notes, and in whatever chat message passed it along — and anyone
who reads it can act as the administrator from anywhere.

A public key is not a secret. Publishing it costs nothing, which is exactly what
makes it safe to keep where a password should not be. Signing in proves control
of the matching private key without that key ever reaching the deployment, so
there is no longer an admin credential the server could leak.

## Configure the administrator

Set the administrator's public key before deploying:

```bash
BOTTIN_ADMIN_NPUB=npub1...          # or the 64-character hex form
```

Both forms are accepted, and the same key entered either way is recognised as one
administrator.

Also set the URL the browser will use to reach the dashboard:

```bash
BOTTIN_ADMIN_EXTERNAL_URL=https://admin.example.com
```

**This must match what the browser actually uses.** The sign-in proof names the
address it is being sent to; if the configured value disagrees — a container port
rather than the published one, say — the proof is rejected and sign-in fails with
no obvious cause.

### Serving over plain HTTP

The session cookie is marked `Secure`, so a browser will not store it over plain
HTTP. Sign-in then appears to work and every page immediately returns to the
sign-in form. Deployments should be served over HTTPS; for a local stack that is
not, set:

```bash
COOKIE_SECURE=false
```

Do not set this on anything reachable beyond your machine — it allows the session
cookie to travel in the clear.

### What to remove

Delete `BOTTIN_ADMIN_USER` and `BOTTIN_ADMIN_PASSWORD` from the **`bottin-admin`**
service only.

`bottin-api` still reads `BOTTIN_ADMIN_PASSWORD` for its own HTTP Basic
credentials, which this change does not touch. Removing it from the file wholesale
leaves the API starting with a random password that changes on every restart.

## Sign in

1. Open `/admin/login`. It asks for your nsec and a passphrase, entered twice.
2. Your key is encrypted with that passphrase and kept **in your browser**. It is
   never sent to the deployment.
3. You reach the dashboard.

The passphrase is confirmed because it cannot be recovered. A typo does not fail
at sign-in — it encrypts your key under something you do not know, and you find
out on your next visit, when only your nsec can get you back in.

Afterwards that browser asks only for the passphrase — on returning, and whenever
a session expires.

**Signing out erases the key from that browser**, so the next sign-in there needs
the nsec again. That is deliberate: a sign-out that left the key behind would be a
false reassurance. Session expiry is different — it leaves the key in place, so
the passphrase alone resumes work.

## Things to know before they surprise you

- **There is no password reset.** Losing the key means changing
  `BOTTIN_ADMIN_NPUB` and restarting. Nothing on the server can recover it, which
  is the same property that makes the key safe to hold.
- **The passphrase cannot be recovered either.** It never leaves your browser and
  is stored nowhere. Forgetting it means discarding the stored key and starting
  again from your nsec; the sign-in page offers exactly that.
- **Each browser is a separate first sign-in.** Nothing syncs between devices.
- **A shared machine keeps an encrypted key until you sign out.** Sign out.
- **An unconfigured deployment admits nobody.** With `BOTTIN_ADMIN_NPUB` unset,
  the sign-in page says so rather than showing a form that cannot work. A value
  that is set but is not a key reads differently again, because it is a different
  mistake with a different fix.
- **The configured key is the super administrator.** Further administrators are
  added from the settings page — see [Manage administrators](#manage-administrators).
  Nobody has to share a private key.

## Manage administrators

The configured key is the **super administrator**. From `/admin/settings` it can
grant access to colleagues, each signing in with their own key.

### The roles

| | Super administrator | Administrator | Read-only |
|---|---|---|---|
| Where it comes from | `BOTTIN_ADMIN_NPUB` | Added on the settings page | Set directly in the database |
| How many | Exactly one | Any number | Any number |
| View dashboard, records, domains, settings | Yes | Yes | Yes |
| Create and change records | Yes | Yes | **No** |
| Add, verify, and remove domains | Yes | **No** | **No** |
| Change deployment settings | Yes | **No** | **No** |
| Add and remove administrators | Yes | **No** | **No** |

Deployment settings — relay topology and the media server — are the super
administrator's alone. They apply to every request the registry serves and take
effect without a restart, which is a wider reach than editing a record, so
adding a colleague to maintain records does not also hand them the deployment.

Domains are the super administrator's for the same reason. A domain is what the
registry answers `.well-known/nostr.json` for, so adding one commits the
deployment to serving a name — closer in reach to repointing the relays than to
editing a record under a domain that already exists. An administrator maintains
records within the domains the deployment has; choosing which domains those are
is a separate decision.

Verification and deletion are reserved along with creation, not just creation.
An administrator who could delete a domain but not recreate it would hold a
capability destructive in one direction only. For the same reason, opening a
domain's page no longer issues its verification token unless the viewer may act
on it — otherwise the reservation would bind only those who used the button.

The read-only role has no control on the settings page; it is set by writing
`READONLY` to `admin_users.role`. Every other administrator added through the
page is an ordinary administrator.

Each refusal is enforced where the decision is made, not by hiding a button: an
administrator who issues the request directly is refused just the same, and the
attempt is logged.

### Add someone

1. Ask for their **public** key — never their nsec. `npub1…` or hex, either works.
2. On `/admin/settings`, under **Administrators**, enter the key and a label.
3. They sign in at `/admin/login` with their own nsec, exactly as you did.

Adding a key that already administers the deployment — one already listed, or
your own — changes nothing and says so. It is not an error, but it is worth
reading: if you meant to add a colleague and pasted your own key, nothing was
granted and they still cannot sign in.

### Remove someone

Use **Remove** beside them. Their access ends immediately: any session they hold
stops working on their very next request, rather than lasting until it expires.

**The super administrator cannot be removed, edited, or demoted here.** It is
deployment configuration, which is what admits you when the database is empty,
wrong, or freshly restored — if a save could remove it, one mistake could lock
out everybody with nothing able to undo it. To move it to a different key, change
`BOTTIN_ADMIN_NPUB` and restart.

### Things worth knowing

- **Removal reaches the sessions this instance holds.** The deployment runs one
  admin instance, so removal is immediate as described. If you ever run several
  behind a load balancer, a removed administrator would keep working on the
  instances that did not process the removal until their session expires. The
  removal log records `sessions_revoked`, so a removal that ended nothing is
  visible.
- **Changing `BOTTIN_ADMIN_NPUB` does not rewrite the list.** Added
  administrators keep their access; the previous holder simply stops being the
  super administrator. If the new key was already an added administrator,
  configuration wins and they become the super administrator.
- **An unreadable `BOTTIN_ADMIN_NPUB` does not lock everyone out.** Added
  administrators still sign in; nobody can manage the list until the
  configuration is fixed.
- **Labels grant nothing.** They exist so the list can be read without comparing
  64-character keys by eye, and are never consulted at sign-in.

## Troubleshoot

| Symptom | Cause |
|---|---|
| "No administrator key is configured" | `BOTTIN_ADMIN_NPUB` is unset. |
| "The configured administrator key is not readable" | It is set but is not an `npub1…` or 64-character hex key. |
| "That key is not authorised for this deployment" | The key signed with is not the configured one. The message does not say which key would be. |
| Sign-in succeeds, then every page returns to the sign-in form | The session cookie is not being stored. Over plain HTTP, set `COOKIE_SECURE=false`; otherwise check `BOTTIN_ADMIN_EXTERNAL_URL` matches the browser's address bar. |
| "The passphrases do not match" | The two passphrase fields differ. Nothing was stored; retype both. |
| "Wrong passphrase" | The stored key is untouched; try again, or discard it and start from your nsec. |
| "Too many sign-in attempts" | The handshake is rate limited per client address. Wait a minute. |
| An added administrator cannot sign in | Check the key in the list is theirs — compare the hex, not the label. A key added for the wrong person grants that person nothing. |
| "That key already administers this deployment" | Nothing was added because the key is already listed, or is your own super-administrator key. If you meant to add a colleague, check what you pasted. |
| The administrators section shows no add or remove controls | You are signed in as an ordinary administrator, not the super administrator. |
| A removed administrator can still use the dashboard | Check `sessions_revoked` in the `administrator_removed` log line. `0` means no session was ended — expected if they were not signed in, and otherwise a sign that another instance holds it. |
| "No administrator is registered with public key …" | The removal named a key that is not on the list. Reload the settings page; it may already have been removed. |

## Related

- [Deploy with Docker](docker-deployment.md)
- [Configure Deployment Settings](configure-deployment-settings.md)
- [Docker Compose Configuration](../reference/docker-compose-configuration.md)
