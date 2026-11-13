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

Example acknowledgement:

```json
{"type":"agent.heartbeat.accepted","agentId":"1e36fb24-0a57-4cdf-87b0-90f3ba3e9f17","status":"ONLINE","message":null}
```

## Submit a monitoring result

```json
{"type":"monitoring.result","agentId":"1e36fb24-0a57-4cdf-87b0-90f3ba3e9f17","deviceId":"b861d57f-42e0-4834-808a-3bd51a56293a","checkType":"ICMP","responseTimeMs":12,"available":true,"packetLossPercent":0.0,"checkedAt":"2026-11-12T12:00:00Z"}
```

Optional fields include `portNumber`, `portOpen`, `snmpData`, and `errorMessage`. The agent uses `checkType` values `ICMP`, `TCP`, and `SNMP`; `snmpData` is a JSON-encoded string containing the returned OID/value pairs. The device must already exist and be assigned to the sending agent. Acknowledgement includes the stored `resultId`:

```json
{"type":"monitoring.result.accepted","agentId":"1e36fb24-0a57-4cdf-87b0-90f3ba3e9f17","status":"ONLINE","message":null,"resultId":"b861d57f-42e0-4834-808a-3bd51a56293a"}
```

Device and monitoring WebSocket updates are emitted after processing. Delivery is at-least-once: if storage succeeds and the acknowledgement is lost, the agent retries and the backend currently has no idempotency key, so duplicate results are possible.

## Device discovery and security boundaries

The TCP protocol does not register discovered devices. The agent discovers reachable IPs and reverse-DNS hostnames locally, but monitoring results can only be sent for device UUIDs that already exist and are assigned to that agent. Automatic discovery-to-dashboard integration needs a coordinated backend message or endpoint that creates/assigns a device and returns its UUID. The current result DTO also does not expose local MAC/interface identity or normalized SNMP interface metrics.

TLS and agent authentication are not implemented by the backend. The current listener defaults to loopback (`127.0.0.1:9001`); the agent must not connect across an untrusted network until both sides implement and verify the same TLS/authentication contract.

## REST endpoints

- `POST /api/agents/register` registers or refreshes an agent. It uses the same fields as the TCP registration message.
- `GET /api/agents` lists registered agents.
- `GET /api/agents/{agentId}` gets one agent by UUID.

REST endpoints use JWT bearer authentication. Obtain a viewer token using `POST /api/auth/register` or `POST /api/auth/login`; `GET /api/health` is public. The TCP protocol has no TLS or agent credential handshake yet and is bound to loopback by default; don't expose it to an untrusted network.
