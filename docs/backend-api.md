# Backend API

Base URL: `http://localhost:8080`. All REST endpoints except health and authentication require `Authorization: Bearer <token>`. WebSocket clients send the bearer token in the STOMP `CONNECT` frame; see [WebSocket events](websocket-events.md).

For browser clients hosted on a separate origin, configure `FRONTEND_ALLOWED_ORIGINS` as a comma-separated list of exact origins. The API allows the required JSON and bearer headers without cookie credentials.

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

- `GET /api/dashboard/summary` returns agent/device counts, 24-hour result availability, and 20 recent results. `availabilityPercent24h` is `null` until at least one check has been recorded in the last 24 hours.
- `POST /api/alerts` (ADMIN/OPERATOR) accepts deduplicationKey, type, severity, message, optional deviceId/agentId. Repeated unresolved keys return the existing alert.
- `GET /api/alerts?status=UNREAD&severity=CRITICAL&page=0&size=50`, `PATCH /api/alerts/{id}/read`, `PATCH /api/alerts/{id}/resolve` (page size max 100). Severity values are `INFO`, `WARNING`, and `CRITICAL`.

Invalid inputs return HTTP 400, unknown resources HTTP 404, and unauthenticated requests HTTP 401. Authenticated users without a required role receive HTTP 403.
# Network discovery

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/discovery/agents/{agentId}/scan` | ADMIN, OPERATOR | Queue a scan for an online agent (202 Accepted). |
| GET | `/api/discovery/agents/{agentId}/scan` | Authenticated | Return the latest scan or 204 when none exists. |
| GET | `/api/discovery/agents/{agentId}/scans` | Authenticated | Return the latest 20 scans. |
| GET | `/api/discovery/agents/{agentId}/devices` | Authenticated | Return registered devices for scan results. |

Only one queued/running scan is allowed for an agent. Agents poll their command through `discovery.poll` on the agent TCP protocol; the dashboard cannot scan a subnet from the browser.

Additional multi-network endpoints:

- `GET /api/discovery/networks` and `GET /api/discovery/agents/{agentId}/networks` return persisted interface network details and scan eligibility.
- `POST /api/discovery/agents/{agentId}/networks/{networkId}/scan` queues one network; `POST /api/discovery/scan` accepts `{ "networkIds": ["UUID", ...] }` to queue multiple authorized networks.
- `GET /api/discovery/scans/{scanId}` and `/results` return per-network scan state and registered devices.
- `PUT /api/agents/{agentId}/authorized-cidrs` (ADMIN) accepts `{ "cidrs": ["10.0.0.0/24"] }`. Agent TCP registration cannot expand this server-managed allowlist.
