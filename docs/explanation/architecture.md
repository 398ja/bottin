# Architecture Overview

This document explains the system architecture of Bottin, a NIP-05 registry service. Understanding this architecture helps when extending or deploying the system.

## Module Structure

Bottin follows a modular architecture with clear separation of concerns:

```
bottin/
├── bottin-core/           # Domain models and interfaces
├── bottin-persistence/    # JPA entities and repositories
├── bottin-service/        # Business logic services
├── bottin-verification/   # Domain verification logic
├── bottin-web/            # REST API controllers
├── bottin-admin-ui/       # Admin dashboard (Thymeleaf)
├── bottin-spring-boot-starter/  # Auto-configuration
└── bottin-tests/          # Test modules
    ├── bottin-it/         # Integration tests
    └── bottin-e2e/        # End-to-end tests with Testcontainers
```

### Module Dependencies

```
bottin-core (no dependencies)
    ↑
bottin-persistence (depends on core)
    ↑
bottin-service (depends on persistence, core)
    ↑
bottin-verification (depends on service, core)
    ↑
├── bottin-web (depends on verification, service, persistence, core)
└── bottin-admin-ui (depends on verification, service, persistence, core)
```

## Deployable Services

The system provides two deployable Spring Boot applications:

### bottin-web (REST API)

The REST API service handles:
- NIP-05 identity resolution (`/.well-known/nostr.json`)
- CRUD operations for NIP-05 records
- Domain management
- External NIP-05 verification

**Package**: `xyz.tcheeric.bottin.web.app.BottinWebApplication`

### bottin-admin (Admin Dashboard)

The Admin Dashboard provides:
- Web-based management interface
- Domain verification workflows
- NIP-05 record management
- Statistics and monitoring

**Package**: `xyz.tcheeric.bottin.admin.app.BottinAdminApplication`

## Data Flow

### NIP-05 Resolution Request

```
Client Request
    ↓
WellKnownController (bottin-web)
    ↓
Nip05RecordService (bottin-service)
    ↓
Nip05RecordRepository (bottin-persistence)
    ↓
PostgreSQL Database
```

### Admin Dashboard Request

```
Browser Request
    ↓
AdminController (bottin-admin-ui)
    ↓
Service Layer (bottin-service)
    ↓
Repository Layer (bottin-persistence)
    ↓
PostgreSQL Database
```

## Database Schema

The persistence layer uses JPA with Flyway migrations:

### Core Entities

- **Domain**: Represents a verified domain for NIP-05 identities
- **Nip05Record**: Individual NIP-05 identity records
- **DomainVerificationLog**: Audit trail for domain verification attempts

### Migrations

Located in `bottin-persistence/src/main/resources/db/migration/`:
- `V1__initial_schema.sql` - Core tables
- `V2__domain_verification_logs.sql` - Verification audit logs

## Security

### REST API (bottin-web)

- HTTP Basic authentication for API endpoints
- Public access for `/.well-known/nostr.json`
- CORS configuration for cross-origin requests

### Admin Dashboard (bottin-admin-ui)

- Form-based authentication
- Session management
- CSRF protection

## Configuration

Both services share common configuration through:
- `application.yml` - Development defaults
- `application-prod.yml` - Production settings
- Environment variables for deployment customization

See [Docker Compose Configuration](../reference/docker-compose-configuration.md) for deployment options.

## Testing Architecture

Bottin uses a layered testing approach with dedicated test modules.

### Test Module Structure

```
bottin-tests/
├── bottin-it/     # Integration tests (mint-specific)
└── bottin-e2e/    # End-to-end tests
    ├── BasicE2ETest        # Tests without external containers
    ├── BaseE2ETest         # Tests with full container infrastructure
    └── TestContainersConfig # Testcontainers configuration
```

### E2E Test Infrastructure

The E2E tests use [Testcontainers](https://testcontainers.org/) to spin up real infrastructure:

- **PostgreSQL** - Database for persistent storage
- **strfry** - Nostr relay fixture for relay-dependent tests (optional)

### Test Profiles

Maven profiles control test execution:

| Profile | Command | Description |
|---------|---------|-------------|
| default | `mvn test` | Unit tests only |
| e2e | `mvn -Pe2e test` | E2E tests with Testcontainers |
| it | `mvn -Pit test` | Integration tests |

See [Running E2E Tests](../how-to/running-e2e-tests.md) for detailed instructions.
