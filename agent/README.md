# NetMoni Java Agent

Java 21 agent for authorized IPv4 subnet discovery and ICMP, TCP-port, and SNMP v1/v2c/v3 monitoring. It sends the backend's newline-delimited JSON protocol over TCP. It does not change the backend or frontend.

## Backend contract and limitations

The agent follows the `backend` branch's `docs/agent-protocol.md`:

- TCP, UTF-8, one JSON object per line, backend default `127.0.0.1:9001`.
- Registration: `agent.register` with `agentId`, `name`, `segmentCidr`, and `version`; expects `agent.registered`.
- Heartbeat: `agent.heartbeat` with `agentId`; expects `agent.heartbeat.accepted`.
- Results: `monitoring.result` with agent/device IDs, check type, availability, timestamp, and applicable check fields; expects `monitoring.result.accepted`.
- Result delivery is at-least-once: if the backend stores a result but the acknowledgement is lost, the agent retries it. The current backend protocol has no idempotency key.
- The current backend is plain TCP (no TLS or agent credentials); its documented listener binds to loopback. Do not expose it to an untrusted network. TLS must be implemented on both sides before enabling it.
- The backend requires a device UUID already registered and assigned to this agent. Device creation/assignment is not available on the TCP protocol. Configure each monitored IP and its backend device UUID in `agent.device-ids`.
- Discovery reports reachable IP addresses and reverse-DNS hostnames locally. The backend protocol has no device-discovery message, so discovered devices are not automatically registered, assigned, or shown in the frontend. No MAC address or device-type inference is sent.
- The backend monitoring DTO stores the supported ICMP/TCP fields and an opaque `snmpData` string. The dashboard/backend do not currently expose interface metrics or a normalized SNMP metric model.
- The frontend only receives data from the backend; the agent does not implement frontend or WebSocket behavior.

## Configure

Copy `config/agent.properties.example` to `config/agent.properties` and edit the values. Keep this file and the data directory private; do not commit SNMP community strings.

```properties
agent.name=segment-a
agent.network-interface=
agent.segment-cidr=192.168.1.0/24
agent.backend.host=127.0.0.1
agent.backend.port=9001
agent.device-ids=192.168.1.10=00000000-0000-0000-0000-000000000001
agent.tcp.ports=22,80,443
agent.snmp.enabled=false
agent.snmp.version=2c
agent.snmp.community=
# For SNMPv3 authPriv instead: version=3, with a security name and private passphrases.
agent.snmp.security-name=
agent.snmp.auth-protocol=SHA256
agent.snmp.auth-passphrase=
agent.snmp.privacy-protocol=AES128
agent.snmp.privacy-passphrase=
```

`agent.device-ids` is a comma-separated `IPv4=UUID` list. Each IP is checked against `agent.segment-cidr` before monitoring; the configured CIDR must be an authorized network you administer. Discovery is limited to 4096 usable IPv4 addresses per scan. Network/broadcast addresses are excluded for prefixes `/0` through `/30`.

The agent generates a UUID once and persists it in `agent.data-directory/agent-id`; keep the data directory between restarts. Each property can be overridden with its corresponding `NETMONI_...` environment variable (for example, `NETMONI_AGENT_ID`, `NETMONI_AGENT_NETWORK_INTERFACE`, `NETMONI_BACKEND_HOST`, `NETMONI_MONITORING_INTERVAL_SECONDS`, `NETMONI_DEVICE_IDS`, and `NETMONI_SNMP_AUTH_PASSPHRASE`). Credentials are redacted from the configuration object's string representation and are never intentionally logged. The config path can also be given as the first command-line argument.

`agent.local-ip` and `agent.network-interface` are optional selectors. The chosen IPv4 address must be assigned to an active local interface and belong to `agent.segment-cidr`. This also ensures the agent cannot be configured to scan a subnet that is not locally attached. Leave the interface blank to select the first matching interface.

## Run

Prerequisites: Java 21 and Maven.

```powershell
cd agent
mvn test
mvn package
java -jar target/netmoni-agent-1.0.0.jar config/agent.properties
```

The package phase creates an executable shaded JAR with its runtime dependencies.

## Wire format

Messages are UTF-8 JSON lines. A registration request and response look like:

```json
{"type":"agent.register","agentId":"1e36fb24-0a57-4cdf-87b0-90f3ba3e9f17","name":"segment-a","segmentCidr":"192.168.1.0/24","version":"1.0.0"}
{"type":"agent.registered","agentId":"1e36fb24-0a57-4cdf-87b0-90f3ba3e9f17","status":"ONLINE","message":null}
```

Heartbeat uses `agent.heartbeat` and expects `agent.heartbeat.accepted`. Results use `monitoring.result` with `agentId`, a pre-registered `deviceId`, `checkType`, `available`, and `checkedAt`; optional properties are `responseTimeMs`, `packetLossPercent`, `portNumber`, `portOpen`, `snmpData`, and `errorMessage`. The server responds with `monitoring.result.accepted` and a `resultId`. These message names and fields are defined by [the shared protocol](../docs/agent-protocol.md).

## Behavior and troubleshooting

- Discovery uses Java `InetAddress.isReachable`; availability depends on OS permissions and network policy, and some platforms may use a TCP fallback rather than ICMP. No response is treated as unreachable, not as proof the host is offline.
- TCP checks only connect to the configured ports; no service exploitation or arbitrary port-range scan is performed.
- SNMP queries `sysDescr`, `sysUpTime`, and `sysName`; interface MIBs are not collected yet. v1/v2c use a community string. v3 uses authPriv with SHA or SHA-256 authentication and AES-128 privacy; configure a security name and passphrases of at least eight characters. Keep all SNMP credentials private.
- Monitoring is concurrent with a fixed worker pool. Registration and heartbeat retry after failures. Results are saved in `data/outbox.jsonl` until acknowledged; the bounded queue fails visibly instead of dropping older results when full.
- If the agent cannot start, verify Java/Maven versions, the properties file, valid CIDR and device UUIDs, backend reachability, and that the backend TCP listener is enabled. Check that configured device IDs exist and belong to this agent; otherwise the backend will reject submissions.
- Unit tests use local-only fixtures. They do not prove ICMP behavior on a specific OS, SNMP access to a physical device, or compatibility with a running backend.

## Checks

```powershell
mvn test
```

Network and backend integration checks are not part of the default test suite.
