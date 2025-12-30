# Docker Compose Configuration

Technical reference for all Docker Compose services, environment variables, and configuration options.

## Services

### bottin-web

The REST API service for NIP-05 identity resolution and management.

| Property | Value |
|----------|-------|
| Dockerfile | `Dockerfile.web` |
| Default Port | 8080 |
| Health Check | `/actuator/health` |
| Container Name | `bottin-web` |

### bottin-admin

The Admin Dashboard service for managing NIP-05 records and domains.

| Property | Value |
|----------|-------|
| Dockerfile | `Dockerfile.admin` |
| Default Port | 8081 |
| Health Check | `/actuator/health` |
| Container Name | `bottin-admin` |

### postgres

PostgreSQL database for persistent storage.

| Property | Value |
|----------|-------|
| Image | `postgres:16-alpine` |
| Database Name | `bottin` |
| Container Name | `bottin-postgres` |
| Volume | `bottin-postgres-data` |

## Environment Variables

### Database Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `BOTTIN_DATABASE_URL` | JDBC connection URL | `jdbc:postgresql://postgres:5432/bottin` |
| `BOTTIN_DATABASE_USER` | Database username | `bottin` |
| `BOTTIN_DATABASE_PASSWORD` | Database password | `bottin` |

### Admin Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `BOTTIN_ADMIN_USER` | Admin username | `admin` |
| `BOTTIN_ADMIN_PASSWORD` | Admin password | `changeme` |

### Service Ports

| Variable | Description | Default |
|----------|-------------|---------|
| `BOTTIN_PORT` | REST API external port | `8080` |
| `BOTTIN_ADMIN_PORT` | Admin UI external port | `8081` |

### Domain Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `BOTTIN_DEFAULT_DOMAIN` | Default domain for NIP-05 records | (empty) |

### API Documentation

| Variable | Description | Default |
|----------|-------------|---------|
| `BOTTIN_API_DOCS_ENABLED` | Enable OpenAPI documentation | `false` |
| `BOTTIN_SWAGGER_ENABLED` | Enable Swagger UI | `false` |

## Volumes

| Volume Name | Purpose | Mount Point |
|-------------|---------|-------------|
| `bottin-postgres-data` | PostgreSQL data persistence | `/var/lib/postgresql/data` |

## Networks

| Network Name | Driver | Purpose |
|--------------|--------|---------|
| `bottin-network` | bridge | Internal service communication |

## Health Checks

All services include health checks for container orchestration:

### bottin-web / bottin-admin

```yaml
healthcheck:
  test: ["CMD", "wget", "-q", "--spider", "http://localhost:{port}/actuator/health"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 60s
```

### postgres

```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U bottin -d bottin"]
  interval: 10s
  timeout: 5s
  retries: 5
```

## Dockerfiles

### Dockerfile.web

Multi-stage build for the REST API service:
- Builder stage: Eclipse Temurin JDK 21 Alpine
- Runtime stage: Eclipse Temurin JRE 21 Alpine
- Runs as non-root user `bottin` (UID 1000)
- Exposes port 8080

### Dockerfile.admin

Multi-stage build for the Admin Dashboard service:
- Builder stage: Eclipse Temurin JDK 21 Alpine
- Runtime stage: Eclipse Temurin JRE 21 Alpine
- Runs as non-root user `bottin` (UID 1000)
- Exposes port 8081

### Dockerfile (default)

Alias for `Dockerfile.web`, maintained for backward compatibility.

## Dependencies

Service startup order is managed via `depends_on` with health checks:

```
postgres (healthy) -> bottin-web
postgres (healthy) -> bottin-admin
```

Both application services wait for PostgreSQL to be ready before starting.
