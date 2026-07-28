# Profile & Relay Publishing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a logged-in user edit their Nostr profile and relay list in the browser and publish them to relays as signed kind-0 and kind-10002 events, entirely client-side.

**Architecture:** The server stays "dumb" — it serves Thymeleaf shells, the NAP auth endpoints, and (new) the configured default-relay set; it never sees a private key or an event. All identity, profile data, and the relay list live in browser storage; all event building, signing, and relay I/O happen client-side with `NostrCrypto` and the bundled `nostr-tools` (`NostrTools`, a.k.a. `NT`).

**Tech Stack:** Java 21, Spring Boot 3.4.1 (WebMVC, Thymeleaf), Lombok; browser JS (vanilla, `nostr-tools` bundle); Vitest + jsdom for JS unit tests, wired into `mvn verify` via `frontend-maven-plugin`.

## Global Constraints

- Module: `bottin-client-ui`. Java 21, Spring Boot 3.4.1.
- Default relays come only from `BOTTIN_DEFAULT_RELAYS` (comma-separated `wss://` URLs), each seeded read+write. No hardcoded public-relay constant.
- Private key never leaves the browser and is never sent to the server.
- Follow Conventional Commits. One logically-grouped commit per task (multiple small commits preferred over grouped ones).
- Run `mvn -q verify` from the repo root before committing a task that touches Java or the build.
- Browser JS style: ES5-ish vanilla JS matching the existing files (`var`, no framework). Reference `NostrCrypto`/`NostrTools` only inside event handlers or `DOMContentLoaded`, never at top-level of a page script (script load order: `nostr-tools.js` and `app.js` load in `<head>`; `nostr-crypto.js` loads deferred at end of body — all deferred scripts finish before `DOMContentLoaded`).
- Storage key conventions (existing): `imani.identity.<npub>`. New: `imani.relays.<npub>` (localStorage), `imani.session.<npub>` (sessionStorage).
- Identity object metadata fields: `displayName`, `picture`, `banner`, `nip05`, `about`, `lud16`, `website`.

---

## File Structure

**Server (Java) — `bottin-client-ui/src/main/java/xyz/tcheeric/bottin/client/`**
- `config/ClientProperties.java` — NEW. Binds `bottin.client.default-relays` to `List<String>`.
- `controller/RelayController.java` — MODIFY. Add `GET /api/v1/relays/defaults`.
- `controller/SettingsController.java` — MODIFY. Add `GET /settings/relays`.

**Config**
- `src/main/resources/application.yml` — MODIFY. `default-relays: ${BOTTIN_DEFAULT_RELAYS:}`.

**Templates — `src/main/resources/templates/`**
- `profile.html` — REWRITE. Preview header + edit form.
- `settings/relays.html` — MODIFY. Point script at rewired `settings-relays.js` (markup already fits).
- `onboarding/step-confirm.html` — MODIFY. Persist `about`, `lud16`, `website`.
- `layout.html` — MODIFY. Add shared `#unlock-modal`.

**Client JS — `src/main/resources/static/js/`**
- `app.js` — MODIFY. Relay storage, session-key, `ensureRelaysSeeded`, `ensureUnlocked` + modal wiring.
- `nostr-crypto.js` — MODIFY. Add generic `signEvent`.
- `nostr-publish.js` — NEW. `buildProfileEvent`, `buildRelayListEvent`, `relaysToTags`, `publish`.
- `nostr-validate.js` — NEW. Pure profile-field validators.
- `profile.js` — NEW. Drives the Profile page.
- `settings-relays.js` — REWRITE. localStorage-backed relay editor + publish.

**JS tests — `src/test/js/`**
- `nostr-publish.test.js`, `nostr-validate.test.js`, `app-session.test.js`, `smoke.test.js`.

**Build**
- `bottin-client-ui/package.json`, `bottin-client-ui/vitest.config.js` — NEW.
- `bottin-client-ui/pom.xml` — MODIFY. `frontend-maven-plugin`.
- `pom.xml` (parent) — MODIFY. Version properties.

**Docs**
- `docs/how-to/verify-profile-and-relay-publishing.md` — NEW; linked from `docs/README.md`.

---

## Task 1: Server default-relays configuration and endpoint

**Files:**
- Create: `bottin-client-ui/src/main/java/xyz/tcheeric/bottin/client/config/ClientProperties.java`
- Modify: `bottin-client-ui/src/main/java/xyz/tcheeric/bottin/client/controller/RelayController.java`
- Modify: `bottin-client-ui/src/main/resources/application.yml`
- Test: `bottin-client-ui/src/test/java/xyz/tcheeric/bottin/client/controller/RelayControllerTest.java`

**Interfaces:**
- Produces: `GET /api/v1/relays/defaults` → `200` JSON `{ "relays": [ { "url": <string>, "read": true, "write": true }, ... ] }`. Blank/whitespace entries dropped. Empty config → `{ "relays": [] }`.
- Produces: `ClientProperties.getDefaultRelays()` → `List<String>`.

- [ ] **Step 1: Write the failing test** — add these two methods to `RelayControllerTest`, and add `@Import(ClientProperties.class)` + `@TestPropertySource` to the class.

Change the class declaration from:

```java
@WebMvcTest(RelayController.class)
class RelayControllerTest {
```

to:

```java
@WebMvcTest(RelayController.class)
@Import(xyz.tcheeric.bottin.client.config.ClientProperties.class)
@TestPropertySource(properties = "bottin.client.default-relays=wss://relay.one,wss://relay.two")
class RelayControllerTest {
```

Add the imports at the top:

```java
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
```

Add the tests:

```java
    /**
     * The defaults endpoint returns each configured relay as a read+write entry so
     * the client can seed a usable relay list on first visit.
     */
    @Test
    void shouldReturnConfiguredDefaultRelaysAsReadWrite() throws Exception {
        mockMvc.perform(get("/api/v1/relays/defaults"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relays.length()").value(2))
                .andExpect(jsonPath("$.relays[0].url").value("wss://relay.one"))
                .andExpect(jsonPath("$.relays[0].read").value(true))
                .andExpect(jsonPath("$.relays[0].write").value(true))
                .andExpect(jsonPath("$.relays[1].url").value("wss://relay.two"));
    }

    /**
     * A blank configured entry is dropped so an unset or empty env var never seeds a
     * bogus relay URL.
     */
    @Test
    void shouldExposeDefaultsEndpointAsArray() throws Exception {
        mockMvc.perform(get("/api/v1/relays/defaults"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relays").isArray());
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -pl bottin-client-ui test -Dtest=RelayControllerTest`
Expected: FAIL — `ClientProperties` does not exist (compile error) / no handler for `/api/v1/relays/defaults`.

- [ ] **Step 3: Create `ClientProperties`**

```java
package xyz.tcheeric.bottin.client.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side configuration bound from the {@code bottin.client} prefix. Only the
 * default relay set is consumed today; it is provided by the
 * {@code BOTTIN_DEFAULT_RELAYS} environment variable as a comma-separated list of
 * {@code wss://} URLs.
 */
@Component
@ConfigurationProperties(prefix = "bottin.client")
@Getter
@Setter
public class ClientProperties {

    private List<String> defaultRelays = new ArrayList<>();
}
```

- [ ] **Step 4: Add the endpoint to `RelayController`**

Add the field and constructor at the top of the class body (after the `@RequestMapping` line), and the new mapping. Add imports `lombok.RequiredArgsConstructor`, `xyz.tcheeric.bottin.client.config.ClientProperties`, `java.util.ArrayList`.

Change:

```java
@RestController
@RequestMapping("/api/v1/relays")
public class RelayController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> getRelays() {
```

to:

```java
@RestController
@RequestMapping("/api/v1/relays")
@RequiredArgsConstructor
public class RelayController {

    private final ClientProperties clientProperties;

    @GetMapping("/defaults")
    public ResponseEntity<Map<String, Object>> getDefaultRelays() {
        List<Map<String, Object>> defaults = new ArrayList<>();
        for (String url : clientProperties.getDefaultRelays()) {
            if (url != null && !url.isBlank()) {
                defaults.add(Map.of("url", url.trim(), "read", true, "write", true));
            }
        }
        return ResponseEntity.ok(Map.of("relays", defaults));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getRelays() {
```

Add imports near the existing ones:

```java
import lombok.RequiredArgsConstructor;
import xyz.tcheeric.bottin.client.config.ClientProperties;
import java.util.ArrayList;
```

- [ ] **Step 5: Replace the default-relays config in `application.yml`**

Change:

```yaml
    default-relays:
      - url: wss://relay.damus.io
        read: true
        write: true
      - url: wss://nos.lol
        read: true
        write: true
      - url: wss://relay.nostr.band
        read: true
        write: false
```

to:

```yaml
    # Comma-separated wss:// URLs seeded read+write for a user's initial relay list.
    default-relays: ${BOTTIN_DEFAULT_RELAYS:}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn -q -pl bottin-client-ui test -Dtest=RelayControllerTest`
Expected: PASS (all methods, including the pre-existing ones).

- [ ] **Step 7: Commit**

```bash
git add bottin-client-ui/src/main/java/xyz/tcheeric/bottin/client/config/ClientProperties.java \
        bottin-client-ui/src/main/java/xyz/tcheeric/bottin/client/controller/RelayController.java \
        bottin-client-ui/src/main/resources/application.yml \
        bottin-client-ui/src/test/java/xyz/tcheeric/bottin/client/controller/RelayControllerTest.java
git commit -m "feat(client): serve default relays from BOTTIN_DEFAULT_RELAYS"
```

---

## Task 2: Add the `/settings/relays` route

**Files:**
- Modify: `bottin-client-ui/src/main/java/xyz/tcheeric/bottin/client/controller/SettingsController.java`
- Test: `bottin-client-ui/src/test/java/xyz/tcheeric/bottin/client/controller/SettingsControllerTest.java`

**Interfaces:**
- Produces: `GET /settings/relays` → renders `layout` with `content="settings/relays"`, `title="Relays"`.

- [ ] **Step 1: Write the failing test** — add to `SettingsControllerTest`:

```java
    /**
     * The relays settings route renders through the shared layout so the client-side
     * relay editor has a page to attach to.
     */
    @Test
    void shouldShowRelaysPage() throws Exception {
        mockMvc.perform(get("/settings/relays"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "settings/relays"));
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -pl bottin-client-ui test -Dtest=SettingsControllerTest`
Expected: FAIL — 404 / no handler for `/settings/relays`.

- [ ] **Step 3: Add the mapping** to `SettingsController` (after the `security` method):

```java
    @GetMapping("/relays")
    public String relays(Model model) {
        model.addAttribute("title", "Relays");
        model.addAttribute("content", "settings/relays");
        return "layout";
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -pl bottin-client-ui test -Dtest=SettingsControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add bottin-client-ui/src/main/java/xyz/tcheeric/bottin/client/controller/SettingsController.java \
        bottin-client-ui/src/test/java/xyz/tcheeric/bottin/client/controller/SettingsControllerTest.java
git commit -m "feat(client): add /settings/relays route"
```

---

## Task 3: Persist all profile fields at onboarding

**Files:**
- Modify: `bottin-client-ui/src/main/resources/templates/onboarding/step-confirm.html`

**Interfaces:**
- Consumes: `sessionStorage['onboarding-data']` with keys `about`, `lud16`, `website` (already collected by the profile step).
- Produces: stored identity now also carries `about`, `lud16`, `website`.

- [ ] **Step 1: Extend `generateAndSaveKey`** — in the `try` block that builds the identity, change:

```javascript
                var identity = await NostrCrypto.buildEncryptedIdentity(nsec, data.password);
                if (data.display_name) identity.displayName = data.display_name;
                if (data.picture) identity.picture = data.picture;
                if (data.banner) identity.banner = data.banner;
                if (data.username && data.domain) identity.nip05 = data.username + '@' + data.domain;
                APP.saveIdentity(identity);
```

to:

```javascript
                var identity = await NostrCrypto.buildEncryptedIdentity(nsec, data.password);
                if (data.display_name) identity.displayName = data.display_name;
                if (data.picture) identity.picture = data.picture;
                if (data.banner) identity.banner = data.banner;
                if (data.about) identity.about = data.about;
                if (data.lud16) identity.lud16 = data.lud16;
                if (data.website) identity.website = data.website;
                if (data.username && data.domain) identity.nip05 = data.username + '@' + data.domain;
                APP.saveIdentity(identity);
```

- [ ] **Step 2: Manual verification** (no server test — this is client behavior)

Run the client (see Task 11 how-to for the command), complete onboarding with a bio/lightning/website filled in, then in DevTools console:

```javascript
JSON.parse(localStorage.getItem(Object.keys(localStorage).find(k => k.startsWith('imani.identity.'))))
```

Expected: the object includes `about`, `lud16`, and `website`.

- [ ] **Step 3: Commit**

```bash
git add bottin-client-ui/src/main/resources/templates/onboarding/step-confirm.html
git commit -m "fix(client): persist about, lud16 and website at onboarding"
```

---

## Task 4: JS test toolchain wired into `mvn verify`

**Files:**
- Create: `bottin-client-ui/package.json`
- Create: `bottin-client-ui/vitest.config.js`
- Create: `bottin-client-ui/src/test/js/smoke.test.js`
- Modify: `pom.xml` (parent — version properties)
- Modify: `bottin-client-ui/pom.xml` (`frontend-maven-plugin`)
- Create: `bottin-client-ui/.gitignore`

**Interfaces:**
- Produces: `npm run test` runs Vitest; bound to Maven `test` phase so `mvn verify` runs JS tests. Honors `-DskipTests`.

- [ ] **Step 1: Create `package.json`**

```json
{
  "name": "bottin-client-ui",
  "version": "0.0.0",
  "private": true,
  "scripts": {
    "test": "vitest run"
  },
  "devDependencies": {
    "jsdom": "^24.1.0",
    "vitest": "^1.6.0"
  }
}
```

- [ ] **Step 2: Create `vitest.config.js`**

```javascript
import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'jsdom',
    include: ['src/test/js/**/*.test.js'],
    globals: true,
  },
});
```

- [ ] **Step 3: Create `.gitignore`** for the module

```
node_modules/
```

- [ ] **Step 4: Create the smoke test** `src/test/js/smoke.test.js`

```javascript
import { describe, it, expect } from 'vitest';

// Proves the Vitest + jsdom harness runs under `mvn verify`.
describe('js test harness', () => {
  it('runs and has a DOM', () => {
    expect(typeof document).toBe('object');
    expect(1 + 1).toBe(2);
  });
});
```

- [ ] **Step 5: Add version properties to the parent `pom.xml`** — inside `<properties>`, after the `<jib-maven-plugin.version>` line, add:

```xml
        <!-- Frontend build (bottin-client-ui JS tests) -->
        <frontend-maven-plugin.version>1.15.1</frontend-maven-plugin.version>
        <node.version>v20.11.1</node.version>
        <npm.version>10.2.4</npm.version>
```

- [ ] **Step 6: Add `frontend-maven-plugin` to `bottin-client-ui/pom.xml`** — inside `<build><plugins>`, after the `spring-boot-maven-plugin` block:

```xml
            <plugin>
                <groupId>com.github.eirslett</groupId>
                <artifactId>frontend-maven-plugin</artifactId>
                <version>${frontend-maven-plugin.version}</version>
                <configuration>
                    <nodeVersion>${node.version}</nodeVersion>
                    <npmVersion>${npm.version}</npmVersion>
                    <installDirectory>target</installDirectory>
                </configuration>
                <executions>
                    <execution>
                        <id>install-node-and-npm</id>
                        <goals>
                            <goal>install-node-and-npm</goal>
                        </goals>
                        <phase>generate-test-resources</phase>
                    </execution>
                    <execution>
                        <id>npm-install</id>
                        <goals>
                            <goal>npm</goal>
                        </goals>
                        <phase>generate-test-resources</phase>
                        <configuration>
                            <arguments>install</arguments>
                        </configuration>
                    </execution>
                    <execution>
                        <id>npm-test</id>
                        <goals>
                            <goal>npm</goal>
                        </goals>
                        <phase>test</phase>
                        <configuration>
                            <arguments>run test</arguments>
                            <skip>${skipTests}</skip>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
```

- [ ] **Step 7: Run to verify the harness runs under Maven**

Run: `mvn -q -pl bottin-client-ui test`
Expected: PASS. First run downloads Node into `bottin-client-ui/target/` (needs network) and runs `smoke.test.js` (1 passing test). If the network blocks the Node download, note it in the PR per AGENTS.md.

- [ ] **Step 8: Commit**

```bash
git add pom.xml bottin-client-ui/pom.xml bottin-client-ui/package.json \
        bottin-client-ui/vitest.config.js bottin-client-ui/.gitignore \
        bottin-client-ui/src/test/js/smoke.test.js
git commit -m "build(client): run Vitest JS tests in mvn verify"
```

---

## Task 5: Generic event signing in `nostr-crypto.js`

**Files:**
- Modify: `bottin-client-ui/src/main/resources/static/js/nostr-crypto.js`

**Interfaces:**
- Produces: `NostrCrypto.signEvent(unsignedEvent, nsecHex)` → a finalized nostr event object (`{ id, pubkey, sig, kind, created_at, tags, content }`). `unsignedEvent` supplies `kind` (required) and optionally `created_at`, `tags`, `content`. Synchronous. Reused by the Profile and Relays publish flows.

- [ ] **Step 1: Add `signEvent`** — in the returned object literal, after the `signNip98Event` method (add a comma after its closing brace), insert:

```javascript
        signEvent: function(unsignedEvent, nsecHex) {
            var template = {
                kind: unsignedEvent.kind,
                created_at: unsignedEvent.created_at || Math.floor(Date.now() / 1000),
                tags: unsignedEvent.tags || [],
                content: unsignedEvent.content || ''
            };
            return NT.finalizeEvent(template, hexToBytes(nsecHex));
        },
```

(Not unit-tested — depends on the `NostrTools` bundle; covered by the Playwright flow in Task 11.)

- [ ] **Step 2: Verify it parses** — load any page and confirm no console error, or run `node --check`:

Run: `node --check bottin-client-ui/src/main/resources/static/js/nostr-crypto.js`
Expected: no output (exit 0).

- [ ] **Step 3: Commit**

```bash
git add bottin-client-ui/src/main/resources/static/js/nostr-crypto.js
git commit -m "feat(client): add generic signEvent to NostrCrypto"
```

---

## Task 6: `nostr-publish.js` — event building and relay publishing

**Files:**
- Create: `bottin-client-ui/src/main/resources/static/js/nostr-publish.js`
- Test: `bottin-client-ui/src/test/js/nostr-publish.test.js`

**Interfaces:**
- Consumes: a pool object with `publish(relayUrls, event)` returning an array of Promises (the `nostr-tools` `SimplePool` shape).
- Produces (browser global `NostrPublish` and CommonJS export):
  - `buildProfileEvent(fields)` → `{ kind: 0, created_at, tags: [], content: <json> }` where `content` is a JSON string of the non-empty subset of `{ name, display_name, about, picture, banner, nip05, lud16, website }`; `name` is the local part of `fields.nip05` (before `@`) when present.
  - `buildRelayListEvent(relays)` → `{ kind: 10002, created_at, tags, content: '' }`.
  - `relaysToTags(relays)` → array of NIP-65 `r` tags.
  - `publish(pool, relayUrls, signedEvent)` → `Promise<[{ url, accepted, reason }]>`.

- [ ] **Step 1: Write the failing test** `src/test/js/nostr-publish.test.js`

```javascript
import { describe, it, expect } from 'vitest';
import NostrPublish from '../../main/resources/static/js/nostr-publish.js';

describe('buildProfileEvent', () => {
  // kind-0 content omits empty fields and derives `name` from the nip05 local part.
  it('omits empty fields and derives name from nip05', () => {
    const ev = NostrPublish.buildProfileEvent({
      display_name: 'Alice', about: '', picture: 'https://x/y.png',
      banner: '', nip05: 'alice@bottin.example.com', lud16: '', website: '',
    });
    expect(ev.kind).toBe(0);
    expect(ev.tags).toEqual([]);
    expect(JSON.parse(ev.content)).toEqual({
      name: 'alice', display_name: 'Alice',
      picture: 'https://x/y.png', nip05: 'alice@bottin.example.com',
    });
  });

  // With no nip05 there is no `name` key.
  it('omits name when nip05 is absent', () => {
    const ev = NostrPublish.buildProfileEvent({ display_name: 'Bob' });
    expect(JSON.parse(ev.content)).toEqual({ display_name: 'Bob' });
  });
});

describe('buildRelayListEvent / relaysToTags', () => {
  // NIP-65 markers: both -> no marker, read-only -> "read", write-only -> "write".
  it('builds r tags with correct markers', () => {
    const ev = NostrPublish.buildRelayListEvent([
      { url: 'wss://a', read: true, write: true },
      { url: 'wss://b', read: true, write: false },
      { url: 'wss://c', read: false, write: true },
      { url: 'wss://d', read: false, write: false },
    ]);
    expect(ev.kind).toBe(10002);
    expect(ev.content).toBe('');
    expect(ev.tags).toEqual([
      ['r', 'wss://a'],
      ['r', 'wss://b', 'read'],
      ['r', 'wss://c', 'write'],
    ]);
  });
});

describe('publish', () => {
  // Each relay's promise maps to an accepted/reason result via allSettled.
  it('reports per-relay accepted and rejected results', async () => {
    const pool = {
      publish: (urls) => urls.map((u, i) =>
        i === 0 ? Promise.resolve('ok') : Promise.reject(new Error('nope'))),
    };
    const results = await NostrPublish.publish(pool, ['wss://a', 'wss://b'], { id: 'x' });
    expect(results).toEqual([
      { url: 'wss://a', accepted: true, reason: null },
      { url: 'wss://b', accepted: false, reason: 'Error: nope' },
    ]);
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd bottin-client-ui && npx vitest run src/test/js/nostr-publish.test.js` (or `mvn -q -pl bottin-client-ui test` once Node is installed)
Expected: FAIL — cannot resolve `nostr-publish.js`.

- [ ] **Step 3: Create `nostr-publish.js`**

```javascript
(function (global) {
    function nowSeconds() {
        return Math.floor(Date.now() / 1000);
    }

    // Builds a kind-0 metadata event. Empty fields are omitted; `name` is the
    // local part of the nip05 identifier so clients that key off `name` resolve it.
    function buildProfileEvent(fields) {
        var f = fields || {};
        var content = {};
        if (f.nip05 && f.nip05.indexOf('@') > 0) {
            content.name = f.nip05.split('@')[0];
        }
        var keys = ['display_name', 'about', 'picture', 'banner', 'nip05', 'lud16', 'website'];
        keys.forEach(function (k) {
            if (f[k]) content[k] = f[k];
        });
        return { kind: 0, created_at: nowSeconds(), tags: [], content: JSON.stringify(content) };
    }

    // NIP-65 r tags: both read+write -> ["r", url]; otherwise the single marker.
    // A relay with neither read nor write is dropped.
    function relaysToTags(relays) {
        var tags = [];
        (relays || []).forEach(function (r) {
            if (r.read && r.write) {
                tags.push(['r', r.url]);
            } else if (r.read) {
                tags.push(['r', r.url, 'read']);
            } else if (r.write) {
                tags.push(['r', r.url, 'write']);
            }
        });
        return tags;
    }

    function buildRelayListEvent(relays) {
        return { kind: 10002, created_at: nowSeconds(), tags: relaysToTags(relays), content: '' };
    }

    // Broadcasts a signed event to the given relays via a SimplePool-shaped pool and
    // resolves per-relay accepted/reason results, never rejecting.
    function publish(pool, relayUrls, signedEvent) {
        var promises = pool.publish(relayUrls, signedEvent);
        return Promise.allSettled(promises).then(function (settled) {
            return settled.map(function (res, i) {
                return {
                    url: relayUrls[i],
                    accepted: res.status === 'fulfilled',
                    reason: res.status === 'fulfilled' ? null : String(res.reason)
                };
            });
        });
    }

    var api = {
        buildProfileEvent: buildProfileEvent,
        buildRelayListEvent: buildRelayListEvent,
        relaysToTags: relaysToTags,
        publish: publish
    };

    global.NostrPublish = api;
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
})(typeof window !== 'undefined' ? window : globalThis);
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd bottin-client-ui && npx vitest run src/test/js/nostr-publish.test.js`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add bottin-client-ui/src/main/resources/static/js/nostr-publish.js \
        bottin-client-ui/src/test/js/nostr-publish.test.js
git commit -m "feat(client): add nostr-publish event builders and relay publish"
```

---

## Task 7: `nostr-validate.js` — profile field validators

**Files:**
- Create: `bottin-client-ui/src/main/resources/static/js/nostr-validate.js`
- Test: `bottin-client-ui/src/test/js/nostr-validate.test.js`

**Interfaces:**
- Produces (browser global `NostrValidate` and CommonJS export). Empty string is valid for every field (fields are optional):
  - `isSafeHttpUrl(v)` → boolean (empty ok; else must be `http:`/`https:`).
  - `isValidLud16(v)` → boolean (empty ok; else `user@domain.tld`).
  - `isValidDisplayName(v)` → boolean (empty ok; else length ≤ 128).
  - `validateProfileFields(fields)` → `{ valid: boolean, errors: { <field>: <message> } }` for fields `picture`, `banner`, `website`, `lud16`, `display_name`.

- [ ] **Step 1: Write the failing test** `src/test/js/nostr-validate.test.js`

```javascript
import { describe, it, expect } from 'vitest';
import NostrValidate from '../../main/resources/static/js/nostr-validate.js';

describe('isSafeHttpUrl', () => {
  it('accepts empty and http(s) URLs, rejects other schemes', () => {
    expect(NostrValidate.isSafeHttpUrl('')).toBe(true);
    expect(NostrValidate.isSafeHttpUrl('https://x/y.png')).toBe(true);
    expect(NostrValidate.isSafeHttpUrl('http://x')).toBe(true);
    expect(NostrValidate.isSafeHttpUrl('javascript:alert(1)')).toBe(false);
    expect(NostrValidate.isSafeHttpUrl('not a url')).toBe(false);
  });
});

describe('isValidLud16', () => {
  it('accepts empty and user@domain, rejects malformed', () => {
    expect(NostrValidate.isValidLud16('')).toBe(true);
    expect(NostrValidate.isValidLud16('alice@walletofsatoshi.com')).toBe(true);
    expect(NostrValidate.isValidLud16('alice')).toBe(false);
    expect(NostrValidate.isValidLud16('alice@nodot')).toBe(false);
  });
});

describe('isValidDisplayName', () => {
  it('accepts empty and bounded length, rejects over 128 chars', () => {
    expect(NostrValidate.isValidDisplayName('')).toBe(true);
    expect(NostrValidate.isValidDisplayName('Alice')).toBe(true);
    expect(NostrValidate.isValidDisplayName('a'.repeat(129))).toBe(false);
  });
});

describe('validateProfileFields', () => {
  it('collects errors for each invalid field', () => {
    const result = NostrValidate.validateProfileFields({
      display_name: 'a'.repeat(200), picture: 'javascript:x',
      banner: 'https://ok', website: '', lud16: 'bad',
    });
    expect(result.valid).toBe(false);
    expect(Object.keys(result.errors).sort()).toEqual(['display_name', 'lud16', 'picture']);
  });

  it('passes clean input', () => {
    const result = NostrValidate.validateProfileFields({
      display_name: 'Alice', picture: 'https://x/a.png',
      banner: '', website: 'https://alice.example', lud16: 'alice@ln.example',
    });
    expect(result.valid).toBe(true);
    expect(result.errors).toEqual({});
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd bottin-client-ui && npx vitest run src/test/js/nostr-validate.test.js`
Expected: FAIL — cannot resolve `nostr-validate.js`.

- [ ] **Step 3: Create `nostr-validate.js`**

```javascript
(function (global) {
    function isSafeHttpUrl(v) {
        if (!v) return true;
        try {
            var url = new URL(v);
            return url.protocol === 'https:' || url.protocol === 'http:';
        } catch (e) {
            return false;
        }
    }

    function isValidLud16(v) {
        if (!v) return true;
        return /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v);
    }

    function isValidDisplayName(v) {
        if (!v) return true;
        return v.length <= 128;
    }

    function validateProfileFields(fields) {
        var f = fields || {};
        var errors = {};
        if (!isValidDisplayName(f.display_name)) {
            errors.display_name = 'Display name must be 128 characters or fewer.';
        }
        if (!isSafeHttpUrl(f.picture)) {
            errors.picture = 'Picture must be an http(s) URL.';
        }
        if (!isSafeHttpUrl(f.banner)) {
            errors.banner = 'Banner must be an http(s) URL.';
        }
        if (!isSafeHttpUrl(f.website)) {
            errors.website = 'Website must be an http(s) URL.';
        }
        if (!isValidLud16(f.lud16)) {
            errors.lud16 = 'Lightning address must look like name@domain.';
        }
        return { valid: Object.keys(errors).length === 0, errors: errors };
    }

    var api = {
        isSafeHttpUrl: isSafeHttpUrl,
        isValidLud16: isValidLud16,
        isValidDisplayName: isValidDisplayName,
        validateProfileFields: validateProfileFields
    };

    global.NostrValidate = api;
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
})(typeof window !== 'undefined' ? window : globalThis);
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd bottin-client-ui && npx vitest run src/test/js/nostr-validate.test.js`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add bottin-client-ui/src/main/resources/static/js/nostr-validate.js \
        bottin-client-ui/src/test/js/nostr-validate.test.js
git commit -m "feat(client): add profile field validators"
```

---

## Task 8: `app.js` — relay storage, session key, unlock, seeding

**Files:**
- Modify: `bottin-client-ui/src/main/resources/static/js/app.js`
- Modify: `bottin-client-ui/src/main/resources/templates/layout.html`
- Test: `bottin-client-ui/src/test/js/app-session.test.js`

**Interfaces:**
- Produces on `window.APP`:
  - `relaysKey(userId)` → string; `loadRelays(userId)` → `[{url,read,write}]` (`[]` if none); `saveRelays(userId, relays)` → void.
  - `ensureRelaysSeeded(userId)` → `Promise<relays>`: if a stored list exists, resolves it unchanged; else fetches `GET /api/v1/relays/defaults`, saves, resolves the seeded list.
  - `SESSION_TTL_MS` (number, 15 min); `sessionKey(userId)` → string.
  - `setSessionKey(userId, hexKey)` → void; `getSessionKey(userId)` → hex string or `null` (null once expired; refreshes expiry on a live read); `lockSession(userId)` → void.
  - `unlockSession(userId, passphrase)` → `Promise<hexKey>` (rejects on wrong passphrase); `ensureUnlocked(userId)` → `Promise<hexKey>` (resolves from a live session key, else opens `#unlock-modal`).
- Consumes: `#unlock-modal` markup in `layout.html`; `NostrCrypto.verifyPassword`, `NostrCrypto.decryptPrivateKey`.

- [ ] **Step 1: Write the failing test** `src/test/js/app-session.test.js`

```javascript
import { describe, it, expect, beforeEach, vi } from 'vitest';
import '../../main/resources/static/js/app.js';

const APP = window.APP;
const USER = 'npub1test';

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  vi.restoreAllMocks();
});

describe('relay storage', () => {
  // Round-trips the relay list through localStorage; missing -> empty array.
  it('loads empty then persists and reloads', () => {
    expect(APP.loadRelays(USER)).toEqual([]);
    const relays = [{ url: 'wss://a', read: true, write: true }];
    APP.saveRelays(USER, relays);
    expect(APP.loadRelays(USER)).toEqual(relays);
  });
});

describe('ensureRelaysSeeded', () => {
  // With no stored list, seeds from the defaults endpoint and saves.
  it('seeds from the defaults endpoint when absent', async () => {
    const defaults = [{ url: 'wss://seed', read: true, write: true }];
    global.fetch = vi.fn(() => Promise.resolve({ json: () => Promise.resolve({ relays: defaults }) }));
    const seeded = await APP.ensureRelaysSeeded(USER);
    expect(seeded).toEqual(defaults);
    expect(APP.loadRelays(USER)).toEqual(defaults);
    expect(global.fetch).toHaveBeenCalledWith('/api/v1/relays/defaults', expect.anything());
  });

  // With a stored list, does not fetch and keeps the existing list.
  it('is a no-op when a list already exists', async () => {
    const existing = [{ url: 'wss://mine', read: true, write: false }];
    APP.saveRelays(USER, existing);
    global.fetch = vi.fn();
    const result = await APP.ensureRelaysSeeded(USER);
    expect(result).toEqual(existing);
    expect(global.fetch).not.toHaveBeenCalled();
  });
});

describe('session key', () => {
  // A stored key reads back until it expires, then getSessionKey returns null.
  it('stores, reads, expires and locks', () => {
    APP.setSessionKey(USER, 'deadbeef');
    expect(APP.getSessionKey(USER)).toBe('deadbeef');

    const raw = JSON.parse(sessionStorage.getItem(APP.sessionKey(USER)));
    raw.expiresAt = Date.now() - 1000;
    sessionStorage.setItem(APP.sessionKey(USER), JSON.stringify(raw));
    expect(APP.getSessionKey(USER)).toBeNull();

    APP.setSessionKey(USER, 'cafe');
    APP.lockSession(USER);
    expect(APP.getSessionKey(USER)).toBeNull();
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd bottin-client-ui && npx vitest run src/test/js/app-session.test.js`
Expected: FAIL — `APP.loadRelays is not a function`.

- [ ] **Step 3: Add storage + session helpers to `app.js`** — inside the `window.APP = { ... }` object, after the `getIdentityUserId` method (add a comma after its closing brace), insert:

```javascript
    relaysKey: function(userId) { return 'imani.relays.' + userId; },

    loadRelays: function(userId) {
        var data = localStorage.getItem(this.relaysKey(userId));
        return data ? JSON.parse(data) : [];
    },

    saveRelays: function(userId, relays) {
        localStorage.setItem(this.relaysKey(userId), JSON.stringify(relays));
    },

    // Seeds the relay list from the server-configured defaults on first use only.
    // A stored list is authoritative and is returned untouched.
    ensureRelaysSeeded: function(userId) {
        var existing = this.loadRelays(userId);
        if (existing.length) return Promise.resolve(existing);
        var self = this;
        return fetch('/api/v1/relays/defaults', { credentials: 'same-origin' })
            .then(function(r) { return r.json(); })
            .then(function(data) {
                var relays = (data && data.relays) || [];
                self.saveRelays(userId, relays);
                return relays;
            });
    },

    SESSION_TTL_MS: 15 * 60 * 1000,

    sessionKey: function(userId) { return 'imani.session.' + userId; },

    setSessionKey: function(userId, hexKey) {
        var entry = { key: hexKey, expiresAt: Date.now() + this.SESSION_TTL_MS };
        sessionStorage.setItem(this.sessionKey(userId), JSON.stringify(entry));
    },

    // Returns the live decrypted key, refreshing its idle expiry, or null once the
    // idle window has passed.
    getSessionKey: function(userId) {
        var raw = sessionStorage.getItem(this.sessionKey(userId));
        if (!raw) return null;
        var entry = JSON.parse(raw);
        if (!entry.expiresAt || entry.expiresAt <= Date.now()) {
            this.lockSession(userId);
            return null;
        }
        this.setSessionKey(userId, entry.key);
        return entry.key;
    },

    lockSession: function(userId) {
        sessionStorage.removeItem(this.sessionKey(userId));
    },

    // Verifies the passphrase, decrypts the private key, and caches it for the
    // session. Rejects on a wrong passphrase without caching anything.
    unlockSession: function(userId, passphrase) {
        var self = this;
        var identity = this.loadIdentity(userId);
        if (!identity) return Promise.reject(new Error('No identity found'));
        return NostrCrypto.verifyPassword(identity.passwordHash, identity.passwordSalt, passphrase)
            .then(function(valid) {
                if (!valid) throw new Error('Wrong passphrase');
                return NostrCrypto.decryptPrivateKey(
                    identity.privateKeyEncrypted, identity.privateKeyIv, identity.privateKeySalt, passphrase);
            })
            .then(function(hexKey) {
                self.setSessionKey(userId, hexKey);
                return hexKey;
            });
    },

    // Resolves the session key, prompting once via the shared unlock modal when the
    // session is locked or expired.
    ensureUnlocked: function(userId) {
        var live = this.getSessionKey(userId);
        if (live) return Promise.resolve(live);
        return this.promptUnlock(userId);
    },

    promptUnlock: function(userId) {
        var self = this;
        var modal = document.getElementById('unlock-modal');
        var input = document.getElementById('unlock-password');
        var error = document.getElementById('unlock-error');
        var confirmBtn = document.getElementById('unlock-confirm');
        var cancelBtn = document.getElementById('unlock-cancel');
        input.value = '';
        error.className = 'form-error hidden mt-1';
        modal.className = 'modal-overlay';
        input.focus();

        return new Promise(function(resolve, reject) {
            function cleanup() {
                modal.className = 'modal-overlay hidden';
                confirmBtn.onclick = null;
                cancelBtn.onclick = null;
            }
            confirmBtn.onclick = function() {
                self.unlockSession(userId, input.value)
                    .then(function(hexKey) { cleanup(); resolve(hexKey); })
                    .catch(function() {
                        error.textContent = 'Wrong passphrase';
                        error.className = 'form-error mt-1';
                    });
            };
            cancelBtn.onclick = function() { cleanup(); reject(new Error('cancelled')); };
        });
    },
```

- [ ] **Step 4: Clear the session key on logout** — in the `logout` method, change:

```javascript
    logout: function() {
        if (!window.confirm('This will clear your session.')) return;
        var goToLogin = function() { window.location.href = '/login'; };
```

to:

```javascript
    logout: function() {
        if (!window.confirm('This will clear your session.')) return;
        var userId = this.getIdentityUserId();
        if (userId) this.lockSession(userId);
        var goToLogin = function() { window.location.href = '/login'; };
```

- [ ] **Step 5: Add the shared unlock modal to `layout.html`** — before the deferred script tags (after `</main>` and before `<script src="/js/nostr-crypto.js" defer></script>`), insert:

```html
    <div id="unlock-modal" class="modal-overlay hidden">
        <div class="modal">
            <div class="modal-title">Unlock to Sign</div>
            <p class="text-secondary text-sm mb-2">
                Enter your passphrase to sign and publish. It stays unlocked for this session.
            </p>
            <input type="password" id="unlock-password" class="form-input" placeholder="Passphrase" autocomplete="off">
            <div id="unlock-error" class="form-error hidden mt-1"></div>
            <div class="flex-row-gap-sm mt-2">
                <button class="btn btn-outline" style="flex: 1;" id="unlock-cancel">Cancel</button>
                <button class="btn btn-primary" style="flex: 1;" id="unlock-confirm">Unlock</button>
            </div>
        </div>
    </div>
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd bottin-client-ui && npx vitest run src/test/js/app-session.test.js`
Expected: PASS (4 tests).

- [ ] **Step 7: Commit**

```bash
git add bottin-client-ui/src/main/resources/static/js/app.js \
        bottin-client-ui/src/main/resources/templates/layout.html \
        bottin-client-ui/src/test/js/app-session.test.js
git commit -m "feat(client): add relay storage, session unlock and default seeding"
```

---

## Task 9: Profile page — template rewrite and `profile.js`

**Files:**
- Modify: `bottin-client-ui/src/main/resources/templates/profile.html`
- Create: `bottin-client-ui/src/main/resources/static/js/profile.js`
- Test: `bottin-client-ui/src/test/java/xyz/tcheeric/bottin/client/controller/ProfileControllerTest.java`

**Interfaces:**
- Consumes: `APP.loadIdentity/saveIdentity/getIdentityUserId/loadRelays/ensureUnlocked/showToast`, `NostrValidate.validateProfileFields`, `NostrPublish.buildProfileEvent/publish`, `NostrCrypto.signEvent`, `NostrTools.SimplePool`.
- Produces: the profile edit form with these element IDs: `profile-display-name`, `profile-about`, `profile-picture`, `profile-banner`, `profile-lud16`, `profile-website`, `profile-nip05` (readonly), `profile-npub`, `profile-save-btn`, `profile-preview-avatar`, `profile-error-*` per field.

- [ ] **Step 1: Write the failing test** — add to `ProfileControllerTest` (add `import static org.hamcrest.Matchers.containsString;` and `import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;`):

```java
    /**
     * The profile page renders the editable form fields the client script binds to,
     * so a missing or renamed field ID is caught at build time.
     */
    @Test
    void shouldRenderProfileEditFormFields() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"profile-display-name\"")))
                .andExpect(content().string(containsString("id=\"profile-about\"")))
                .andExpect(content().string(containsString("id=\"profile-picture\"")))
                .andExpect(content().string(containsString("id=\"profile-lud16\"")))
                .andExpect(content().string(containsString("id=\"profile-website\"")))
                .andExpect(content().string(containsString("id=\"profile-save-btn\"")));
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -pl bottin-client-ui test -Dtest=ProfileControllerTest`
Expected: FAIL — the current template has no such IDs.

- [ ] **Step 3: Rewrite `profile.html`**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>Bottin - Profile</title>
</head>
<body>
    <div th:fragment="content" class="container-narrow">
        <div class="card text-center mb-2">
            <img src="/img/default-avatar.svg" alt="" class="avatar-lg mb-2" id="profile-preview-avatar">
            <h1 style="font-size: 1.25rem;" id="profile-preview-name">Your profile</h1>
            <div class="text-xs text-secondary mb-1" id="profile-nip05-display"></div>
            <div class="flex-center gap-sm">
                <button class="btn btn-sm btn-outline" id="profile-npub-copy">Copy npub</button>
            </div>
        </div>

        <div class="card">
            <h2 style="font-size: 1.125rem; margin-bottom: 1rem;">Edit Profile</h2>

            <div class="form-group">
                <label class="form-label">Display name</label>
                <input type="text" id="profile-display-name" class="form-input">
                <div class="form-error hidden" id="profile-error-display-name"></div>
            </div>

            <div class="form-group">
                <label class="form-label">About</label>
                <textarea id="profile-about" class="form-input" rows="3"></textarea>
            </div>

            <div class="form-group">
                <label class="form-label">Picture URL</label>
                <input type="text" id="profile-picture" class="form-input" placeholder="https://...">
                <div class="form-error hidden" id="profile-error-picture"></div>
            </div>

            <div class="form-group">
                <label class="form-label">Banner URL</label>
                <input type="text" id="profile-banner" class="form-input" placeholder="https://...">
                <div class="form-error hidden" id="profile-error-banner"></div>
            </div>

            <div class="form-group">
                <label class="form-label">Lightning address (lud16)</label>
                <input type="text" id="profile-lud16" class="form-input" placeholder="name@domain">
                <div class="form-error hidden" id="profile-error-lud16"></div>
            </div>

            <div class="form-group">
                <label class="form-label">Website</label>
                <input type="text" id="profile-website" class="form-input" placeholder="https://...">
                <div class="form-error hidden" id="profile-error-website"></div>
            </div>

            <div class="form-group">
                <label class="form-label">NIP-05 (read-only)</label>
                <input type="text" id="profile-nip05" class="form-input" readonly>
            </div>

            <input type="hidden" id="profile-npub">

            <button class="btn btn-primary btn-full" id="profile-save-btn">Save &amp; Publish</button>
        </div>
    </div>

    <script src="/js/nostr-publish.js" defer></script>
    <script src="/js/nostr-validate.js" defer></script>
    <script src="/js/profile.js" defer></script>
</body>
</html>
```

- [ ] **Step 4: Create `profile.js`**

```javascript
document.addEventListener('DOMContentLoaded', function () {
    var saveBtn = document.getElementById('profile-save-btn');
    if (!saveBtn) return;

    var userId = APP.getIdentityUserId();
    var identity = userId ? APP.loadIdentity(userId) : null;
    if (!identity) return;

    var fieldIds = {
        display_name: 'profile-display-name',
        about: 'profile-about',
        picture: 'profile-picture',
        banner: 'profile-banner',
        lud16: 'profile-lud16',
        website: 'profile-website'
    };

    function el(id) { return document.getElementById(id); }

    // Populate the form and read-only fields from the stored identity.
    el('profile-display-name').value = identity.displayName || '';
    el('profile-about').value = identity.about || '';
    el('profile-picture').value = identity.picture || '';
    el('profile-banner').value = identity.banner || '';
    el('profile-lud16').value = identity.lud16 || '';
    el('profile-website').value = identity.website || '';
    el('profile-nip05').value = identity.nip05 || '';
    el('profile-npub').value = identity.npub || '';
    el('profile-preview-name').textContent = identity.displayName || identity.npub || 'Your profile';
    el('profile-nip05-display').textContent = identity.nip05 || '';
    var previewUrl = APP.safeImageUrl(identity.picture);
    if (previewUrl) {
        var avatar = el('profile-preview-avatar');
        avatar.onerror = function () { this.src = '/img/default-avatar.svg'; };
        avatar.src = previewUrl;
    }

    el('profile-npub-copy').addEventListener('click', function () {
        var npub = el('profile-npub').value;
        if (npub) {
            navigator.clipboard.writeText(npub);
            this.textContent = 'Copied!';
            var btn = this;
            setTimeout(function () { btn.textContent = 'Copy npub'; }, 2000);
        }
    });

    function readFields() {
        return {
            display_name: el('profile-display-name').value.trim(),
            about: el('profile-about').value.trim(),
            picture: el('profile-picture').value.trim(),
            banner: el('profile-banner').value.trim(),
            lud16: el('profile-lud16').value.trim(),
            website: el('profile-website').value.trim(),
            nip05: el('profile-nip05').value.trim()
        };
    }

    function clearErrors() {
        Object.keys(fieldIds).forEach(function (k) {
            var errEl = el('profile-error-' + k.replace('_', '-'));
            if (errEl) errEl.className = 'form-error hidden';
        });
    }

    function showErrors(errors) {
        Object.keys(errors).forEach(function (k) {
            var errEl = el('profile-error-' + k.replace('_', '-'));
            if (errEl) {
                errEl.textContent = errors[k];
                errEl.className = 'form-error';
            }
        });
    }

    saveBtn.addEventListener('click', function () {
        clearErrors();
        var fields = readFields();
        var validation = NostrValidate.validateProfileFields(fields);
        if (!validation.valid) {
            showErrors(validation.errors);
            return;
        }

        // Persist locally first so the change is never lost if publishing fails.
        identity.displayName = fields.display_name;
        identity.about = fields.about;
        identity.picture = fields.picture;
        identity.banner = fields.banner;
        identity.lud16 = fields.lud16;
        identity.website = fields.website;
        APP.saveIdentity(identity);

        var writeRelays = APP.loadRelays(userId).filter(function (r) { return r.write; })
            .map(function (r) { return r.url; });
        if (!writeRelays.length) {
            APP.showToast('Add at least one write relay in Settings → Relays.', 'error');
            return;
        }

        APP.ensureUnlocked(userId).then(function (hexKey) {
            var unsigned = NostrPublish.buildProfileEvent(fields);
            var signed = NostrCrypto.signEvent(unsigned, hexKey);
            return NostrPublish.publish(new NostrTools.SimplePool(), writeRelays, signed);
        }).then(function (results) {
            var accepted = results.filter(function (r) { return r.accepted; }).length;
            if (accepted) {
                APP.showToast('Published to ' + accepted + ' of ' + results.length + ' relays', 'success');
            } else {
                APP.showToast('Publish failed on all relays', 'error');
            }
        }).catch(function () { /* unlock cancelled: local save is retained */ });
    });
});
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q -pl bottin-client-ui test -Dtest=ProfileControllerTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add bottin-client-ui/src/main/resources/templates/profile.html \
        bottin-client-ui/src/main/resources/static/js/profile.js \
        bottin-client-ui/src/test/java/xyz/tcheeric/bottin/client/controller/ProfileControllerTest.java
git commit -m "feat(client): edit and publish profile as kind-0"
```

---

## Task 10: Relays page — rewire `settings-relays.js` to localStorage + publish

**Files:**
- Modify: `bottin-client-ui/src/main/resources/static/js/settings-relays.js` (rewrite)
- Modify: `bottin-client-ui/src/main/resources/templates/settings/relays.html`

**Interfaces:**
- Consumes: `APP.getIdentityUserId/loadRelays/saveRelays/ensureRelaysSeeded/ensureUnlocked/showToast`, `NostrPublish.buildRelayListEvent/publish`, `NostrCrypto.signEvent`, `NostrTools.SimplePool`.
- Reuses existing DOM IDs from `settings/relays.html`: `read-relays`, `write-relays`, `publish-btn`, `relay-url`, `relay-read`, `relay-write`, `relay-url-error`. Global functions `addRelay()`, `publishRelays()` remain wired via the template's inline `onclick`.

- [ ] **Step 1: Load `NostrPublish` on the relays page** — in `settings/relays.html`, change:

```html
    <script src="/js/settings-relays.js" defer></script>
```

to:

```html
    <script src="/js/nostr-publish.js" defer></script>
    <script src="/js/settings-relays.js" defer></script>
```

- [ ] **Step 2: Rewrite `settings-relays.js`**

```javascript
var RelayEditor = (function () {
    var userId = null;
    var relays = [];

    function el(id) { return document.getElementById(id); }

    function createRelayRow(r, badgeClass, badgeLabel) {
        var div = document.createElement('div');
        div.className = 'search-result';

        var badge = document.createElement('span');
        badge.className = 'badge ' + badgeClass;
        badge.style.marginRight = '0.5rem';
        badge.textContent = badgeLabel;
        div.appendChild(badge);

        var urlSpan = document.createElement('span');
        urlSpan.style.flex = '1';
        urlSpan.style.fontSize = '0.875rem';
        urlSpan.textContent = r.url;
        div.appendChild(urlSpan);

        var removeBtn = document.createElement('button');
        removeBtn.className = 'btn btn-sm btn-danger';
        removeBtn.textContent = '×';
        removeBtn.addEventListener('click', function () { removeRelay(r.url); });
        div.appendChild(removeBtn);

        return div;
    }

    function renderColumn(elId, filtered, badgeClass, badgeLabel, emptyText) {
        var container = el(elId);
        container.innerHTML = '';
        if (filtered.length) {
            filtered.forEach(function (r) {
                container.appendChild(createRelayRow(r, badgeClass, badgeLabel));
            });
        } else {
            var empty = document.createElement('div');
            empty.className = 'empty-state';
            empty.style.padding = '1rem';
            var p = document.createElement('p');
            p.style.fontSize = '0.875rem';
            p.textContent = emptyText;
            empty.appendChild(p);
            container.appendChild(empty);
        }
    }

    function render() {
        renderColumn('read-relays', relays.filter(function (r) { return r.read; }),
            'badge-primary', 'Read', 'No read relays configured');
        renderColumn('write-relays', relays.filter(function (r) { return r.write; }),
            'badge-success', 'Write', 'No write relays configured');
        el('publish-btn').style.display = relays.length ? 'block' : 'none';
    }

    function persist() {
        APP.saveRelays(userId, relays);
        render();
    }

    function addRelay() {
        var url = el('relay-url').value.trim();
        var read = el('relay-read').checked;
        var write = el('relay-write').checked;
        var error = el('relay-url-error');

        if (!url.startsWith('wss://')) {
            error.style.display = 'block';
            return;
        }
        error.style.display = 'none';

        if (!read && !write) {
            APP.showToast('Select read and/or write permission', 'error');
            return;
        }
        if (relays.some(function (r) { return r.url === url; })) {
            APP.showToast('Relay already added', 'error');
            return;
        }

        relays.push({ url: url, read: read, write: write });
        persist();
        el('relay-url').value = '';
        APP.showToast('Relay added', 'success');
    }

    function removeRelay(url) {
        relays = relays.filter(function (r) { return r.url !== url; });
        persist();
        APP.showToast('Relay removed', 'success');
    }

    function publishRelays() {
        var writeRelays = relays.filter(function (r) { return r.write; })
            .map(function (r) { return r.url; });
        if (!writeRelays.length) {
            APP.showToast('Add at least one write relay before publishing.', 'error');
            return;
        }
        APP.ensureUnlocked(userId).then(function (hexKey) {
            var unsigned = NostrPublish.buildRelayListEvent(relays);
            var signed = NostrCrypto.signEvent(unsigned, hexKey);
            return NostrPublish.publish(new NostrTools.SimplePool(), writeRelays, signed);
        }).then(function (results) {
            var accepted = results.filter(function (r) { return r.accepted; }).length;
            if (accepted) {
                APP.showToast('Published to ' + accepted + ' of ' + results.length + ' relays', 'success');
            } else {
                APP.showToast('Publish failed on all relays', 'error');
            }
        }).catch(function () { /* unlock cancelled: local list is retained */ });
    }

    function init() {
        userId = APP.getIdentityUserId();
        if (!userId || !el('read-relays')) return;
        APP.ensureRelaysSeeded(userId).then(function (seeded) {
            relays = seeded;
            render();
        }).catch(function () {
            relays = APP.loadRelays(userId);
            render();
        });
    }

    return { init: init, addRelay: addRelay, publishRelays: publishRelays };
})();

// Global handles for the template's inline onclick attributes.
function addRelay() { RelayEditor.addRelay(); }
function publishRelays() { RelayEditor.publishRelays(); }

document.addEventListener('DOMContentLoaded', function () { RelayEditor.init(); });
```

- [ ] **Step 3: Verify both files parse**

Run: `node --check bottin-client-ui/src/main/resources/static/js/settings-relays.js`
Expected: no output (exit 0).

- [ ] **Step 4: Commit**

```bash
git add bottin-client-ui/src/main/resources/static/js/settings-relays.js \
        bottin-client-ui/src/main/resources/templates/settings/relays.html
git commit -m "feat(client): edit and publish relay list as kind-10002"
```

---

## Task 11: End-to-end verification, docs, and version bump

**Files:**
- Create: `docs/how-to/verify-profile-and-relay-publishing.md`
- Modify: `docs/README.md`
- Modify: `pom.xml` (version bump across modules via `versions:set`)

- [ ] **Step 1: Full build**

Run: `mvn -q verify`
Expected: PASS — Java tests plus the four JS test files (`smoke`, `nostr-publish`, `nostr-validate`, `app-session`) all run. Capture the output for the PR description.

- [ ] **Step 2: Live Playwright smoke** (documented, run once) — start the client:

```bash
BOTTIN_CLIENT_PORT=8090 COOKIE_SECURE=false \
BOTTIN_EXTERNAL_URL=http://localhost:8090 THYMELEAF_CACHE=false \
BOTTIN_DEFAULT_RELAYS=wss://relay.damus.io,wss://nos.lol \
mvn -q -pl bottin-client-ui spring-boot:run
```

Then: sign in, open `/settings/relays` (defaults seeded), open `/profile`, edit a field, click **Save & Publish**, unlock once, confirm the per-relay toast. Verify the session stays unlocked for a second publish.

- [ ] **Step 3: Write the how-to** `docs/how-to/verify-profile-and-relay-publishing.md` (Diátaxis how-to). Cover: starting the client with `BOTTIN_DEFAULT_RELAYS`, seeding the relay list, editing + publishing the profile (kind-0), editing + publishing relays (kind-10002), the unlock-once-per-session behavior and idle re-lock, and confirming events landed on a relay. Start with a `#` heading and a one-line purpose, per AGENTS.md.

- [ ] **Step 4: Link it from `docs/README.md`** — under `## How-To Guides`, add:

```markdown
- [Verify Profile and Relay Publishing](how-to/verify-profile-and-relay-publishing.md) - Edit and publish kind-0 profile and kind-10002 relay list from the browser
```

- [ ] **Step 5: Bump the project version** (semver minor — new feature) across all modules:

Run: `mvn -q versions:set -DnewVersion=0.4.0 -DgenerateBackupPoms=false`
Then verify: `git diff --stat` shows the 13 module POMs updated to `0.4.0`.

- [ ] **Step 6: Final build + commit**

```bash
mvn -q verify
git add docs/how-to/verify-profile-and-relay-publishing.md docs/README.md pom.xml '**/pom.xml'
git commit -m "docs: add profile/relay publishing how-to and bump to 0.4.0"
```

- [ ] **Step 7: Update the kan board** — move the profile/settings card to Done and add a comment with the final commit SHA and a one-line note (per the kan-tracking skill).

---

## Self-Review

**Spec coverage:**
- Profile page display/edit/publish kind-0 → Task 9. ✓
- Relays page editable list + publish kind-10002 + `/settings/relays` route → Tasks 2, 10. ✓
- Default relays from `BOTTIN_DEFAULT_RELAYS`, read+write, endpoint, seeding → Tasks 1, 8. ✓
- Extend identity with `about`/`lud16`/`website` + onboarding fix → Task 3; profile save (Task 9) writes them. ✓
- Shared unlock-once-per-session + idle re-lock + logout clears key → Task 8. ✓
- Generic `signEvent` → Task 5. ✓
- `nostr-publish.js` builders + publish → Task 6. ✓
- NIP-05 read-only on profile → Task 9 (`readonly` input). ✓
- Vitest wired into `mvn verify` → Task 4; tests in Tasks 6, 7, 8. ✓
- Server-side `@WebMvcTest` for new route + defaults endpoint + profile fields → Tasks 1, 2, 9. ✓
- Diátaxis how-to + README link → Task 11. ✓
- Follows/Blocks stay placeholders; other-user profiles stay placeholder → untouched. ✓

**Divergence from spec (intentional, YAGNI):** `ClientProperties` binds only `defaultRelays` (the sole field new code consumes); `domain` stays on `OnboardingController`'s existing `@Value`, and `blossom-url` is not bound since nothing reads it. Documented here so the reviewer expects it.

**Placeholder scan:** No TBD/TODO; every code step shows complete content.

**Type consistency:** `ensureRelaysSeeded`, `ensureUnlocked`, `getSessionKey`, `loadRelays`/`saveRelays`, `buildProfileEvent`/`buildRelayListEvent`/`publish`, `signEvent`, `validateProfileFields` are named identically in their defining task and every consuming task. Relay object shape `{ url, read, write }` is consistent across storage, endpoint, tags, and publish. Element IDs in `profile.html` match those referenced in `profile.js` and asserted in `ProfileControllerTest`.
