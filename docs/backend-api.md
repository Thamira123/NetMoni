# Backend API

Base URL: `http://localhost:8080`. All endpoints except health and authentication require `Authorization: Bearer <token>`.

## Authentication

- `POST /api/auth/register` accepts `{ "username": "person@example.com", "password": "at-least-10-characters" }`. Public registration always assigns `VIEWER`.
- `POST /api/auth/login` accepts the same fields and returns `accessToken`, `tokenType`, `expiresAt`, username, and role.

## Agents

- `POST /api/agents/register` (ADMIN/OPERATOR): `{ "agentId": "UUID", "name": "segment-a", "segmentCidr": "192.168.1.0/24", "version": "1.0.0" }`
- `GET /api/agents?page=0&size=50`, `GET /api/agents/{agentId}` (authenticated; list size max 100)

## Devices

- `POST /api/devices` (ADMIN/OPERATOR) and `PUT /api/devices/{id}` accept name, ipAddress, optional hostname, deviceType, optional location, and agentId.
- `GET /api/devices?page=0&size=50`, `GET /api/devices/{id}`, `GET /api/devices/by-agent/{agentId}` (main list size max 100)
- `PATCH /api/devices/{id}/status?status=ONLINE|OFFLINE|UNKNOWN` (ADMIN/OPERATOR)
- `DELETE /api/devices/{id}` (ADMIN)

## Monitoring results

- `POST /api/monitoring-results` (ADMIN/OPERATOR) accepts deviceId, agentId, checkType, available, optional responseTimeMs, packetLossPercent, portNumber, portOpen, snmpData, checkedAt, and errorMessage.
- `GET /api/monitoring-results/device/{deviceId}?from=<ISO-8601>&to=<ISO-8601>`
- `GET /api/monitoring-results/agent/{agentId}?from=<ISO-8601>&to=<ISO-8601>`
- `GET /api/monitoring-results/recent?limit=50` (1–500)
- `GET /api/monitoring-results/device/{deviceId}/availability?from=<ISO-8601>&to=<ISO-8601>`

## Dashboard and alerts

- `GET /api/dashboard/summary` returns agent/device counts, 24-hour result availability, and 20 recent results.
- `POST /api/alerts` (ADMIN/OPERATOR) accepts deduplicationKey, type, severity, message, optional deviceId/agentId. Repeated unresolved keys return the existing alert.
- `GET /api/alerts?status=UNREAD&severity=CRITICAL&page=0&size=50`, `PATCH /api/alerts/{id}/read`, `PATCH /api/alerts/{id}/resolve` (page size max 100).

Invalid inputs return HTTP 400, unknown resources HTTP 404, and unauthenticated requests HTTP 401. Authenticated users without a required role receive HTTP 403.
