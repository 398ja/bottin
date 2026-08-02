# Quickstart — Admin sign-in with a Nostr key

Two audiences: an operator configuring a deployment after this lands, and a developer verifying it.

---

## For the operator

### What changes

The admin dashboard no longer has a username and password. You configure the **public** key of the
person who administers the deployment, and they sign in by proving they hold the matching private
key.

The value you put in the configuration is not a secret — an npub is safe to publish. That is the
point: a password in `docker-compose.yml` is a shared secret anyone who reads the file can use from
anywhere, and this replaces it with something that is useless to a reader.

### Before upgrading

**This is breaking.** Configure the administrator key *before* deploying, or nobody can sign in.

```bash
BOTTIN_ADMIN_NPUB=npub1...        # the administrator's public key
```

Then remove `BOTTIN_ADMIN_USER` and `BOTTIN_ADMIN_PASSWORD` **from the `bottin-admin` service
only**. `bottin-api` still reads `BOTTIN_ADMIN_PASSWORD` for its own HTTP Basic credentials, which
this feature does not change — deleting it from the file wholesale will leave the API starting with
a random password that changes on every restart.

### Signing in

1. Open the dashboard. It asks for your nsec and a passphrase.
2. The nsec is encrypted with your passphrase and kept **in your browser**. It is never sent to the
   deployment.
3. You reach the dashboard.

Afterwards you are asked only for the passphrase — on returning, and whenever a session expires.

**Signing out erases the key from that browser**, so the next sign-in there needs the nsec again.
That is deliberate: a sign-out that left the key behind would be a false reassurance.

### Things to know before they surprise you

- **There is no password reset.** Losing the key means changing `BOTTIN_ADMIN_NPUB` and restarting.
  There is nothing on the server that can recover it, which is the same property that makes the key
  safe to hold.
- **The passphrase cannot be recovered either.** It never leaves your browser and is not stored
  anywhere. Forgetting it means discarding the stored key and starting again from the nsec — the
  sign-in page offers that.
- **Each browser is a separate first sign-in.** Nothing syncs between devices.
- **A shared or public machine keeps an encrypted key until you sign out.** Sign out.
- **An unconfigured deployment admits nobody.** If `BOTTIN_ADMIN_NPUB` is unset, the sign-in page
  says so rather than showing a form that cannot work.

---

## For the developer

### Build and test

```bash
mvn -q verify                            # unit, including the admin slices
cd bottin-admin-ui && npm test           # browser key handling, if the module gains a JS suite
mvn -Pit -pl bottin-tests/bottin-it -am verify
```

### Verify the handshake

```bash
# 1. a challenge is issued for any well-formed npub — deliberately no oracle
curl -s -X POST http://localhost:8091/api/v1/auth/init \
  -H 'Content-Type: application/json' -d '{"npub":"npub1someoneelse..."}'
# expect: a challenge, not an error

# 2. an unauthenticated browser is redirected, not 401'd
curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' \
  -H 'Accept: text/html' http://localhost:8091/admin/dashboard
# expect: 302 .../admin/login

# 3. a non-browser caller gets 401
curl -s -o /dev/null -w '%{http_code}\n' \
  -H 'Accept: application/json' http://localhost:8091/admin/dashboard
# expect: 401

# 4. the sign-in page is reachable and does not redirect to itself
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8091/admin/login
# expect: 200
```

### Verify the refusals

Each must produce **no session**, and a log line naming a distinct reason:

| Set up | Expect |
|---|---|
| A key that is not the configured one | refused, `reason=not_authorised` |
| `BOTTIN_ADMIN_NPUB` unset | refused, `reason=no_admin_key_configured`, and the sign-in page says so |
| `BOTTIN_ADMIN_NPUB=not-a-key` | refused, `reason=admin_key_unreadable` — distinct from the above |
| Replay a completed challenge | refused |
| Complete a challenge after its TTL | refused |

### Verify no route lost its guard

The regression this feature is most likely to cause. Enumerate every admin route and confirm each
still redirects when signed out:

```bash
for p in /admin/dashboard /admin/records /admin/domains /admin/settings; do
  printf '%-22s %s\n' "$p" "$(curl -s -o /dev/null -w '%{http_code}' -H 'Accept: text/html' http://localhost:8091$p)"
done
# expect: 302 for every one
```

### Verify the browser side

In devtools, with an unlocked session:

- Local storage holds `privateKeyEncrypted`, `privateKeyIv`, `privateKeySalt`, `passwordHash` —
  and **no plaintext nsec and no passphrase**.
- Network: the nsec and passphrase appear in **zero** requests (SC-004, SC-010). Search the full
  request log, not just the auth calls.
- Sign out → storage is empty.
- Let the session expire instead → storage still holds the identity, and the passphrase alone
  resumes. This is the pair that distinguishes expiry from sign-out.

### Manual walkthrough

1. Configure `BOTTIN_ADMIN_NPUB`, start the stack.
2. Sign in with the matching nsec and a passphrase → dashboard.
3. Reload → passphrase only, not the nsec.
4. Enter a wrong passphrase → refused, and the stored key still works on the next try.
5. Sign out → next visit asks for the nsec again.
6. Sign in with a *different* nsec → refused, and nothing is left stored.
7. Unset `BOTTIN_ADMIN_NPUB`, restart → the page says no administrator key is configured.

---

## Related

- [spec.md](./spec.md) · [plan.md](./plan.md) · [research.md](./research.md) ·
  [data-model.md](./data-model.md) · [contracts/](./contracts/)
- Board card `367swx4lb8gb` — the follow-up adding further administrators and the second role
