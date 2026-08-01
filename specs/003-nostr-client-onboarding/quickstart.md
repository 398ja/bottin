# Quickstart: Nostr Client Onboarding & Account Management

## Prerequisites

- Java 21+ SDK
- Maven 3.9+
- Docker + Docker Compose (for integration tests)
- Running bottin stack (PostgreSQL, nostrdb with indexed profiles)

## Setup

### 1. Add the new submodule to the parent POM

Add to `pom.xml` `<modules>` section:
```xml
<module>bottin-client-ui</module>
```

### 2. Create the submodule POM

`bottin-client-ui/pom.xml` inherits from the parent and depends on:
- `bottin-core` (shared domain types)
- `nap-spring` (NAP auto-configuration, auth controller, session filter)
- `nap-server` (if custom ACL wiring needed)
- `nap-client` (for integration test proof building)
- `nostr-java-core`, `nostr-java-event` (NIP-98 event construction)
- `spring-boot-starter-web`, `spring-boot-starter-thymeleaf`
- `spring-boot-starter-validation`

### 3. Configure NAP

`bottin-client-ui/src/main/resources/application.yml`:
```yaml
nap:
  enabled: true
  external-base-url: ${BOTTIN_EXTERNAL_URL:http://localhost:8080}
  session-idle-ttl-seconds: 1800
  session-absolute-ttl-seconds: 43200
  protected-path-prefixes: /api/v1/follow,/api/v1/block,/api/v1/backup
  cookie:
    name: client_session
```

### 4. Configure nostrdb path

```yaml
nostrdb:
  path: ${NOSTRDB_PATH:/var/lib/nostrdb}
```

## Running Validation Scenarios

### Scenario 1: Onboarding Flow

1. Start bottin with `mvn -q spring-boot:run -pl bottin-client-ui`
2. Open `http://localhost:8080/onboarding` in a browser
3. Select "Create New Key"
4. Fill profile fields: username, display name, avatar URL
5. Set password (min 8 characters)
6. Review confirmation card
7. Click "Create Account"
8. **Expected**: Identity is generated, profile metadata saved locally, welcome screen displayed

**Validation**:
- Open browser DevTools → Application → Local Storage → verify `imani.identity.*` key exists
- Verify the key contains `publicKeyHex` (64-char hex) and `privateKeyEncrypted` (non-empty base64)

### Scenario 2: NAP Login

1. After onboarding, log out (or open a private window)
2. Navigate to `http://localhost:8080/login`
3. Import the nsec from onboarding (or use a known test key)
4. Click "Sign In"
5. **Expected**: The browser calls `POST /api/v1/auth/init`, user signs the challenge (simulated in dev), receives session cookie

**Validation**:
- Open DevTools → Network → check `POST /api/v1/auth/init` returns `challenge_id` + `challenge`
- Check `POST /api/v1/auth/complete` returns `200` with session cookie
- Check `GET /api/v1/auth/session` returns `{"pubkey": "...", "expires_at": ...}`

### Scenario 3: Profile Search

1. Log in via NAP (Scenario 2)
2. Navigate to search page (`/search`)
3. Type a partial name query (e.g., "will")
4. **Expected**: Results appear within 500ms showing matched profiles with avatar, display name, NIP-05

**Validation**:
- Type a query that matches no profiles → see "No results" message
- Type an empty query → no request fired, empty state shown

### Scenario 4: Follow a User

1. Log in and search for a profile (Scenario 3)
2. Click "Follow" on a search result
3. **Expected**: Button changes to "Following", kind-3 event published to relay

**Validation**:
- Navigate to follow list (`/settings/follows`) → verify the followed user appears
- Click "Unfollow" → verify removal from list

### Scenario 5: Block a User

1. Log in and search for a profile
2. Click "Block" on a search result
3. **Expected**: User is added to block list, removed from follows (if followed), hidden from future searches

**Validation**:
- Navigate to block list (`/settings/blocks`) → verify blocked user appears
- Search for the blocked user's name → verify they are excluded from results
- Attempt to follow the blocked user → verify error
- Click "Unblock" → verify removal from block list and reappearance in search

### Scenario 6: Backup and Restore

1. Log in as a user with follows and blocks
2. Navigate to settings → "Export Backup"
3. Enter a backup passphrase, download the backup file
4. Clear local storage (or open in incognito)
5. Navigate to login → "Restore from Backup"
6. Select the backup file, enter the passphrase
7. **Expected**: Identity, follow list, and block list are restored

**Validation**:
- Check follow list matches pre-backup state
- Check block list matches pre-backup state
- NAP login works with the restored identity

## Running Tests

```bash
# Unit tests
mvn -q test -pl bottin-client-ui

# Integration tests (requires Docker for PostgreSQL + strfry relay)
mvn -q verify -pl bottin-client-ui -Pit

# E2E tests (requires running server)
mvn -q verify -pl bottin-client-ui -Pe2e
```

## Expected Test Coverage

| Area | Unit | Integration | E2E |
|------|------|-------------|-----|
| Key generation | ✓ | | |
| Key import (nsec) | ✓ | | |
| Password encryption | ✓ | | |
| Profile validation | ✓ | | |
| NAP init (server) | ✓ | ✓ | |
| NAP complete (server) | ✓ | ✓ | |
| nostrdb search | ✓ | ✓ | |
| Follow list management | ✓ | ✓ | |
| Block list management | ✓ | ✓ | |
| Kind-3 event publication | | ✓ | |
| Backup export/restore | ✓ | | ✓ |
| Full onboarding wizard | | | ✓ |
| NAP login flow | | ✓ | ✓ |
