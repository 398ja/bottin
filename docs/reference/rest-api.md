# REST API Reference

Technical reference for the Bottin REST API endpoints. All API endpoints require authentication unless otherwise noted.

## Base URL

```
http://localhost:8080
```

## Authentication

API endpoints use HTTP Basic authentication. Provide credentials via the `Authorization` header:

```
Authorization: Basic base64(username:password)
```

## NIP-05 Resolution

### GET /.well-known/nostr.json

Resolves NIP-05 identities. **Public endpoint - no authentication required.**

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `name` | string | Yes | Username to resolve |

**Response:**

```json
{
  "names": {
    "alice": "pubkey-hex-string"
  },
  "relays": {
    "pubkey-hex-string": ["wss://relay.example.com"]
  }
}
```

**Example:**

```bash
curl "http://localhost:8080/.well-known/nostr.json?name=alice"
```

## NIP-05 Records

### GET /api/v1/records

List all NIP-05 records with pagination.

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | int | 0 | Page number |
| `size` | int | 20 | Page size |

**Response:** Paginated list of records.

### GET /api/v1/records/{id}

Get a specific record by ID.

### GET /api/v1/records/by-nip05/{nip05}

Get a record by NIP-05 identifier (e.g., `alice@example.com`).

### POST /api/v1/records

Create a new NIP-05 record.

**Request Body:**

```json
{
  "username": "alice",
  "domain": "example.com",
  "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
  "relays": ["wss://relay.example.com"]
}
```

### PUT /api/v1/records/{id}

Update an existing record.

**Request Body:**

```json
{
  "relays": ["wss://new-relay.example.com"],
  "enabled": true
}
```

### DELETE /api/v1/records/{id}

Delete a record.

### PATCH /api/v1/records/{id}/toggle

Toggle the enabled status of a record.

## Domains

### GET /api/v1/domains

List domains with pagination.

### GET /api/v1/domains/all

List all domains without pagination.

### GET /api/v1/domains/{id}

Get a domain by ID.

### GET /api/v1/domains/by-name/{name}

Get a domain by name.

### POST /api/v1/domains

Create a new domain.

**Request Body:**

```json
{
  "name": "example.com",
  "ownerPubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
}
```

### DELETE /api/v1/domains/{id}

Delete a domain.

### GET /api/v1/domains/verified

List all verified domains.

### GET /api/v1/domains/unverified

List all unverified domains.

### POST /api/v1/domains/{id}/verify

Initiate domain verification.

**Request Body:**

```json
{
  "method": "DNS_TXT"
}
```

### GET /api/v1/domains/{id}/verification

Get verification status for a domain.

### POST /api/v1/domains/{id}/verify/attempt

Attempt to verify a domain using the previously initiated method.

### POST /api/v1/domains/{id}/verify/{method}

Initiate and attempt verification with a specific method (`DNS_TXT` or `HTTP_FILE`).

## External Verification

### GET /api/v1/verify

Verify an external NIP-05 identifier.

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `nip05` | string | Yes | NIP-05 identifier to verify |

**Response:**

```json
{
  "nip05": "alice@example.com",
  "valid": true,
  "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
  "relays": ["wss://relay.example.com"]
}
```

## Client UI Endpoints

Served by the client UI (default `http://localhost:8081`), not by the API above.
Authentication is a NAP session cookie rather than HTTP Basic.

### POST /api/v1/register

Registers the handle chosen during onboarding. The client UI calls
`POST /api/v1/records` on the API above with its own credentials, so the browser
never holds them.

**Request Body:**

```json
{
  "username": "alice",
  "relays": ["wss://relay.example.com"]
}
```

The pubkey is taken from the caller's NAP session, not from the request, so a
handle can only be claimed for the key that signed in.

**Response:**

```json
{
  "status": "registered",
  "nip05": "alice@example.com"
}
```

**Errors:** `401 NAP_SESSION_REQUIRED`, `400 INVALID_USERNAME`,
`409 USERNAME_TAKEN`, `404 DOMAIN_NOT_FOUND` (the handle suffix has no domain
record yet), `502 DIRECTORY_UNAVAILABLE`.

## Error Responses

All errors follow this format:

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "errorCode": "VALIDATION_ERROR",
  "message": "Description of the error",
  "path": "/api/v1/records"
}
```

### HTTP Status Codes

| Code | Description |
|------|-------------|
| 200 | Success |
| 201 | Created |
| 204 | No Content (successful deletion) |
| 400 | Bad Request (validation error) |
| 401 | Unauthorized |
| 404 | Not Found |
| 409 | Conflict (duplicate record) |
| 500 | Internal Server Error |

## API Documentation

When enabled, interactive API documentation is available:

- **OpenAPI Spec**: http://localhost:8080/v3/api-docs
- **Swagger UI**: http://localhost:8080/swagger-ui.html

Enable with environment variables:
```bash
BOTTIN_API_DOCS_ENABLED=true
BOTTIN_SWAGGER_ENABLED=true
```
