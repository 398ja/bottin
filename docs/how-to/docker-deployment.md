# Deploy with Docker

This guide shows you how to deploy Bottin services using Docker Compose. By the end, you will have the REST API and Admin Dashboard running with a PostgreSQL database.

## Prerequisites

- Docker Engine 20.10+
- Docker Compose v2.0+

## Deploy All Services

Run all services (REST API, Admin UI, and PostgreSQL):

```bash
docker-compose up -d
```

This starts:
- **bottin-web** on port 8080 - REST API for NIP-05 resolution
- **bottin-admin** on port 8081 - Admin Dashboard
- **postgres** - PostgreSQL database (internal)

## Deploy Individual Services

### REST API Only

Deploy just the REST API for NIP-05 resolution:

```bash
docker-compose up -d bottin-web postgres
```

### Admin Dashboard Only

Deploy just the Admin Dashboard for management:

```bash
docker-compose up -d bottin-admin postgres
```

## Configure Environment Variables

Create a `.env` file in the project root to customize the deployment:

```bash
# Database
BOTTIN_DATABASE_PASSWORD=your-secure-password

# Admin credentials
BOTTIN_ADMIN_USER=admin
BOTTIN_ADMIN_PASSWORD=your-admin-password

# Ports (optional)
BOTTIN_PORT=8080
BOTTIN_ADMIN_PORT=8081

# Domain configuration
BOTTIN_DEFAULT_DOMAIN=example.com

# API documentation (optional, disabled by default in production)
BOTTIN_API_DOCS_ENABLED=false
BOTTIN_SWAGGER_ENABLED=false
```

## Verify Deployment

Check service health:

```bash
# Check all services
docker-compose ps

# Check REST API health
curl http://localhost:8080/actuator/health

# Check Admin UI health
curl http://localhost:8081/actuator/health
```

## Access Services

- **REST API**: http://localhost:8080
- **Admin Dashboard**: http://localhost:8081/admin/login
- **API Documentation** (if enabled): http://localhost:8080/swagger-ui.html

## Stop Services

```bash
# Stop all services
docker-compose down

# Stop and remove volumes (clears database)
docker-compose down -v
```

## Build Images with Jib

Bottin uses [Google Jib](https://github.com/GoogleContainerTools/jib) for building optimized Docker images without requiring a Docker daemon.

### Build to Local Docker Daemon

```bash
# Build both services to local Docker
mvn -Pdocker jib:dockerBuild -pl bottin-web,bottin-admin-ui

# Build a single service
mvn -Pdocker jib:dockerBuild -pl bottin-web
```

### Push to Private Registry

Push images to `docker.398ja.xyz`:

```bash
# Login to registry first
docker login docker.398ja.xyz

# Build and push both services
mvn -Pdocker jib:build -pl bottin-web,bottin-admin-ui

# Build and push a single service
mvn -Pdocker jib:build -pl bottin-web
```

### Image Tags

Images are tagged with both the version and `latest`:
- `docker.398ja.xyz/bottin-web:0.1.0-SNAPSHOT`
- `docker.398ja.xyz/bottin-web:latest`
- `docker.398ja.xyz/bottin-admin-ui:0.1.0-SNAPSHOT`
- `docker.398ja.xyz/bottin-admin-ui:latest`

### Container Configuration

Default container settings:
- **Base image**: `eclipse-temurin:21-jre-alpine`
- **JVM**: G1GC with container support, 256MB-512MB heap
- **Port**: 8080
- **Spring profile**: `docker`

## Build Images with Docker Compose

Alternatively, rebuild images using Docker Compose:

```bash
# Rebuild all images
docker-compose build

# Rebuild specific service
docker-compose build bottin-web
docker-compose build bottin-admin
```

## View Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f bottin-web
docker-compose logs -f bottin-admin
```

## Next Steps

- See [Docker Compose Configuration](../reference/docker-compose-configuration.md) for all available environment variables
- See [REST API Reference](../reference/rest-api.md) for API endpoints
