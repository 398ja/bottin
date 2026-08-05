# Running E2E Tests

This guide shows you how to run the end-to-end tests for Bottin. These tests verify the full system behavior using real containers managed by Testcontainers.

## Prerequisites

- Java 21
- Maven 3.8+
- Docker Engine 20.10+ (running)

## Run All E2E Tests

Execute all E2E tests with the `e2e` Maven profile:

```bash
mvn -Pe2e -pl bottin-tests/bottin-e2e test
```

This starts:
- PostgreSQL container for database
- The Spring Boot test application

## Run Specific Test Classes

Run a single test class:

```bash
# Security tests
mvn -Pe2e -pl bottin-tests/bottin-e2e test -Dtest=SecurityE2ETest

# REST API CRUD tests
mvn -Pe2e -pl bottin-tests/bottin-e2e test -Dtest=RestApiCrudE2ETest

# NIP-05 registration flow
mvn -Pe2e -pl bottin-tests/bottin-e2e test -Dtest=Nip05RegistrationFlowE2ETest

# Error handling tests
mvn -Pe2e -pl bottin-tests/bottin-e2e test -Dtest=ErrorHandlingE2ETest
```

## Run a Single Test Method

```bash
mvn -Pe2e -pl bottin-tests/bottin-e2e test \
  -Dtest=SecurityE2ETest#shouldAllowAccessWithValidCredentials
```

## Test Categories

### Basic E2E Tests

Tests that only require PostgreSQL:

| Test Class | Description |
|------------|-------------|
| `SecurityE2ETest` | Authentication and authorization |
| `RestApiCrudE2ETest` | REST API CRUD operations |
| `Nip05RegistrationFlowE2ETest` | NIP-05 registration workflow |
| `ErrorHandlingE2ETest` | Error responses and validation |

## Container Images

The tests use these container images:

| Container | Image | Purpose |
|-----------|-------|---------|
| PostgreSQL | `postgres:16-alpine` | Database |
| strfry | `dockurr/strfry:latest` | Nostr relay (optional) |

Pull images in advance to speed up test execution:

```bash
docker pull postgres:16-alpine
docker pull dockurr/strfry:latest
```

## Test Configuration

E2E tests use the `e2e` Spring profile with these settings:

| Property | Value | Description |
|----------|-------|-------------|
| Database | PostgreSQL (Testcontainers) | Real database via container |
| Schema | `create-drop` | Fresh schema per test |
| Admin user | `admin` | Test admin username |
| Admin password | `e2e-test-password` | Test admin password |

Configuration file: `bottin-tests/bottin-e2e/src/test/resources/application-e2e.yml`

## Troubleshooting

### Docker Not Running

```
Could not find a valid Docker environment
```

Start Docker daemon:
```bash
# Linux
sudo systemctl start docker

# macOS
open -a Docker
```

### Container Startup Timeout

If containers fail to start within the timeout:

1. Pull images manually first
2. Check Docker resource limits
3. Increase timeout in `TestContainersConfig.java`

### Authentication Failures (401)

If tests fail with 401 errors:

1. Verify `TestSecurityConfig` is loaded
2. Check credentials match `application-e2e.yml`
3. Run with debug logging:
   ```bash
   mvn -Pe2e -pl bottin-tests/bottin-e2e test \
     -Dlogging.level.org.springframework.security=DEBUG
   ```

### Port Conflicts

Testcontainers uses random ports. If you see port binding errors:

1. Check for stale containers: `docker ps -a`
2. Clean up: `docker container prune`

## Verify Full Build

Run the complete verification including E2E tests:

```bash
mvn verify
```

This runs:
1. Unit tests (all modules)
2. Integration tests (if configured)
3. E2E tests are skipped by default in `verify`

To include E2E tests in verify:

```bash
mvn -Pe2e verify
```

## Next Steps

- See [Architecture Overview](../explanation/architecture.md) for test module structure
- See [REST API Reference](../reference/rest-api.md) for API endpoints being tested
