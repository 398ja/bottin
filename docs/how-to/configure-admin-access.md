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

### What to remove

Delete `BOTTIN_ADMIN_USER` and `BOTTIN_ADMIN_PASSWORD` from the **`bottin-admin`**
service only.

`bottin-api` still reads `BOTTIN_ADMIN_PASSWORD` for its own HTTP Basic
credentials, which this change does not touch. Removing it from the file wholesale
leaves the API starting with a random password that changes on every restart.

## Sign in

1. Open `/admin/login`. It asks for your nsec and a passphrase.
2. Your key is encrypted with that passphrase and kept **in your browser**. It is
   never sent to the deployment.
3. You reach the dashboard.

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
- **One administrator per deployment.** Supporting several is tracked separately;
  until then, two operators would have to share a private key, which is the
  shared secret this change removes.

## Troubleshoot

| Symptom | Cause |
|---|---|
| "No administrator key is configured" | `BOTTIN_ADMIN_NPUB` is unset. |
| "The configured administrator key is not readable" | It is set but is not an `npub1…` or 64-character hex key. |
| "That key is not authorised for this deployment" | The key signed with is not the configured one. The message does not say which key would be. |
| Sign-in fails with no message | Check `BOTTIN_ADMIN_EXTERNAL_URL` matches the URL in the browser's address bar. |
| "Wrong passphrase" | The stored key is untouched; try again, or discard it and start from your nsec. |
| Sign-in refused after many attempts | The handshake is rate limited per client address. Wait a minute. |

## Related

- [Deploy with Docker](docker-deployment.md)
- [Configure Deployment Settings](configure-deployment-settings.md)
- [Docker Compose Configuration](../reference/docker-compose-configuration.md)
