# Docker Compose Configuration

Technical reference for all Docker Compose services, environment variables, and configuration options.

## Services

### bottin-api

The REST API service for NIP-05 identity resolution and management.

| Property | Value |
|----------|-------|
| Dockerfile | `Dockerfile.api` |
| Default Port | 8080 |
| Health Check | `/actuator/health` |
| Container Name | `bottin-api` |

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
| `BOTTIN_ADMIN_NPUB` | Public key of the administrator who may sign in to the dashboard, as `npub1…` or hex. No default — unset admits nobody. | (empty) |
| `BOTTIN_ADMIN_EXTERNAL_URL` | The dashboard URL as the browser reaches it. Must match, or the sign-in proof is rejected. | `http://localhost:8081` |
| `BOTTIN_ADMIN_USER` | Username for `bottin-api`'s HTTP Basic credentials. **Not** used by the dashboard. | `admin` |
| `BOTTIN_ADMIN_PASSWORD` | Password for that user, on `bottin-api` only. **Not** used by the dashboard. | `changeme` |
| `BOTTIN_API_USER` | Username machine callers present to the API | `api` |
| `BOTTIN_API_PASSWORD` | Password for that user, on both `bottin-api` and `bottin-client` | `changeme-api` |
| `BOTTIN_TRUSTED_PROXIES` | Regex of proxy addresses allowed to set `X-Forwarded-For` | empty (trust none) |

The `api` user holds API access without admin rights, and its password is separate
from the admin one: `bottin-client` needs it to register onboarded handles, and
handing it out must not hand out the admin credential. Both services read
`BOTTIN_API_PASSWORD`, so the two must be set to the same value. Under the `prod`
profile `bottin-api` refuses to start unless `BOTTIN_ADMIN_PASSWORD` and
`BOTTIN_API_PASSWORD` are both set — unset, they fall back to a random password
that changes on every restart.

`BOTTIN_TRUSTED_PROXIES` governs which peer may state the caller's address. It is
empty by default, so `X-Forwarded-For` is ignored and the rate limiters key on the
connection itself — otherwise any caller could rotate the header and bypass them.
Set it to a regex matching your edge proxy (for example `10\.0\.0\.5`) when one
is in front of the API; until then every client behind that proxy shares a single
rate-limit bucket keyed on the proxy's address.

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

The admin dashboard authenticates by Nostr key rather than by password: an
administrator proves control of the private key matching `BOTTIN_ADMIN_NPUB`, and
that key never reaches the deployment. `BOTTIN_ADMIN_USER` and
`BOTTIN_ADMIN_PASSWORD` survive for `bottin-api`'s HTTP Basic credentials only,
and removing them would leave the API with a password that changes on every
restart. See [Configure Admin Access](../how-to/configure-admin-access.md).

### Not Configured Here

Four settings that were previously environment variables are now stored in the
database and edited in the admin UI at `/admin/settings`:

| Setting | Former variable |
|---------|-----------------|
| Media server (Blossom) URL | `BOTTIN_BLOSSOM_URL` |
| System relays | `BOTTIN_DEFAULT_RELAYS` |
| Profile discovery relays | (was hardcoded in the client) |
| API rate limit per minute | `bottin.ratelimit.requests-per-minute` |

They moved because they are operational data rather than bootstrap or
infrastructure configuration: changing one is an ordinary operator decision that
should not require editing this file and recreating containers. The two variables
above no longer have any effect and can be deleted from `.env`.

A deployment comes up with them unset by design. See
[Configure Deployment Settings](../how-to/configure-deployment-settings.md).

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

### bottin-api / bottin-admin

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

### Dockerfile.api

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

Alias for `Dockerfile.api`, maintained for backward compatibility.

## Dependencies

Service startup order is managed via `depends_on` with health checks:

```
postgres (healthy) -> bottin-api
postgres (healthy) -> bottin-admin
```

Both application services wait for PostgreSQL to be ready before starting.
