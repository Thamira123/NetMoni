# Agent TCP protocol

This is the TCP contract implemented by the Java agent and Spring Boot server. Messages use the exact field names below.

## Connection

- Host: `127.0.0.1` during local development
- Port: `9001`
- Encoding: UTF-8
- Framing: one JSON object per line; each message must end with a newline
- TLS: not enabled yet; the listener is bound to loopback only

## Register an agent

The agent generates a UUID once and keeps using it after restarts.

```json
{"type":"agent.register","agentId":"1e36fb24-0a57-4cdf-87b0-90f3ba3e9f17","name":"segment-a","segmentCidr":"192.168.1.0/24","version":"1.0.0"}
```

The server creates or updates that agent and replies:

```json
{"type":"agent.registered","agentId":"1e36fb24-0a57-4cdf-87b0-90f3ba3e9f17","status":"ONLINE","message":null}
```

## Send a heartbeat

```json
{"type":"agent.heartbeat","agentId":"1e36fb24-0a57-4cdf-87b0-90f3ba3e9f17"}
```

The server replies with `type: "agent.heartbeat.accepted"` and the current status. A heartbeat for an unknown ID returns `agent_not_registered`; register first. If no heartbeat is received within the configured `AGENT_HEARTBEAT_TIMEOUT_SECONDS` (default 90), the server marks the agent offline and emits a WebSocket event.

## Register discovered devices

After registration and after each subnet scan, the agent sends the reachable IPv4 devices in one batch:

```json
{"type":"device.discovered","agentId":"1e36fb24-0a57-4cdf-87b0-90f3ba3e9f17","devices":[{"ipAddress":"192.168.1.20","hostname":"printer-20","discoveredAt":"2026-11-13T12:00:00Z"}]}
```

The server accepts only addresses inside the CIDR registered by that agent. It creates or updates one device per `(agentId, ipAddress)` and replies with the stable device IDs:

```json
{"type":"device.discovery.accepted","agentId":"1e36fb24-0a57-4cdf-87b0-90f3ba3e9f17","status":"ONLINE","message":null,"resultId":null,"devices":[{"ipAddress":"192.168.1.20","deviceId":"b861d57f-42e0-4834-808a-3bd51a56293a"}]}
```

Repeated scans are idempotent. New devices are stored in PostgreSQL with status `UNKNOWN` until their first monitoring result changes them to `ONLINE` or `OFFLINE`. The server emits a device WebSocket event so the dashboard refreshes immediately. The agent uses the returned IDs for subsequent monitoring messages; `agent.device-ids` is optional and only seeds mappings for existing devices.

Example acknowledgement:

```json
{"type":"agent.heartbeat.accepted","agentId":"1e36fb24-0a57-4cdf-87b0-90f3ba3e9f17","status":"ONLINE","message":null}
```

## Submit a monitoring result

```json
{"type":"monitoring.result","agentId":"1e36fb24-0a57-4cdf-87b0-90f3ba3e9f17","deviceId":"b861d57f-42e0-4834-808a-3bd51a56293a","checkType":"ICMP","responseTimeMs":12,"available":true,"packetLossPercent":0.0,"checkedAt":"2026-11-12T12:00:00Z"}
```

Optional fields include `portNumber`, `portOpen`, `snmpData`, and `errorMessage`. The agent uses `checkType` values `ICMP`, `TCP`, and `SNMP`; `snmpData` is a JSON-encoded string containing the returned OID/value pairs. The device ID comes from the discovery acknowledgement and must be assigned to the sending agent. Acknowledgement includes the stored `resultId`:

```json
{"type":"monitoring.result.accepted","agentId":"1e36fb24-0a57-4cdf-87b0-90f3ba3e9f17","status":"ONLINE","message":null,"resultId":"b861d57f-42e0-4834-808a-3bd51a56293a"}
```

Device and monitoring WebSocket updates are emitted after processing. Delivery is at-least-once: if storage succeeds and the acknowledgement is lost, the agent retries and the backend currently has no idempotency key, so duplicate results are possible.

## Device discovery and security boundaries

The agent discovers reachable IPs and reverse-DNS hostnames and registers them with `device.discovered`. The server validates the addresses against the agent's registered subnet, stores devices idempotently, and returns the UUIDs used for monitoring results. The current result DTO does not expose local MAC/interface identity or normalized SNMP interface metrics.

## Dashboard requested scans

The authenticated dashboard starts a scan with `POST /api/discovery/agents/{agentId}/scan`. The backend persists a `QUEUED` scan and rejects requests for offline agents or agents with an active scan. Agents poll the existing outbound TCP protocol with `discovery.poll`; the backend returns `discovery.command` with a scan ID until the agent acknowledges it with `discovery.started`. Agents report `discovery.progress`, register reachable devices using `device.discovered` with that scan ID, then send `discovery.completed` or `discovery.failed`. The backend persists state and publishes `discovery.started`, `discovery.progress`, `discovery.completed`, and `discovery.failed` on `/topic/discovery`. This command polling works with the current one-request-per-socket agent transport and does not require backend-initiated connections.

The dashboard reads the latest scan at `GET /api/discovery/agents/{agentId}/scan`, history at `/scans`, and registered results at `/devices`. Scan starts require ADMIN or OPERATOR; reads require authentication. Scan devices are validated against the agent's administrator-configured IPv4 allowlist and the existing duplicate-safe device registration path.

## Multiple authorized interface networks

Agents may configure `agent.allowed-cidrs` as a comma-separated local allowlist. If omitted, only `agent.segment-cidr` is authorized locally. The agent reports IPv4 interface networks and metadata (IP, CIDR, mask, broadcast, MAC, IPv6, and best-effort default gateway); loopback is excluded. A report that omits a previously known interface marks that network unavailable so it cannot be scanned. Only active interface CIDRs contained by the backend's administrator-managed allowlist and small enough for the configured bounded scan are eligible. Agent-originated registration cannot expand an existing backend allowlist. Administrators must configure the CIDR locally and authorize the same range in NetMoni using `PUT /api/agents/{agentId}/authorized-cidrs` with `{ "cidrs": ["10.0.0.0/24"] }`; eligibility updates on the next agent report.

`POST /api/discovery/scan` accepts `{ "networkIds": ["..."] }` and queues one durable scan per selected eligible interface network. Per-network starts are available at `POST /api/discovery/agents/{agentId}/networks/{networkId}/scan`. Each queued command contains that network's CIDR; before scanning, the agent verifies the CIDR still belongs to an active local interface and its local allowlist. Devices carry the interface network metadata; MAC lookup is best-effort from the local operating-system neighbor cache and remains absent when unavailable.

Device monitoring can be paused or resumed with `PATCH /api/devices/{deviceId}/monitoring` and `{ "enabled": false|true }`. Agents poll `discovery.poll` and receive a `monitoringTargets` snapshot; paused devices are removed from the agent's active monitoring set and enabled devices are added. Polling retains the current queued scan command in the same response.

TLS and agent authentication are not implemented by the backend. The current listener defaults to loopback (`127.0.0.1:9001`); the agent must not connect across an untrusted network until both sides implement and verify the same TLS/authentication contract.

## REST endpoints

- `POST /api/agents/register` registers or refreshes an agent. It uses the same fields as the TCP registration message.
- `GET /api/agents` lists registered agents.
- `GET /api/agents/{agentId}` gets one agent by UUID.

REST endpoints use JWT bearer authentication. Obtain a viewer token using `POST /api/auth/register` or `POST /api/auth/login`; `GET /api/health` is public. The TCP protocol has no TLS or agent credential handshake yet and is bound to loopback by default; don't expose it to an untrusted network.
