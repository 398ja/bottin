# Bottin - NIP-05 Registry Service

A production-ready NIP-05 registry service for Nostr with persistent storage, REST API, admin dashboard, and domain verification.

## Features

- **NIP-05 Compliant** - Serves `.well-known/nostr.json` per the NIP-05 specification
- **REST API** - Full CRUD operations for NIP-05 records and domains
- **Admin Dashboard** - Web-based management UI with Thymeleaf + HTMX + Tailwind CSS
- **Domain Verification** - DNS TXT and well-known file verification methods
- **External Verification** - Verify external NIP-05 identifiers with caching
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
BOTTIN_ADMIN_PASSWORD=your-admin-password
EOF
```

3. Start the services:
```bash
docker-compose up -d
```

4. Access the services:
   - Admin Dashboard: http://localhost:8080/admin
   - API Docs: http://localhost:8080/swagger-ui.html (if enabled)
   - Well-Known: http://localhost:8080/.well-known/nostr.json

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
| `BOTTIN_PORT` | 8080 | Server port |
| `BOTTIN_DATABASE_URL` | H2 memory | JDBC URL |
| `BOTTIN_DATABASE_USER` | bottin | Database username |
| `BOTTIN_DATABASE_PASSWORD` | - | Database password |
| `BOTTIN_ADMIN_USER` | admin | Admin username |
| `BOTTIN_ADMIN_PASSWORD` | - | Admin password |
| `BOTTIN_DEFAULT_DOMAIN` | - | Default domain for records |
| `BOTTIN_API_DOCS_ENABLED` | false | Enable API docs in production |

### Application Properties

```yaml
bottin:
  enabled: true
  admin:
    enabled: true
  verification:
    dns-timeout-seconds: 5
    http-timeout-seconds: 10
    cache:
      ttl-minutes: 5
      max-size: 1000
  ratelimit:
    requests-per-minute: 30
```

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
    <version>0.3.0</version>
</dependency>
```

With the starter on the classpath and `bottin.enabled=true` (the default), it
auto-configures all bottin services and endpoints — database-backed NIP-05
record management, domain verification, external NIP-05 verification, the REST
API, and the admin dashboard.

## Project Structure

```
bottin/
├── bottin-core/                 # Domain models, interfaces, exceptions
├── bottin-persistence/          # JPA entities, repositories
├── bottin-service/              # Business logic
├── bottin-api/                  # REST controllers, well-known endpoint
├── bottin-admin-ui/             # Admin dashboard (Thymeleaf)
├── bottin-verification/         # Domain & external NIP-05 verification
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
- `docker.398ja.xyz/bottin-api:0.1.0` / `latest`
- `docker.398ja.xyz/bottin-admin-ui:0.1.0` / `latest`

## License

MIT License - see LICENSE file for details.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Run tests: `mvn verify`
4. Submit a pull request

## Related Projects

- [nostr-java](https://github.com/tcheeric/nostr-java) - Nostr protocol library
