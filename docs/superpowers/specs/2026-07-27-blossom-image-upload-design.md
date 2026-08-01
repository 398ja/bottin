# Blossom Image Upload for Avatar and Banner — Design

**Date:** 2026-07-27
**Status:** Approved (pending spec review)
**Module:** `bottin-client-ui`

## Purpose

Let a user pick an avatar or banner image from their device instead of typing a
URL. The selected file is uploaded to the configured Blossom server, and the URL
the server returns becomes the profile's `picture` or `banner` value. This
implements FR-025 and task 1.5.1 of `specs/003-nostr-client-onboarding`, both of
which are currently unimplemented.

The upload goes directly from the browser to the Blossom server, authenticated by
an event signed with the user's own key. No image bytes pass through bottin, and
every blob is owned by the uploading pubkey.

## Scope

**In scope**

- `/profile`: avatar and banner file pickers replacing the URL text inputs.
- Onboarding profile step: the same treatment for its two URL inputs.
- A `blossom.js` client module: BUD-01 auth event + BUD-02 upload.
- Binding the already-declared `bottin.client.blossom-url` property and exposing
  it to both templates.
- Widening the page CSP so the browser may reach a local Blossom server.

**Out of scope**

- Cropping, rotation, or any client-side image editing. The Blossom server already
  resizes to 1920×1080 webp and strips EXIF.
- Drag-and-drop and upload progress bars.
- Listing, replacing, or deleting previously uploaded blobs (BUD-02 `/list`,
  BUD-04 mirror, BUD-01 `DELETE`).
- Any change to how the profile event itself is built or published.

## Architecture

Uploading is a client-side concern, consistent with the module's existing "dumb
server" design: bottin serves templates and configuration, and the browser does
the signing and the network I/O.

```
browser                                  blossom server (:8888)
  |                                              |
  | 1. user picks a file                         |
  | 2. sha256(file) via crypto.subtle            |
  | 3. build kind-24242 auth event               |
  |    tags: t=upload, x=<sha256>, expiration    |
  | 4. sign with the user's key                  |
  | 5. PUT /upload                               |
  |    Authorization: Nostr <base64(event)>      |
  |    body: raw file bytes            --------> |
  |                                              | verifies signature,
  |                                              | processes and stores blob
  | <-------- 200 {url, sha256, size, type}      |
  | 6. url -> identity.picture / hidden input    |
```

The key material differs by page but the module does not: `/profile` obtains the
hex key through the existing `APP.ensureUnlocked(userId)` unlock flow, while the
onboarding step decodes the nsec already held in `sessionStorage` under
`onboarding-nsec`. Both hand a hex key to the same `BlossomUpload.upload()` call.
This is why upload works during onboarding, where no NAP session exists yet.

## Components

### `static/js/blossom.js` (new)

A pure module in the style of `nostr-publish.js`: UMD wrapper exposing
`window.BlossomUpload` and a CommonJS export so Vitest can load it.

- `sha256Hex(file)` — reads the file as an `ArrayBuffer` and returns the lowercase
  hex digest from `crypto.subtle.digest('SHA-256', …)`.
- `buildAuthEvent(hashHex, expirationSeconds)` — returns the unsigned kind-24242
  event: `content` a human-readable "Upload image", `tags` `["t","upload"]`,
  `["x",hashHex]`, and `["expiration", <now + expirationSeconds>]`. Signing is the
  caller's job so the module never touches key material handling.
- `encodeAuthHeader(signedEvent)` — `'Nostr ' + btoa(JSON.stringify(signedEvent))`.
- `upload(file, blossomUrl, signer)` — rejects a file that is not `image/*` or
  exceeds 5 MB, then orchestrates the above, `PUT`s to `{blossomUrl}/upload` with
  the raw file as the body, and resolves with the blob descriptor. `signer` is a
  function taking the unsigned event and returning the signed one, which keeps the
  module free of any dependency on `NostrCrypto` and makes it trivially testable.

File validation lives here rather than in the UI layer so both call sites get it
from one place; the UI only decides how to display the rejection.

`upload` rejects with an `Error` carrying the server's `X-Reason` header when
present, since Blossom servers report the rejection reason there.

### `static/js/profile-image.js` (new)

The shared UI behaviour for one image field, used twice per page. Given the ids of
a file input, a preview image, and a remove button, it:

1. shows a local `URL.createObjectURL` preview immediately, so the user sees the
   image before the round trip finishes;
2. surfaces a rejected file (wrong type, too large) as a field error through the
   existing `.form-error` element;
3. resolves a hex key through the page's signer, uploads, and on success stores
   the returned URL through a caller-supplied `onUploaded(url)` callback;
4. on failure, revokes the object URL, restores the previous preview, and shows a
   toast.

Keeping this out of `profile.js` avoids duplicating the same forty lines in the
onboarding step, and keeps both call sites down to a few lines of wiring.

### `templates/profile.html`

The two `<input type="text">` fields become file pickers. Each field is a preview
thumbnail (using the existing `safeImageUrl` guard and `default-avatar.svg`
fallback for the avatar), a `<input type="file" accept="image/*">`, and a Remove
button that clears the stored value. `profile.js` no longer reads
`#profile-picture` / `#profile-banner` as text; `readFields()` takes the current
`identity.picture` / `identity.banner` instead, which is what upload writes.

### `templates/onboarding/step-profile.html`

Same two controls. Because the step submits a form whose values are harvested by
the existing `saveProfileData()`, the upload writes the returned URL into a hidden
`<input name="picture">` / `<input name="banner">`. Nothing downstream of the form
changes.

### `config/ClientProperties.java`

Add `private String blossomUrl;`. The property `bottin.client.blossom-url` already
exists in `application.yml` but nothing binds it today, so it is currently inert.

### `ProfileController` and `OnboardingController`

Each adds the blossom URL to its model so the template can render it into a
`data-blossom-url` attribute, which is how the page hands configuration to JS
elsewhere in this module.

### `templates/layout.html`

The CSP `connect-src` gains `http://localhost:* http://127.0.0.1:*`. Production
Blossom servers are reached over `https:`, which the directive already permits;
only a local, plain-HTTP server needs this, the same situation as the local relay
over `ws://`. This widening is a known wart — the standing follow-up to template
the CSP from configuration under a dev profile covers it.

## Data flow

**Profile page.** File selected → validated → preview swapped → `ensureUnlocked`
resolves the hex key (showing the unlock modal if the session is locked) →
`BlossomUpload.upload` → `identity.picture` set and `APP.saveIdentity(identity)`
→ success toast. The value is published to relays on the next Save & Publish,
unchanged from today.

**Onboarding step.** File selected → validated → preview swapped →
`NostrCrypto.nsecToHex(sessionStorage['onboarding-nsec'])` → `BlossomUpload.upload`
→ hidden input value set → success toast. The value flows into
`onboarding-data` through the existing `saveProfileData()`.

## Error handling

| Condition | Handling |
|---|---|
| Not an image, or over 5 MB | Field error under the control; no request sent. |
| Unlock cancelled (`/profile`) | Preview reverts, no toast — cancelling is a deliberate no-op. |
| 401 from Blossom | Toast "Upload rejected: `<X-Reason>`". |
| 413 or any other non-2xx | Toast with status and `X-Reason` when present. |
| Network failure | Toast "Upload failed: `<message>`". |
| Any failure | The previously stored URL is left untouched. |

A failed upload never writes to the identity or the hidden input, so a user whose
upload fails keeps the image they already had.

## Security considerations

- The private key never leaves the browser. The auth event is signed locally and
  only the signature travels, matching how relay publishing already works.
- The auth event carries an `expiration` tag and an `x` tag bound to the file's
  hash, so a captured header cannot be replayed for a different file or
  indefinitely.
- Blobs are owned by the user's pubkey, so the user can later delete them with
  their own key. A server-side proxy would have had to upload under a bottin key,
  making the blobs undeletable by their owner.
- The uploaded URL is rendered through the existing `safeImageUrl` guard, which
  already constrains the schemes that may appear in an `img` `src`.
- The CSP widening admits `http://localhost` origins only, which are not reachable
  from a hostile network position that does not already control the machine.

## Testing

**Vitest (`src/test/js/blossom.test.js`)**

- `buildAuthEvent` produces kind 24242 with `t=upload`, the given `x` hash, and an
  `expiration` in the future.
- `encodeAuthHeader` yields a `Nostr `-prefixed base64 payload that decodes back to
  the signed event.
- `upload` `PUT`s to `{blossomUrl}/upload` with the file as the body and the auth
  header set (mocked `fetch`).
- `upload` rejects with the `X-Reason` text on a non-2xx response.
- `upload` rejects when the file is not an image or exceeds the size cap.

**Java**

- `ClientPropertiesTest` (or the existing configuration test) asserts
  `blossom-url` binds from `BOTTIN_BLOSSOM_URL`.
- `ProfileControllerTest` and the onboarding controller test assert the blossom URL
  reaches the model.

**Manual**

Upload an avatar and a banner on both pages against the local Blossom server on
:8888, confirm the returned URL renders, and confirm the value survives a page
reload and reaches the published kind-0 event.

## File-by-file changes

| File | Change |
|---|---|
| `static/js/blossom.js` | New. Auth event + upload. |
| `static/js/profile-image.js` | New. Shared file-field behaviour. |
| `static/js/profile.js` | Wire both fields; stop reading picture/banner as text. |
| `templates/profile.html` | Replace two text inputs with file controls. |
| `templates/onboarding/step-profile.html` | Same, writing to hidden inputs. |
| `templates/layout.html` | CSP `connect-src` gains local http origins; load the new scripts. |
| `config/ClientProperties.java` | Bind `blossomUrl`. |
| `controller/ProfileController.java` | Expose the blossom URL to the model. |
| `controller/OnboardingController.java` | Same for the profile step. |
| `src/test/js/blossom.test.js` | New unit tests. |
| Controller tests | Assert the model attribute. |

## Open questions

None. The Blossom server's defaults were verified against the running container:
`requireAuth: true` and `requirePubkeyInRule: false`, so any pubkey with a valid
signed auth event may upload; CORS permits `PUT` with an `Authorization` header
from any origin; and the server resizes and strips EXIF on its own.
