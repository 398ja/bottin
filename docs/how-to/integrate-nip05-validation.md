# Integrate NIP-05 Validation with bottin

This guide shows you how to make a bottin deployment answer NIP-05 lookups for
your domain, and how a client application validates an identity against it.

There are two distinct integrations, and they are not alternatives — most
deployments want the first, and only some want the second:

| You want | Use |
|---|---|
| Your domain to serve the identities bottin holds | [Serve NIP-05 for your domain](#serve-nip-05-for-your-domain) |
| Your client to validate an identity | [Validate from a client](#validate-from-a-client) |
| bottin to verify *someone else's* NIP-05 on your behalf | [Verify a third-party identifier](#verify-a-third-party-identifier) |

## How NIP-05 validation actually works

Worth being explicit, because it decides everything below. A NIP-05 identifier
`alice@example.com` is validated by:

1. Fetching `https://example.com/.well-known/nostr.json?name=alice`
2. Reading `names.alice` from the response
3. **Comparing it to the pubkey you already have**

The comparison is the validation. The server only publishes a mapping — it cannot
tell you an identity is "valid", because only you know which pubkey you expected.

The domain queried is the one *in the identifier*. Nothing lets you redirect that
elsewhere: if you issue identities as `alice@example.com`, then `example.com`
must serve `/.well-known/nostr.json`, whatever runs behind it.

## Serve NIP-05 for your domain

bottin serves `/.well-known/nostr.json` on its API service (port 8080 by
default), over plain HTTP, with no TLS of its own. Your edge proxy terminates TLS
and passes the path through.

### The proxy passes, it does not redirect

This is the part most often got wrong. `/.well-known/nostr.json` must be
**proxied** — the client's request is answered at your domain, by bottin, in one
round trip.

A `301`/`302` redirect to bottin's own hostname will fail:

- The identifier names *your* domain. A client that follows the redirect is
  reading a file from a different origin, which is exactly what NIP-05
  authenticates against — several clients refuse to follow it for that reason.
- The browser's CORS preflight applies to the redirect target, not your domain.
- Some clients simply do not follow redirects for this fetch.

### nginx

```nginx
server {
    listen 443 ssl;
    server_name example.com;

    # ... your TLS configuration ...

    location = /.well-known/nostr.json {
        proxy_pass http://bottin-api:8080/.well-known/nostr.json;

        # bottin decides which domain to serve from the Host header. Passing
        # nginx's own upstream name here instead makes every lookup resolve
        # against the wrong domain — and it fails silently, returning an empty
        # names map rather than an error.
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # NIP-05 requires this for browser-based clients. bottin deliberately
        # does not set it: setting it in both places produces two headers and
        # browsers reject the response as "Multiple CORS header not allowed".
        add_header Access-Control-Allow-Origin "*" always;
    }
}
```

Use `location =` (an exact match) rather than a prefix. `/.well-known/` also
carries ACME challenges for certificate renewal, and a prefix match sends those
to bottin too, breaking renewal.

### Caddy

```caddyfile
example.com {
    handle /.well-known/nostr.json {
        header Access-Control-Allow-Origin "*"
        reverse_proxy bottin-api:8080
    }
    # ... the rest of your site ...
}
```

Caddy passes the original `Host` upstream by default, so nothing extra is needed
for the multi-domain behaviour.

### Serving several domains

One bottin deployment serves any number of domains: it reads the `Host` header
and answers with that domain's records. Add the same `location` block to each
server block. No bottin-side configuration is needed per domain beyond
registering and verifying the domain in the admin dashboard.

### Check it

```bash
# Your domain answers, with the right pubkey
curl -s "https://example.com/.well-known/nostr.json?name=alice"

# The CORS header is present exactly once
curl -sI "https://example.com/.well-known/nostr.json?name=alice" \
  | grep -ci "access-control-allow-origin"     # must print 1, not 0 or 2

# The Host header reached bottin: a name you know exists must resolve.
# An empty names map here, when the record exists, means Host was rewritten.
curl -s "https://example.com/.well-known/nostr.json?name=alice" | grep -q '"names":{}' \
  && echo "EMPTY — check proxy_set_header Host" || echo "ok"
```

Browser clients are the ones that fail when CORS is missing, and `curl` will not
show you that. Test from a browser console on a different origin:

```js
fetch('https://example.com/.well-known/nostr.json?name=alice')
  .then(r => r.json()).then(console.log)   // a CORS error here means the header is missing
```

## Validate from a client

Nothing bottin-specific. Standard NIP-05:

```js
async function validateNip05(identifier, expectedPubkeyHex) {
  const [name, domain] = identifier.split('@');
  if (!name || !domain) return false;

  const response = await fetch(
    `https://${domain}/.well-known/nostr.json?name=${encodeURIComponent(name)}`
  );
  if (!response.ok) return false;

  const body = await response.json();
  const found = body.names && body.names[name];

  // The comparison IS the validation. A response arriving is not a pass.
  return found === expectedPubkeyHex;
}
```

Points that catch people out with bottin specifically:

- **An unknown name returns `200` with `{"names": {}}`**, not `404`. Check for
  the key, not the status.
- **Names are case-sensitive** as stored. Query the exact local part.
- **Responses are cached for an hour** (`Cache-Control: public, max-age=3600`).
  A newly registered identity may not appear immediately behind a CDN.
- **`relays` may be absent.** It is optional in NIP-05; treat it as a hint.
- Compare the hex form. If you hold an `npub`, decode it with a NIP-19 library
  first — never compare the two encodings directly.

## Verify a third-party identifier

If you would rather not perform the fetch yourself — to avoid CORS, to get
caching, or to keep the outbound request server-side — bottin will do it:

```bash
curl "https://api.example.com/api/v1/verify?nip05=alice@somewhere-else.com"
```

```json
{
  "nip05": "alice@somewhere-else.com",
  "valid": true,
  "pubkey": "79be667e…98",
  "relays": ["wss://relay.example.com"],
  "message": "Verification successful",
  "verifiedAt": "2026-08-02T10:30:00Z",
  "cached": true
}
```

- Public, no authentication. Rate limited per client address, using the
  deployment's configured limit from `/admin/settings`; `429` when exceeded.
- Results cached for five minutes. `&noCache=true` forces a fresh fetch.

> **`valid` does not mean "matches my key".** It means the identifier resolved to
> a well-formed 64-character hex pubkey. There is no expected-pubkey parameter,
> so you must still compare `pubkey` against the one you hold. Treating `valid`
> as the answer accepts any identifier that merely exists.

This endpoint couples your client to a particular bottin deployment and cannot
validate identities faster than the remote host answers. Prefer fetching
`.well-known/nostr.json` directly unless you specifically want the caching.

## Troubleshoot

| Symptom | Cause |
|---|---|
| `{"names":{}}` for a name you know exists | The proxy rewrote `Host`, so bottin resolved against the wrong domain. Set `proxy_set_header Host $host`. |
| Works in `curl`, fails in a browser | `Access-Control-Allow-Origin` is missing. bottin does not set it; the proxy must. |
| Browser reports "Multiple CORS header not allowed" | It is being set twice — by the proxy *and* something else. bottin does not set it, so look for a second `add_header`, or an outer proxy. |
| Certificate renewal breaks after adding the proxy rule | A prefix `location /.well-known/` captured the ACME challenge. Use the exact match `location = /.well-known/nostr.json`. |
| A new record does not appear | Responses carry a one-hour cache lifetime. Check for an intermediate CDN. |
| `404` from your domain | The proxy rule is missing or on the wrong server block. |
| `429` from `/api/v1/verify` | Rate limited per client address. Raise the limit at `/admin/settings` or use the cached result. |

## Related

- [REST API Reference](../reference/rest-api.md)
- [Deploy with Docker](docker-deployment.md)
- [Configure Deployment Settings](configure-deployment-settings.md)
- [NIP-05 specification](https://github.com/nostr-protocol/nips/blob/master/05.md)
