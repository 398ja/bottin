# Bottin - NIP-05 Registry Service

A production-ready NIP-05 registry service for Nostr with persistent storage, REST API, admin dashboard, and domain verification.

## Features

- **NIP-05 Compliant** - Serves `.well-known/nostr.json` per the NIP-05 specification
- **REST API** - Full CRUD operations for NIP-05 records and domains
- **Admin Dashboard** - Web-based management UI with Thymeleaf + HTMX + Tailwind CSS
- **Key-based admin sign-in** - Administrators prove control of a Nostr key; no password reaches the deployment
- **Multiple administrators** - The configured super administrator grants access to colleagues, each with their own key
- **Admin-maintained settings** - Media server and relay topology configured at runtime, not baked into the image
- **Client UI** - Self-service onboarding for people registering a NIP-05 identity
- **Domain Verification** - DNS TXT and well-known file verification methods
- **External Verification** - Verify external NIP-05 identifiers with caching
- **Profile Reach** - Follower counts gathered across relays (NIP-02 / NIP-65)
- **Spring Boot Starter** - Embeddable auto-configuration for host applications
- **Production Ready** - PostgreSQL support, Docker deployment, security

## Quick Start

### Docker Compose (Recommended)

1. Clone the repository:
```bash
git clone https://github.com/tcheeric/bottin.git
cd bottin
```

2. Create environment file:
```bash
cat > .env << EOF
BOTTIN_DATABASE_PASSWORD=your-secure-password

# The administrator's PUBLIC key. Sign-in proves control of the matching private
# key, which never reaches the deployment. Without this nobody can sign in to the
# dashboard, so set it before the first start.
BOTTIN_ADMIN_NPUB=npub1...

# Still used by bottin-api for its HTTP Basic credentials. The dashboard does not
# use them — see docs/how-to/configure-admin-access.md.
BOTTIN_ADMIN_USER=admin
BOTTIN_ADMIN_PASSWORD=your-api-password
EOF
```

3. Start the services:
```bash
docker-compose up -d
```

4. Access the services — the API, dashboard, and client are separate services on
   their own ports:
   - REST API / Well-Known: http://localhost:8080/.well-known/nostr.json
   - Admin Dashboard: http://localhost:8081/admin
   - Client UI: http://localhost:8082
   - API Docs: http://localhost:8080/swagger-ui.html (if enabled)

5. Sign in to the dashboard with the nsec matching `BOTTIN_ADMIN_NPUB`, then set
   the media server and relays at `/admin/settings` — a deployment starts
   unconfigured by design. See
   [Configure Deployment Settings](docs/how-to/configure-deployment-settings.md).

> **Serving over plain HTTP?** The session cookie is `Secure`, so a browser will
> not store it and every page returns to the sign-in form. Set `COOKIE_SECURE=false`
> for local stacks only — never where it is reachable beyond your machine.

### Development Setup

1. Prerequisites:
   - Java 21
   - Maven 3.8+

2. Run with H2 database:
```bash
mvn spring-boot:run -pl bottin-api
```

3. Access H2 Console at http://localhost:8080/h2-console

## API

The REST API provides:
- **NIP-05 Resolution**: Public `/.well-known/nostr.json` endpoint
- **Records Management**: CRUD operations for NIP-05 identities
- **Domain Management**: Register and verify domains
- **External Verification**: Verify third-party NIP-05 identifiers

See the [REST API Reference](docs/reference/rest-api.md) for complete endpoint documentation.

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `BOTTIN_PORT` | 8080 | REST API port |
| `BOTTIN_ADMIN_PORT` | 8081 | Admin dashboard port |
| `BOTTIN_CLIENT_PORT` | 8082 | Client UI port |
| `BOTTIN_DATABASE_URL` | H2 memory | JDBC URL |
| `BOTTIN_DATABASE_USER` | bottin | Database username |
| `BOTTIN_DATABASE_PASSWORD` | - | Database password |
| `BOTTIN_ADMIN_NPUB` | - | **The administrator's public key.** Unset means nobody can sign in to the dashboard |
| `BOTTIN_ADMIN_EXTERNAL_URL` | `http://localhost:${BOTTIN_ADMIN_PORT}` | The URL the browser uses to reach the dashboard. A mismatch makes the sign-in proof name a different address than it is sent to, and sign-in fails with no obvious cause |
| `COOKIE_SECURE` | true | Set `false` only for a local stack served over plain HTTP |
| `BOTTIN_ADMIN_USER` | admin | HTTP Basic username **for `bottin-api`**. The dashboard does not use it |
| `BOTTIN_ADMIN_PASSWORD` | - | HTTP Basic password **for `bottin-api`**. Must not be removed from that service |
| `BOTTIN_DOMAIN` | - | Single knob: fans out to `BOTTIN_DEFAULT_DOMAIN` and `BOTTIN_CLIENT_DOMAIN` |
| `BOTTIN_DEFAULT_DOMAIN` | - | Default domain for records |
| `BOTTIN_TRUSTED_PROXIES` | - | Regex of peer addresses allowed to set `X-Forwarded-For`, which the rate limiters key on. Empty trusts no peer |
| `BOTTIN_API_DOCS_ENABLED` | false | Enable API docs in production |

The media server URL, the system and discovery relays, and the public rate limit
are **not** environment variables. They are set at `/admin/settings` and take
effect without a restart — see
[Configure Deployment Settings](docs/how-to/configure-deployment-settings.md).

### Application Properties

```yaml
bottin:
  enabled: true
  admin:
    enabled: true
    npub: ${BOTTIN_ADMIN_NPUB:}   # who may administer this deployment
  verification:
    dns-timeout-seconds: 5
    http-timeout-seconds: 10
    cache:
      ttl-minutes: 5
      max-size: 1000
```

> `bottin.ratelimit.requests-per-minute` was removed in 0.6.0. The public rate
> limit is now part of the admin-maintained settings and applies without a
> restart.

## Domain Verification

### Method 1: DNS TXT Record

1. Register domain via API or admin dashboard
2. Add TXT record to `_nostr-verification.yourdomain.com`:
   ```
   nostr-verification=<your-verification-token>
   ```
3. Trigger verification check (DNS propagation may take up to 24 hours)

### Method 2: Well-Known File

1. Register domain via API or admin dashboard
2. Create file at `https://yourdomain.com/.well-known/nostr-verification.txt`
3. Add the exact verification token as file contents
4. Trigger verification check

## Embedding bottin (Spring Boot starter)

Add the Spring Boot starter to your project to embed the registry:

```xml
<dependency>
    <groupId>xyz.tcheeric</groupId>
    <artifactId>bottin-spring-boot-starter</artifactId>
    <version>0.8.0</version>
</dependency>
```

With the starter on the classpath and `bottin.enabled=true` (the default), it
auto-configures bottin's **services**: database-backed NIP-05 record management,
domain verification, external NIP-05 verification, and profile reach.

The delivery layers are deliberately **not** included. The starter does not scan
`xyz.tcheeric.bottin.api`, and brings neither the REST controllers nor the admin
dashboard: doing so grafted bottin's security filter chain onto every application
that merely put the starter on the classpath, and registered its repositories
twice. An application that wants the REST layer declares it explicitly, as
`BottinApiApplication` does. A starter offers services; it does not decide how
its consumer is secured.

## Project Structure

```
bottin/
├── bottin-core/                 # Domain models, interfaces, exceptions
├── bottin-persistence/          # JPA entities, repositories, Flyway migrations
├── bottin-service/              # Business logic
├── bottin-api/                  # REST controllers, well-known endpoint
├── bottin-verification/         # Domain & external NIP-05 verification
├── bottin-reach/                # Profile reach across relays (NIP-02 / NIP-65)
├── bottin-admin-ui/             # Admin dashboard (Thymeleaf)
├── bottin-client-ui/            # Self-service client (Thymeleaf)
├── bottin-web-assets/           # Browser code shared by both UIs (key handling, NAP handshake)
├── bottin-spring-boot-starter/  # Auto-configuration for embedding
└── bottin-tests/                # Integration and E2E tests
    ├── bottin-it/               # Integration tests
    └── bottin-e2e/              # End-to-end tests with Testcontainers
```

## Building

```bash
# Compile
mvn compile

# Run unit tests
mvn test

# Run all tests and build
mvn verify

# Package
mvn package
```

### Running Integration Tests

E2E and integration tests are skipped by default and require explicit activation:

```bash
# Run E2E tests (requires Docker for Testcontainers)
mvn -Pe2e -DskipE2ETests=false -pl bottin-tests/bottin-e2e test

# Run integration tests
mvn -Pit -pl bottin-tests/bottin-it test
```

### Building Docker Images

Build Docker images using [Jib](https://github.com/GoogleContainerTools/jib):

```bash
# Build to local Docker daemon
mvn jib:dockerBuild -pl bottin-api,bottin-admin-ui

# Deploy to Maven repo and push Docker images to registry
mvn deploy

# Push to registry without deploying Maven artifacts
mvn jib:build -pl bottin-api,bottin-admin-ui
```

Images are published to `docker.398ja.xyz`:
- `docker.398ja.xyz/bottin-api:0.8.0` / `latest`
- `docker.398ja.xyz/bottin-admin-ui:0.8.0` / `latest`

## License

MIT License - see LICENSE file for details.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Run tests: `mvn verify`
4. Submit a pull request

## Documentation

Documentation follows the [Diataxis](https://diataxis.fr/) framework and is
indexed in [docs/README.md](docs/README.md). Start with:

- [Configure Admin Access](docs/how-to/configure-admin-access.md) — who may administer a deployment, and how they sign in
- [Configure Deployment Settings](docs/how-to/configure-deployment-settings.md) — media server and relays
- [Deploy with Docker](docs/how-to/docker-deployment.md)
- [REST API Reference](docs/reference/rest-api.md)

## Related Projects

- [nostr-java](https://github.com/tcheeric/nostr-java) - Nostr protocol library
- [nap-java](https://github.com/tcheeric/nap-java) - Nostr Authentication Protocol, used for admin and client sign-in
