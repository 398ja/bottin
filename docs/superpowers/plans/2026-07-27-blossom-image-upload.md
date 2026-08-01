# Blossom Image Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the avatar and banner URL text inputs on `/profile` and the onboarding profile step with local file pickers that upload the selected image to the configured Blossom server and store the returned URL.

**Architecture:** Uploads go straight from the browser to the Blossom server, authenticated by a kind-24242 event signed with the user's own key — no image bytes pass through bottin. Two new UMD JS modules carry the work: `blossom.js` (hashing, auth event, `PUT /upload`) and `profile-image.js` (the per-field UI behaviour, used twice on each of the two pages). The server's only job is to hand the configured Blossom URL to the templates.

**Tech Stack:** Java 21, Spring Boot 3.4 (Thymeleaf, `@ConfigurationProperties`), vanilla ES5-style browser JS in the UMD style of `nostr-publish.js`, Vitest + jsdom for the JS tests, JUnit 5 + `@WebMvcTest` for the Java tests.

**Spec:** `docs/superpowers/specs/2026-07-27-blossom-image-upload-design.md`

## Global Constraints

- Module under change: `bottin-client-ui`. All relative paths below are relative to `bottin-client-ui/` unless they start with `docs/` or are a repo-root file (`pom.xml`).
- Browser JS is ES5-style function syntax in the existing UMD wrapper form: `(function (global) { … global.X = api; if (typeof module !== 'undefined' && module.exports) module.exports = api; })(typeof window !== 'undefined' ? window : globalThis);`. No modules, no arrow functions, no `const`/`let` in `src/main/resources/static/js/**`.
- Test files under `src/test/js/**` are modern ESM (`import … from`, arrow functions, `const`) — that is the existing convention there.
- Every test method/`it` carries a one-line plain-English comment above it describing what it tests (AGENTS.md).
- Max upload size: **5 MB** (`5 * 1024 * 1024` bytes). Accepted types: anything whose MIME type starts with `image/`.
- Blossom auth event: `kind: 24242`, `content: 'Upload image'`, tags `['t','upload']`, `['x', <sha256 hex>]`, `['expiration', <unix now + 300>]`. Header: `Authorization: Nostr <base64(JSON(signedEvent))>`.
- A successful Blossom upload returns **201**, not 200 — always branch on `response.ok`, never `status === 200`.
- Conventional Commits for every commit. One commit per task, at the end of the task.
- `mvn -q verify` from the repo root must pass before the final commit of the last task. It runs the Vitest suite through `frontend-maven-plugin`.
- Do not start or restart Docker containers. If a service is needed, edit `docker-compose.yml` only.

---

### Task 1: `blossom.js` — hashing, auth event, upload

**Files:**
- Create: `src/main/resources/static/js/blossom.js`
- Test: `src/test/js/blossom.test.js`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `window.BlossomUpload` / CommonJS default export with:
  - `MAX_BYTES: number` — `5242880`
  - `sha256Hex(file): Promise<string>` — lowercase hex SHA-256 of the file bytes. `file` need only expose `arrayBuffer()`.
  - `buildAuthEvent(hashHex: string, expirationSeconds: number): object` — unsigned kind-24242 event.
  - `encodeAuthHeader(signedEvent: object): string` — `'Nostr ' + base64`.
  - `rejectionReason(file): string|null` — human-readable reason the file is unusable, or `null` when acceptable.
  - `upload(file, blossomUrl: string, signer: (unsigned) => signed|Promise<signed>): Promise<{url: string, sha256: string, size: number, type: string}>`

**Note on the test fixtures:** jsdom's `File` does **not** implement `arrayBuffer()`, so the tests pass a plain stub object exposing `type`, `size`, and `arrayBuffer()`. `blossom.js` only ever touches those three members, so a real browser `File` satisfies it.

- [ ] **Step 1: Write the failing tests**

Create `src/test/js/blossom.test.js`:

```js
import { describe, it, expect, beforeEach, vi } from 'vitest';
import Blossom from '../../main/resources/static/js/blossom.js';

// A stand-in for a browser File: jsdom's File has no arrayBuffer(), and those
// three members are all blossom.js ever reads.
function fakeFile(type, size, bytes) {
  const buffer = new Uint8Array(bytes || [1, 2, 3]).buffer;
  return { type: type, size: size, arrayBuffer: () => Promise.resolve(buffer) };
}

function signer(unsigned) {
  return Object.assign({}, unsigned, { id: 'abc', sig: 'def', pubkey: 'cafe' });
}

beforeEach(() => {
  vi.restoreAllMocks();
});

describe('rejectionReason', () => {
  // A JPEG under the cap is acceptable, so no reason is returned.
  it('accepts an image within the size cap', () => {
    expect(Blossom.rejectionReason(fakeFile('image/jpeg', 1024))).toBeNull();
  });

  // A non-image MIME type is refused before any network call is made.
  it('rejects a non-image file', () => {
    expect(Blossom.rejectionReason(fakeFile('application/pdf', 1024)))
      .toBe('Choose an image file.');
  });

  // Anything over 5 MB is refused so the server never has to.
  it('rejects a file over the size cap', () => {
    expect(Blossom.rejectionReason(fakeFile('image/png', Blossom.MAX_BYTES + 1)))
      .toBe('Image must be 5 MB or smaller.');
  });
});

describe('sha256Hex', () => {
  // The digest is the lowercase hex SHA-256 of the file's bytes.
  it('hashes the file bytes', async () => {
    const hex = await Blossom.sha256Hex(fakeFile('image/png', 3, [1, 2, 3]));
    expect(hex).toBe('039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81');
  });
});

describe('buildAuthEvent', () => {
  // The BUD-01 auth event is kind 24242 with the upload verb, the file hash,
  // and an expiry in the future.
  it('builds a kind-24242 upload event carrying the hash and an expiry', () => {
    const now = Math.floor(Date.now() / 1000);
    const ev = Blossom.buildAuthEvent('deadbeef', 300);
    expect(ev.kind).toBe(24242);
    expect(ev.content).toBe('Upload image');
    expect(ev.tags).toContainEqual(['t', 'upload']);
    expect(ev.tags).toContainEqual(['x', 'deadbeef']);
    const expiration = Number(ev.tags.find((t) => t[0] === 'expiration')[1]);
    expect(expiration).toBeGreaterThanOrEqual(now + 300);
  });
});

describe('encodeAuthHeader', () => {
  // The header is the base64 of the signed event behind a `Nostr ` scheme.
  it('base64-encodes the signed event behind the Nostr scheme', () => {
    const signed = { kind: 24242, sig: 'ff' };
    const header = Blossom.encodeAuthHeader(signed);
    expect(header.startsWith('Nostr ')).toBe(true);
    expect(JSON.parse(atob(header.slice('Nostr '.length)))).toEqual(signed);
  });
});

describe('upload', () => {
  // A successful upload PUTs the raw file to {blossomUrl}/upload with the auth
  // header and resolves with the blob descriptor the server returned.
  it('PUTs the file and resolves with the blob descriptor', async () => {
    const descriptor = { url: 'http://blossom.test/abc.png', sha256: 'abc', size: 3, type: 'image/png' };
    global.fetch = vi.fn(() => Promise.resolve({
      ok: true,
      status: 201,
      headers: { get: () => null },
      json: () => Promise.resolve(descriptor),
    }));

    const file = fakeFile('image/png', 3);
    const result = await Blossom.upload(file, 'http://blossom.test', signer);

    expect(result).toEqual(descriptor);
    const [url, init] = global.fetch.mock.calls[0];
    expect(url).toBe('http://blossom.test/upload');
    expect(init.method).toBe('PUT');
    expect(init.body).toBe(file);
    expect(init.headers.Authorization.startsWith('Nostr ')).toBe(true);
  });

  // A trailing slash on the configured URL must not produce a double slash.
  it('normalises a trailing slash on the blossom URL', async () => {
    global.fetch = vi.fn(() => Promise.resolve({
      ok: true, status: 201, headers: { get: () => null }, json: () => Promise.resolve({}),
    }));
    await Blossom.upload(fakeFile('image/png', 3), 'http://blossom.test/', signer);
    expect(global.fetch.mock.calls[0][0]).toBe('http://blossom.test/upload');
  });

  // A rejection is surfaced with the server's X-Reason text so the user learns why.
  it('rejects with the X-Reason header on a non-2xx response', async () => {
    global.fetch = vi.fn(() => Promise.resolve({
      ok: false,
      status: 401,
      headers: { get: (name) => (name === 'X-Reason' ? 'Missing auth event' : null) },
      json: () => Promise.resolve({}),
    }));
    await expect(Blossom.upload(fakeFile('image/png', 3), 'http://blossom.test', signer))
      .rejects.toThrow('Upload rejected: Missing auth event');
  });

  // Without an X-Reason header the status code stands in as the detail.
  it('falls back to the status code when X-Reason is absent', async () => {
    global.fetch = vi.fn(() => Promise.resolve({
      ok: false, status: 413, headers: { get: () => null }, json: () => Promise.resolve({}),
    }));
    await expect(Blossom.upload(fakeFile('image/png', 3), 'http://blossom.test', signer))
      .rejects.toThrow('Upload rejected: HTTP 413');
  });

  // An unusable file is refused locally, so no request reaches the server.
  it('rejects an oversized file without calling fetch', async () => {
    global.fetch = vi.fn();
    await expect(Blossom.upload(fakeFile('image/png', Blossom.MAX_BYTES + 1), 'http://blossom.test', signer))
      .rejects.toThrow('Image must be 5 MB or smaller.');
    expect(global.fetch).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd bottin-client-ui && npx vitest run src/test/js/blossom.test.js`
Expected: FAIL — `Failed to resolve import ".../static/js/blossom.js"`.

- [ ] **Step 3: Write the implementation**

Create `src/main/resources/static/js/blossom.js`:

```js
(function (global) {
    var MAX_BYTES = 5 * 1024 * 1024;
    var AUTH_TTL_SECONDS = 300;

    function bytesToHex(bytes) {
        var hex = '';
        for (var i = 0; i < bytes.length; i++) {
            hex += bytes[i].toString(16).padStart(2, '0');
        }
        return hex;
    }

    // Blossom addresses a blob by the SHA-256 of its bytes, and the auth event
    // is bound to that same hash so a captured header cannot be reused.
    function sha256Hex(file) {
        return file.arrayBuffer()
            .then(function (buffer) { return crypto.subtle.digest('SHA-256', buffer); })
            .then(function (digest) { return bytesToHex(new Uint8Array(digest)); });
    }

    function buildAuthEvent(hashHex, expirationSeconds) {
        var now = Math.floor(Date.now() / 1000);
        return {
            kind: 24242,
            created_at: now,
            content: 'Upload image',
            tags: [
                ['t', 'upload'],
                ['x', hashHex],
                ['expiration', String(now + expirationSeconds)]
            ]
        };
    }

    function encodeAuthHeader(signedEvent) {
        return 'Nostr ' + btoa(JSON.stringify(signedEvent));
    }

    // Returns why the file cannot be uploaded, or null when it is acceptable.
    // Validation lives here so both call sites share one rule set.
    function rejectionReason(file) {
        if (!file || (file.type || '').indexOf('image/') !== 0) return 'Choose an image file.';
        if (file.size > MAX_BYTES) return 'Image must be 5 MB or smaller.';
        return null;
    }

    // Uploads to a BUD-02 `PUT /upload` endpoint, authenticated by a kind-24242
    // event the caller signs, and resolves with the blob descriptor.
    // `signer` takes the unsigned event and returns the signed one, keeping this
    // module free of any dependency on how key material is held.
    function upload(file, blossomUrl, signer) {
        var reason = rejectionReason(file);
        if (reason) return Promise.reject(new Error(reason));
        return sha256Hex(file)
            .then(function (hashHex) { return signer(buildAuthEvent(hashHex, AUTH_TTL_SECONDS)); })
            .then(function (signedEvent) {
                return fetch(blossomUrl.replace(/\/+$/, '') + '/upload', {
                    method: 'PUT',
                    headers: { 'Authorization': encodeAuthHeader(signedEvent) },
                    body: file
                });
            })
            .then(function (response) {
                if (!response.ok) {
                    var detail = response.headers.get('X-Reason') || ('HTTP ' + response.status);
                    throw new Error('Upload rejected: ' + detail);
                }
                return response.json();
            });
    }

    var api = {
        MAX_BYTES: MAX_BYTES,
        sha256Hex: sha256Hex,
        buildAuthEvent: buildAuthEvent,
        encodeAuthHeader: encodeAuthHeader,
        rejectionReason: rejectionReason,
        upload: upload
    };

    global.BlossomUpload = api;
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
})(typeof window !== 'undefined' ? window : globalThis);
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd bottin-client-ui && npx vitest run src/test/js/blossom.test.js`
Expected: PASS — 10 tests.

- [ ] **Step 5: Commit**

```bash
git add bottin-client-ui/src/main/resources/static/js/blossom.js bottin-client-ui/src/test/js/blossom.test.js
git commit -m "feat(client-ui): add blossom upload module for BUD-01/BUD-02"
```

---

### Task 2: Expose the Blossom URL to the templates and widen the CSP

**Files:**
- Modify: `src/main/java/xyz/tcheeric/bottin/client/config/ClientProperties.java`
- Modify: `src/main/java/xyz/tcheeric/bottin/client/controller/ProfileController.java`
- Modify: `src/main/java/xyz/tcheeric/bottin/client/controller/OnboardingController.java`
- Modify: `src/main/resources/templates/layout.html:6`
- Test: `src/test/java/xyz/tcheeric/bottin/client/controller/ProfileControllerTest.java`
- Test: `src/test/java/xyz/tcheeric/bottin/client/controller/OnboardingControllerTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: model attribute `blossomUrl` (a `String`) on `GET /profile`, `GET /onboarding/step/profile`, and `POST /onboarding/step-method`. Tasks 4 and 5 render it into a `<span id="blossom-url" hidden>`.

**Background:** `application.yml` already declares `bottin.client.blossom-url: ${BOTTIN_BLOSSOM_URL:https://localhost:8888}`, but nothing binds it, so it is inert today. `ClientProperties` is the existing typed home for the `bottin.client` prefix.

- [ ] **Step 1: Write the failing tests**

In `src/test/java/xyz/tcheeric/bottin/client/controller/ProfileControllerTest.java`, replace the `@WebMvcTest` annotation line with the three-annotation block and add one test method:

```java
@WebMvcTest(ProfileController.class)
@org.springframework.context.annotation.Import(xyz.tcheeric.bottin.client.config.ClientProperties.class)
@org.springframework.test.context.TestPropertySource(properties = "bottin.client.blossom-url=http://blossom.test:8888")
class ProfileControllerTest {
```

```java
    /**
     * The profile page uploads images straight to the Blossom server, so the
     * configured URL has to reach the template.
     */
    @Test
    void shouldExposeBlossomUrlToTheProfilePage() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("blossomUrl", "http://blossom.test:8888"));
    }
```

In `src/test/java/xyz/tcheeric/bottin/client/controller/OnboardingControllerTest.java`, add the same two annotations under the existing `@WebMvcTest(OnboardingController.class)` line:

```java
@org.springframework.context.annotation.Import(xyz.tcheeric.bottin.client.config.ClientProperties.class)
@org.springframework.test.context.TestPropertySource(properties = "bottin.client.blossom-url=http://blossom.test:8888")
```

and add two test methods:

```java
    /**
     * The onboarding profile step uploads avatars before any account exists, so
     * it needs the Blossom URL on the model just like the profile page.
     */
    @Test
    void shouldExposeBlossomUrlOnTheProfileStep() throws Exception {
        mockMvc.perform(get("/onboarding/step/profile"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("blossomUrl", "http://blossom.test:8888"));
    }

    /**
     * The profile step is also reached by posting the method step, which renders
     * the same template and therefore needs the same attribute.
     */
    @Test
    void shouldExposeBlossomUrlWhenPostingTheMethodStep() throws Exception {
        mockMvc.perform(post("/onboarding/step-method").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("blossomUrl", "http://blossom.test:8888"));
    }
```

`OnboardingControllerTest` already imports `csrf`, `post`, `get`, and `MockMvcResultMatchers.*`, so those two methods need no new imports.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd bottin-client-ui && mvn -q -Dtest='ProfileControllerTest,OnboardingControllerTest' test`
Expected: FAIL — `No ModelAndView found` / `Model attribute 'blossomUrl' does not exist`.

- [ ] **Step 3: Write the implementation**

`ClientProperties.java` — add the field below `defaultRelays`:

```java
    private String blossomUrl;
```

and update the class Javadoc's first sentence to mention both settings:

```java
/**
 * Client-side configuration bound to the {@code bottin.client} prefix: the
 * default relay set, provided by {@code BOTTIN_DEFAULT_RELAYS} as a
 * comma-separated list of {@code wss://} URLs, and the Blossom media server
 * the browser uploads profile images to, provided by {@code BOTTIN_BLOSSOM_URL}.
 */
```

`ProfileController.java` — inject the properties and add the attribute to both handlers:

```java
package xyz.tcheeric.bottin.client.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import xyz.tcheeric.bottin.client.config.ClientProperties;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ClientProperties clientProperties;
```

Then in `ownProfile(Model model)` and in `userProfile(String pubkey, Model model)`, immediately before the `return "layout";`, add:

```java
        model.addAttribute("blossomUrl", clientProperties.getBlossomUrl());
```

`OnboardingController.java` — add the same constructor injection (`@RequiredArgsConstructor` on the class, a `private final ClientProperties clientProperties;` field, and the `lombok.RequiredArgsConstructor` + `xyz.tcheeric.bottin.client.config.ClientProperties` imports), keeping the existing `@Value` domain field untouched. Then add the attribute in the two places that render `onboarding/step-profile`:

In `postStepMethod`, after the existing `model.addAttribute("bottinDomain", bottinDomain);`:

```java
        model.addAttribute("blossomUrl", clientProperties.getBlossomUrl());
```

In `step(String step, Model model)`, inside the existing `if ("profile".equals(step))` block that adds `bottinDomain`, add the same line.

`layout.html:6` — the object-URL preview is a `blob:` URL and a local Blossom server serves images over plain HTTP, so `img-src` needs widening alongside `connect-src`. Replace the whole `content` attribute value with:

```
default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn.jsdelivr.net; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https: http://localhost:* http://127.0.0.1:*; connect-src 'self' wss: https: ws://localhost:* ws://127.0.0.1:* http://localhost:* http://127.0.0.1:*; font-src 'self' data:;
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd bottin-client-ui && mvn -q -Dtest='ProfileControllerTest,OnboardingControllerTest' test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add bottin-client-ui/src/main/java/xyz/tcheeric/bottin/client/config/ClientProperties.java \
        bottin-client-ui/src/main/java/xyz/tcheeric/bottin/client/controller/ProfileController.java \
        bottin-client-ui/src/main/java/xyz/tcheeric/bottin/client/controller/OnboardingController.java \
        bottin-client-ui/src/main/resources/templates/layout.html \
        bottin-client-ui/src/test/java/xyz/tcheeric/bottin/client/controller/ProfileControllerTest.java \
        bottin-client-ui/src/test/java/xyz/tcheeric/bottin/client/controller/OnboardingControllerTest.java
git commit -m "feat(client-ui): bind blossom URL and expose it to the profile templates"
```

---

### Task 3: `profile-image.js` — the shared file-field behaviour

**Files:**
- Create: `src/main/resources/static/js/profile-image.js`
- Test: `src/test/js/profile-image.test.js`

**Interfaces:**
- Consumes: `BlossomUpload.rejectionReason` and `BlossomUpload.upload` from Task 1; `APP.showToast(message, type)` from `app.js`.
- Produces: `window.ProfileImage` / CommonJS default export with a single function:

```
bind({
  fileInputId: string,     // id of the <input type="file">
  previewId: string,       // id of the <img> that shows the image
  errorId: string|null,    // id of the .form-error div, or null
  blossomUrl: string,
  resolveSigner: () => Promise<(unsignedEvent) => signedEvent>,
  onUploaded: (url: string) => void
}): void
```

`bind` returns nothing and does nothing when `fileInputId` names no element, so both pages can call it unconditionally.

**Behaviour contract:**
1. On `change`, take `input.files[0]`; do nothing if absent.
2. Ask `BlossomUpload.rejectionReason`. On a reason: write it into the error element, clear `input.value`, stop — no preview change, no request.
3. Otherwise show `URL.createObjectURL(file)` in the preview immediately (and un-hide the preview), remembering the previous `src`.
4. `resolveSigner()` then `BlossomUpload.upload(file, blossomUrl, signer)`.
5. On success: revoke the object URL, point the preview at the returned URL, call `onUploaded(url)`, toast success.
6. On failure: revoke the object URL, restore the previous `src`, clear `input.value`, and toast — except when the error carries `err.cancelled === true`, which is the unlock modal being dismissed and is a deliberate no-op.

- [ ] **Step 1: Write the failing tests**

Create `src/test/js/profile-image.test.js`:

```js
import { describe, it, expect, beforeEach, vi } from 'vitest';
import '../../main/resources/static/js/app.js';
import '../../main/resources/static/js/blossom.js';
import ProfileImage from '../../main/resources/static/js/profile-image.js';

const UPLOADED = 'http://blossom.test/uploaded.png';

function fakeFile(type, size) {
  return { type: type, size: size, arrayBuffer: () => Promise.resolve(new Uint8Array([1]).buffer) };
}

// Renders the three elements a bound field needs and returns them.
function renderField() {
  document.body.innerHTML =
    '<img id="pic-preview" src="/img/default-avatar.svg">' +
    '<input type="file" id="pic-input">' +
    '<div class="form-error hidden" id="pic-error"></div>';
  return {
    input: document.getElementById('pic-input'),
    preview: document.getElementById('pic-preview'),
    error: document.getElementById('pic-error'),
  };
}

// Attaches a file to the input and fires the change event the module listens for.
function selectFile(input, file) {
  Object.defineProperty(input, 'files', { value: [file], configurable: true });
  input.dispatchEvent(new Event('change'));
}

const signer = () => ({ id: 'a', sig: 'b' });

beforeEach(() => {
  vi.restoreAllMocks();
  // jsdom implements neither of these.
  URL.createObjectURL = vi.fn(() => 'blob:preview');
  URL.revokeObjectURL = vi.fn();
});

describe('ProfileImage.bind', () => {
  // A rejected file never reaches the network and is reported under the control.
  it('shows a field error and sends no request for a non-image', async () => {
    const { input, error } = renderField();
    const upload = vi.spyOn(window.BlossomUpload, 'upload');
    ProfileImage.bind({
      fileInputId: 'pic-input', previewId: 'pic-preview', errorId: 'pic-error',
      blossomUrl: 'http://blossom.test',
      resolveSigner: () => Promise.resolve(signer),
      onUploaded: () => {},
    });

    selectFile(input, fakeFile('application/pdf', 10));
    await Promise.resolve();

    expect(error.textContent).toBe('Choose an image file.');
    expect(error.className).toBe('form-error');
    expect(upload).not.toHaveBeenCalled();
  });

  // A successful upload repoints the preview at the stored URL and reports it back.
  it('previews locally, uploads, and hands the stored URL to onUploaded', async () => {
    const { input, preview } = renderField();
    vi.spyOn(window.BlossomUpload, 'upload').mockResolvedValue({ url: UPLOADED });
    const onUploaded = vi.fn();
    ProfileImage.bind({
      fileInputId: 'pic-input', previewId: 'pic-preview', errorId: 'pic-error',
      blossomUrl: 'http://blossom.test',
      resolveSigner: () => Promise.resolve(signer),
      onUploaded: onUploaded,
    });

    selectFile(input, fakeFile('image/png', 10));
    expect(preview.getAttribute('src')).toBe('blob:preview');

    await vi.waitFor(() => expect(onUploaded).toHaveBeenCalledWith(UPLOADED));
    expect(preview.getAttribute('src')).toBe(UPLOADED);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview');
  });

  // A failed upload leaves the user with the image they already had.
  it('restores the previous preview when the upload fails', async () => {
    const { input, preview } = renderField();
    vi.spyOn(window.BlossomUpload, 'upload').mockRejectedValue(new Error('Upload rejected: HTTP 413'));
    const onUploaded = vi.fn();
    const toast = vi.spyOn(window.APP, 'showToast').mockImplementation(() => {});
    ProfileImage.bind({
      fileInputId: 'pic-input', previewId: 'pic-preview', errorId: 'pic-error',
      blossomUrl: 'http://blossom.test',
      resolveSigner: () => Promise.resolve(signer),
      onUploaded: onUploaded,
    });

    selectFile(input, fakeFile('image/png', 10));

    await vi.waitFor(() => expect(toast).toHaveBeenCalled());
    expect(preview.getAttribute('src')).toBe('/img/default-avatar.svg');
    expect(onUploaded).not.toHaveBeenCalled();
    expect(toast).toHaveBeenCalledWith('Upload failed: Upload rejected: HTTP 413', 'error');
  });

  // Dismissing the unlock modal is a deliberate no-op, so it raises no toast.
  it('stays silent when the unlock prompt is cancelled', async () => {
    const { input, preview } = renderField();
    const toast = vi.spyOn(window.APP, 'showToast').mockImplementation(() => {});
    ProfileImage.bind({
      fileInputId: 'pic-input', previewId: 'pic-preview', errorId: 'pic-error',
      blossomUrl: 'http://blossom.test',
      resolveSigner: () => {
        const cancellation = new Error('cancelled');
        cancellation.cancelled = true;
        return Promise.reject(cancellation);
      },
      onUploaded: () => {},
    });

    selectFile(input, fakeFile('image/png', 10));

    await vi.waitFor(() => expect(preview.getAttribute('src')).toBe('/img/default-avatar.svg'));
    expect(toast).not.toHaveBeenCalled();
  });

  // Binding a field the page does not render must not throw.
  it('is a no-op when the file input is absent', () => {
    document.body.innerHTML = '';
    expect(() => ProfileImage.bind({
      fileInputId: 'missing', previewId: 'missing', errorId: null,
      blossomUrl: 'http://blossom.test',
      resolveSigner: () => Promise.resolve(signer),
      onUploaded: () => {},
    })).not.toThrow();
  });
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd bottin-client-ui && npx vitest run src/test/js/profile-image.test.js`
Expected: FAIL — `Failed to resolve import ".../static/js/profile-image.js"`.

- [ ] **Step 3: Write the implementation**

Create `src/main/resources/static/js/profile-image.js`:

```js
(function (global) {
    // Binds one image field: a file input, the img that previews it, and the
    // error slot under it. Both the profile page and the onboarding step use
    // this twice, differing only in how they resolve a signer and where the
    // resulting URL is stored.
    function bind(config) {
        var input = document.getElementById(config.fileInputId);
        if (!input) return;
        var preview = document.getElementById(config.previewId);
        var error = config.errorId ? document.getElementById(config.errorId) : null;

        function showError(message) {
            if (!error) return;
            error.textContent = message;
            error.className = 'form-error';
        }

        function clearError() {
            if (!error) return;
            error.textContent = '';
            error.className = 'form-error hidden';
        }

        function setPreview(src) {
            if (!preview) return;
            preview.src = src;
            preview.classList.remove('hidden');
        }

        input.addEventListener('change', function () {
            var file = input.files && input.files[0];
            if (!file) return;
            clearError();

            var reason = BlossomUpload.rejectionReason(file);
            if (reason) {
                showError(reason);
                input.value = '';
                return;
            }

            var previousSrc = preview ? preview.getAttribute('src') : null;
            var objectUrl = URL.createObjectURL(file);
            setPreview(objectUrl);

            config.resolveSigner()
                .then(function (signer) {
                    return BlossomUpload.upload(file, config.blossomUrl, signer);
                })
                .then(function (blob) {
                    URL.revokeObjectURL(objectUrl);
                    setPreview(blob.url);
                    config.onUploaded(blob.url);
                    APP.showToast('Image uploaded', 'success');
                })
                .catch(function (err) {
                    URL.revokeObjectURL(objectUrl);
                    if (preview && previousSrc) preview.src = previousSrc;
                    input.value = '';
                    // A dismissed unlock prompt is a deliberate no-op, not a failure.
                    // app.js tags the cancellation error with this flag on purpose;
                    // match profile.js rather than comparing the message text.
                    if (err && err.cancelled) return;
                    var message = err && err.message ? err.message : String(err);
                    APP.showToast('Upload failed: ' + message, 'error');
                });
        });
    }

    var api = { bind: bind };

    global.ProfileImage = api;
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
})(typeof window !== 'undefined' ? window : globalThis);
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd bottin-client-ui && npx vitest run src/test/js/profile-image.test.js`
Expected: PASS — 5 tests.

- [ ] **Step 5: Commit**

```bash
git add bottin-client-ui/src/main/resources/static/js/profile-image.js bottin-client-ui/src/test/js/profile-image.test.js
git commit -m "feat(client-ui): add shared profile image field behaviour"
```

---

### Task 4: Wire the profile page to the file pickers

**Files:**
- Modify: `src/main/resources/templates/profile-edit.html:22-32`, `:56-58`
- Modify: `src/main/resources/java/.../controller/ProfileController.java` (see Step 3a)
- Modify: `src/main/resources/static/js/profile.js`
- Test: `src/test/java/xyz/tcheeric/bottin/client/controller/ProfileControllerTest.java`

**Interfaces:**
- Consumes: `ProfileImage.bind(...)` (Task 3), model attribute `blossomUrl` (Task 2), and the existing `APP.ensureUnlocked(userId)`, `APP.saveIdentity(identity)`, `APP.safeImageUrl(value)`, `NostrCrypto.signEvent(unsigned, hexKey)`.
- Produces: nothing consumed by later tasks.

**Note — plan correction (2026-07-27):** the profile page was split into a
read-only view (`profile.html`) and an edit form (`profile-edit.html`) by the
client-UI redesign, after this plan's spec was written. The avatar and banner
URL text inputs live in **`profile-edit.html`**, served by
`ProfileController.editOwnProfile()` at **`/profile/edit`** — not in
`profile.html` at `/profile`. Task 2 followed the stale plan and put the
`blossomUrl` model attribute on `ownProfile()` and `userProfile()`, which
render the read-only view and never upload. Step 3a below moves it to the
handler that actually needs it.

**Note:** `ProfileControllerTest.shouldRenderProfileEditFormFields` already asserts the page contains `id="profile-picture"`. Keep that id on the new file input so the assertion stays meaningful.

**Note:** `profile-edit.html` has **no** image preview elements today, and
`profile.js` has no preview logic. Both preview `<img>` elements are created
by this task.

- [ ] **Step 1: Write the failing test**

Add to `ProfileControllerTest`:

```java
    /**
     * Avatar and banner are chosen from the device now, so the page must render
     * file inputs and the Blossom URL the uploader reads, not URL text boxes.
     */
    @Test
    void shouldRenderImageFilePickersAndBlossomUrl() throws Exception {
        mockMvc.perform(get("/profile/edit"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("blossomUrl", "http://blossom.test:8888"))
                .andExpect(content().string(containsString("id=\"profile-picture\" type=\"file\"")))
                .andExpect(content().string(containsString("id=\"profile-banner\" type=\"file\"")))
                .andExpect(content().string(containsString("id=\"profile-preview-avatar\"")))
                .andExpect(content().string(containsString("id=\"profile-preview-banner\"")))
                .andExpect(content().string(containsString("id=\"profile-picture-remove\"")))
                .andExpect(content().string(containsString("id=\"profile-banner-remove\"")))
                .andExpect(content().string(containsString("id=\"blossom-url\"")));
    }
```

`model()` is already statically imported in this test class by Task 2's
additions; if it is not, add
`import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd bottin-client-ui && mvn -q -Dtest='ProfileControllerTest#shouldRenderImageFilePickersAndBlossomUrl' test`
Expected: FAIL — no `blossomUrl` model attribute on `/profile/edit`, and the response body does not contain `id="profile-picture" type="file"`.

- [ ] **Step 3a: Move the `blossomUrl` model attribute to the edit handler**

`ProfileController` — the upload happens only on the edit form, so add the
attribute to `editOwnProfile` and drop it from the two handlers that render the
read-only `profile` view:

```java
    @GetMapping
    public String ownProfile(Model model) {
        model.addAttribute("title", "My Profile");
        model.addAttribute("content", "profile");
        return "layout";
    }

    @GetMapping("/edit")
    public String editOwnProfile(Model model) {
        model.addAttribute("title", "Edit Profile");
        model.addAttribute("content", "profile-edit");
        model.addAttribute("blossomUrl", clientProperties.getBlossomUrl());
        return "layout";
    }

    @GetMapping("/{pubkey}")
    public String userProfile(@PathVariable String pubkey, Model model) {
        if (pubkey == null || !pubkey.matches("[0-9a-f]{64}")) {
            return "redirect:/profile";
        }
        model.addAttribute("title", "Profile");
        model.addAttribute("content", "profile");
        model.addAttribute("profilePubkey", pubkey);
        return "layout";
    }
```

Task 2 added a test asserting `blossomUrl` on `/profile` and `/profile/{pubkey}`.
Update that test to assert the attribute on `/profile/edit` instead — it was
written against the stale plan, and asserting configuration on a page that never
reads it is a test with no subject.

- [ ] **Step 3b: Write the template and script changes**

`profile-edit.html` — replace lines 22-32 (the two `form-group` blocks for Picture URL and Banner URL) with:

```html
            <div class="form-group">
                <label class="form-label">Avatar</label>
                <img id="profile-preview-avatar" src="/img/default-avatar.svg" alt=""
                     style="width:72px;height:72px;object-fit:cover;border-radius:50%;display:block;margin-bottom:0.5rem;">
                <input id="profile-picture" type="file" accept="image/*" class="form-input">
                <div class="flex-row-gap-sm mt-1">
                    <button type="button" class="btn btn-sm btn-outline" id="profile-picture-remove">Remove</button>
                </div>
                <div class="form-hint">Uploaded to your media server. JPEG, PNG, GIF or WebP, up to 5 MB.</div>
                <div class="form-error hidden" id="profile-error-picture"></div>
            </div>

            <div class="form-group">
                <label class="form-label">Banner</label>
                <img id="profile-preview-banner" class="hidden" alt=""
                     style="width:100%;max-height:120px;object-fit:cover;border-radius:var(--radius);margin-bottom:0.5rem;">
                <input id="profile-banner" type="file" accept="image/*" class="form-input">
                <div class="flex-row-gap-sm mt-1">
                    <button type="button" class="btn btn-sm btn-outline" id="profile-banner-remove">Remove</button>
                </div>
                <div class="form-hint">Shown across the top of your profile. Up to 5 MB.</div>
                <div class="form-error hidden" id="profile-error-banner"></div>
            </div>
```

Then, just before the closing `</div>` of the `th:fragment="content"` block (after line 53's `</div>` that closes the card, i.e. as the last child of the fragment div), add:

```html
        <span id="blossom-url" th:text="${blossomUrl}" hidden></span>
```

And extend the script block at lines 56-58 so `blossom.js` and `profile-image.js` load before `profile.js`:

```html
    <script src="/js/nostr-publish.js" defer></script>
    <script src="/js/nostr-validate.js" defer></script>
    <script src="/js/blossom.js" defer></script>
    <script src="/js/profile-image.js" defer></script>
    <script src="/js/profile.js" defer></script>
```

`profile.js` — four edits:

(a) Delete the two lines that populated the removed text inputs (currently lines 23-24):

```js
    el('profile-picture').value = identity.picture || '';
    el('profile-banner').value = identity.banner || '';
```

(b) After the block that populates the form fields from the identity (the run of
`el(...).value = ...` lines ending with `el('profile-nip05').value = ...`, currently
line 25 after edit (a)), add both previews and the two field bindings. There is no
pre-existing preview code on this page — this is where previews are introduced:

```js
    var pictureUrl = APP.safeImageUrl(identity.picture);
    if (pictureUrl) el('profile-preview-avatar').src = pictureUrl;

    var bannerUrl = APP.safeImageUrl(identity.banner);
    var bannerPreview = el('profile-preview-banner');
    if (bannerUrl) {
        bannerPreview.src = bannerUrl;
        bannerPreview.classList.remove('hidden');
    }

    var blossomUrl = (document.getElementById('blossom-url') || {}).textContent || '';

    // The unlock modal may be needed to sign the upload auth event, so the signer
    // is resolved lazily, once per selected file.
    function resolveSigner() {
        return APP.ensureUnlocked(userId).then(function (hexKey) {
            return function (unsigned) { return NostrCrypto.signEvent(unsigned, hexKey); };
        });
    }

    // An uploaded URL is stored immediately so it survives a reload even if the
    // user never presses Save & Publish.
    function storeImageUrl(field, url) {
        identity[field] = url;
        APP.saveIdentity(identity);
    }

    ProfileImage.bind({
        fileInputId: 'profile-picture',
        previewId: 'profile-preview-avatar',
        errorId: 'profile-error-picture',
        blossomUrl: blossomUrl.trim(),
        resolveSigner: resolveSigner,
        onUploaded: function (url) { storeImageUrl('picture', url); }
    });

    ProfileImage.bind({
        fileInputId: 'profile-banner',
        previewId: 'profile-preview-banner',
        errorId: 'profile-error-banner',
        blossomUrl: blossomUrl.trim(),
        resolveSigner: resolveSigner,
        onUploaded: function (url) { storeImageUrl('banner', url); }
    });

    // Removing clears the stored URL only; the blob stays on the media server,
    // where the user can delete it with their own key.
    function bindRemove(buttonId, field, previewId, defaultSrc) {
        el(buttonId).addEventListener('click', function () {
            storeImageUrl(field, '');
            var preview = el(previewId);
            if (defaultSrc) {
                preview.src = defaultSrc;
            } else {
                preview.removeAttribute('src');
                preview.classList.add('hidden');
            }
            el('profile-' + field).value = '';
        });
    }

    bindRemove('profile-picture-remove', 'picture', 'profile-preview-avatar', '/img/default-avatar.svg');
    bindRemove('profile-banner-remove', 'banner', 'profile-preview-banner', null);
```

(c) In `readFields()`, take the image URLs from the identity rather than from the now-removed text inputs:

```js
            picture: identity.picture || '',
            banner: identity.banner || '',
```

(d) In the save handler, drop the two now-redundant assignments (upload already wrote them):

```js
        identity.picture = fields.picture;
        identity.banner = fields.banner;
```

Leave `NostrValidate.validateProfileFields` untouched: a Blossom URL is an http(s) URL and an unset field is an empty string, both of which it already accepts, so it remains a correct backstop against a stale hand-edited value in localStorage.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd bottin-client-ui && mvn -q -Dtest='ProfileControllerTest' test && npx vitest run`
Expected: PASS for both.

- [ ] **Step 5: Commit**

```bash
git add bottin-client-ui/src/main/resources/templates/profile-edit.html \
        bottin-client-ui/src/main/java/xyz/tcheeric/bottin/client/controller/ProfileController.java \
        bottin-client-ui/src/main/resources/static/js/profile.js \
        bottin-client-ui/src/test/java/xyz/tcheeric/bottin/client/controller/ProfileControllerTest.java
git commit -m "feat(client-ui): upload avatar and banner from the profile page"
```

---

### Task 5: Wire the onboarding profile step

**Files:**
- Modify: `src/main/resources/templates/onboarding/step-profile.html:57-65`, `:87-111`
- Modify: `src/main/resources/templates/onboarding/step-confirm.html` (the `generateAndSaveKey` generate branch)
- Test: `src/test/java/xyz/tcheeric/bottin/client/controller/OnboardingControllerTest.java`

**Interfaces:**
- Consumes: `ProfileImage.bind(...)` (Task 3), model attribute `blossomUrl` (Task 2), and the globals already loaded by `layout.html`: `NostrTools`, `NostrCrypto.nsecToHex`, `NostrCrypto.signEvent`, `APP.showToast`.
- Produces: a new `onboarding-data` key, `generatedNsec`, holding the nsec generated during the profile step for the "generate a new key" path.

**The problem this task solves.** Signing an upload needs a key, but on the "generate a new key" path the keypair is not created until step 4 (`step-confirm.html`, `generateAndSaveKey`). Only the "import" path has a key at step 2, in `onboarding-data.nsec`. So the profile step generates the keypair on demand the first time an image is uploaded, stores it as `onboarding-data.generatedNsec`, and `step-confirm` adopts that key instead of generating a second, different one. Switching method between the two steps stays coherent because the import path never reads `generatedNsec`.

**Form-harvesting constraint.** `saveProfileData()` copies every `[name]` element's `value` into `onboarding-data`, and the restore IIFE writes them back. The file inputs therefore carry **no `name` attribute**; the uploaded URL goes into hidden `<input name="picture">` / `<input name="banner">`, which the existing harvest and restore handle unchanged.

- [ ] **Step 1: Write the failing test**

Add to `OnboardingControllerTest`:

```java
    /**
     * The onboarding profile step now takes images from the device, so it must
     * render file pickers, the hidden fields the uploaded URLs land in, and the
     * Blossom URL the uploader reads.
     */
    @Test
    void shouldRenderImageFilePickersOnTheProfileStep() throws Exception {
        mockMvc.perform(get("/onboarding/step/profile"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"onboarding-picture-file\"")))
                .andExpect(content().string(containsString("id=\"onboarding-banner-file\"")))
                .andExpect(content().string(containsString("name=\"picture\"")))
                .andExpect(content().string(containsString("name=\"banner\"")))
                .andExpect(content().string(containsString("id=\"blossom-url\"")))
                .andExpect(content().string(containsString("http://blossom.test:8888")));
    }
```

The last assertion is the load-bearing one. `th:text="${blossomUrl}"` renders an
empty span when the attribute is absent, so asserting only `id="blossom-url"`
would pass even with no Blossom URL bound — and the uploader would then POST to
an empty base URL at runtime. The class already sets
`bottin.client.blossom-url=http://blossom.test:8888` via `@TestPropertySource`
(line 15), so asserting the value costs nothing and pins the wiring.

`OnboardingController` already binds `blossomUrl` on both routes that render the
profile step — `postStepMethod` and `step(...)` for `"profile"` — from Task 2.
No controller change is needed in this task.

Add `import static org.hamcrest.Matchers.containsString;` and `import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;` if the file does not already have them.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd bottin-client-ui && mvn -q -Dtest='OnboardingControllerTest#shouldRenderImageFilePickersOnTheProfileStep' test`
Expected: FAIL — the body does not contain `id="onboarding-picture-file"`.

- [ ] **Step 3: Write the implementation**

`step-profile.html` — replace lines 57-65 (the Avatar URL and Banner URL form groups) with:

```html
                <div class="form-group">
                    <label class="form-label">Avatar</label>
                    <img id="onboarding-preview-picture" class="hidden avatar-lg" alt=""
                         style="display:block;margin-bottom:0.5rem;">
                    <input id="onboarding-picture-file" type="file" accept="image/*" class="form-input">
                    <input type="hidden" name="picture">
                    <div class="form-hint">Uploaded to your media server. Up to 5 MB.</div>
                    <div class="form-error hidden" id="onboarding-error-picture"></div>
                </div>

                <div class="form-group">
                    <label class="form-label">Banner</label>
                    <img id="onboarding-preview-banner" class="hidden" alt=""
                         style="width:100%;max-height:120px;object-fit:cover;border-radius:var(--radius);margin-bottom:0.5rem;">
                    <input id="onboarding-banner-file" type="file" accept="image/*" class="form-input">
                    <input type="hidden" name="banner">
                    <div class="form-hint">Shown across the top of your profile. Up to 5 MB.</div>
                    <div class="form-error hidden" id="onboarding-error-banner"></div>
                </div>
```

Then add the blossom URL carrier and the two script tags just before the closing `</div>` of the `th:fragment="content"` block (after the card `</div>` on line 85's block):

```html
        <span id="blossom-url" th:text="${blossomUrl}" hidden></span>
    </div>

    <script src="/js/blossom.js"></script>
    <script src="/js/profile-image.js"></script>
```

These must load synchronously (no `defer`) so they are defined before the inline script below runs.

Finally, replace the whole inline `<script>` block (lines 87-111) with:

```html
    <script>
        function saveProfileData() {
            var data = JSON.parse(sessionStorage.getItem('onboarding-data') || '{}');
            document.querySelectorAll('[name]').forEach(function(el) {
                if (el.name !== 'method' && el.name !== 'nsec') {
                    data[el.name] = el.value;
                }
            });
            sessionStorage.setItem('onboarding-data', JSON.stringify(data));
        }

        // Uploads must be signed, but on the "generate a new key" path no key
        // exists until the confirm step. The key is therefore minted here on
        // first use and stashed as generatedNsec, which the confirm step adopts
        // so the identity is built from this very key.
        function resolveOnboardingSigner() {
            var data = JSON.parse(sessionStorage.getItem('onboarding-data') || '{}');
            var nsec;
            if (data.method === 'import') {
                nsec = (data.nsec || '').trim();
                if (!nsec) {
                    return Promise.reject(new Error('Enter your nsec on the previous step first.'));
                }
            } else {
                nsec = data.generatedNsec;
                if (!nsec) {
                    nsec = NostrTools.nip19.nsecEncode(NostrTools.generateSecretKey());
                    data.generatedNsec = nsec;
                    sessionStorage.setItem('onboarding-data', JSON.stringify(data));
                }
            }
            var hexKey = NostrCrypto.nsecToHex(nsec);
            return Promise.resolve(function(unsigned) {
                return NostrCrypto.signEvent(unsigned, hexKey);
            });
        }

        function storeUploadedUrl(field, url) {
            document.querySelector('[name="' + field + '"]').value = url;
            saveProfileData();
        }

        (function() {
            var data = JSON.parse(sessionStorage.getItem('onboarding-data') || '{}');
            var domainEl = document.getElementById('nip05-domain');
            if (domainEl) {
                data.domain = domainEl.textContent.trim();
                sessionStorage.setItem('onboarding-data', JSON.stringify(data));
            }
            document.querySelectorAll('[name]').forEach(function(el) {
                if (el.name !== 'method' && el.name !== 'nsec' && data[el.name]) {
                    el.value = data[el.name];
                }
            });
            ['picture', 'banner'].forEach(function(field) {
                var img = document.getElementById('onboarding-preview-' + field);
                if (img && data[field]) {
                    img.src = data[field];
                    img.classList.remove('hidden');
                }
            });

            var blossomUrl = ((document.getElementById('blossom-url') || {}).textContent || '').trim();
            ['picture', 'banner'].forEach(function(field) {
                ProfileImage.bind({
                    fileInputId: 'onboarding-' + field + '-file',
                    previewId: 'onboarding-preview-' + field,
                    errorId: 'onboarding-error-' + field,
                    blossomUrl: blossomUrl,
                    resolveSigner: resolveOnboardingSigner,
                    onUploaded: function(url) { storeUploadedUrl(field, url); }
                });
            });
        })();
    </script>
```

`step-confirm.html` — in `generateAndSaveKey`, the `else` branch currently reads:

```js
            } else {
                if (typeof NostrTools === 'undefined') {
                    alert('Key generation library not loaded. Please refresh and try again.');
                    return;
                }
                try {
                    var secretKey = NostrTools.generateSecretKey();
                    nsec = NostrTools.nip19.nsecEncode(secretKey);
                } catch(e) {
                    alert('Failed to generate key: ' + e.message);
                    return;
                }
            }
```

Replace it with the version that adopts a key already minted by the profile step, so an uploaded image is owned by the pubkey the identity ends up with:

```js
            } else if (data.generatedNsec) {
                // The profile step already minted this key to sign an image
                // upload; reusing it keeps the uploaded blobs owned by the
                // pubkey this identity is built from.
                nsec = data.generatedNsec;
            } else {
                if (typeof NostrTools === 'undefined') {
                    alert('Key generation library not loaded. Please refresh and try again.');
                    return;
                }
                try {
                    var secretKey = NostrTools.generateSecretKey();
                    nsec = NostrTools.nip19.nsecEncode(secretKey);
                } catch(e) {
                    alert('Failed to generate key: ' + e.message);
                    return;
                }
            }
```

No cleanup step is needed for `generatedNsec`: `generateAndSaveKey` already calls `sessionStorage.removeItem('onboarding-data')` once the encrypted identity is stored.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd bottin-client-ui && mvn -q -Dtest='OnboardingControllerTest' test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add bottin-client-ui/src/main/resources/templates/onboarding/step-profile.html \
        bottin-client-ui/src/main/resources/templates/onboarding/step-confirm.html \
        bottin-client-ui/src/test/java/xyz/tcheeric/bottin/client/controller/OnboardingControllerTest.java
git commit -m "feat(client-ui): upload avatar and banner during onboarding"
```

---

### Task 6: Documentation, version bump, and full verification

**Files:**
- Create: `docs/how-to/upload-profile-images.md`
- Modify: `docs/README.md` (the `## How-To Guides` list, currently lines 17-20)
- Modify: `pom.xml:10` (and any module `pom.xml` that pins a literal `0.4.0` parent version)

**Interfaces:**
- Consumes: everything from Tasks 1-5.
- Produces: nothing.

- [ ] **Step 1: Write the how-to guide**

Create `docs/how-to/upload-profile-images.md`:

```markdown
# Upload a Profile Avatar and Banner

This guide explains how to set your profile avatar and banner from a local file
and how to point the client at the media server that stores them.

## Choose an image

1. Open `/profile/edit` (or reach the profile step during onboarding).
2. Under **Avatar** or **Banner**, choose a file from your device.
3. The image appears immediately as a local preview while it uploads.
4. If your session is locked, the unlock prompt appears: the upload is
   authorised by an event signed with your own key, so the key must be available.
5. On success a toast confirms the upload and the preview switches to the stored
   image. On `/profile/edit`, press **Save & Publish** to publish the new kind-0
   profile event to your write relays.

Images must be an image type and 5 MB or smaller. Larger or non-image files are
refused before anything is sent.

**Remove** clears the image from your profile. The uploaded file stays on the
media server, where you can delete it with your own key.

## Configure the media server

The client uploads to a [Blossom](https://github.com/hzrd149/blossom-server)
server using BUD-01 authorisation and a BUD-02 `PUT /upload` request. Set its
base URL with `BOTTIN_BLOSSOM_URL`:

```bash
BOTTIN_BLOSSOM_URL=https://blossom.example.com
```

The bundled `docker-compose.yml` runs one locally, published on
`${BOTTIN_BLOSSOM_PORT:-8888}`.

The upload goes straight from your browser to that server: no image bytes pass
through Bottin, and every blob is owned by your pubkey, so you can delete it
later with your own key.

## Troubleshoot

| Symptom | Cause |
|---|---|
| "Choose an image file." | The selected file is not an image type. |
| "Image must be 5 MB or smaller." | The file exceeds the size cap. |
| "Upload rejected: …" | The server refused the request; the text is its `X-Reason`. |
| "Upload failed: …" | The server could not be reached. Check `BOTTIN_BLOSSOM_URL`. |
| Nothing happens after the unlock prompt | The prompt was cancelled; the previous image is kept. |
```

- [ ] **Step 2: Link it from the documentation index**

In `docs/README.md`, add to the `## How-To Guides` list:

```markdown
- [Upload a Profile Avatar and Banner](how-to/upload-profile-images.md) - Set profile images from a local file and configure the Blossom media server
```

- [ ] **Step 3: Bump the version**

This adds a backwards-compatible feature, so bump the minor version: `0.4.0` → `0.5.0`.

The version is repeated literally in **13 POM files** — the parent `<version>` at `pom.xml:10`, plus a `<parent><version>` at line 11 of each of the 12 module POMs (`bottin-service`, `bottin-client-ui`, `bottin-admin-ui`, `bottin-persistence`, `bottin-spring-boot-starter`, `bottin-reach`, `bottin-core`, `bottin-web`, `bottin-verification`, `bottin-tests`, `bottin-tests/bottin-it`, `bottin-tests/bottin-e2e`). All 13 must move together or the reactor build breaks.

Run: `grep -rn "0\.4\.0" --include=pom.xml .`
Update every occurrence that refers to this project's own version; leave third-party dependency versions alone. Re-run the grep afterwards and confirm it returns nothing.

- [ ] **Step 4: Run the full build**

Run: `cd /home/eric/IdeaProjects/bottin && mvn -q verify`
Expected: PASS, exit 0, with the Vitest suite green inside the `frontend-maven-plugin` `npm-test` execution.

If it fails, fix the cause before committing — do not commit a red build.

- [ ] **Step 5: Commit**

```bash
git add docs/how-to/upload-profile-images.md docs/README.md pom.xml */pom.xml bottin-tests/*/pom.xml
git commit -m "docs(client-ui): document blossom profile image upload and bump to 0.5.0"
```

- [ ] **Step 6: Update the status board**

Per the repository's tracking rule, create or move the kan card for this feature on the **bottin** board to Done, comment it with the commit ids from Tasks 1-6 and a one-line note, and label it `enhancement`.

---

## Manual verification (after Task 6)

Not a task — the acceptance walkthrough to run once, against the local stack, before calling the feature done. The stack must already be running; do not start containers as part of this plan.

1. `/profile/edit` → choose an avatar → confirm the preview swaps, a success toast appears, and `localStorage['imani.identity.<npub>']` holds a `picture` pointing at the Blossom server.
2. Reload `/profile/edit` → the avatar still renders from the stored URL.
3. **Save & Publish** → the toast reports the publish, and the kind-0 event carries the Blossom `picture` URL.
4. Repeat 1-3 for the banner.
5. Choose a non-image file → the field error appears and no request is made (check the network panel).
6. Onboarding: start a fresh account with **generate a new key**, upload an avatar on the profile step, finish onboarding, then open `/profile` → the avatar is present and the npub matches the one that signed the upload.
```
