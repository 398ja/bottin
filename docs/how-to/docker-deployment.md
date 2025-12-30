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

## Build Images Locally

To rebuild Docker images after code changes:

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
